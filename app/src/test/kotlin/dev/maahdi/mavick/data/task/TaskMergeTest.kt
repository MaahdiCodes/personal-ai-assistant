package dev.maahdi.mavick.data.task

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.testing.TEST_NOW
import dev.maahdi.mavick.time.RepeatRule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random
import org.junit.Test

class TaskMergeTest {
    private fun task(id: String = "t1", title: String = "Call", updated: Long = 100, configure: (TaskEntity) -> TaskEntity = { it }) = configure(
        TaskEntity(id = id, title = title, createdAt = TEST_NOW, updatedAt = Instant.ofEpochMilli(updated)),
    )

    private fun deleted(id: String = "t1", at: Long = 100) =
        task(id, updated = at) { it.copy(deletedAt = Instant.ofEpochMilli(at)) }

    /** The tasks a phone has after the plan is carried out. */
    private fun applied(local: List<TaskEntity>, plan: MergePlan): Map<String, TaskEntity> =
        local.associateBy { it.id } + plan.toWrite.associateBy { it.id }

    // --- Which version wins ---

    @Test
    fun `the version edited last wins`() {
        val older = task(title = "Old", updated = 100)
        val newer = task(title = "New", updated = 200)

        assertThat(TaskMerge.winner(older, newer)).isEqualTo(newer)
        assertThat(TaskMerge.winner(newer, older)).isEqualTo(newer)
    }

    @Test
    fun `at the same moment a deletion beats an edit`() {
        val edited = task(title = "Edited", updated = 100)
        val gone = deleted(at = 100)

        assertThat(TaskMerge.winner(edited, gone)).isEqualTo(gone)
        assertThat(TaskMerge.winner(gone, edited)).isEqualTo(gone)
    }

    @Test
    fun `at the same moment with the same state the choice does not depend on the order`() {
        val a = task(title = "Apples", updated = 100)
        val b = task(title = "Bananas", updated = 100)

        assertThat(TaskMerge.winner(a, b)).isEqualTo(TaskMerge.winner(b, a))
    }

    @Test
    fun `identical versions give that version`() {
        val a = task()

        assertThat(TaskMerge.winner(a, a.copy())).isEqualTo(a)
    }

    @Test
    fun `putting a deleted task back later wins over the deletion`() {
        val gone = deleted(at = 100)
        val undone = task(updated = 200)

        assertThat(TaskMerge.winner(gone, undone)).isEqualTo(undone)
    }

    @Test
    fun `whichever side is local, the same version wins, for many random pairs`() {
        val random = Random(2026)
        repeat(500) {
            val a = randomTask(random)
            val b = randomTask(random)

            assertThat(TaskMerge.winner(a, b)).isEqualTo(TaskMerge.winner(b, a))
        }
    }

    private fun randomTask(random: Random): TaskEntity {
        val updated = random.nextLong(0, 4) // few values, so ties are common
        return task(title = listOf("a", "b", "c")[random.nextInt(3)], updated = updated) {
            it.copy(
                notes = listOf(null, "n")[random.nextInt(2)],
                dueDate = listOf(null, LocalDate.of(2026, 10, 8))[random.nextInt(2)],
                dueTime = listOf(null, LocalTime.of(9, 0))[random.nextInt(2)],
                status = TaskStatus.entries[random.nextInt(TaskStatus.entries.size)],
                deletedAt = if (random.nextBoolean()) Instant.ofEpochMilli(updated) else null,
                repeatRule = listOf(null, RepeatRule.Daily())[random.nextInt(2)],
            )
        }
    }

    // --- Plans ---

    @Test
    fun `a task this phone doesn't have is added`() {
        val plan = TaskMerge.plan(local = emptyList(), incoming = listOf(task("a")))

        assertThat(plan.toWrite.map { it.id }).containsExactly("a")
        assertThat(plan.added).isEqualTo(1)
        assertThat(plan.updated + plan.keptNewer + plan.unchanged).isEqualTo(0)
    }

    @Test
    fun `a deleted task this phone didn't have is kept apart from the ones people see`() {
        val plan = TaskMerge.plan(local = emptyList(), incoming = listOf(task("a"), deleted("b")))

        assertThat(plan.toWrite.map { it.id }).containsExactly("a", "b")
        assertThat(plan.added).isEqualTo(1)
        assertThat(plan.addedDeleted).isEqualTo(1)
    }

    @Test
    fun `an older copy of a task here is replaced by the newer one`() {
        val mine = task("a", "Old", updated = 100)
        val theirs = task("a", "New", updated = 200)

        val plan = TaskMerge.plan(listOf(mine), listOf(theirs))

        assertThat(plan.toWrite).containsExactly(theirs)
        assertThat(plan.updated).isEqualTo(1)
    }

    @Test
    fun `a newer copy here is kept`() {
        val mine = task("a", "New", updated = 200)
        val theirs = task("a", "Old", updated = 100)

        val plan = TaskMerge.plan(listOf(mine), listOf(theirs))

        assertThat(plan.toWrite).isEmpty()
        assertThat(plan.keptNewer).isEqualTo(1)
    }

    @Test
    fun `identical tasks change nothing`() {
        val plan = TaskMerge.plan(listOf(task("a")), listOf(task("a")))

        assertThat(plan.toWrite).isEmpty()
        assertThat(plan.unchanged).isEqualTo(1)
    }

    @Test
    fun `a deletion made later replaces the task here`() {
        val plan = TaskMerge.plan(listOf(task("a", updated = 100)), listOf(deleted("a", at = 200)))

        assertThat(plan.toWrite.single().deletedAt).isNotNull()
        assertThat(plan.updated).isEqualTo(1)
    }

    @Test
    fun `an older deletion does not remove a task edited since`() {
        val plan = TaskMerge.plan(listOf(task("a", updated = 300)), listOf(deleted("a", at = 200)))

        assertThat(plan.toWrite).isEmpty()
        assertThat(plan.keptNewer).isEqualTo(1)
    }

    @Test
    fun `the same task twice in the incoming list counts once, with its newest copy`() {
        val plan = TaskMerge.plan(emptyList(), listOf(task("a", "First", 100), task("a", "Second", 200)))

        assertThat(plan.toWrite.map { it.title }).containsExactly("Second")
        assertThat(plan.added).isEqualTo(1)
    }

    @Test
    fun `merging nothing, or into nothing, is fine`() {
        assertThat(TaskMerge.plan(emptyList(), emptyList()).toWrite).isEmpty()
        assertThat(TaskMerge.plan(listOf(task("a")), emptyList()).toWrite).isEmpty()
    }

    // --- Two phones ---

    @Test
    fun `merging twice changes nothing the second time`() {
        val here = listOf(task("a", "Mine", 100), task("b", updated = 50))
        val there = listOf(task("a", "Theirs", 200), task("c", updated = 10))
        val once = applied(here, TaskMerge.plan(here, there)).values.toList()

        val again = TaskMerge.plan(once, there)

        assertThat(again.toWrite).isEmpty()
    }

    @Test
    fun `two phones merging into each other end with the same tasks, for many random lists`() {
        val random = Random(7)
        repeat(200) {
            val ids = listOf("a", "b", "c", "d", "e")
            val phoneA = ids.filter { random.nextInt(3) > 0 }.map { randomTask(random).copy(id = it) }
            val phoneB = ids.filter { random.nextInt(3) > 0 }.map { randomTask(random).copy(id = it) }

            val aAfter = applied(phoneA, TaskMerge.plan(phoneA, phoneB))
            val bAfter = applied(phoneB, TaskMerge.plan(phoneB, phoneA))

            assertThat(aAfter).isEqualTo(bAfter)
        }
    }

    @Test
    fun `both phones changing the same task offline keeps the later change and loses neither deletion nor edit order`() {
        val original = task("a", "Call the bank", updated = 100)
        val phoneA = original.copy(title = "Call the bank at 5", updatedAt = Instant.ofEpochMilli(200))
        val phoneB = original.copy(title = "Call the bank at 6", updatedAt = Instant.ofEpochMilli(300))

        val aAfter = applied(listOf(phoneA), TaskMerge.plan(listOf(phoneA), listOf(phoneB)))
        val bAfter = applied(listOf(phoneB), TaskMerge.plan(listOf(phoneB), listOf(phoneA)))

        assertThat(aAfter["a"]!!.title).isEqualTo("Call the bank at 6")
        assertThat(bAfter["a"]!!.title).isEqualTo("Call the bank at 6")
    }
}

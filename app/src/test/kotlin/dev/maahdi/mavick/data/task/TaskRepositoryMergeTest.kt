package dev.maahdi.mavick.data.task

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.MavickDatabase
import dev.maahdi.mavick.testing.FakeReminderScheduler
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.MutableClock
import dev.maahdi.mavick.testing.TEST_ZONE
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Merging another list of tasks in: what is stored, which alarms follow, and who is told. */
@RunWith(AndroidJUnit4::class)
class TaskRepositoryMergeTest {
    private lateinit var database: MavickDatabase
    private val scheduler = FakeReminderScheduler()
    private val clock = MutableClock(MONDAY_10AM)
    private val told = mutableListOf<String>()
    private lateinit var repository: TaskRepository

    private val listener = object : TaskChangeListener {
        override suspend fun taskChanged(taskId: String) {
            told += taskId
        }
    }

    private fun at(minutes: Long): Instant = MONDAY_10AM.atZone(TEST_ZONE).toInstant().plusSeconds(minutes * 60)

    private fun task(id: String, title: String = "Call", updatedMinutes: Long = 0, configure: (TaskEntity) -> TaskEntity = { it }) = configure(
        TaskEntity(id = id, title = title, createdAt = at(-1000), updatedAt = at(updatedMinutes)),
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MavickDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TaskRepository(database.taskDao(), scheduler, clock = { clock }, listeners = listOf(listener))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `tasks this phone didn't have are added exactly as they were`() = runTest {
        val incoming = task("a", "Pay rent", updatedMinutes = -50) { it.copy(notes = "Blue folder", priority = TaskPriority.HIGH) }

        val plan = repository.merge(listOf(incoming))

        assertThat(plan.added).isEqualTo(1)
        // Its own edit time is kept: restoring must not make an old task look edited just now.
        assertThat(database.taskDao().findById("a")).isEqualTo(incoming)
    }

    @Test
    fun `an older copy here is replaced and a newer one kept`() = runTest {
        database.taskDao().insert(task("old", "Mine, old", updatedMinutes = -100))
        database.taskDao().insert(task("new", "Mine, new", updatedMinutes = -10))

        val plan = repository.merge(listOf(task("old", "Theirs, newer", -50), task("new", "Theirs, older", -50)))

        assertThat(database.taskDao().findById("old")!!.title).isEqualTo("Theirs, newer")
        assertThat(database.taskDao().findById("new")!!.title).isEqualTo("Mine, new")
        assertThat(plan.updated).isEqualTo(1)
        assertThat(plan.keptNewer).isEqualTo(1)
    }

    @Test
    fun `merging the same list again changes and tells nobody`() = runTest {
        val incoming = listOf(task("a", updatedMinutes = -5), task("b", updatedMinutes = -5))
        repository.merge(incoming)
        told.clear()

        val again = repository.merge(incoming)

        assertThat(again.toWrite).isEmpty()
        assertThat(again.unchanged).isEqualTo(2)
        assertThat(told).isEmpty()
    }

    // --- Reminders ---

    @Test
    fun `a reminder still ahead gets its alarm`() = runTest {
        val ahead = LocalDateTime.of(2026, 10, 5, 17, 0)

        repository.merge(listOf(task("a") { it.copy(remindAt = ahead) }))

        assertThat(scheduler.alarms).containsExactly("a", ahead)
        assertThat(database.taskDao().findById("a")!!.remindAt).isEqualTo(ahead)
    }

    @Test
    fun `a reminder already past is not replayed`() = runTest {
        repository.merge(listOf(task("a") { it.copy(remindAt = LocalDateTime.of(2026, 10, 5, 8, 0)) }))

        assertThat(scheduler.alarms).isEmpty()
        assertThat(database.taskDao().findById("a")!!.remindAt).isNull()
    }

    @Test
    fun `a task deleted in the backup loses its alarm here`() = runTest {
        val ahead = LocalDateTime.of(2026, 10, 5, 17, 0)
        database.taskDao().insert(task("a", updatedMinutes = -100) { it.copy(remindAt = ahead) })
        scheduler.schedule("a", ahead)

        repository.merge(listOf(task("a", updatedMinutes = -5) { it.copy(deletedAt = at(-5), updatedAt = at(-5)) }))

        assertThat(scheduler.alarms).isEmpty()
        assertThat(repository.observeOpen().first()).isEmpty()
        assertThat(database.taskDao().findById("a")!!.deletedAt).isNotNull()
    }

    // --- Who is told ---

    @Test
    fun `listeners are told about each task that changed and only those`() = runTest {
        database.taskDao().insert(task("keep", "Mine", updatedMinutes = -5))

        repository.merge(listOf(task("keep", "Older", -50), task("new1", updatedMinutes = -5), task("new2", updatedMinutes = -5)))

        assertThat(told).containsExactly("new1", "new2")
    }

    @Test
    fun `deleted tasks arrive as deleted, hidden from the lists`() = runTest {
        repository.merge(listOf(task("gone") { it.copy(deletedAt = at(-5)) }, task("here")))

        assertThat(repository.observeOpen().first().map { it.id }).containsExactly("here")
        assertThat(repository.everything().map { it.id }).containsExactly("gone", "here")
    }

    // --- Preview ---

    @Test
    fun `a preview says what a merge would do and does nothing`() = runTest {
        database.taskDao().insert(task("a", updatedMinutes = -100))

        val plan = repository.previewMerge(listOf(task("a", "Newer", -5), task("b", updatedMinutes = -5)))

        assertThat(plan.added).isEqualTo(1)
        assertThat(plan.updated).isEqualTo(1)
        assertThat(database.taskDao().getAll().map { it.id }).containsExactly("a")
        assertThat(told).isEmpty()
        assertThat(scheduler.alarms).isEmpty()
    }

    @Test
    fun `everything includes deleted tasks`() = runTest {
        database.taskDao().insert(task("live"))
        database.taskDao().insert(task("gone") { it.copy(deletedAt = at(-5)) })

        assertThat(repository.everything().map { it.id }).containsExactly("live", "gone")
    }
}

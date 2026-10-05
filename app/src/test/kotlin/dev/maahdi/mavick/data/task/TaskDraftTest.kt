package dev.maahdi.mavick.data.task

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.time.DEFAULT_WORK_DAYS
import dev.maahdi.mavick.time.RepeatRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertThrows
import org.junit.Test

class TaskDraftTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private val friday = LocalDate.of(2026, 10, 9)

    @Test
    fun `title and notes are trimmed, empty notes dropped`() {
        val clean = TaskDraft(title = "  Call the bank  ", notes = "   ").normalized(monday)

        assertThat(clean.title).isEqualTo("Call the bank")
        assertThat(clean.notes).isNull()
    }

    @Test
    fun `a blank title is refused`() {
        assertThrows(IllegalArgumentException::class.java) { TaskDraft(title = "   ").normalized(monday) }
    }

    @Test
    fun `very long text is shortened`() {
        val clean = TaskDraft(title = "x".repeat(600), notes = "y".repeat(6_000), sourceExcerpt = "z".repeat(400)).normalized(monday)

        assertThat(clean.title).hasLength(TaskDraft.MAX_TITLE_LENGTH)
        assertThat(clean.notes).hasLength(TaskDraft.MAX_NOTES_LENGTH)
        assertThat(clean.sourceExcerpt).hasLength(TaskDraft.MAX_EXCERPT_LENGTH)
    }

    @Test
    fun `a time, reminder or repeat without a date means today`() {
        assertThat(TaskDraft(title = "a", dueTime = LocalTime.NOON).normalized(monday).dueDate).isEqualTo(monday)
        assertThat(TaskDraft(title = "a", reminderTime = LocalTime.NOON).normalized(monday).dueDate).isEqualTo(monday)
        assertThat(TaskDraft(title = "a", repeatRule = RepeatRule.Daily()).normalized(monday).dueDate).isEqualTo(monday)
    }

    @Test
    fun `a plain task keeps no date`() {
        assertThat(TaskDraft(title = "Someday").normalized(monday).dueDate).isNull()
    }

    @Test
    fun `a repeating task starts on its first real occurrence`() {
        // "Every work day" set up on a Friday starts on Sunday.
        val clean = TaskDraft(title = "Standup", repeatRule = RepeatRule.Weekly(DEFAULT_WORK_DAYS)).normalized(friday)
        assertThat(clean.dueDate).isEqualTo(LocalDate.of(2026, 10, 11))

        val onTuesday = TaskDraft(title = "Gym", dueDate = monday, repeatRule = RepeatRule.Weekly(setOf(DayOfWeek.TUESDAY)))
            .normalized(monday)
        assertThat(onTuesday.dueDate).isEqualTo(LocalDate.of(2026, 10, 6))
    }
}

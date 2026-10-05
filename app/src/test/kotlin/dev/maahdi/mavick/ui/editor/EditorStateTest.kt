package dev.maahdi.mavick.ui.editor

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.task.TaskRepository
import dev.maahdi.mavick.testing.task
import dev.maahdi.mavick.time.DEFAULT_WORK_DAYS
import dev.maahdi.mavick.time.RepeatRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import org.junit.Test

class EditorStateTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private val workDays = DEFAULT_WORK_DAYS

    @Test
    fun `repeat rules map to the matching editor choice`() {
        fun choice(rule: RepeatRule?, due: LocalDate? = monday) = EditorState.choiceFor(rule, due, workDays)

        assertThat(choice(null)).isEqualTo(RepeatChoice.NONE)
        assertThat(choice(RepeatRule.Daily())).isEqualTo(RepeatChoice.DAILY)
        assertThat(choice(RepeatRule.Weekly(workDays))).isEqualTo(RepeatChoice.WORK_DAYS)
        assertThat(choice(RepeatRule.Weekly(setOf(DayOfWeek.MONDAY)))).isEqualTo(RepeatChoice.WEEKLY)
        assertThat(choice(RepeatRule.Monthly(5))).isEqualTo(RepeatChoice.MONTHLY)
        assertThat(choice(RepeatRule.Yearly(Month.OCTOBER, 5))).isEqualTo(RepeatChoice.YEARLY)
    }

    @Test
    fun `rules the simple choices can't express stay custom`() {
        assertThat(EditorState.choiceFor(RepeatRule.Daily(3), monday, workDays)).isEqualTo(RepeatChoice.CUSTOM)
        assertThat(EditorState.choiceFor(RepeatRule.Weekly(setOf(DayOfWeek.FRIDAY)), monday, workDays)).isEqualTo(RepeatChoice.CUSTOM)
        // "Monthly on the 31st" shown on 30 November must not quietly become "on the 30th".
        assertThat(EditorState.choiceFor(RepeatRule.Monthly(31), LocalDate.of(2026, 11, 30), workDays)).isEqualTo(RepeatChoice.CUSTOM)
    }

    @Test
    fun `saving keeps a custom rule unchanged`() {
        val state = EditorState.fromTask(task(title = "Stretch", dueDate = monday, repeatRule = RepeatRule.Daily(3)), workDays)

        assertThat(state.toDraft(monday, workDays).repeatRule).isEqualTo(RepeatRule.Daily(3))
    }

    @Test
    fun `weekly, monthly and yearly follow the chosen date`() {
        val thursday = LocalDate.of(2026, 10, 8)
        fun ruleFor(choice: RepeatChoice) = EditorState(title = "a", dueDate = thursday, repeatChoice = choice).toDraft(monday, workDays).repeatRule

        assertThat(ruleFor(RepeatChoice.WEEKLY)).isEqualTo(RepeatRule.Weekly(setOf(DayOfWeek.THURSDAY)))
        assertThat(ruleFor(RepeatChoice.MONTHLY)).isEqualTo(RepeatRule.Monthly(8))
        assertThat(ruleFor(RepeatChoice.YEARLY)).isEqualTo(RepeatRule.Yearly(Month.OCTOBER, 8))
        assertThat(ruleFor(RepeatChoice.WORK_DAYS)).isEqualTo(RepeatRule.Weekly(workDays))
        assertThat(ruleFor(RepeatChoice.NONE)).isNull()
    }

    @Test
    fun `a reminder defaults to the due time, then to 9 am`() {
        val withTime = EditorState(title = "a", dueDate = monday, dueTime = LocalTime.of(17, 0), reminderOn = true)
        val withoutTime = EditorState(title = "a", dueDate = monday, reminderOn = true)

        assertThat(withTime.toDraft(monday, workDays).reminderTime).isEqualTo(LocalTime.of(17, 0))
        assertThat(withoutTime.toDraft(monday, workDays).reminderTime).isEqualTo(TaskRepository.DEFAULT_REMINDER_TIME)
    }

    @Test
    fun `turning the reminder off removes it`() {
        val state = EditorState(title = "a", dueDate = monday, reminderOn = false, reminderTime = LocalTime.NOON)

        assertThat(state.toDraft(monday, workDays).reminderTime).isNull()
    }

    @Test
    fun `an existing task loads every field`() {
        val saved = task(title = "Pay rent", dueDate = monday, dueTime = LocalTime.of(10, 0), reminderTime = LocalTime.of(9, 30))
        val state = EditorState.fromTask(saved, workDays)

        assertThat(state.isNew).isFalse()
        assertThat(state.title).isEqualTo("Pay rent")
        assertThat(state.reminderOn).isTrue()
        assertThat(state.toDraft(monday, workDays).reminderTime).isEqualTo(LocalTime.of(9, 30))
    }
}

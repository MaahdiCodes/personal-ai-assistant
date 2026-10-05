package dev.maahdi.mavick.share

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.data.task.TaskSource
import dev.maahdi.mavick.testing.suggestion
import dev.maahdi.mavick.time.RepeatRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Test

class SuggestionToTaskTest {
    private val origin = "From Sam in Family (WhatsApp)"

    @Test
    fun `a suggestion becomes a task with its date, a reminder at its time, and the message's excerpt`() {
        val suggestion = suggestion(
            title = "Send the form to Sam",
            dueDate = LocalDate.of(2026, 10, 8),
            dueTime = LocalTime.of(17, 0),
            excerpt = "Can you send me the form by Thursday 5pm?",
        )

        val draft = SuggestionToTask.draft(suggestion, origin, whenNote = null)

        assertThat(draft).isEqualTo(
            TaskDraft(
                title = "Send the form to Sam",
                notes = origin,
                dueDate = LocalDate.of(2026, 10, 8),
                dueTime = LocalTime.of(17, 0),
                reminderTime = LocalTime.of(17, 0),
                source = TaskSource.MESSAGE,
                sourceExcerpt = "Can you send me the form by Thursday 5pm?",
            ),
        )
    }

    @Test
    fun `words about when that couldn't be read are kept in the notes`() {
        val suggestion = suggestion(whenText = "end of the month", needsTime = true)

        val draft = SuggestionToTask.draft(suggestion, origin, whenNote = "When: end of the month")

        assertThat(draft.notes).isEqualTo("$origin\n\nWhen: end of the month")
        assertThat(draft.dueDate).isNull()
        assertThat(draft.reminderTime).isNull()
    }

    @Test
    fun `a repeat is kept`() {
        val weekly = RepeatRule.Weekly(setOf(DayOfWeek.MONDAY))

        val draft = SuggestionToTask.draft(suggestion(repeatRule = weekly, dueDate = LocalDate.of(2026, 10, 12)), origin, whenNote = null)

        assertThat(draft.repeatRule).isEqualTo(weekly)
    }
}

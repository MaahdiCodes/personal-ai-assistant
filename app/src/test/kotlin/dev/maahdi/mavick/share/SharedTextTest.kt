package dev.maahdi.mavick.share

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.data.task.TaskSource
import dev.maahdi.mavick.time.WhenParser
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Test

class SharedTextTest {
    private val parser = WhenParser()
    private val now = LocalDateTime.of(2026, 10, 5, 10, 0) // Monday

    private fun share(subject: String?, text: String?, from: String? = SharedText.KEEP_PACKAGE) =
        SharedText.toDraft(subject, text, from, parser, now)

    @Test
    fun `a Keep note with a title uses the title, and finds the date in the body`() {
        val draft = share(subject = "Dentist", text = "Thursday 4pm\nBring the card")!!

        assertThat(draft.title).isEqualTo("Dentist")
        assertThat(draft.dueDate).isEqualTo(LocalDate.of(2026, 10, 8))
        assertThat(draft.dueTime).isEqualTo(LocalTime.of(16, 0))
        assertThat(draft.reminderTime).isEqualTo(LocalTime.of(16, 0))
        assertThat(draft.notes).isEqualTo("Thursday 4pm\nBring the card")
        assertThat(draft.source).isEqualTo(TaskSource.KEEP)
    }

    @Test
    fun `without a title, the first line becomes the task and the rest the notes`() {
        val draft = share(subject = null, text = "Pay rent on the 1st\nAccount 123", from = "com.example.notes")!!

        assertThat(draft.title).isEqualTo("Pay rent")
        assertThat(draft.dueDate).isEqualTo(LocalDate.of(2026, 11, 1))
        assertThat(draft.notes).isEqualTo("Account 123")
        assertThat(draft.source).isEqualTo(TaskSource.SHARE)
    }

    @Test
    fun `a date in the title wins over one in the body`() {
        val draft = share(subject = "Call the bank tomorrow", text = "Friday at the latest")!!

        assertThat(draft.title).isEqualTo("Call the bank")
        assertThat(draft.dueDate).isEqualTo(LocalDate.of(2026, 10, 6))
    }

    @Test
    fun `a title that is only a date keeps its words as the title`() {
        val draft = share(subject = "tomorrow", text = null)!!

        assertThat(draft.title).isEqualTo("tomorrow")
        assertThat(draft.dueDate).isEqualTo(LocalDate.of(2026, 10, 6))
    }

    @Test
    fun `nothing shared gives nothing`() {
        assertThat(share(subject = "  ", text = "\n \n")).isNull()
        assertThat(share(subject = null, text = null)).isNull()
    }

    @Test
    fun `the source quote is kept short`() {
        val draft = share(subject = "Read", text = "word ".repeat(200))!!

        assertThat(draft.sourceExcerpt!!.length).isAtMost(TaskDraft.MAX_EXCERPT_LENGTH)
    }
}

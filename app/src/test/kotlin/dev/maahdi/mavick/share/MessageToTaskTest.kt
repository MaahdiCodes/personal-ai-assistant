package dev.maahdi.mavick.share

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.message.MessageEntity
import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.data.task.TaskSource
import dev.maahdi.mavick.time.WhenParser
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test

class MessageToTaskTest {
    private val dhaka = ZoneId.of("Asia/Dhaka")
    private val parser = WhenParser()

    /** Sent on Sunday 4 October 2026 at 20:00 in Dhaka. */
    private fun message(text: String) = MessageEntity(
        id = "m1",
        app = SourceApp.WHATSAPP,
        accountKey = "0",
        conversationKey = "s:sam",
        conversationTitle = "Sam",
        sender = "Sam",
        text = text,
        postedAt = LocalDateTime.of(2026, 10, 4, 20, 0).atZone(dhaka).toInstant(),
        receivedAt = LocalDateTime.of(2026, 10, 4, 20, 0).atZone(dhaka).toInstant(),
        isFromMe = false,
        isGroup = false,
        cutShort = false,
        dedupHash = "hash",
    )

    @Test
    fun `dates count from when the message was sent`() {
        val draft = MessageToTask.draft(message("Call me tomorrow at 12"), "From Sam (WhatsApp)", parser, dhaka)

        assertThat(draft.title).isEqualTo("Call me")
        // "Tomorrow" in Sunday's message is Monday, whenever the task is made.
        assertThat(draft.dueDate).isEqualTo(LocalDate.of(2026, 10, 5))
        assertThat(draft.dueTime).isEqualTo(LocalTime.of(12, 0))
        assertThat(draft.reminderTime).isEqualTo(LocalTime.of(12, 0))
    }

    @Test
    fun `the task remembers where it came from`() {
        val draft = MessageToTask.draft(message("Send the form\nThe blue one, signed"), "From Sam (WhatsApp)", parser, dhaka)

        assertThat(draft.source).isEqualTo(TaskSource.MESSAGE)
        assertThat(draft.notes).isEqualTo("From Sam (WhatsApp)\n\nThe blue one, signed")
        assertThat(draft.sourceExcerpt).isEqualTo("Send the form\nThe blue one, signed")
    }

    @Test
    fun `a long message keeps a short excerpt`() {
        val draft = MessageToTask.draft(message("Plan " + "x".repeat(1_000)), "From Sam (WhatsApp)", parser, dhaka)

        assertThat(draft.sourceExcerpt).hasLength(TaskDraft.MAX_EXCERPT_LENGTH)
    }

    @Test
    fun `a message with no usable text still opens a draft named after its origin`() {
        val draft = MessageToTask.draft(message("   "), "From Sam (WhatsApp)", parser, dhaka)

        assertThat(draft.title).isEqualTo("From Sam (WhatsApp)")
        assertThat(draft.source).isEqualTo(TaskSource.MESSAGE)
    }
}

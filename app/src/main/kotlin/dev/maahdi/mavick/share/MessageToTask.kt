package dev.maahdi.mavick.share

import dev.maahdi.mavick.data.message.MessageEntity
import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.data.task.TaskSource
import dev.maahdi.mavick.time.WhenParser
import java.time.LocalDateTime
import java.time.ZoneId

/** "Add as task" on a message: a draft that opens in the editor, so nothing is saved until Save. */
object MessageToTask {
    /**
     * Reads the message like shared text (first line as the title, dates and times found in it),
     * counting from when it was sent: "tomorrow" in yesterday's message means today.
     *
     * @param origin a first line for the notes, such as "From Sam in Family (WhatsApp)".
     */
    fun draft(message: MessageEntity, origin: String, parser: WhenParser, zone: ZoneId): TaskDraft {
        val sentAt = LocalDateTime.ofInstant(message.postedAt, zone)
        val read = SharedText.toDraft(subject = null, text = message.text, fromPackage = null, parser = parser, now = sentAt)
            ?: TaskDraft(title = origin)
        return read.copy(
            notes = listOfNotNull(origin, read.notes).joinToString("\n\n"),
            source = TaskSource.MESSAGE,
            sourceExcerpt = message.text.trim().take(TaskDraft.MAX_EXCERPT_LENGTH),
        )
    }
}

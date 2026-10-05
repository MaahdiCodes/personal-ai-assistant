package dev.maahdi.mavick.importers

import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.share.SharedText
import dev.maahdi.mavick.time.WhenParser
import java.time.LocalDateTime
import java.time.ZoneId

/** A Keep note from Takeout as a task: the same as sharing that note from Keep to Mavick. */
object KeepNoteToTask {
    /**
     * The title (or the first line) becomes the task; its words' dates are read counting from when
     * the note was last edited, as a message's are from when it was sent. [fallbackNow] serves notes
     * without a time. Null for a note with nothing to make a task of.
     */
    fun draft(note: KeepNote, parser: WhenParser, zone: ZoneId, fallbackNow: LocalDateTime): TaskDraft? = SharedText.toDraft(
        subject = note.title.takeIf { it.isNotBlank() },
        text = note.body(),
        fromPackage = SharedText.KEEP_PACKAGE,
        parser = parser,
        now = note.editedAt?.let { LocalDateTime.ofInstant(it, zone) } ?: fallbackNow,
    )
}

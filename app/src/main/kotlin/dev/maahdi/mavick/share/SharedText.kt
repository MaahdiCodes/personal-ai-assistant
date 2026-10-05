package dev.maahdi.mavick.share

import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.data.task.TaskSource
import dev.maahdi.mavick.time.WhenParser
import java.time.LocalDateTime

/**
 * Turns text shared from another app (Google Keep: ⋮ > Send > Mavick) into a task draft that
 * opens in the editor, so nothing is saved until you press Save.
 */
object SharedText {
    const val KEEP_PACKAGE = "com.google.android.keep"
    private const val MAX_SHARED_TEXT_LENGTH = 5_000

    /**
     * @param subject the note's title (Keep sends it as the subject), if any.
     * @param fromPackage the app it came from, if Android says.
     * @return null when nothing usable was shared.
     */
    fun toDraft(subject: String?, text: String?, fromPackage: String?, parser: WhenParser, now: LocalDateTime): TaskDraft? {
        val cleanSubject = subject?.trim()?.takeIf { it.isNotEmpty() }
        val cleanText = text?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_SHARED_TEXT_LENGTH)
        val lines = cleanText?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val titleSource = cleanSubject ?: lines.firstOrNull() ?: return null

        val fromTitle = parser.parse(titleSource, now)
        // A date only in the note's body still counts: "Dentist" / "Thursday 4pm".
        val fromBody = if (fromTitle.dueDate == null && cleanText != null) parser.parse(cleanText, now) else null
        val dueTime = fromTitle.dueTime ?: fromBody?.dueTime
        val notes = if (cleanSubject != null) cleanText else lines.drop(1).joinToString("\n").takeIf { it.isNotEmpty() }

        return TaskDraft(
            title = fromTitle.title.ifBlank { titleSource }.take(TaskDraft.MAX_TITLE_LENGTH),
            notes = notes,
            dueDate = fromTitle.dueDate ?: fromBody?.dueDate,
            dueTime = dueTime,
            reminderTime = dueTime,
            repeatRule = fromTitle.repeatRule ?: fromBody?.repeatRule,
            source = if (fromPackage == KEEP_PACKAGE) TaskSource.KEEP else TaskSource.SHARE,
            sourceExcerpt = (cleanText ?: cleanSubject)?.take(TaskDraft.MAX_EXCERPT_LENGTH),
        )
    }
}

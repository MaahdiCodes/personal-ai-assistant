package dev.maahdi.mavick.data.task

import dev.maahdi.mavick.time.RepeatRule
import java.time.LocalDate
import java.time.LocalTime

/** What the user (or a share, or later the AI) wants a task to be, before it is saved. */
data class TaskDraft(
    val title: String,
    val notes: String? = null,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    /** Clock time of the reminder on the due date; null means no reminder. */
    val reminderTime: LocalTime? = null,
    val repeatRule: RepeatRule? = null,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val source: TaskSource = TaskSource.MANUAL,
    val sourceExcerpt: String? = null,
) {
    /**
     * Cleans the draft up and fills in what the rest implies: a reminder, time or repeat needs a
     * date (today if none), and a repeating task starts on its first real occurrence.
     *
     * @throws IllegalArgumentException if the title is blank.
     */
    fun normalized(today: LocalDate): TaskDraft {
        val cleanTitle = title.trim().take(MAX_TITLE_LENGTH)
        require(cleanTitle.isNotEmpty()) { "A task needs a title." }
        val needsDate = dueTime != null || reminderTime != null || repeatRule != null
        val date = dueDate ?: if (needsDate) today else null
        return copy(
            title = cleanTitle,
            notes = notes?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NOTES_LENGTH),
            dueDate = if (date != null && repeatRule != null) repeatRule.firstOnOrAfter(date) else date,
            sourceExcerpt = sourceExcerpt?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_EXCERPT_LENGTH),
        )
    }

    companion object {
        const val MAX_TITLE_LENGTH = 500
        const val MAX_NOTES_LENGTH = 5_000
        const val MAX_EXCERPT_LENGTH = 300
    }
}

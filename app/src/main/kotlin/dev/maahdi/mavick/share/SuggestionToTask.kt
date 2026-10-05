package dev.maahdi.mavick.share

import dev.maahdi.mavick.data.suggestion.SuggestionEntity
import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.data.task.TaskSource

/** A suggestion as a task: added straight away, or opened in the editor first ("Edit"). */
object SuggestionToTask {
    /**
     * A task with a time is reminded at that time, as with quick-add. The task keeps the message's
     * excerpt, so it still makes sense after the message is deleted.
     *
     * @param origin the first line of the notes, such as "From Sam in Family (WhatsApp)".
     * @param whenNote kept in the notes when the "when" words couldn't be read, such as
     *   "When: end of the month", so nothing the message said is lost.
     */
    fun draft(suggestion: SuggestionEntity, origin: String, whenNote: String?): TaskDraft = TaskDraft(
        title = suggestion.title,
        notes = listOfNotNull(origin, whenNote).joinToString("\n\n"),
        dueDate = suggestion.dueDate,
        dueTime = suggestion.dueTime,
        reminderTime = suggestion.dueTime,
        repeatRule = suggestion.repeatRule,
        source = TaskSource.MESSAGE,
        sourceExcerpt = suggestion.excerpt,
    )
}

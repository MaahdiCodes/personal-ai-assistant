package dev.maahdi.mavick.ui.editor

import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.data.task.TaskEntity
import dev.maahdi.mavick.data.task.TaskPriority
import dev.maahdi.mavick.data.task.TaskRepository
import dev.maahdi.mavick.data.task.TaskSource
import dev.maahdi.mavick.time.RepeatRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * The repeat options in the editor. Weekly, monthly and yearly follow the task's date. A rule
 * that fits none of them (say, "every 3 days" from quick-add) is kept as [CUSTOM], unchanged.
 */
enum class RepeatChoice { NONE, DAILY, WORK_DAYS, WEEKLY, MONTHLY, YEARLY, CUSTOM }

/** Everything the task editor shows and edits. */
data class EditorState(
    val taskId: String? = null,
    val title: String = "",
    val notes: String = "",
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val reminderOn: Boolean = false,
    val reminderTime: LocalTime? = null,
    val repeatChoice: RepeatChoice = RepeatChoice.NONE,
    val customRule: RepeatRule? = null,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val source: TaskSource = TaskSource.MANUAL,
    val sourceExcerpt: String? = null,
    val loading: Boolean = false,
    val missing: Boolean = false,
    val showTitleError: Boolean = false,
) {
    val isNew: Boolean get() = taskId == null

    /** The reminder time shown when the reminder is on: its own, else the due time, else 09:00. */
    val effectiveReminderTime: LocalTime get() = reminderTime ?: dueTime ?: TaskRepository.DEFAULT_REMINDER_TIME

    fun toDraft(today: LocalDate, workDays: Set<DayOfWeek>): TaskDraft {
        val anchor = dueDate ?: today
        val rule = when (repeatChoice) {
            RepeatChoice.NONE -> null
            RepeatChoice.DAILY -> RepeatRule.Daily()
            RepeatChoice.WORK_DAYS -> RepeatRule.Weekly(workDays)
            RepeatChoice.WEEKLY -> RepeatRule.Weekly(setOf(anchor.dayOfWeek))
            RepeatChoice.MONTHLY -> RepeatRule.Monthly(anchor.dayOfMonth)
            RepeatChoice.YEARLY -> RepeatRule.Yearly(anchor.month, anchor.dayOfMonth)
            RepeatChoice.CUSTOM -> customRule
        }
        return TaskDraft(
            title = title,
            notes = notes,
            dueDate = dueDate,
            dueTime = dueTime,
            reminderTime = if (reminderOn) effectiveReminderTime else null,
            repeatRule = rule,
            priority = priority,
            source = source,
            sourceExcerpt = sourceExcerpt,
        )
    }

    companion object {
        fun fromTask(task: TaskEntity, workDays: Set<DayOfWeek>) = EditorState(
            taskId = task.id,
            title = task.title,
            notes = task.notes.orEmpty(),
            dueDate = task.dueDate,
            dueTime = task.dueTime,
            reminderOn = task.reminderTime != null,
            reminderTime = task.reminderTime,
            repeatChoice = choiceFor(task.repeatRule, task.dueDate, workDays),
            customRule = task.repeatRule,
            priority = task.priority,
            source = task.source,
            sourceExcerpt = task.sourceExcerpt,
        )

        fun fromDraft(draft: TaskDraft, workDays: Set<DayOfWeek>) = EditorState(
            title = draft.title,
            notes = draft.notes.orEmpty(),
            dueDate = draft.dueDate,
            dueTime = draft.dueTime,
            reminderOn = draft.reminderTime != null,
            reminderTime = draft.reminderTime,
            repeatChoice = choiceFor(draft.repeatRule, draft.dueDate, workDays),
            customRule = draft.repeatRule,
            priority = draft.priority,
            source = draft.source,
            sourceExcerpt = draft.sourceExcerpt,
        )

        fun choiceFor(rule: RepeatRule?, dueDate: LocalDate?, workDays: Set<DayOfWeek>): RepeatChoice = when {
            rule == null -> RepeatChoice.NONE
            rule == RepeatRule.Daily() -> RepeatChoice.DAILY
            rule == RepeatRule.Weekly(workDays) -> RepeatChoice.WORK_DAYS
            dueDate != null && rule == RepeatRule.Weekly(setOf(dueDate.dayOfWeek)) -> RepeatChoice.WEEKLY
            dueDate != null && rule == RepeatRule.Monthly(dueDate.dayOfMonth) -> RepeatChoice.MONTHLY
            dueDate != null && rule == RepeatRule.Yearly(dueDate.month, dueDate.dayOfMonth) -> RepeatChoice.YEARLY
            else -> RepeatChoice.CUSTOM
        }
    }
}

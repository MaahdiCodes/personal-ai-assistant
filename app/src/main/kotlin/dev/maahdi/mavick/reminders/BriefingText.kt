package dev.maahdi.mavick.reminders

import android.content.res.Resources
import dev.maahdi.mavick.R
import dev.maahdi.mavick.data.task.TaskEntity
import dev.maahdi.mavick.time.DueFormatter

/** The words about today's tasks, shared by the morning briefing and the home-screen widget. */
object BriefingText {
    /** "2 tasks due today" and "1 overdue": each only when there is something to say. */
    fun dueParts(resources: Resources, dueToday: Int, overdue: Int): List<String> = listOfNotNull(
        dueToday.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.briefing_due_today, it, it) },
        overdue.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.briefing_overdue, it, it) },
    )

    /** "Overdue: Pay rent", "17:00  Call the bank", or just the title of a task with no time. */
    fun taskLine(resources: Resources, task: TaskEntity, overdue: Boolean, use24Hour: Boolean): String = when {
        overdue -> resources.getString(R.string.briefing_overdue_line, task.title)
        else -> task.dueTime?.let { "${DueFormatter.time(it, use24Hour)}  ${task.title}" } ?: task.title
    }
}

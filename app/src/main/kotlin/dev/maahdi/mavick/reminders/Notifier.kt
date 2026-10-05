package dev.maahdi.mavick.reminders

import dev.maahdi.mavick.data.task.Briefing
import dev.maahdi.mavick.data.task.TaskEntity

/** Shows Mavick's notifications. */
interface Notifier {
    /** @param missed the reminder time passed while the phone was off or Mavick was stopped. */
    fun showReminder(task: TaskEntity, missed: Boolean)

    fun cancelReminder(taskId: String)

    fun showBriefing(briefing: Briefing)
}

package dev.maahdi.mavick.reminders

import java.time.LocalDateTime

/** Sets and cancels the phone alarms that wake Mavick for reminders and the morning briefing. */
interface ReminderScheduler {
    /** Sets the task's reminder alarm, replacing any earlier one for that task. */
    fun schedule(taskId: String, at: LocalDateTime)

    fun cancel(taskId: String)

    fun scheduleBriefing(at: LocalDateTime)

    fun cancelBriefing()
}

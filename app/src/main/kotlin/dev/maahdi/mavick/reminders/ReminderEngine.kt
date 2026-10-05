package dev.maahdi.mavick.reminders

import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.data.task.TaskRepository
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean

/** Decides what happens when an alarm, a notification button or a system event arrives. */
class ReminderEngine(
    private val tasks: TaskRepository,
    private val notifier: Notifier,
    private val scheduler: ReminderScheduler,
    private val settings: SettingsRepository,
    private val clock: () -> Clock,
) {
    private val resyncedThisProcess = AtomicBoolean(false)

    suspend fun onReminderAlarm(taskId: String) {
        tasks.takeDueReminder(taskId)?.let { notifier.showReminder(it, missed = false) }
    }

    suspend fun onNotificationAction(taskId: String, action: ReminderAction) {
        notifier.cancelReminder(taskId)
        when (action) {
            ReminderAction.DONE -> tasks.complete(taskId)
            ReminderAction.SNOOZE -> tasks.snooze(taskId, SNOOZE_MINUTES)
            ReminderAction.TOMORROW -> tasks.moveToTomorrow(taskId)
        }
    }

    /**
     * Sets all alarms again and shows reminders that were missed. Runs after a restart, a time or
     * time-zone change, an app update, and once each time Mavick's process starts with the
     * screen open (alarms are wiped if Android force-stops an app).
     */
    suspend fun resync() {
        tasks.rescheduleAll().forEach { notifier.showReminder(it, missed = true) }
        scheduleBriefing()
        tasks.purgeOldDeleted()
    }

    suspend fun resyncOncePerProcess() {
        if (resyncedThisProcess.compareAndSet(false, true)) resync()
    }

    /** The briefing runs every day, weekends included. */
    fun scheduleBriefing() {
        val current = settings.current
        if (!current.briefingEnabled) {
            scheduler.cancelBriefing()
            return
        }
        val now = LocalDateTime.now(clock())
        val todayAt = now.toLocalDate().atTime(current.briefingTime)
        scheduler.scheduleBriefing(if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1))
    }

    /** Shows the briefing (only if something is due) and sets the next one. */
    suspend fun onBriefingAlarm() {
        if (settings.current.briefingEnabled) {
            val briefing = tasks.briefing(LocalDate.now(clock()))
            if (!briefing.isEmpty) notifier.showBriefing(briefing)
        }
        scheduleBriefing()
    }

    companion object {
        const val SNOOZE_MINUTES = 10L
    }
}

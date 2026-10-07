package dev.maahdi.mavick.reminders

import dev.maahdi.mavick.calendar.CalendarOccurrence
import dev.maahdi.mavick.calendar.Clash
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.data.task.TaskEntity
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
    private val chores: DailyChores = DailyChores.NONE,
    /** Suggested tasks waiting for you (Phase 3), mentioned in the briefing. */
    private val countSuggestions: suspend () -> Int = { 0 },
    /** Calendar events each task overlaps, by task ID (Phase 4); mentioned in the briefing. */
    private val findClashes: suspend (List<TaskEntity>) -> Map<String, List<CalendarOccurrence>> = { emptyMap() },
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
        scheduleDailyAlarm()
        tasks.purgeOldDeleted()
        chores.cleanUp()
    }

    suspend fun resyncOncePerProcess() {
        if (resyncedThisProcess.compareAndSet(false, true)) resync()
    }

    /**
     * Sets the daily alarm at the briefing time. It runs every day, weekends included, even with
     * the briefing off, because it also deletes old messages and checks message reading.
     */
    fun scheduleDailyAlarm() = scheduleDailyAlarm(notBefore = LocalDateTime.now(clock()))

    private fun scheduleDailyAlarm(notBefore: LocalDateTime) {
        val todayAt = notBefore.toLocalDate().atTime(settings.current.briefingTime)
        scheduler.scheduleDaily(if (todayAt.isAfter(notBefore)) todayAt else todayAt.plusDays(1))
    }

    /**
     * The daily alarm went off: sets tomorrow's first, then the briefing (if on, and anything is
     * due or suggested, with any clash of today's tasks with the calendar), then the chores.
     */
    suspend fun onDailyAlarm() {
        // First, so nothing below can stop tomorrow's alarm. The minute's margin keeps an alarm
        // that fires a moment early from setting itself again for today.
        scheduleDailyAlarm(notBefore = LocalDateTime.now(clock()).plusMinutes(1))
        if (settings.current.briefingEnabled) {
            val plain = tasks.briefing(LocalDate.now(clock()))
            val found = findClashes(plain.today)
            val briefing = plain.copy(
                suggestionsWaiting = countSuggestions(),
                clashes = plain.today.mapNotNull { task -> found[task.id]?.let { Clash(task, it) } },
            )
            if (!briefing.isEmpty) notifier.showBriefing(briefing)
        }
        chores.cleanUp()
        chores.daily()
    }

    companion object {
        const val SNOOZE_MINUTES = 10L
    }
}

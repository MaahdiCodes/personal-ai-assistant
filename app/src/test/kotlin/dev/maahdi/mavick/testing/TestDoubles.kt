package dev.maahdi.mavick.testing

import dev.maahdi.mavick.data.task.Briefing
import dev.maahdi.mavick.data.task.TaskEntity
import dev.maahdi.mavick.reminders.Notifier
import dev.maahdi.mavick.reminders.ReminderScheduler
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** The test time zone: no daylight saving, so local times map to instants one-to-one. */
val TEST_ZONE: ZoneId = ZoneId.of("Asia/Dhaka")

/** Monday 5 October 2026, 10:00 — the "now" most tests start from. */
val MONDAY_10AM: LocalDateTime = LocalDateTime.of(2026, 10, 5, 10, 0)

/** A clock tests can set and move forward. */
class MutableClock(private var now: Instant, private val zone: ZoneId = TEST_ZONE) : Clock() {
    constructor(local: LocalDateTime, zone: ZoneId = TEST_ZONE) : this(local.atZone(zone).toInstant(), zone)

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)

    override fun instant(): Instant = now

    fun setLocal(local: LocalDateTime) {
        now = local.atZone(zone).toInstant()
    }

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}

/** Records alarms instead of setting real ones. */
class FakeReminderScheduler : ReminderScheduler {
    val alarms = mutableMapOf<String, LocalDateTime>()
    var briefingAt: LocalDateTime? = null
        private set

    override fun schedule(taskId: String, at: LocalDateTime) {
        alarms[taskId] = at
    }

    override fun cancel(taskId: String) {
        alarms.remove(taskId)
    }

    override fun scheduleBriefing(at: LocalDateTime) {
        briefingAt = at
    }

    override fun cancelBriefing() {
        briefingAt = null
    }
}

/** Records notifications instead of showing them. */
class FakeNotifier : Notifier {
    data class Shown(val taskId: String, val title: String, val missed: Boolean)

    val reminders = mutableListOf<Shown>()
    val cancelled = mutableListOf<String>()
    val briefings = mutableListOf<Briefing>()

    override fun showReminder(task: TaskEntity, missed: Boolean) {
        reminders += Shown(task.id, task.title, missed)
    }

    override fun cancelReminder(taskId: String) {
        cancelled += taskId
    }

    override fun showBriefing(briefing: Briefing) {
        briefings += briefing
    }
}

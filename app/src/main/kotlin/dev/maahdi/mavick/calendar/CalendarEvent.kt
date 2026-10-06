package dev.maahdi.mavick.calendar

import dev.maahdi.mavick.data.task.TaskEntity
import dev.maahdi.mavick.data.task.TaskStatus
import dev.maahdi.mavick.data.toHex
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** What a task looks like in the phone's calendar: a title and a time, nothing else. */
data class CalendarEvent(val title: String, val start: Instant, val end: Instant, val zone: ZoneId) {
    /**
     * Changes exactly when the event has to be written again: its title, its time or the time zone
     * it is shown in. Kept in [dev.maahdi.mavick.data.calendar.CalendarLinkEntity] instead of the
     * title itself.
     */
    val fingerprint: String
        get() {
            val parts = listOf(title, start.toEpochMilli().toString(), end.toEpochMilli().toString(), zone.id)
            return MessageDigest.getInstance("SHA-256").digest(parts.joinToString(SEPARATOR).toByteArray(Charsets.UTF_8)).toHex()
        }

    private companion object {
        /** Never appears in a title, so "a" + "bc" and "ab" + "c" can't give the same fingerprint. */
        const val SEPARATOR = "\u0000"
    }
}

/** Which tasks go to the calendar, and how (docs/PLAN.md §5.9). */
object CalendarEventPolicy {
    /** Tasks have no length, so each event lasts this long. */
    val EVENT_LENGTH: Duration = Duration.ofMinutes(30)

    /**
     * The event for [task], or null when it has none: only open tasks with a date and a time get
     * one (a date-only task is covered by the morning briefing). The task's local time becomes an
     * instant in [zone], the phone's current time zone.
     */
    fun eventFor(task: TaskEntity, zone: ZoneId): CalendarEvent? {
        val date = task.dueDate ?: return null
        val time = task.dueTime ?: return null
        if (task.deletedAt != null || task.status != TaskStatus.OPEN) return null
        val start = date.atTime(time).atZone(zone).toInstant()
        return CalendarEvent(title = task.title, start = start, end = start.plus(EVENT_LENGTH), zone = zone)
    }
}

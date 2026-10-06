package dev.maahdi.mavick.data.calendar

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The calendar event Mavick wrote for a task (Phase 4). Event IDs mean something on this phone only,
 * so they live in a table of their own and never in the task: a backup or a sync between phones
 * (Phases 5 and 6) must not carry them.
 *
 * There is deliberately no foreign key to the task: a link must outlive a purged task until its
 * event has been removed from the calendar (which can wait for a permission or a calendar).
 */
@Entity(tableName = "calendar_link")
data class CalendarLinkEntity(
    @PrimaryKey val taskId: String,
    /** The calendar the event was written to, so a different choice moves it. */
    val calendarId: Long,
    val eventId: Long,
    /** [dev.maahdi.mavick.calendar.CalendarEvent.fingerprint] of what was written last. */
    val fingerprint: String,
)

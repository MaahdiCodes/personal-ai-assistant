package dev.maahdi.mavick.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events

/** The calendar permissions Mavick asks for when the user turns the feature on (one prompt for both). */
object CalendarPermissions {
    val REQUIRED = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
}

/**
 * Android's calendar storage (the same one the Calendar app uses). Events added here are copied to
 * Google by the Calendar app's own sync, never by Mavick, which has no internet access.
 */
class ContentResolverCalendarGateway(private val context: Context) : CalendarGateway {
    override fun hasPermission(): Boolean =
        CalendarPermissions.REQUIRED.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    override fun writableCalendars(): List<DeviceCalendar> = guarded {
        val calendars = mutableListOf<DeviceCalendar>()
        context.contentResolver.query(Calendars.CONTENT_URI, CALENDAR_COLUMNS, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                // Filtered here, not in SQL: there are only a handful of calendars.
                val canAddEvents = cursor.getInt(ACCESS_LEVEL) >= Calendars.CAL_ACCESS_CONTRIBUTOR
                if (canAddEvents && cursor.getInt(VISIBLE) == 1) {
                    calendars += DeviceCalendar(
                        id = cursor.getLong(ID),
                        name = cursor.getString(NAME).orEmpty(),
                        account = cursor.getString(ACCOUNT).orEmpty(),
                    )
                }
            }
        }
        calendars.sortedWith(compareBy({ it.account.lowercase() }, { it.name.lowercase() }, { it.id }))
    }

    override fun insert(calendarId: Long, event: CalendarEvent): Long = guarded {
        val values = values(event).apply { put(Events.CALENDAR_ID, calendarId) }
        val uri = context.contentResolver.insert(Events.CONTENT_URI, values) ?: throw CalendarAccessException(CalendarProblem.UNAVAILABLE)
        ContentUris.parseId(uri)
    }

    override fun update(eventId: Long, event: CalendarEvent): Boolean = guarded {
        context.contentResolver.update(eventUri(eventId), values(event), null, null) > 0
    }

    override fun delete(eventId: Long): Boolean = guarded {
        context.contentResolver.delete(eventUri(eventId), null, null) > 0
    }

    /**
     * Only the title and the time: no notes, no place, no guests. Private, so a calendar shared with
     * other people shows "busy" instead of the title; free, because a to-do is not a meeting; and
     * no alarm, because Mavick's own reminders already go off.
     */
    private fun values(event: CalendarEvent) = ContentValues().apply {
        put(Events.TITLE, event.title)
        put(Events.DTSTART, event.start.toEpochMilli())
        put(Events.DTEND, event.end.toEpochMilli())
        put(Events.EVENT_TIMEZONE, event.zone.id)
        put(Events.ALL_DAY, 0)
        put(Events.HAS_ALARM, 0)
        put(Events.AVAILABILITY, Events.AVAILABILITY_FREE)
        put(Events.ACCESS_LEVEL, Events.ACCESS_PRIVATE)
    }

    private fun eventUri(eventId: Long): Uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)

    /** Checks the permission first, and turns anything the calendar storage throws into a [CalendarAccessException]. */
    private inline fun <T> guarded(block: () -> T): T {
        if (!hasPermission()) throw CalendarAccessException(CalendarProblem.NO_PERMISSION)
        try {
            return block()
        } catch (e: SecurityException) {
            throw CalendarAccessException(CalendarProblem.NO_PERMISSION, e)
        } catch (e: RuntimeException) {
            throw CalendarAccessException(CalendarProblem.UNAVAILABLE, e)
        }
    }

    private companion object {
        val CALENDAR_COLUMNS = arrayOf(
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.CALENDAR_ACCESS_LEVEL,
            Calendars.VISIBLE,
            Calendars.ACCOUNT_NAME,
        )
        const val ID = 0
        const val NAME = 1
        const val ACCESS_LEVEL = 2
        const val VISIBLE = 3
        const val ACCOUNT = 4
    }
}

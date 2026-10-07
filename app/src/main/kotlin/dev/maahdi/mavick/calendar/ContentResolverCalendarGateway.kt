package dev.maahdi.mavick.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import java.time.Instant

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

    override fun visibleCalendars(): List<DeviceCalendar> = guarded {
        val calendars = mutableListOf<DeviceCalendar>()
        context.contentResolver.query(Calendars.CONTENT_URI, CALENDAR_COLUMNS, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                // Filtered here, not in SQL: there are only a handful of calendars.
                if (cursor.getInt(VISIBLE) != 1) continue
                calendars += DeviceCalendar(
                    id = cursor.getLong(ID),
                    name = cursor.getString(NAME).orEmpty(),
                    account = cursor.getString(ACCOUNT).orEmpty(),
                    writable = cursor.getInt(ACCESS_LEVEL) >= Calendars.CAL_ACCESS_CONTRIBUTOR,
                )
            }
        }
        calendars.sortedWith(compareBy({ it.account.lowercase() }, { it.name.lowercase() }, { it.id }))
    }

    override fun writableCalendars(): List<DeviceCalendar> = visibleCalendars().filter { it.writable }

    override fun occurrences(from: Instant, to: Instant): List<CalendarOccurrence> = guarded {
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from.toEpochMilli())
            ContentUris.appendId(it, to.toEpochMilli())
        }.build()
        val found = mutableListOf<CalendarOccurrence>()
        context.contentResolver.query(uri, INSTANCE_COLUMNS, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                // Only events of calendars shown in the Calendar app, as the Calendar app does.
                if (cursor.getInt(INSTANCE_VISIBLE) != 1) continue
                found += CalendarOccurrence(
                    eventId = cursor.getLong(INSTANCE_EVENT_ID),
                    calendarId = cursor.getLong(INSTANCE_CALENDAR_ID),
                    title = cursor.getString(INSTANCE_TITLE).orEmpty(),
                    start = Instant.ofEpochMilli(cursor.getLong(INSTANCE_BEGIN)),
                    end = Instant.ofEpochMilli(cursor.getLong(INSTANCE_END)),
                    allDay = cursor.getInt(INSTANCE_ALL_DAY) == 1,
                    busy = cursor.getInt(INSTANCE_AVAILABILITY) != Events.AVAILABILITY_FREE &&
                        cursor.getInt(INSTANCE_SELF_STATUS) != Attendees.ATTENDEE_STATUS_DECLINED &&
                        cursor.getInt(INSTANCE_STATUS) != Events.STATUS_CANCELED,
                )
            }
        }
        found
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

        val INSTANCE_COLUMNS = arrayOf(
            Instances.EVENT_ID,
            Instances.CALENDAR_ID,
            Instances.TITLE,
            Instances.BEGIN,
            Instances.END,
            Instances.ALL_DAY,
            Instances.AVAILABILITY,
            Instances.SELF_ATTENDEE_STATUS,
            Instances.STATUS,
            Instances.VISIBLE,
        )
        const val INSTANCE_EVENT_ID = 0
        const val INSTANCE_CALENDAR_ID = 1
        const val INSTANCE_TITLE = 2
        const val INSTANCE_BEGIN = 3
        const val INSTANCE_END = 4
        const val INSTANCE_ALL_DAY = 5
        const val INSTANCE_AVAILABILITY = 6
        const val INSTANCE_SELF_STATUS = 7
        const val INSTANCE_STATUS = 8
        const val INSTANCE_VISIBLE = 9
    }
}

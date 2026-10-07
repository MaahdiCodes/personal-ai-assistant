package dev.maahdi.mavick.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real calendar storage on the phone, in a calendar of its own (an "On device" account that
 * never syncs), so nothing in your Google calendar is touched. The calendar and everything in it
 * is removed afterwards.
 */
@RunWith(AndroidJUnit4::class)
class CalendarGatewayDeviceTest {
    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val gateway = ContentResolverCalendarGateway(context)
    private var calendarId = 0L

    private val event = CalendarEvent(
        title = "Mavick device test",
        start = Instant.parse("2030-01-08T11:00:00Z"),
        end = Instant.parse("2030-01-08T11:30:00Z"),
        zone = ZoneId.of("Asia/Dhaka"),
    )

    /** Only a sync adapter may add a calendar; for the local account Android allows it to anyone holding the permission. */
    private fun asLocalAccount(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    @Before
    fun createCalendar() {
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, ACCOUNT)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, "mavick-device-test")
            put(Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_NAME)
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, ACCOUNT)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
        }
        val uri = context.contentResolver.insert(asLocalAccount(Calendars.CONTENT_URI), values)
        calendarId = ContentUris.parseId(requireNotNull(uri) { "The phone would not create a calendar" })
    }

    @After
    fun removeCalendar() {
        // Deleting the calendar deletes its events too.
        context.contentResolver.delete(asLocalAccount(ContentUris.withAppendedId(Calendars.CONTENT_URI, calendarId)), null, null)
    }

    private fun readEvent(eventId: Long): ContentValues? {
        val columns = arrayOf(
            Events.CALENDAR_ID,
            Events.TITLE,
            Events.DTSTART,
            Events.DTEND,
            Events.EVENT_TIMEZONE,
            Events.HAS_ALARM,
            Events.AVAILABILITY,
            Events.ACCESS_LEVEL,
        )
        context.contentResolver.query(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), columns, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            return ContentValues().apply {
                put(Events.CALENDAR_ID, cursor.getLong(0))
                put(Events.TITLE, cursor.getString(1))
                put(Events.DTSTART, cursor.getLong(2))
                put(Events.DTEND, cursor.getLong(3))
                put(Events.EVENT_TIMEZONE, cursor.getString(4))
                put(Events.HAS_ALARM, cursor.getInt(5))
                put(Events.AVAILABILITY, cursor.getInt(6))
                put(Events.ACCESS_LEVEL, cursor.getInt(7))
            }
        }
        return null
    }

    @Test
    fun the_permission_is_granted() {
        assertThat(gateway.hasPermission()).isTrue()
    }

    @Test
    fun a_calendar_you_can_add_to_is_listed() {
        val listed = gateway.writableCalendars().firstOrNull { it.id == calendarId }

        assertThat(listed).isEqualTo(DeviceCalendar(calendarId, CALENDAR_NAME, ACCOUNT))
    }

    @Test
    fun an_event_is_written_with_its_title_time_and_privacy_settings() {
        val eventId = gateway.insert(calendarId, event)

        val stored = requireNotNull(readEvent(eventId))
        assertThat(stored.getAsLong(Events.CALENDAR_ID)).isEqualTo(calendarId)
        assertThat(stored.getAsString(Events.TITLE)).isEqualTo(event.title)
        assertThat(stored.getAsLong(Events.DTSTART)).isEqualTo(event.start.toEpochMilli())
        assertThat(stored.getAsLong(Events.DTEND)).isEqualTo(event.end.toEpochMilli())
        assertThat(stored.getAsString(Events.EVENT_TIMEZONE)).isEqualTo("Asia/Dhaka")
        assertThat(stored.getAsInteger(Events.HAS_ALARM)).isEqualTo(0)
        assertThat(stored.getAsInteger(Events.AVAILABILITY)).isEqualTo(Events.AVAILABILITY_FREE)
        assertThat(stored.getAsInteger(Events.ACCESS_LEVEL)).isEqualTo(Events.ACCESS_PRIVATE)
    }

    @Test
    fun an_event_can_be_rewritten_and_then_removed() {
        val eventId = gateway.insert(calendarId, event)
        val later = event.copy(title = "Mavick device test, moved", start = event.start.plusSeconds(3600), end = event.end.plusSeconds(3600))

        assertThat(gateway.update(eventId, later)).isTrue()
        val stored = requireNotNull(readEvent(eventId))
        assertThat(stored.getAsString(Events.TITLE)).isEqualTo("Mavick device test, moved")
        assertThat(stored.getAsLong(Events.DTSTART)).isEqualTo(later.start.toEpochMilli())

        assertThat(gateway.delete(eventId)).isTrue()
        assertThat(readEvent(eventId)).isNull()
    }

    @Test
    fun an_event_is_found_in_its_time_range_and_shows_as_free_because_Mavick_writes_free_events() {
        val eventId = gateway.insert(calendarId, event)

        val found = gateway.occurrences(event.start.minusSeconds(60), event.end.plusSeconds(60)).firstOrNull { it.eventId == eventId }

        assertThat(found).isNotNull()
        assertThat(found!!.calendarId).isEqualTo(calendarId)
        assertThat(found.title).isEqualTo(event.title)
        assertThat(found.start).isEqualTo(event.start)
        assertThat(found.end).isEqualTo(event.end)
        assertThat(found.allDay).isFalse()
        assertThat(found.busy).isFalse()
    }

    @Test
    fun an_event_outside_the_range_is_not_found() {
        val eventId = gateway.insert(calendarId, event)

        val found = gateway.occurrences(event.end.plusSeconds(60), event.end.plusSeconds(3600))

        assertThat(found.map { it.eventId }).doesNotContain(eventId)
    }

    @Test
    fun the_test_calendar_is_among_the_visible_calendars_and_takes_events() {
        val listed = gateway.visibleCalendars().firstOrNull { it.id == calendarId }

        assertThat(listed).isEqualTo(DeviceCalendar(calendarId, CALENDAR_NAME, ACCOUNT, writable = true))
    }

    @Test
    fun an_event_that_never_existed_is_not_updated_or_deleted() {
        assertThat(gateway.update(Long.MAX_VALUE, event)).isFalse()
        assertThat(gateway.delete(Long.MAX_VALUE)).isFalse()
    }

    private companion object {
        const val ACCOUNT = "mavick-device-test"
        const val CALENDAR_NAME = "Mavick device test"
    }
}

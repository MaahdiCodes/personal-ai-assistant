package dev.maahdi.mavick.calendar

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.testing.TEST_ZONE
import java.time.Instant
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/** A calendar storage in memory, standing in for Android's calendar provider. */
class FakeCalendarProvider : ContentProvider() {
    /** Calendars as column name to value, so the test doesn't depend on the order of the columns asked for. */
    val calendarRows = mutableListOf<Map<String, Any?>>()
    val events = linkedMapOf<Long, ContentValues>()

    /** Makes every call throw this. */
    var failure: RuntimeException? = null
    var insertReturnsNull = false
    var calls = 0
        private set
    private var nextId = 1L

    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        enter()
        val columns = requireNotNull(projection)
        return MatrixCursor(columns).apply { calendarRows.forEach { row -> addRow(columns.map { row[it] }) } }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        enter()
        if (insertReturnsNull) return null
        val id = nextId++
        events[id] = ContentValues(requireNotNull(values))
        return ContentUris.withAppendedId(uri, id)
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        enter()
        val row = events[ContentUris.parseId(uri)] ?: return 0
        row.putAll(requireNotNull(values))
        return 1
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        enter()
        return if (events.remove(ContentUris.parseId(uri)) != null) 1 else 0
    }

    override fun getType(uri: Uri): String? = null

    private fun enter() {
        calls++
        failure?.let { throw it }
    }
}

@RunWith(AndroidJUnit4::class)
class ContentResolverCalendarGatewayTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val application = context as Application
    private lateinit var provider: FakeCalendarProvider
    private lateinit var gateway: ContentResolverCalendarGateway

    private val event = CalendarEvent(
        title = "Call the bank",
        start = Instant.parse("2026-10-08T11:00:00Z"),
        end = Instant.parse("2026-10-08T11:30:00Z"),
        zone = TEST_ZONE,
    )

    @Before
    fun setUp() {
        provider = Robolectric.buildContentProvider(FakeCalendarProvider::class.java).create(CalendarContract.AUTHORITY).get()
        shadowOf(application).grantPermissions(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        gateway = ContentResolverCalendarGateway(context)
    }

    private fun calendar(id: Long, name: String, account: String, access: Int = Calendars.CAL_ACCESS_OWNER, visible: Int = 1) =
        mapOf<String, Any?>(
            Calendars._ID to id,
            Calendars.CALENDAR_DISPLAY_NAME to name,
            Calendars.ACCOUNT_NAME to account,
            Calendars.CALENDAR_ACCESS_LEVEL to access,
            Calendars.VISIBLE to visible,
        )

    private fun stored() = provider.events.values.single()

    // --- Permission ---

    @Test
    fun `the permission is there only when both calendar permissions are granted`() {
        assertThat(gateway.hasPermission()).isTrue()

        shadowOf(application).denyPermissions(Manifest.permission.WRITE_CALENDAR)
        assertThat(gateway.hasPermission()).isFalse()

        shadowOf(application).denyPermissions(Manifest.permission.READ_CALENDAR)
        assertThat(gateway.hasPermission()).isFalse()
    }

    @Test
    fun `without the permission every call says so and the calendar is never touched`() {
        shadowOf(application).denyPermissions(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

        val problems = listOf(
            runCatching { gateway.writableCalendars() },
            runCatching { gateway.insert(1, event) },
            runCatching { gateway.update(1, event) },
            runCatching { gateway.delete(1) },
        ).map { (it.exceptionOrNull() as CalendarAccessException).problem }

        assertThat(problems).containsExactly(
            CalendarProblem.NO_PERMISSION,
            CalendarProblem.NO_PERMISSION,
            CalendarProblem.NO_PERMISSION,
            CalendarProblem.NO_PERMISSION,
        )
        assertThat(provider.calls).isEqualTo(0)
    }

    // --- Listing calendars ---

    @Test
    fun `only visible calendars that take new events are listed, by account then name`() {
        provider.calendarRows += calendar(3, "Work", "me@work.example")
        provider.calendarRows += calendar(1, "Personal", "me@gmail.com")
        provider.calendarRows += calendar(2, "Holidays in Bangladesh", "holidays@group", access = Calendars.CAL_ACCESS_READ)
        provider.calendarRows += calendar(4, "Hidden", "me@gmail.com", visible = 0)
        provider.calendarRows += calendar(5, "Family", "me@gmail.com", access = Calendars.CAL_ACCESS_CONTRIBUTOR)
        provider.calendarRows += calendar(6, "Only free and busy", "me@gmail.com", access = Calendars.CAL_ACCESS_FREEBUSY)

        val calendars = gateway.writableCalendars()

        assertThat(calendars.map { it.id }).containsExactly(5L, 1L, 3L).inOrder()
        assertThat(calendars.first()).isEqualTo(DeviceCalendar(5, "Family", "me@gmail.com"))
    }

    @Test
    fun `a phone with no calendars lists none`() {
        assertThat(gateway.writableCalendars()).isEmpty()
    }

    // --- Writing events ---

    @Test
    fun `an event is added with its title time and calendar, and the new id comes back`() {
        val id = gateway.insert(calendarId = 7, event = event)

        assertThat(provider.events.keys).containsExactly(id)
        val values = stored()
        assertThat(values.getAsLong(Events.CALENDAR_ID)).isEqualTo(7L)
        assertThat(values.getAsString(Events.TITLE)).isEqualTo("Call the bank")
        assertThat(values.getAsLong(Events.DTSTART)).isEqualTo(event.start.toEpochMilli())
        assertThat(values.getAsLong(Events.DTEND)).isEqualTo(event.end.toEpochMilli())
        assertThat(values.getAsString(Events.EVENT_TIMEZONE)).isEqualTo("Asia/Dhaka")
    }

    @Test
    fun `an event is private and free with no alarm, and holds nothing but the title and time`() {
        gateway.insert(calendarId = 7, event = event)

        val values = stored()
        assertThat(values.getAsInteger(Events.ACCESS_LEVEL)).isEqualTo(Events.ACCESS_PRIVATE)
        assertThat(values.getAsInteger(Events.AVAILABILITY)).isEqualTo(Events.AVAILABILITY_FREE)
        assertThat(values.getAsInteger(Events.HAS_ALARM)).isEqualTo(0)
        assertThat(values.getAsInteger(Events.ALL_DAY)).isEqualTo(0)
        assertThat(values.keySet()).containsExactly(
            Events.CALENDAR_ID,
            Events.TITLE,
            Events.DTSTART,
            Events.DTEND,
            Events.EVENT_TIMEZONE,
            Events.ALL_DAY,
            Events.HAS_ALARM,
            Events.AVAILABILITY,
            Events.ACCESS_LEVEL,
        )
    }

    @Test
    fun `updating rewrites the title and time and says it worked`() {
        val id = gateway.insert(7, event)
        val later = event.copy(title = "Call the bank again", start = event.start.plusSeconds(3600), end = event.end.plusSeconds(3600))

        assertThat(gateway.update(id, later)).isTrue()

        assertThat(stored().getAsString(Events.TITLE)).isEqualTo("Call the bank again")
        assertThat(stored().getAsLong(Events.DTSTART)).isEqualTo(later.start.toEpochMilli())
        // The calendar an event is in is never part of an update.
        assertThat(stored().getAsLong(Events.CALENDAR_ID)).isEqualTo(7L)
    }

    @Test
    fun `updating an event that is gone says so`() {
        assertThat(gateway.update(99, event)).isFalse()
    }

    @Test
    fun `deleting removes the event and says it worked`() {
        val id = gateway.insert(7, event)

        assertThat(gateway.delete(id)).isTrue()
        assertThat(provider.events).isEmpty()
    }

    @Test
    fun `deleting an event that is already gone says so`() {
        assertThat(gateway.delete(99)).isFalse()
    }

    // --- Failures ---

    @Test
    fun `a refusal by the calendar storage is a permission problem`() {
        provider.failure = SecurityException("no")

        val error = runCatching { gateway.insert(7, event) }.exceptionOrNull() as CalendarAccessException

        assertThat(error.problem).isEqualTo(CalendarProblem.NO_PERMISSION)
    }

    @Test
    fun `any other failure of the calendar storage is an unavailable problem`() {
        provider.failure = IllegalStateException("broken")

        val errors = listOf(
            runCatching { gateway.writableCalendars() },
            runCatching { gateway.insert(7, event) },
            runCatching { gateway.update(1, event) },
            runCatching { gateway.delete(1) },
        ).map { (it.exceptionOrNull() as CalendarAccessException).problem }

        assertThat(errors).containsExactly(
            CalendarProblem.UNAVAILABLE,
            CalendarProblem.UNAVAILABLE,
            CalendarProblem.UNAVAILABLE,
            CalendarProblem.UNAVAILABLE,
        )
    }

    @Test
    fun `an insert the calendar storage turns down is an unavailable problem`() {
        provider.insertReturnsNull = true

        val error = runCatching { gateway.insert(7, event) }.exceptionOrNull() as CalendarAccessException

        assertThat(error.problem).isEqualTo(CalendarProblem.UNAVAILABLE)
    }

    @Test
    fun `an error carries only the name of the problem`() {
        provider.failure = IllegalStateException("Call the bank at 17:00")

        val error = runCatching { gateway.insert(7, event) }.exceptionOrNull() as CalendarAccessException

        assertThat(error.message).isEqualTo("UNAVAILABLE")
    }
}

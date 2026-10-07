package dev.maahdi.mavick.calendar

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.MavickDatabase
import dev.maahdi.mavick.data.calendar.CalendarLinkEntity
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.testing.FakeCalendarGateway
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.MutableClock
import dev.maahdi.mavick.testing.PERSONAL_CALENDAR
import dev.maahdi.mavick.testing.TEST_ZONE
import dev.maahdi.mavick.testing.WORK_CALENDAR
import dev.maahdi.mavick.testing.task
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClashServiceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("clash-service-test", Context.MODE_PRIVATE)
    private lateinit var database: MavickDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var service: ClashService
    private val gateway = FakeCalendarGateway()
    private val clock = MutableClock(MONDAY_10AM)

    private val today: LocalDate = MONDAY_10AM.toLocalDate()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MavickDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(preferences)
        settings.update { it.copy(clashCheckEnabled = true) }
        service = ClashService(gateway, database.calendarLinkDao(), settings, { clock }, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        database.close()
        preferences.edit().clear().commit()
    }

    private fun at(date: LocalDate, time: LocalTime): Instant = date.atTime(time).atZone(TEST_ZONE).toInstant()

    private fun busy(
        id: Long,
        date: LocalDate = today,
        from: LocalTime,
        minutes: Long = 60,
        calendarId: Long = PERSONAL_CALENDAR.id,
        title: String = "Dentist",
    ) = CalendarOccurrence(id, calendarId, title, at(date, from), at(date, from).plus(Duration.ofMinutes(minutes)), allDay = false, busy = true)

    private fun timed(id: String, time: LocalTime, date: LocalDate = today) = task(id = id, dueDate = date, dueTime = time)

    // --- Tasks ---

    @Test
    fun `a task overlapping a calendar event clashes with it`() = runTest {
        val dentist = busy(1, from = LocalTime.of(17, 0))
        gateway.agenda = listOf(dentist)

        val clashes = service.clashesFor(listOf(timed("a", LocalTime.of(17, 15)), timed("b", LocalTime.of(19, 0))))

        assertThat(clashes).containsExactly("a", listOf(dentist))
    }

    @Test
    fun `one calendar read covers every task, over the span of all of them`() = runTest {
        val tasks = listOf(timed("a", LocalTime.of(17, 0)), timed("b", LocalTime.of(9, 0), date = today.plusDays(3)))

        service.clashesFor(tasks)

        assertThat(gateway.occurrenceRequests).containsExactly(at(today, LocalTime.of(17, 0)) to at(today.plusDays(3), LocalTime.of(9, 30)))
    }

    @Test
    fun `nothing is read while the warnings are off`() = runTest {
        settings.update { it.copy(clashCheckEnabled = false) }
        gateway.agenda = listOf(busy(1, from = LocalTime.of(17, 0)))

        assertThat(service.clashesFor(listOf(timed("a", LocalTime.of(17, 0))))).isEmpty()
        assertThat(service.clashesAt(today, LocalTime.of(17, 0))).isEmpty()
        assertThat(gateway.occurrenceRequests).isEmpty()
    }

    @Test
    fun `nothing is read without the permission, and nothing is said about it`() = runTest {
        gateway.permission = false

        assertThat(service.clashesFor(listOf(timed("a", LocalTime.of(17, 0))))).isEmpty()
        assertThat(service.clashesAt(today, LocalTime.of(17, 0))).isEmpty()
        assertThat(gateway.occurrenceRequests).isEmpty()
    }

    @Test
    fun `tasks without a time need no calendar read`() = runTest {
        assertThat(service.clashesFor(listOf(task(id = "a", dueDate = today), task(id = "b")))).isEmpty()
        assertThat(service.clashesFor(emptyList())).isEmpty()
        assertThat(gateway.occurrenceRequests).isEmpty()
    }

    @Test
    fun `tasks from before today are not looked at`() = runTest {
        service.clashesFor(listOf(timed("old", LocalTime.of(17, 0), date = today.minusDays(1))))

        assertThat(gateway.occurrenceRequests).isEmpty()
    }

    @Test
    fun `a task earlier today still counts`() = runTest {
        gateway.agenda = listOf(busy(1, from = LocalTime.of(8, 0)))

        assertThat(service.clashesFor(listOf(timed("a", LocalTime.of(8, 30))))).hasSize(1)
    }

    @Test
    fun `tasks more than 60 days away are not looked at`() = runTest {
        service.clashesFor(listOf(timed("far", LocalTime.of(17, 0), date = today.plusDays(61))))
        assertThat(gateway.occurrenceRequests).isEmpty()

        service.clashesFor(listOf(timed("near", LocalTime.of(17, 0), date = today.plusDays(59))))
        assertThat(gateway.occurrenceRequests).hasSize(1)
    }

    // --- Which events ---

    @Test
    fun `Mavick's own events are left out`() = runTest {
        val own = busy(50, from = LocalTime.of(17, 0), title = "Call the bank")
        gateway.agenda = listOf(own)
        database.calendarLinkDao().upsert(CalendarLinkEntity("some-task", PERSONAL_CALENDAR.id, 50, "fingerprint"))

        assertThat(service.clashesFor(listOf(timed("a", LocalTime.of(17, 0))))).isEmpty()
    }

    @Test
    fun `only the chosen calendars are checked`() = runTest {
        settings.update { it.copy(clashCalendarIds = setOf(WORK_CALENDAR.id)) }
        val personal = busy(1, from = LocalTime.of(17, 0), calendarId = PERSONAL_CALENDAR.id)
        val work = busy(2, from = LocalTime.of(17, 0), calendarId = WORK_CALENDAR.id, title = "Standup")
        gateway.agenda = listOf(personal, work)

        assertThat(service.clashesFor(listOf(timed("a", LocalTime.of(17, 0))))).containsExactly("a", listOf(work))
    }

    @Test
    fun `a chosen calendar that is gone is forgotten, and with none left every calendar is checked`() = runTest {
        settings.update { it.copy(clashCalendarIds = setOf(99)) }
        val dentist = busy(1, from = LocalTime.of(17, 0))
        gateway.agenda = listOf(dentist)

        assertThat(service.clashesFor(listOf(timed("a", LocalTime.of(17, 0))))).containsExactly("a", listOf(dentist))
    }

    @Test
    fun `free and all-day events are left out`() = runTest {
        gateway.agenda = listOf(
            busy(1, from = LocalTime.of(17, 0)).copy(busy = false),
            busy(2, from = LocalTime.of(0, 0), minutes = 24 * 60).copy(allDay = true),
        )

        assertThat(service.clashesFor(listOf(timed("a", LocalTime.of(17, 0))))).isEmpty()
    }

    // --- Failures ---

    @Test
    fun `a calendar that cannot be read means no clashes, not an error`() = runTest {
        gateway.occurrencesFailure = CalendarProblem.UNAVAILABLE

        assertThat(service.clashesFor(listOf(timed("a", LocalTime.of(17, 0))))).isEmpty()
        assertThat(service.clashesAt(today, LocalTime.of(17, 0))).isEmpty()
    }

    // --- The editor's single slot ---

    @Test
    fun `a date and time clash with the events that overlap that slot`() = runTest {
        val dentist = busy(1, from = LocalTime.of(17, 0))
        val later = busy(2, from = LocalTime.of(18, 0))
        gateway.agenda = listOf(dentist, later)

        assertThat(service.clashesAt(today, LocalTime.of(17, 20))).containsExactly(dentist)
        assertThat(gateway.occurrenceRequests).containsExactly(at(today, LocalTime.of(17, 20)) to at(today, LocalTime.of(17, 50)))
    }

    @Test
    fun `the slot of a task being edited ignores its own event`() = runTest {
        gateway.agenda = listOf(busy(50, from = LocalTime.of(17, 0)))
        database.calendarLinkDao().upsert(CalendarLinkEntity("this-task", PERSONAL_CALENDAR.id, 50, "fingerprint"))

        assertThat(service.clashesAt(today, LocalTime.of(17, 0))).isEmpty()
    }
}

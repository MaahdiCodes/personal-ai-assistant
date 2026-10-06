package dev.maahdi.mavick.calendar

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.MavickDatabase
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.data.task.TaskRepository
import dev.maahdi.mavick.testing.FakeCalendarGateway
import dev.maahdi.mavick.testing.FakeReminderScheduler
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.MutableClock
import dev.maahdi.mavick.testing.PERSONAL_CALENDAR
import dev.maahdi.mavick.testing.TEST_ZONE
import dev.maahdi.mavick.testing.WORK_CALENDAR
import dev.maahdi.mavick.time.RepeatRule
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tasks and their calendar events, through the real task repository, a real database and a fake calendar. */
@RunWith(AndroidJUnit4::class)
class CalendarSyncTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("calendar-sync-test", Context.MODE_PRIVATE)
    private lateinit var database: MavickDatabase
    private lateinit var settings: SettingsRepository
    private val gateway = FakeCalendarGateway()
    private var clock = MutableClock(MONDAY_10AM)
    private lateinit var sync: CalendarSync
    private lateinit var repository: TaskRepository

    private val today: LocalDate = MONDAY_10AM.toLocalDate()
    private val tomorrow: LocalDate = today.plusDays(1)
    private val fivePm = LocalTime.of(17, 0)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MavickDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(preferences)
        sync = CalendarSync(
            tasks = database.taskDao(),
            links = database.calendarLinkDao(),
            gateway = gateway,
            settings = settings,
            clock = { clock },
            dispatcher = Dispatchers.Unconfined,
        )
        repository = TaskRepository(database.taskDao(), FakeReminderScheduler(), clock = { clock }, calendar = sync)
    }

    @After
    fun tearDown() {
        database.close()
        preferences.edit().clear().commit()
    }

    private fun switchOn(calendar: DeviceCalendar = PERSONAL_CALENDAR) {
        settings.update { it.copy(calendarEnabled = true, calendarId = calendar.id, calendarName = calendar.label) }
    }

    private fun switchOff() {
        settings.update { it.copy(calendarEnabled = false) }
    }

    private suspend fun timed(
        title: String = "Call the bank",
        date: LocalDate = today,
        time: LocalTime = fivePm,
        repeat: RepeatRule? = null,
    ) = repository.create(TaskDraft(title = title, dueDate = date, dueTime = time, repeatRule = repeat))

    private fun at(date: LocalDate, time: LocalTime, zone: ZoneId = TEST_ZONE): Instant = date.atTime(time).atZone(zone).toInstant()

    private fun theEvent(): FakeCalendarGateway.Stored = gateway.events.values.single()

    // --- Writing events ---

    @Test
    fun `a task with a date and a time becomes a 30-minute event in the chosen calendar`() = runTest {
        switchOn()

        timed("Call the bank")

        val stored = theEvent()
        assertThat(stored.calendarId).isEqualTo(PERSONAL_CALENDAR.id)
        assertThat(stored.event.title).isEqualTo("Call the bank")
        assertThat(stored.event.start).isEqualTo(at(today, fivePm))
        assertThat(stored.event.end).isEqualTo(at(today, fivePm).plus(Duration.ofMinutes(30)))
        assertThat(stored.event.zone).isEqualTo(TEST_ZONE)
        assertThat(sync.eventCount()).isEqualTo(1)
    }

    @Test
    fun `a task with only a date gets no event`() = runTest {
        switchOn()

        repository.create(TaskDraft(title = "Pay rent", dueDate = tomorrow))

        assertThat(gateway.events).isEmpty()
        assertThat(gateway.calls).isEmpty()
    }

    @Test
    fun `a task with no date gets no event`() = runTest {
        switchOn()

        repository.create(TaskDraft(title = "Someday"))

        assertThat(gateway.calls).isEmpty()
    }

    @Test
    fun `nothing is written while the feature is off`() = runTest {
        timed()

        assertThat(gateway.calls).isEmpty()
        assertThat(sync.eventCount()).isEqualTo(0)
    }

    @Test
    fun `a task done and not changed since is left out when the feature is switched on`() = runTest {
        val task = timed()
        repository.complete(task.id)

        switchOn()
        sync.reconcileAll()

        assertThat(gateway.events).isEmpty()
    }

    // --- Following changes ---

    @Test
    fun `changing the title rewrites the event`() = runTest {
        switchOn()
        val task = timed("Call the bank")

        repository.update(task.id, TaskDraft(title = "Call the bank about the loan", dueDate = today, dueTime = fivePm))

        assertThat(theEvent().event.title).isEqualTo("Call the bank about the loan")
        assertThat(gateway.calls).containsExactly("insert", "update").inOrder()
    }

    @Test
    fun `moving the task moves the event`() = runTest {
        switchOn()
        val task = timed()

        repository.update(task.id, TaskDraft(title = task.title, dueDate = tomorrow, dueTime = LocalTime.of(9, 30)))

        assertThat(theEvent().event.start).isEqualTo(at(tomorrow, LocalTime.of(9, 30)))
    }

    @Test
    fun `moving a task to tomorrow moves its event`() = runTest {
        switchOn()
        val task = timed()

        repository.moveToTomorrow(task.id)

        assertThat(theEvent().event.start).isEqualTo(at(tomorrow, fivePm))
    }

    @Test
    fun `a change that does not touch the event writes nothing`() = runTest {
        switchOn()
        val task = timed()
        gateway.calls.clear()

        repository.snooze(task.id, 10)
        repository.update(task.id, TaskDraft(title = task.title, dueDate = today, dueTime = fivePm, notes = "Bring the form"))

        assertThat(gateway.calls).isEmpty()
    }

    @Test
    fun `finishing the task removes its event`() = runTest {
        switchOn()
        val task = timed()

        repository.complete(task.id)

        assertThat(gateway.events).isEmpty()
        assertThat(sync.eventCount()).isEqualTo(0)
    }

    @Test
    fun `reopening a finished task writes its event again`() = runTest {
        switchOn()
        val task = timed()
        repository.complete(task.id)

        repository.reopen(task.id)

        assertThat(theEvent().event.title).isEqualTo(task.title)
    }

    @Test
    fun `finishing a repeating task moves its event to the next day`() = runTest {
        switchOn()
        val task = timed(repeat = RepeatRule.Daily())

        repository.complete(task.id)

        assertThat(theEvent().event.start).isEqualTo(at(tomorrow, fivePm))
        assertThat(sync.eventCount()).isEqualTo(1)
    }

    @Test
    fun `deleting a task removes its event and undoing the delete writes it again`() = runTest {
        switchOn()
        val task = timed()
        val before = repository.find(task.id)!!

        repository.delete(task.id)
        assertThat(gateway.events).isEmpty()

        repository.restore(before)
        assertThat(theEvent().event.title).isEqualTo(task.title)
        assertThat(sync.eventCount()).isEqualTo(1)
    }

    @Test
    fun `taking the time off a task removes its event`() = runTest {
        switchOn()
        val task = timed()

        repository.update(task.id, TaskDraft(title = task.title, dueDate = today))

        assertThat(gateway.events).isEmpty()
        assertThat(sync.eventCount()).isEqualTo(0)
    }

    @Test
    fun `giving a date-only task a time writes its event`() = runTest {
        switchOn()
        val task = repository.create(TaskDraft(title = "Pay rent", dueDate = tomorrow))

        repository.update(task.id, TaskDraft(title = task.title, dueDate = tomorrow, dueTime = LocalTime.of(10, 8)))

        assertThat(theEvent().event.start).isEqualTo(at(tomorrow, LocalTime.of(10, 8)))
    }

    // --- Switching on, off, and changing calendar ---

    @Test
    fun `switching on writes events for the tasks that already have a time`() = runTest {
        timed("First")
        timed("Second", time = LocalTime.of(18, 0))
        repository.create(TaskDraft(title = "Date only", dueDate = today))

        switchOn()
        sync.reconcileAll()

        assertThat(gateway.titlesIn(PERSONAL_CALENDAR.id)).containsExactly("First", "Second")
        assertThat(sync.eventCount()).isEqualTo(2)
    }

    @Test
    fun `switching on does not fill the past with overdue tasks`() = runTest {
        timed("Overdue", date = today.minusDays(1))
        timed("Earlier today", time = LocalTime.of(9, 0))

        switchOn()
        sync.reconcileAll()

        assertThat(gateway.titlesIn(PERSONAL_CALENDAR.id)).containsExactly("Earlier today")
    }

    @Test
    fun `an event already written stays when its time has passed`() = runTest {
        switchOn()
        timed()
        gateway.calls.clear()

        clock.advance(Duration.ofDays(3))
        sync.reconcileAll()

        assertThat(gateway.calls).isEmpty()
        assertThat(gateway.events).hasSize(1)
    }

    @Test
    fun `switching off removes the events Mavick added and forgets them`() = runTest {
        switchOn()
        timed()
        timed("Second", time = LocalTime.of(18, 0))

        switchOff()
        sync.reconcileAll()

        assertThat(gateway.events).isEmpty()
        assertThat(sync.eventCount()).isEqualTo(0)
    }

    @Test
    fun `switching off leaves other events in the calendar alone`() = runTest {
        switchOn()
        gateway.events[7] = FakeCalendarGateway.Stored(PERSONAL_CALENDAR.id, CalendarEvent("Dentist", Instant.EPOCH, Instant.EPOCH, TEST_ZONE))
        timed()

        switchOff()
        sync.reconcileAll()

        assertThat(gateway.events.keys).containsExactly(7L)
    }

    @Test
    fun `choosing another calendar moves the events`() = runTest {
        switchOn(PERSONAL_CALENDAR)
        timed()

        switchOn(WORK_CALENDAR)
        sync.reconcileAll()

        assertThat(gateway.titlesIn(PERSONAL_CALENDAR.id)).isEmpty()
        assertThat(gateway.titlesIn(WORK_CALENDAR.id)).containsExactly("Call the bank")
        assertThat(sync.eventCount()).isEqualTo(1)
    }

    @Test
    fun `choosing another calendar still works when the old one is gone`() = runTest {
        switchOn(PERSONAL_CALENDAR)
        timed()
        gateway.events.clear() // the account was removed from the phone, and its events with it

        switchOn(WORK_CALENDAR)
        sync.reconcileAll()

        assertThat(gateway.titlesIn(WORK_CALENDAR.id)).containsExactly("Call the bank")
    }

    @Test
    fun `reconciling twice writes nothing the second time`() = runTest {
        timed()
        switchOn()
        sync.reconcileAll()
        gateway.calls.clear()

        sync.reconcileAll()

        assertThat(gateway.calls).isEmpty()
    }

    @Test
    fun `while off with no events reconciling never touches the calendar`() = runTest {
        timed()
        gateway.permission = false

        sync.reconcileAll()

        assertThat(gateway.calls).isEmpty()
    }

    // --- The phone's time zone ---

    @Test
    fun `a time zone change rewrites the events at the same local time`() = runTest {
        switchOn()
        timed()
        val kolkata = ZoneId.of("Asia/Kolkata")

        clock = MutableClock(clock.instant(), kolkata)
        sync.reconcileAll()

        assertThat(theEvent().event.start).isEqualTo(at(today, fivePm, kolkata))
        assertThat(theEvent().event.zone).isEqualTo(kolkata)
    }

    // --- Someone changes the calendar behind Mavick's back ---

    @Test
    fun `an event deleted in the calendar app stays deleted while the task is unchanged`() = runTest {
        switchOn()
        timed()
        gateway.deleteInCalendarApp(gateway.events.keys.single())
        gateway.calls.clear()

        sync.reconcileAll()

        assertThat(gateway.calls).isEmpty()
        assertThat(gateway.events).isEmpty()
    }

    @Test
    fun `an event deleted in the calendar app is written again when the task changes`() = runTest {
        switchOn()
        val task = timed()
        gateway.deleteInCalendarApp(gateway.events.keys.single())

        repository.update(task.id, TaskDraft(title = "Call the bank, new number", dueDate = today, dueTime = fivePm))

        assertThat(theEvent().event.title).isEqualTo("Call the bank, new number")
        assertThat(sync.eventCount()).isEqualTo(1)
    }

    @Test
    fun `finishing a task whose event was deleted in the calendar app is fine`() = runTest {
        switchOn()
        val task = timed()
        gateway.deleteInCalendarApp(gateway.events.keys.single())

        repository.complete(task.id)

        assertThat(sync.eventCount()).isEqualTo(0)
    }

    // --- Problems ---

    @Test
    fun `a calendar problem never stops a task from being saved`() = runTest {
        switchOn()
        gateway.failure = CalendarProblem.UNAVAILABLE

        val task = timed()

        assertThat(repository.find(task.id)).isNotNull()
        assertThat(gateway.events).isEmpty()
        assertThat(sync.eventCount()).isEqualTo(0)
    }

    @Test
    fun `an event that failed is written by the next reconcile`() = runTest {
        switchOn()
        gateway.failure = CalendarProblem.UNAVAILABLE
        timed()

        gateway.failure = null
        sync.reconcileAll()

        assertThat(gateway.events).hasSize(1)
    }

    @Test
    fun `an event that could not be removed stays linked until it can be`() = runTest {
        switchOn()
        val task = timed()
        gateway.permission = false

        repository.complete(task.id)
        assertThat(gateway.events).hasSize(1)
        assertThat(sync.eventCount()).isEqualTo(1)

        gateway.permission = true
        sync.reconcileAll()

        assertThat(gateway.events).isEmpty()
        assertThat(sync.eventCount()).isEqualTo(0)
    }

    @Test
    fun `the event of a task that was purged is removed once the calendar is reachable`() = runTest {
        switchOn()
        val task = timed()
        gateway.permission = false
        repository.delete(task.id)
        clock.advance(Duration.ofDays(31))
        repository.purgeOldDeleted()
        assertThat(repository.find(task.id)).isNull()

        gateway.permission = true
        sync.reconcileAll()

        assertThat(gateway.events).isEmpty()
        assertThat(sync.eventCount()).isEqualTo(0)
    }

    @Test
    fun `without the permission a reconcile stops after the first attempt`() = runTest {
        timed("First")
        timed("Second", time = LocalTime.of(18, 0))
        switchOn()
        gateway.permission = false

        sync.reconcileAll()

        assertThat(gateway.calls).hasSize(1)
    }

    @Test
    fun `one task whose event fails does not stop the others`() = runTest {
        timed("First")
        timed("Second", time = LocalTime.of(18, 0))
        switchOn()
        gateway.failure = CalendarProblem.UNAVAILABLE
        gateway.failuresLeft = 1

        sync.reconcileAll()
        assertThat(gateway.events).hasSize(1)
        assertThat(sync.eventCount()).isEqualTo(1)

        sync.reconcileAll()
        assertThat(gateway.events).hasSize(2)
        assertThat(sync.eventCount()).isEqualTo(2)
    }

    @Test
    fun `a task that is no longer there has nothing to write`() = runTest {
        switchOn()

        sync.taskChanged("no-such-task")

        assertThat(gateway.calls).isEmpty()
    }
}

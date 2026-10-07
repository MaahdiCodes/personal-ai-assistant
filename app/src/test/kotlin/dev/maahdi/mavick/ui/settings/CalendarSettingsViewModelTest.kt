package dev.maahdi.mavick.ui.settings

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.calendar.CalendarProblem
import dev.maahdi.mavick.calendar.CalendarSync
import dev.maahdi.mavick.calendar.DeviceCalendar
import dev.maahdi.mavick.data.MavickDatabase
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.data.task.TaskRepository
import dev.maahdi.mavick.testing.FakeCalendarGateway
import dev.maahdi.mavick.testing.FakeReminderScheduler
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.MainDispatcherRule
import dev.maahdi.mavick.testing.MutableClock
import dev.maahdi.mavick.testing.PERSONAL_CALENDAR
import dev.maahdi.mavick.testing.WORK_CALENDAR
import java.time.LocalTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarSettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("calendar-settings-test", Context.MODE_PRIVATE)
    private val gateway = FakeCalendarGateway()
    private val clock = MutableClock(MONDAY_10AM)
    private lateinit var database: MavickDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var sync: CalendarSync
    private lateinit var tasks: TaskRepository
    private var opening: suspend () -> CalendarSync = { error("not ready") }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MavickDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(preferences)
        sync = CalendarSync(database.taskDao(), database.calendarLinkDao(), gateway, settings, { clock }, Dispatchers.Unconfined)
        tasks = TaskRepository(database.taskDao(), FakeReminderScheduler(), { clock }, calendar = sync)
        opening = { sync }
    }

    @After
    fun tearDown() {
        database.close()
        preferences.edit().clear().commit()
    }

    private fun viewModel() = CalendarSettingsViewModel(gateway, { opening() }, settings, Dispatchers.Unconfined)

    /** A view model whose first look at the phone has finished (the event count comes from a real database query on another thread). */
    private suspend fun loadedViewModel() = viewModel().also { it.refresh().join() }

    private suspend fun timedTask(title: String = "Call the bank") = tasks.create(
        TaskDraft(title = title, dueDate = MONDAY_10AM.toLocalDate(), dueTime = LocalTime.of(17, 0)),
    )

    // --- Reading the state ---

    @Test
    fun `it starts by reading the permission and the calendars`() = runTest {
        val state = loadedViewModel().state.value

        assertThat(state.permissionGranted).isTrue()
        assertThat(state.calendars).containsExactly(PERSONAL_CALENDAR, WORK_CALENDAR).inOrder()
        assertThat(state.loaded).isTrue()
        assertThat(state.eventCount).isEqualTo(0)
        assertThat(state.picking).isFalse()
    }

    @Test
    fun `without the permission no calendars are listed`() = runTest {
        gateway.permission = false

        val state = loadedViewModel().state.value

        assertThat(state.permissionGranted).isFalse()
        assertThat(state.calendars).isEmpty()
        assertThat(state.loaded).isTrue()
    }

    @Test
    fun `a calendar list that fails shows no calendars instead of crashing`() = runTest {
        gateway.listFailure = CalendarProblem.UNAVAILABLE

        val state = loadedViewModel().state.value

        assertThat(state.permissionGranted).isTrue()
        assertThat(state.calendars).isEmpty()
    }

    @Test
    fun `refreshing picks up a permission given in Android's settings`() = runTest {
        gateway.permission = false
        val viewModel = viewModel()

        gateway.permission = true
        viewModel.refresh().join()

        assertThat(viewModel.state.value.permissionGranted).isTrue()
        assertThat(viewModel.state.value.calendars).isNotEmpty()
    }

    @Test
    fun `every visible calendar is known, and the writable ones are picked from them`() = runTest {
        val holidays = DeviceCalendar(5, "Holidays", "holidays@group", writable = false)
        gateway.calendars = listOf(PERSONAL_CALENDAR, holidays)

        val state = loadedViewModel().state.value

        assertThat(state.allCalendars).containsExactly(PERSONAL_CALENDAR, holidays).inOrder()
        assertThat(state.calendars).containsExactly(PERSONAL_CALENDAR)
    }

    @Test
    fun `a slow refresh cannot overwrite the answer of a newer one`() = runTest {
        val release = CompletableDeferred<Unit>()
        var first = true
        opening = {
            if (first) {
                first = false
                release.await() // the first refresh is stuck opening the database
            }
            sync
        }
        val viewModel = viewModel()

        gateway.permission = false
        viewModel.refresh().join()
        release.complete(Unit)

        assertThat(viewModel.state.value.permissionGranted).isFalse()
        assertThat(viewModel.state.value.calendars).isEmpty()
    }

    // --- The picker and the permission prompt ---

    @Test
    fun `opening the picker shows it with the calendars looked up again`() = runTest {
        val viewModel = viewModel()
        gateway.calendars = listOf(WORK_CALENDAR)

        viewModel.openPicker().join()

        assertThat(viewModel.state.value.picking).isTrue()
        assertThat(viewModel.state.value.calendars).containsExactly(WORK_CALENDAR)
    }

    @Test
    fun `closing the picker hides it`() = runTest {
        val viewModel = viewModel()
        viewModel.openPicker().join()

        viewModel.closePicker()

        assertThat(viewModel.state.value.picking).isFalse()
    }

    @Test
    fun `allowing the permission goes on to the picker`() = runTest {
        val viewModel = viewModel()

        viewModel.onPermissionResult(granted = true).join()

        assertThat(viewModel.state.value.picking).isTrue()
        assertThat(viewModel.state.value.permissionDenied).isFalse()
    }

    @Test
    fun `refusing the permission says so and does not open the picker`() = runTest {
        gateway.permission = false
        val viewModel = viewModel()

        viewModel.onPermissionResult(granted = false).join()

        assertThat(viewModel.state.value.permissionDenied).isTrue()
        assertThat(viewModel.state.value.picking).isFalse()
        assertThat(settings.current.calendarEnabled).isFalse()
    }

    // --- Clash warnings ---

    @Test
    fun `clash warnings can be switched on and off`() = runTest {
        val viewModel = loadedViewModel()

        viewModel.setClashCheck(true)
        assertThat(settings.current.clashCheckEnabled).isTrue()

        viewModel.setClashCheck(false)
        assertThat(settings.current.clashCheckEnabled).isFalse()
    }

    @Test
    fun `allowing the permission for clash warnings switches them on`() = runTest {
        val viewModel = loadedViewModel()

        viewModel.onClashPermissionResult(granted = true).join()

        assertThat(settings.current.clashCheckEnabled).isTrue()
        assertThat(viewModel.state.value.picking).isFalse()
    }

    @Test
    fun `refusing the permission for clash warnings leaves them off and says so`() = runTest {
        gateway.permission = false
        val viewModel = loadedViewModel()

        viewModel.onClashPermissionResult(granted = false).join()

        assertThat(settings.current.clashCheckEnabled).isFalse()
        assertThat(viewModel.state.value.permissionDenied).isTrue()
    }

    @Test
    fun `the calendars to check are chosen in a picker and saved`() = runTest {
        val viewModel = loadedViewModel()

        viewModel.openCheckPicker().join()
        assertThat(viewModel.state.value.pickingChecked).isTrue()

        viewModel.setCheckedCalendars(setOf(WORK_CALENDAR.id))

        assertThat(settings.current.clashCalendarIds).containsExactly(WORK_CALENDAR.id)
        assertThat(viewModel.state.value.pickingChecked).isFalse()
    }

    @Test
    fun `closing the picker of calendars to check changes nothing`() = runTest {
        settings.update { it.copy(clashCalendarIds = setOf(PERSONAL_CALENDAR.id)) }
        val viewModel = loadedViewModel()
        viewModel.openCheckPicker().join()

        viewModel.closeCheckPicker()

        assertThat(viewModel.state.value.pickingChecked).isFalse()
        assertThat(settings.current.clashCalendarIds).containsExactly(PERSONAL_CALENDAR.id)
    }

    // --- Choosing and switching off ---

    @Test
    fun `choosing a calendar switches the feature on and writes the tasks that have a time`() = runTest {
        timedTask("Call the bank")
        val viewModel = viewModel()
        viewModel.openPicker().join()

        viewModel.choose(PERSONAL_CALENDAR).join()

        assertThat(settings.current.calendarEnabled).isTrue()
        assertThat(settings.current.calendarId).isEqualTo(PERSONAL_CALENDAR.id)
        assertThat(settings.current.calendarName).isEqualTo("Personal (me@example.com)")
        assertThat(gateway.titlesIn(PERSONAL_CALENDAR.id)).containsExactly("Call the bank")
        val state = viewModel.state.value
        assertThat(state.picking).isFalse()
        assertThat(state.working).isFalse()
        assertThat(state.eventCount).isEqualTo(1)
    }

    @Test
    fun `choosing another calendar moves the events there`() = runTest {
        timedTask()
        val viewModel = viewModel()
        viewModel.choose(PERSONAL_CALENDAR).join()

        viewModel.choose(WORK_CALENDAR).join()

        assertThat(gateway.titlesIn(PERSONAL_CALENDAR.id)).isEmpty()
        assertThat(gateway.titlesIn(WORK_CALENDAR.id)).containsExactly("Call the bank")
        assertThat(settings.current.calendarId).isEqualTo(WORK_CALENDAR.id)
        assertThat(viewModel.state.value.eventCount).isEqualTo(1)
    }

    @Test
    fun `switching off removes the events and remembers the calendar chosen`() = runTest {
        timedTask()
        val viewModel = viewModel()
        viewModel.choose(PERSONAL_CALENDAR).join()

        viewModel.turnOff().join()

        assertThat(settings.current.calendarEnabled).isFalse()
        assertThat(settings.current.calendarId).isEqualTo(PERSONAL_CALENDAR.id)
        assertThat(gateway.events).isEmpty()
        assertThat(viewModel.state.value.eventCount).isEqualTo(0)
        assertThat(viewModel.state.value.working).isFalse()
    }

    @Test
    fun `switching off without the permission keeps the events counted until they can go`() = runTest {
        timedTask()
        val viewModel = viewModel()
        viewModel.choose(PERSONAL_CALENDAR).join()
        gateway.permission = false

        viewModel.turnOff().join()

        assertThat(settings.current.calendarEnabled).isFalse()
        assertThat(gateway.events).hasSize(1)
        assertThat(viewModel.state.value.eventCount).isEqualTo(1)
        assertThat(viewModel.state.value.permissionGranted).isFalse()
    }

    @Test
    fun `a database that cannot be opened does not crash the screen`() = runTest {
        opening = { throw IllegalStateException("cannot open") }
        val viewModel = viewModel()

        viewModel.choose(PERSONAL_CALENDAR).join()

        assertThat(viewModel.state.value.eventCount).isEqualTo(0)
        assertThat(viewModel.state.value.working).isFalse()
        // The choice is still saved; the events follow once the database opens.
        assertThat(settings.current.calendarId).isEqualTo(PERSONAL_CALENDAR.id)
    }
}

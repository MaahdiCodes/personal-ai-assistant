package dev.maahdi.mavick.ui.tasks

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.calendar.CalendarOccurrence
import dev.maahdi.mavick.data.MavickDatabase
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.data.suggestion.SuggestionRepository
import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.data.task.TaskEntity
import dev.maahdi.mavick.data.task.TaskRepository
import dev.maahdi.mavick.testing.FakeReminderScheduler
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.MainDispatcherRule
import dev.maahdi.mavick.testing.MutableClock
import dev.maahdi.mavick.testing.TEST_ZONE
import dev.maahdi.mavick.time.DEFAULT_WORK_DAYS
import dev.maahdi.mavick.time.DateOrder
import dev.maahdi.mavick.time.WhenParser
import java.time.LocalTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What the task list shows, as far as it touches the calendar clash warnings. */
@RunWith(AndroidJUnit4::class)
class TasksViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("tasks-vm-test", Context.MODE_PRIVATE)
    private val clock = MutableClock(MONDAY_10AM)
    private lateinit var database: MavickDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var settings: SettingsRepository

    private val today = MONDAY_10AM.toLocalDate()

    private val dentist = CalendarOccurrence(
        1, 1, "Dentist",
        today.atTime(17, 0).atZone(TEST_ZONE).toInstant(), today.atTime(18, 0).atZone(TEST_ZONE).toInstant(),
        allDay = false, busy = true,
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MavickDatabase::class.java).allowMainThreadQueries().build()
        tasks = TaskRepository(database.taskDao(), FakeReminderScheduler(), clock = { clock })
        settings = SettingsRepository(preferences)
    }

    @After
    fun tearDown() {
        database.close()
        preferences.edit().clear().commit()
    }

    private fun viewModel(findClashes: suspend (List<TaskEntity>) -> Map<String, List<CalendarOccurrence>> = { emptyMap() }) = TasksViewModel(
        openTasks = { tasks },
        openSuggestions = { SuggestionRepository(database.suggestionDao(), clock = { clock }) },
        settings = settings,
        parser = { WhenParser(DateOrder.DAY_MONTH, DEFAULT_WORK_DAYS) },
        clock = { clock },
        findClashes = findClashes,
    )

    private suspend fun timedTask(title: String = "Call the bank") =
        tasks.create(TaskDraft(title = title, dueDate = today, dueTime = LocalTime.of(17, 15)))

    @Test
    fun `a task that clashes comes with the events it overlaps`() = runTest {
        val task = timedTask()
        val viewModel = viewModel { found -> found.associate { it.id to listOf(dentist) } }
        backgroundScope.launch { viewModel.state.collect {} }

        val state = viewModel.state.first { it.clashes.isNotEmpty() }

        assertThat(state.clashes).containsExactly(task.id, listOf(dentist))
        assertThat(state.dueToday.map { it.id }).containsExactly(task.id)
        assertThat(state.zone).isEqualTo(TEST_ZONE)
    }

    @Test
    fun `the list shows at once, before the calendar has been read`() = runTest {
        val task = timedTask()
        val answer = CompletableDeferred<Map<String, List<CalendarOccurrence>>>()
        val viewModel = viewModel { answer.await() }
        backgroundScope.launch { viewModel.state.collect {} }

        val before = viewModel.state.first { !it.loading }
        assertThat(before.dueToday.map { it.id }).containsExactly(task.id)
        assertThat(before.clashes).isEmpty()

        answer.complete(mapOf(task.id to listOf(dentist)))
        assertThat(viewModel.state.first { it.clashes.isNotEmpty() }.clashes).containsKey(task.id)
    }

    @Test
    fun `coming back to the screen reads the calendar again`() = runTest {
        timedTask()
        var reads = 0
        val viewModel = viewModel { reads++; emptyMap() }
        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.state.first { !it.loading }
        val before = reads

        viewModel.refreshToday()

        assertThat(reads).isGreaterThan(before)
    }

    @Test
    fun `switching clash warnings on or choosing other calendars reads the calendar again`() = runTest {
        timedTask()
        var reads = 0
        val viewModel = viewModel { reads++; emptyMap() }
        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.state.first { !it.loading }

        val start = reads
        settings.update { it.copy(clashCheckEnabled = true) }
        val afterSwitch = reads
        settings.update { it.copy(clashCalendarIds = setOf(2)) }
        val afterChoice = reads
        settings.update { it.copy(briefingEnabled = false) } // nothing to do with clashes

        assertThat(afterSwitch).isGreaterThan(start)
        assertThat(afterChoice).isGreaterThan(afterSwitch)
        assertThat(reads).isEqualTo(afterChoice)
    }

    @Test
    fun `a calendar problem leaves the list working, without warnings`() = runTest {
        val task = timedTask()
        val viewModel = viewModel { throw IllegalStateException("calendar storage broke") }
        backgroundScope.launch { viewModel.state.collect {} }

        val state = viewModel.state.first { !it.loading }

        assertThat(state.storageError).isNull()
        assertThat(state.dueToday.map { it.id }).containsExactly(task.id)
        assertThat(state.clashes).isEmpty()
    }

    @Test
    fun `a new task is checked as soon as it is added`() = runTest {
        val checked = mutableListOf<Int>()
        val viewModel = viewModel { found -> checked += found.size; emptyMap() }
        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.state.first { !it.loading }

        timedTask()
        viewModel.state.first { it.dueToday.isNotEmpty() }

        assertThat(checked).contains(1)
    }
}

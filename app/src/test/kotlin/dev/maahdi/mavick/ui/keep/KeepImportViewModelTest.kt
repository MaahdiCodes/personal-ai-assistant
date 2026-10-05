package dev.maahdi.mavick.ui.keep

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.MavickDatabase
import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.data.task.TaskRepository
import dev.maahdi.mavick.data.task.TaskSource
import dev.maahdi.mavick.importers.KeepNote
import dev.maahdi.mavick.importers.TakeoutProblem
import dev.maahdi.mavick.importers.TakeoutResult
import dev.maahdi.mavick.testing.FakeReminderScheduler
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.MainDispatcherRule
import dev.maahdi.mavick.testing.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeepImportViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: MavickDatabase
    private lateinit var tasks: TaskRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MavickDatabase::class.java).allowMainThreadQueries().build()
        tasks = TaskRepository(database.taskDao(), FakeReminderScheduler(), clock = { MutableClock(MONDAY_10AM) })
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun note(fileName: String, title: String) = KeepNote(fileName, title, "", emptyList(), emptyList(), null, pinned = false, archived = false)

    private fun viewModel(result: TakeoutResult?) = KeepImportViewModel(
        readExport = { result },
        openTasks = { tasks },
        makeDraft = { note -> note.title.takeIf { it.isNotBlank() }?.let { TaskDraft(title = it, source = TaskSource.KEEP) } },
        work = Dispatchers.Unconfined,
    )

    private val export = TakeoutResult.Notes(listOf(note("a.json", "Dentist"), note("b.json", "Shopping"), note("c.json", "Plumber")), skipped = 2)

    @Test
    fun `the notes are shown with none chosen at first`() = runTest {
        val viewModel = viewModel(export)
        viewModel.reading.join()

        val state = viewModel.state.value as KeepImportState.Ready
        assertThat(state.items.map { it.draft.title }).containsExactly("Dentist", "Shopping", "Plumber").inOrder()
        assertThat(state.selected).isEmpty()
        assertThat(state.skipped).isEqualTo(2)
    }

    @Test
    fun `notes that can't become a task are counted as left out`() = runTest {
        val viewModel = viewModel(TakeoutResult.Notes(listOf(note("a.json", "Dentist"), note("b.json", " ")), skipped = 0))
        viewModel.reading.join()

        val state = viewModel.state.value as KeepImportState.Ready
        assertThat(state.items).hasSize(1)
        assertThat(state.skipped).isEqualTo(1)
    }

    @Test
    fun `ticking, select all and select none change the choice`() = runTest {
        val viewModel = viewModel(export)
        viewModel.reading.join()

        viewModel.toggle("b.json")
        assertThat((viewModel.state.value as KeepImportState.Ready).selected).containsExactly("b.json")
        viewModel.toggle("b.json")
        assertThat((viewModel.state.value as KeepImportState.Ready).selected).isEmpty()
        viewModel.selectAll()
        assertThat((viewModel.state.value as KeepImportState.Ready).selected).containsExactly("a.json", "b.json", "c.json")
        viewModel.selectNone()
        assertThat((viewModel.state.value as KeepImportState.Ready).selected).isEmpty()
    }

    @Test
    fun `only the chosen notes become tasks`() = runTest {
        val viewModel = viewModel(export)
        viewModel.reading.join()
        viewModel.toggle("a.json")
        viewModel.toggle("c.json")

        viewModel.add()!!.join()

        assertThat(viewModel.state.value).isEqualTo(KeepImportState.Done(added = 2))
        assertThat(tasks.observeOpen().first().map { it.title }).containsExactly("Dentist", "Plumber")
    }

    @Test
    fun `nothing chosen adds nothing`() = runTest {
        val viewModel = viewModel(export)
        viewModel.reading.join()

        assertThat(viewModel.add()).isNull()
        assertThat(tasks.observeOpen().first()).isEmpty()
    }

    @Test
    fun `a file that couldn't be opened, or isn't an export, says so`() = runTest {
        val unopened = viewModel(null)
        unopened.reading.join()
        assertThat(unopened.state.value).isEqualTo(KeepImportState.Failed(problem = null))

        val notAZip = viewModel(TakeoutResult.Failed(TakeoutProblem.NOT_A_ZIP))
        notAZip.reading.join()
        assertThat(notAZip.state.value).isEqualTo(KeepImportState.Failed(TakeoutProblem.NOT_A_ZIP))
    }
}

package dev.maahdi.mavick.ui.editor

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.calendar.CalendarOccurrence
import dev.maahdi.mavick.capture.IncomingMessage
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.MavickDatabase
import dev.maahdi.mavick.data.message.MessageRepository
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.data.suggestion.SuggestionRepository
import dev.maahdi.mavick.data.suggestion.SuggestionState
import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.data.task.TaskRepository
import dev.maahdi.mavick.testing.FakeReminderScheduler
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.MainDispatcherRule
import dev.maahdi.mavick.testing.MutableClock
import dev.maahdi.mavick.testing.suggestion
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Saving from the editor, as far as it touches suggestions. */
@RunWith(AndroidJUnit4::class)
class EditorViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = MutableClock(MONDAY_10AM)
    private val preferences = context.getSharedPreferences("editor-vm-test", Context.MODE_PRIVATE)
    private lateinit var database: MavickDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var suggestions: SuggestionRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MavickDatabase::class.java).allowMainThreadQueries().build()
        tasks = TaskRepository(database.taskDao(), FakeReminderScheduler(), clock = { clock })
        suggestions = SuggestionRepository(database.suggestionDao(), clock = { clock })
    }

    @After
    fun tearDown() {
        database.close()
        preferences.edit().clear().commit()
    }

    private fun editor(draft: TaskDraft, suggestionId: String?) =
        EditorViewModel({ tasks }, { suggestions }, SettingsRepository(preferences), { clock }, taskId = null, draft = draft, suggestionId = suggestionId)

    private val dentist = CalendarOccurrence(
        1, 1, "Dentist",
        Instant.parse("2026-10-05T11:00:00Z"), Instant.parse("2026-10-05T12:00:00Z"),
        allDay = false, busy = true,
    )

    /** An editor for a new or existing task that looks for clashes with [findClashesAt]. */
    private fun clashEditor(
        draft: TaskDraft? = null,
        taskId: String? = null,
        findClashesAt: suspend (LocalDate, LocalTime) -> List<CalendarOccurrence>,
    ) = EditorViewModel(
        { tasks }, { suggestions }, SettingsRepository(preferences), { clock },
        taskId = taskId, draft = draft, findClashesAt = findClashesAt,
    )

    private val today = MONDAY_10AM.toLocalDate()

    private suspend fun EditorViewModel.saveAndWait() {
        val saved = CompletableDeferred<Unit>()
        save { saved.complete(Unit) }
        saved.await()
    }

    @Test
    fun `a draft with a date and a time is checked for clashes when the editor opens`() = runTest {
        val asked = mutableListOf<Pair<LocalDate, LocalTime>>()
        val editor = clashEditor(TaskDraft(title = "Call", dueDate = today, dueTime = LocalTime.of(17, 0))) { date, time ->
            asked += date to time
            listOf(dentist)
        }

        assertThat(asked).containsExactly(today to LocalTime.of(17, 0))
        assertThat(editor.state.value.clashes).containsExactly(dentist)
    }

    @Test
    fun `a draft without a time asks nothing`() = runTest {
        var asked = 0
        val editor = clashEditor(TaskDraft(title = "Call", dueDate = today)) { _, _ -> asked++; listOf(dentist) }

        assertThat(asked).isEqualTo(0)
        assertThat(editor.state.value.clashes).isEmpty()
    }

    @Test
    fun `an existing task is checked once it has loaded`() = runTest {
        val task = tasks.create(TaskDraft(title = "Call", dueDate = today, dueTime = LocalTime.of(17, 0)))
        val asked = mutableListOf<LocalTime>()
        val editor = clashEditor(taskId = task.id) { _, time -> asked += time; listOf(dentist) }

        editor.state.first { !it.loading && it.clashes.isNotEmpty() }

        assertThat(asked).containsExactly(LocalTime.of(17, 0))
    }

    @Test
    fun `changing the time checks the new time and replaces the warning`() = runTest {
        val editor = clashEditor(TaskDraft(title = "Call", dueDate = today, dueTime = LocalTime.of(17, 0))) { _, time ->
            if (time == LocalTime.of(17, 0)) listOf(dentist) else emptyList()
        }
        assertThat(editor.state.value.clashes).isNotEmpty()

        editor.edit { it.copy(dueTime = LocalTime.of(19, 0)) }

        assertThat(editor.state.value.clashes).isEmpty()
    }

    @Test
    fun `taking the time or the date off clears the warning without asking`() = runTest {
        var asked = 0
        val editor = clashEditor(TaskDraft(title = "Call", dueDate = today, dueTime = LocalTime.of(17, 0))) { _, _ -> asked++; listOf(dentist) }
        assertThat(asked).isEqualTo(1)

        editor.edit { it.copy(dueTime = null) }
        assertThat(editor.state.value.clashes).isEmpty()

        editor.edit { it.copy(dueTime = LocalTime.of(17, 0)) }
        editor.edit { it.copy(dueDate = null, dueTime = null) }
        assertThat(editor.state.value.clashes).isEmpty()
        assertThat(asked).isEqualTo(2)
    }

    @Test
    fun `editing something else does not ask the calendar again`() = runTest {
        var asked = 0
        val editor = clashEditor(TaskDraft(title = "Call", dueDate = today, dueTime = LocalTime.of(17, 0))) { _, _ -> asked++; listOf(dentist) }

        editor.edit { it.copy(title = "Call the bank", notes = "Ask about the loan") }

        assertThat(asked).isEqualTo(1)
        assertThat(editor.state.value.clashes).containsExactly(dentist)
    }

    @Test
    fun `a slow answer for an earlier time cannot overwrite the answer for the current one`() = runTest {
        val slow = CompletableDeferred<List<CalendarOccurrence>>()
        val editor = clashEditor(TaskDraft(title = "Call", dueDate = today, dueTime = LocalTime.of(17, 0))) { _, time ->
            if (time == LocalTime.of(17, 0)) slow.await() else emptyList()
        }

        editor.edit { it.copy(dueTime = LocalTime.of(19, 0)) }
        slow.complete(listOf(dentist))

        assertThat(editor.state.value.dueTime).isEqualTo(LocalTime.of(19, 0))
        assertThat(editor.state.value.clashes).isEmpty()
    }

    @Test
    fun `a clash never stops a task from being saved`() = runTest {
        val editor = clashEditor(TaskDraft(title = "Call the bank", dueDate = today, dueTime = LocalTime.of(17, 0))) { _, _ -> listOf(dentist) }

        editor.saveAndWait()

        assertThat(tasks.observeOpen().first().map { it.title }).containsExactly("Call the bank")
    }

    @Test
    fun `saving a task from a suggestion marks the suggestion added, with the new task`() = runTest {
        val messages = MessageRepository(database.messageDao())
        val now = clock.instant()
        messages.save(IncomingMessage(SourceApp.WHATSAPP, "0", "s:family", "Family", "Sam", "Pay the rent", now, false, true, false), now)
        val waiting = suggestion(messageId = messages.recent(1).single().id, title = "Pay the rent")
        suggestions.addUnlessDuplicate(waiting)

        editor(TaskDraft(title = "Pay the rent"), suggestionId = waiting.id).saveAndWait()

        val task = tasks.observeOpen().first().single()
        val added = suggestions.find(waiting.id)!!
        assertThat(added.state).isEqualTo(SuggestionState.ACCEPTED)
        assertThat(added.taskId).isEqualTo(task.id)
    }

    @Test
    fun `a task not from a suggestion saves as before`() = runTest {
        editor(TaskDraft(title = "Call the bank"), suggestionId = null).saveAndWait()

        assertThat(tasks.observeOpen().first().map { it.title }).containsExactly("Call the bank")
    }
}

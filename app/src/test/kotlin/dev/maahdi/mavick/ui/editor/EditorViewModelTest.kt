package dev.maahdi.mavick.ui.editor

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
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

    private suspend fun EditorViewModel.saveAndWait() {
        val saved = CompletableDeferred<Unit>()
        save { saved.complete(Unit) }
        saved.await()
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

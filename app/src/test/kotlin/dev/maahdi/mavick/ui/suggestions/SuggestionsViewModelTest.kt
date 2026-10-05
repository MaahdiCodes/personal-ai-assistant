package dev.maahdi.mavick.ui.suggestions

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.capture.IncomingMessage
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.MavickDatabase
import dev.maahdi.mavick.data.message.MessageEntity
import dev.maahdi.mavick.data.message.MessageRepository
import dev.maahdi.mavick.data.rules.ExclusionRepository
import dev.maahdi.mavick.data.rules.RuleEffect
import dev.maahdi.mavick.data.rules.RuleType
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.data.suggestion.SuggestionEntity
import dev.maahdi.mavick.data.suggestion.SuggestionRepository
import dev.maahdi.mavick.data.suggestion.SuggestionState
import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.data.task.TaskRepository
import dev.maahdi.mavick.testing.FakeReminderScheduler
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.MainDispatcherRule
import dev.maahdi.mavick.testing.MutableClock
import dev.maahdi.mavick.testing.suggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SuggestionsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = MutableClock(MONDAY_10AM)
    private val preferences = context.getSharedPreferences("suggestions-vm-test", Context.MODE_PRIVATE)
    private lateinit var database: MavickDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var messages: MessageRepository
    private lateinit var suggestions: SuggestionRepository
    private lateinit var exclusions: ExclusionRepository
    private lateinit var viewModel: SuggestionsViewModel

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MavickDatabase::class.java).allowMainThreadQueries().build()
        tasks = TaskRepository(database.taskDao(), FakeReminderScheduler(), clock = { clock })
        messages = MessageRepository(database.messageDao())
        suggestions = SuggestionRepository(database.suggestionDao(), clock = { clock })
        exclusions = ExclusionRepository(database.exclusionRuleDao(), SettingsRepository(preferences), clock = { clock })
        viewModel = SuggestionsViewModel({ suggestions }, { tasks }, { messages }, { exclusions })
    }

    @After
    fun tearDown() {
        database.close()
        preferences.edit().clear().commit()
    }

    /** A message in the "Family" group with one waiting suggestion. */
    private suspend fun waitingSuggestion(text: String = "Pay the rent tomorrow", chat: String = "s:family"): SuggestionEntity {
        val now = clock.instant()
        messages.save(IncomingMessage(SourceApp.WHATSAPP, "0", chat, "Family", "Sam", text, now, false, true, false), receivedAt = now)
        val message: MessageEntity = messages.recent(1_000).first { it.text == text }
        return suggestion(messageId = message.id, title = text, conversationKey = chat).also { suggestions.addUnlessDuplicate(it) }
    }

    @Test
    fun `Add creates the task and marks the suggestion added, and Undo takes both back`() = runTest {
        val waiting = waitingSuggestion()
        val events = mutableListOf<SuggestionUndo>()
        backgroundScope.launch(Dispatchers.Unconfined) { viewModel.undo.collect { events += it } }

        viewModel.add(waiting, TaskDraft(title = "Pay the rent")).join()

        val task = tasks.observeOpen().first().single()
        assertThat(task.title).isEqualTo("Pay the rent")
        assertThat(suggestions.find(waiting.id)!!.state).isEqualTo(SuggestionState.ACCEPTED)
        assertThat(suggestions.find(waiting.id)!!.taskId).isEqualTo(task.id)
        val event = events.single() as SuggestionUndo.Added

        viewModel.undo(event).join()

        assertThat(tasks.observeOpen().first()).isEmpty()
        assertThat(suggestions.find(waiting.id)!!.state).isEqualTo(SuggestionState.NEW)
    }

    @Test
    fun `Ignore can be undone`() = runTest {
        val waiting = waitingSuggestion()

        viewModel.ignore(waiting).join()
        assertThat(suggestions.observeNew().first()).isEmpty()

        viewModel.undo(SuggestionUndo.Ignored(waiting.id)).join()
        assertThat(suggestions.observeNew().first().map { it.id }).containsExactly(waiting.id)
    }

    @Test
    fun `Never read this chat asks with the chat's message count, then excludes it and deletes its messages and suggestions`() = runTest {
        val family = waitingSuggestion()
        waitingSuggestion(text = "Bring the cake at 5")
        val work = waitingSuggestion(text = "Send the report", chat = "s:work")

        viewModel.askNeverRead(family).join()
        assertThat(viewModel.neverReadPrompt.value).isEqualTo(NeverReadPrompt(family, messageCount = 2))

        viewModel.neverReadChat("Family")!!.join()

        assertThat(viewModel.neverReadPrompt.value).isNull()
        assertThat(suggestions.observeNew().first().map { it.id }).containsExactly(work.id)
        assertThat(messages.countConversation(SourceApp.WHATSAPP, "0", "s:family")).isEqualTo(0)
        val rule = database.exclusionRuleDao().getAll().single { it.type == RuleType.CHAT }
        assertThat(rule.effect).isEqualTo(RuleEffect.EXCLUDE)
        assertThat(rule.value).isEqualTo("s:family")
        assertThat(rule.displayName).isEqualTo("Family")
        assertThat(rule.app).isEqualTo(SourceApp.WHATSAPP)
        assertThat(rule.accountKey).isEqualTo("0")
    }

    @Test
    fun `dismissing the question changes nothing`() = runTest {
        val family = waitingSuggestion()
        viewModel.askNeverRead(family).join()

        viewModel.dismissNeverRead()

        assertThat(viewModel.neverReadPrompt.value).isNull()
        assertThat(viewModel.neverReadChat("Family")).isNull()
        assertThat(suggestions.observeNew().first()).hasSize(1)
    }
}

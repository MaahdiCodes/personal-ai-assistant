package dev.maahdi.mavick.ai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.capture.IncomingMessage
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.MavickDatabase
import dev.maahdi.mavick.data.message.AiState
import dev.maahdi.mavick.data.message.MessageEntity
import dev.maahdi.mavick.data.message.MessageRepository
import dev.maahdi.mavick.data.rules.ExclusionRepository
import dev.maahdi.mavick.data.rules.RuleEffect
import dev.maahdi.mavick.data.rules.RuleType
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.data.suggestion.SuggestionEntity
import dev.maahdi.mavick.data.suggestion.SuggestionKind
import dev.maahdi.mavick.data.suggestion.SuggestionRepository
import dev.maahdi.mavick.data.suggestion.SuggestionSource
import dev.maahdi.mavick.testing.FakeElapsed
import dev.maahdi.mavick.testing.FakeLanguageModel
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.MutableClock
import dev.maahdi.mavick.testing.testModelStore
import dev.maahdi.mavick.time.WhenParser
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** From a waiting message to a saved suggestion, on an in-memory database. */
@RunWith(AndroidJUnit4::class)
class SuggestionQueueTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = MutableClock(MONDAY_10AM)
    private val now: Instant get() = clock.instant()
    private val settingsPreferences = context.getSharedPreferences("queue-test-settings", Context.MODE_PRIVATE)
    private val statusPreferences = context.getSharedPreferences("queue-test-status", Context.MODE_PRIVATE)
    private val status = AiStatusStore(statusPreferences)
    private val model = FakeLanguageModel()
    private var pause: AiPause? = null
    private lateinit var database: MavickDatabase
    private lateinit var messages: MessageRepository
    private lateinit var suggestions: SuggestionRepository
    private lateinit var rules: ExclusionRepository
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MavickDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(settingsPreferences)
        messages = MessageRepository(database.messageDao())
        suggestions = SuggestionRepository(database.suggestionDao(), clock = { clock })
        rules = ExclusionRepository(database.exclusionRuleDao(), settings, clock = { clock })
    }

    @After
    fun tearDown() {
        database.close()
        settingsPreferences.edit().clear().commit()
        statusPreferences.edit().clear().commit()
    }

    private fun queue(withModel: Boolean = false, languageModel: LanguageModel = model) = SuggestionQueue(
        messages = messages,
        suggestions = suggestions,
        rules = rules,
        settings = settings,
        modelHost = ModelHost(testModelStore(File(folder.root, "models"), withModel), status, { languageModel }, { clock }, FakeElapsed()),
        ruleExtractor = RuleExtractor { WhenParser() },
        conditions = { pause },
        status = status,
        whenParser = { WhenParser() },
        clock = { clock },
    )

    /** A message in the "Family" WhatsApp group, saved as capture would. */
    private suspend fun receive(
        text: String,
        sender: String? = "Sam",
        minutesAgo: Long = 1,
        fromMe: Boolean = false,
        app: SourceApp = SourceApp.WHATSAPP,
        chat: String = "s:family",
        title: String = "Family",
    ): MessageEntity {
        val incoming = IncomingMessage(app, "0", chat, title, sender, text, now.minus(Duration.ofMinutes(minutesAgo)), fromMe, isGroup = app != SourceApp.KEEP, cutShort = false)
        messages.save(incoming, receivedAt = now)
        return messages.recent(1_000).first { it.text == text }
    }

    private suspend fun stateOf(text: String): AiState = messages.recent(1_000).single { it.text == text }.aiState

    private suspend fun waiting(): List<SuggestionEntity> = suggestions.observeNew().first()

    @Test
    fun `without a model, a date and a to-do become a rules suggestion, dated from when it was sent`() = runTest {
        val message = receive("Can you pay the rent tomorrow at 10am?", minutesAgo = Duration.ofDays(1).toMinutes())

        assertThat(queue().processPending()).isEqualTo(1)

        val suggestion = waiting().single()
        assertThat(suggestion.source).isEqualTo(SuggestionSource.RULES)
        assertThat(suggestion.title).isEqualTo("Can you pay the rent")
        // Sent yesterday: "tomorrow" is today.
        assertThat(suggestion.dueDate).isEqualTo(LocalDate.of(2026, 10, 5))
        assertThat(suggestion.dueTime).isEqualTo(LocalTime.of(10, 0))
        assertThat(suggestion.whenText).isNull()
        assertThat(suggestion.messageId).isEqualTo(message.id)
        assertThat(suggestion.chatTitle).isEqualTo("Family")
        assertThat(suggestion.sender).isEqualTo("Sam")
        assertThat(suggestion.excerpt).isEqualTo("Can you pay the rent tomorrow at 10am?")
        assertThat(suggestion.messagePostedAt).isEqualTo(message.postedAt)
        assertThat(stateOf(message.text)).isEqualTo(AiState.DONE)
        assertThat(status.snapshot().suggested).isEqualTo(1)
    }

    @Test
    fun `small talk is skipped by the prefilter and never reaches the model`() = runTest {
        receive("haha nice photo")

        assertThat(queue(withModel = true).processPending()).isEqualTo(0)

        assertThat(stateOf("haha nice photo")).isEqualTo(AiState.SKIPPED)
        assertThat(model.requests).isEmpty()
        assertThat(status.snapshot().skipped).isEqualTo(1)
    }

    @Test
    fun `with a model, its answer becomes a suggestion, dated from the message's time`() = runTest {
        model.answer(answer(kind = "event", title = "Go to Sam's party", whenText = "Thursday 5pm"))
        receive("Party at mine on Thursday 5pm, come!")

        assertThat(queue(withModel = true).processPending()).isEqualTo(1)

        val suggestion = waiting().single()
        assertThat(suggestion.source).isEqualTo(SuggestionSource.MODEL)
        assertThat(suggestion.kind).isEqualTo(SuggestionKind.EVENT)
        assertThat(suggestion.title).isEqualTo("Go to Sam's party")
        assertThat(suggestion.whenText).isEqualTo("Thursday 5pm")
        assertThat(suggestion.dueDate).isEqualTo(LocalDate.of(2026, 10, 8))
        assertThat(suggestion.dueTime).isEqualTo(LocalTime.of(17, 0))
        assertThat(suggestion.needsTime).isFalse()
        assertThat(suggestion.confidence).isEqualTo(0.9)
        assertThat(status.snapshot().modelAnswers).isEqualTo(1)
    }

    @Test
    fun `when words the code can't read ask for a time`() = runTest {
        model.answer(answer(title = "Pay the school fees", whenText = "end of the month"))
        receive("Please pay the school fees by end of the month")

        queue(withModel = true).processPending()

        with(waiting().single()) {
            assertThat(needsTime).isTrue()
            assertThat(dueDate).isNull()
            assertThat(whenText).isEqualTo("end of the month")
        }
    }

    @Test
    fun `the model reads the chat's earlier messages as context, and the newest message comes first`() = runTest {
        model.answer(NOT_ACTIONABLE, NOT_ACTIONABLE)
        receive("Are you coming tomorrow?", sender = "Rina", minutesAgo = 5)
        receive("yes", sender = null, fromMe = true, minutesAgo = 4)
        receive("ok see you then at 5", minutesAgo = 1)

        queue(withModel = true).processPending()

        assertThat(model.requests).hasSize(2)
        assertThat(model.requests[0].prompt).isEqualTo(
            """
            The WhatsApp group "Family".
            Earlier messages:
            Rina: Are you coming tomorrow?
            Me: yes
            New message:
            Sam: ok see you then at 5
            """.trimIndent(),
        )
        assertThat(model.requests[1].prompt).endsWith("New message:\nRina: Are you coming tomorrow?")
    }

    @Test
    fun `a chat excluded after its messages arrived is skipped, unread by the model`() = runTest {
        receive("Pay the rent tomorrow")
        rules.add(RuleType.CHAT, RuleEffect.EXCLUDE, "s:family", "Family")

        queue(withModel = true).processPending()

        assertThat(stateOf("Pay the rent tomorrow")).isEqualTo(AiState.SKIPPED)
        assertThat(model.requests).isEmpty()
        assertThat(waiting()).isEmpty()
    }

    @Test
    fun `excluded earlier messages are left out of the context too`() = runTest {
        model.answer(NOT_ACTIONABLE)
        receive("the secret plan is ready", sender = "Rina", minutesAgo = 3)
        receive("Bring the cake tomorrow at 5", minutesAgo = 1)
        rules.add(RuleType.KEYWORD, RuleEffect.EXCLUDE, "secret", "secret")

        queue(withModel = true).processPending()

        assertThat(model.requests.single().prompt).doesNotContain("secret")
        assertThat(stateOf("the secret plan is ready")).isEqualTo(AiState.SKIPPED)
    }

    @Test
    fun `with suggestions off, nothing is looked at and messages keep waiting`() = runTest {
        settings.update { it.copy(suggestionsEnabled = false) }
        receive("Pay the rent tomorrow")

        assertThat(queue(withModel = true).processPending()).isEqualTo(0)

        assertThat(stateOf("Pay the rent tomorrow")).isEqualTo(AiState.PENDING)
        assertThat(model.requests).isEmpty()
    }

    @Test
    fun `while the phone needs a pause nothing is processed, and the next run carries on`() = runTest {
        receive("Pay the rent tomorrow")
        pause = AiPause.BATTERY_LOW

        assertThat(queue().processPending()).isEqualTo(0)
        assertThat(stateOf("Pay the rent tomorrow")).isEqualTo(AiState.PENDING)
        assertThat(status.snapshot().pause).isEqualTo(AiPause.BATTERY_LOW)

        pause = null
        assertThat(queue().processPending()).isEqualTo(1)
        assertThat(status.snapshot().pause).isNull()
    }

    @Test
    fun `messages older than three days are skipped without being read`() = runTest {
        receive("Pay the rent tomorrow", minutesAgo = Duration.ofDays(4).toMinutes())

        queue(withModel = true).processPending()

        assertThat(stateOf("Pay the rent tomorrow")).isEqualTo(AiState.SKIPPED)
        assertThat(model.requests).isEmpty()
    }

    @Test
    fun `a message is marked failed while the model reads it, so one that stops Mavick is never read again`() = runTest {
        val statesDuringAnswer = mutableListOf<String>()
        val watching = object : LanguageModel {
            override fun generate(request: GenerationRequest): String {
                database.query("SELECT aiState FROM message", null).use { cursor ->
                    while (cursor.moveToNext()) statesDuringAnswer += cursor.getString(0)
                }
                return NOT_ACTIONABLE
            }

            override fun close() = Unit
        }
        receive("Pay the rent tomorrow")

        queue(withModel = true, languageModel = watching).processPending()

        assertThat(statesDuringAnswer).containsExactly("FAILED")
        assertThat(stateOf("Pay the rent tomorrow")).isEqualTo(AiState.DONE)
    }

    @Test
    fun `a model error fails that message, and the next one uses the rules while the model rests`() = runTest {
        model.failure = IllegalStateException("engine broke")
        receive("Pay the rent tomorrow", minutesAgo = 10)
        receive("Bring the cake tomorrow at 5", minutesAgo = 1)

        assertThat(queue(withModel = true).processPending()).isEqualTo(1)

        assertThat(stateOf("Bring the cake tomorrow at 5")).isEqualTo(AiState.FAILED)
        assertThat(stateOf("Pay the rent tomorrow")).isEqualTo(AiState.DONE)
        assertThat(waiting().single().source).isEqualTo(SuggestionSource.RULES)
        assertThat(model.requests).hasSize(1)
        with(status.snapshot()) {
            assertThat(failed).isEqualTo(1)
            assertThat(problem).isEqualTo("IllegalStateException")
        }
    }

    @Test
    fun `two unusable answers fail the message, and are counted`() = runTest {
        model.answer("I think you should pay the rent", "{\"actionable\": maybe}")
        receive("Pay the rent tomorrow")

        assertThat(queue(withModel = true).processPending()).isEqualTo(0)

        assertThat(stateOf("Pay the rent tomorrow")).isEqualTo(AiState.FAILED)
        assertThat(waiting()).isEmpty()
        assertThat(status.snapshot().failed).isEqualTo(1)
    }

    @Test
    fun `the same plan in two messages is suggested once`() = runTest {
        receive("Bring the cake tomorrow at 5", minutesAgo = 10)
        receive("Bring the cake tomorrow at 5 pls", sender = "Rina", minutesAgo = 1)

        assertThat(queue().processPending()).isEqualTo(1)

        assertThat(waiting()).hasSize(1)
        assertThat(stateOf("Bring the cake tomorrow at 5")).isEqualTo(AiState.DONE)
        assertThat(stateOf("Bring the cake tomorrow at 5 pls")).isEqualTo(AiState.DONE)
    }

    @Test
    fun `a Keep reminder becomes a reminder`() = runTest {
        receive("Buy milk", sender = null, app = SourceApp.KEEP, chat = "t:", title = "")

        queue().processPending()

        with(waiting().single()) {
            assertThat(kind).isEqualTo(SuggestionKind.REMINDER)
            assertThat(title).isEqualTo("Buy milk")
        }
    }

    @Test
    fun `your own promise is suggested without another person`() = runTest {
        receive("I'll send the report tomorrow", sender = null, fromMe = true)

        queue().processPending()

        with(waiting().single()) {
            assertThat(title).isEqualTo("I'll send the report")
            assertThat(person).isNull()
            assertThat(isFromMe).isTrue()
        }
    }

    @Test
    fun `the status keeps counts and error types only, never message text`() = runTest {
        model.failure = IllegalStateException("Pay the rent tomorrow")
        receive("Pay the rent tomorrow")
        receive("haha nice photo")

        queue(withModel = true).processPending()

        val stored = statusPreferences.all.values.joinToString { it.toString() }
        assertThat(stored).doesNotContain("rent")
        assertThat(stored).doesNotContain("photo")
    }

    private companion object {
        const val NOT_ACTIONABLE = """{"actionable": false, "items": []}"""

        fun answer(kind: String = "task", title: String, whenText: String?, person: String = "Sam", confidence: Double = 0.9): String {
            val whenJson = whenText?.let { "\"$it\"" } ?: "null"
            return """{"actionable": true, "items": [{"kind": "$kind", "title": "$title", "when_text": $whenJson, "person": "$person", "confidence": $confidence}]}"""
        }
    }
}

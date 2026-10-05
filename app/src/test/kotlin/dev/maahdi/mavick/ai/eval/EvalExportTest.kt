package dev.maahdi.mavick.ai.eval

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.capture.AppCapture
import dev.maahdi.mavick.capture.IncomingMessage
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.MavickDatabase
import dev.maahdi.mavick.data.message.MessageRepository
import dev.maahdi.mavick.data.rules.ExclusionRepository
import dev.maahdi.mavick.data.rules.RuleEffect
import dev.maahdi.mavick.data.rules.RuleType
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.MutableClock
import dev.maahdi.mavick.testing.TEST_ZONE
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EvalExportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = MutableClock(MONDAY_10AM)
    private val preferences = context.getSharedPreferences("eval-export-test", Context.MODE_PRIVATE)
    private lateinit var database: MavickDatabase
    private lateinit var messages: MessageRepository
    private lateinit var rules: ExclusionRepository
    private lateinit var settings: SettingsRepository
    private lateinit var export: EvalExport

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MavickDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(preferences)
        messages = MessageRepository(database.messageDao())
        rules = ExclusionRepository(database.exclusionRuleDao(), settings, clock = { clock })
        export = EvalExport(messages, rules, settings, clock = { clock })
    }

    @After
    fun tearDown() {
        database.close()
        preferences.edit().clear().commit()
    }

    private suspend fun receive(text: String, minutesAgo: Long, chat: String = "s:family", sender: String = "Sam", app: SourceApp = SourceApp.WHATSAPP) {
        val sentAt = clock.instant().minus(Duration.ofMinutes(minutesAgo))
        messages.save(IncomingMessage(app, "0", chat, chat.removePrefix("s:"), sender, text, sentAt, false, true, false), receivedAt = clock.instant())
    }

    private fun exported(file: EvalExportFile) = EvalSet.read(file.csv, TEST_ZONE)

    @Test
    fun `the newest messages are exported unlabelled, each with its earlier ones as context`() = runTest {
        receive("Are you coming tomorrow?", minutesAgo = 5, sender = "Rina")
        receive("Party at 5, bring the cake", minutesAgo = 1)

        val file = export.export()

        assertThat(file.count).isEqualTo(2)
        val rows = exported(file).rows
        assertThat(rows.map { it.input.message.text }).containsExactly("Party at 5, bring the cake", "Are you coming tomorrow?").inOrder()
        assertThat(rows.first().input.earlier.map { it.text }).containsExactly("Are you coming tomorrow?")
        assertThat(rows.all { it.expected.actionable == null }).isTrue()
    }

    @Test
    fun `chats, people and words excluded since are left out, from the context too`() = runTest {
        receive("the secret plan", minutesAgo = 4, sender = "Rina")
        receive("Bring the cake tomorrow", minutesAgo = 3)
        receive("Work stuff tomorrow", minutesAgo = 2, chat = "s:work")
        rules.add(RuleType.KEYWORD, RuleEffect.EXCLUDE, "secret", "secret")
        rules.add(RuleType.CHAT, RuleEffect.EXCLUDE, "s:work", "work")

        val file = export.export()

        assertThat(file.count).isEqualTo(1)
        assertThat(file.csv).doesNotContain("secret")
        assertThat(file.csv).doesNotContain("Work stuff")
        assertThat(exported(file).rows.single().input.earlier).isEmpty()
    }

    @Test
    fun `an app switched off is left out`() = runTest {
        receive("Pay the rent tomorrow", minutesAgo = 1, app = SourceApp.MESSENGER)
        settings.update { it.copy(appCapture = it.appCapture + (SourceApp.MESSENGER to AppCapture(enabled = false))) }

        assertThat(export.export().count).isEqualTo(0)
    }

    @Test
    fun `at most the limit is exported, newest first`() = runTest {
        (1..5).forEach { receive("message $it", minutesAgo = 10L - it) }

        val file = export.export(limit = 3)

        assertThat(exported(file).rows.map { it.input.message.text }).containsExactly("message 5", "message 4", "message 3").inOrder()
    }
}

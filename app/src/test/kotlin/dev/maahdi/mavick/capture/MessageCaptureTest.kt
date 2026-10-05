package dev.maahdi.mavick.capture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.MavickDatabase
import dev.maahdi.mavick.data.message.MessageEntity
import dev.maahdi.mavick.data.message.MessageRepository
import dev.maahdi.mavick.data.rules.ExclusionRepository
import dev.maahdi.mavick.data.rules.RuleEffect
import dev.maahdi.mavick.data.rules.RuleType
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.MutableClock
import dev.maahdi.mavick.testing.TEST_ZONE
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The whole pipeline, from a notification to the database, on an in-memory database. */
@RunWith(AndroidJUnit4::class)
class MessageCaptureTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = MutableClock(MONDAY_10AM)
    private val now: Instant get() = clock.instant()
    private val settingsPreferences = context.getSharedPreferences("capture-test-settings", Context.MODE_PRIVATE)
    private val statusPreferences = context.getSharedPreferences("capture-test-status", Context.MODE_PRIVATE)
    private lateinit var database: MavickDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var messages: MessageRepository
    private lateinit var rules: ExclusionRepository
    private lateinit var status: CaptureStatusStore
    private lateinit var capture: MessageCapture

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MavickDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(settingsPreferences)
        messages = MessageRepository(database.messageDao())
        rules = ExclusionRepository(database.exclusionRuleDao(), settings, clock = { clock })
        status = CaptureStatusStore(statusPreferences)
        capture = MessageCapture(messages, rules, settings, status, clock = { clock })
    }

    @After
    fun tearDown() {
        database.close()
        settingsPreferences.edit().clear().commit()
        statusPreferences.edit().clear().commit()
    }

    private fun chat(vararg texts: Pair<String, String>, packageName: String = SourceApp.WHATSAPP.packageName, chat: String = "Family", minutesAgo: Long = 1) =
        RawNotification(
            packageName = packageName,
            userId = 0,
            postedAt = now,
            shortcutId = "s-$chat",
            conversationTitle = chat,
            isGroupConversation = true,
            messages = texts.mapIndexed { index, (sender, text) ->
                RawMessage(text, now.minus(Duration.ofMinutes(minutesAgo)).plusMillis(index.toLong()), sender)
            },
        )

    private suspend fun saved(): List<MessageEntity> = database.messageDao().observeRecent(1_000).first()

    @Test
    fun `a supported app's message is saved with its chat, sender and account`() = runTest {
        val result = capture.onNotification(chat("Sam" to "Call me today at 12"))

        assertThat(result).isEqualTo(CaptureResult(saved = 1))
        val message = saved().single()
        assertThat(message.app).isEqualTo(SourceApp.WHATSAPP)
        assertThat(message.accountKey).isEqualTo("0")
        assertThat(message.conversationKey).isEqualTo("s:s-Family")
        assertThat(message.conversationTitle).isEqualTo("Family")
        assertThat(message.sender).isEqualTo("Sam")
        assertThat(message.text).isEqualTo("Call me today at 12")
        assertThat(message.receivedAt).isEqualTo(now)
        assertThat(message.isGroup).isTrue()
    }

    @Test
    fun `other apps are ignored completely, with nothing saved or counted`() = runTest {
        val result = capture.onNotification(chat("Bank" to "Your OTP is 4821", packageName = "com.bkash.customerapp"))

        assertThat(result.ignored).isTrue()
        assertThat(saved()).isEmpty()
        assertThat(statusPreferences.all).isEmpty()
    }

    @Test
    fun `a chat's recent messages posted again are saved once`() = runTest {
        capture.onNotification(chat("Sam" to "Are you coming?"))

        val result = capture.onNotification(chat("Sam" to "Are you coming?", "Rina" to "I'll bring cake"))

        assertThat(result).isEqualTo(CaptureResult(saved = 1, duplicates = 1))
        assertThat(saved().map { it.text }).containsExactly("Are you coming?", "I'll bring cake")
    }

    @Test
    fun `the same words from two accounts are two messages`() = runTest {
        capture.onNotification(chat("Sam" to "Hi"))
        capture.onNotification(chat("Sam" to "Hi").copy(userId = 999))

        assertThat(saved().map { it.accountKey }).containsExactly("0", "999")
    }

    @Test
    fun `an excluded chat's text never reaches the database or any saved file`() = runTest {
        rules.add(RuleType.CHAT, RuleEffect.EXCLUDE, "Family", "Family")
        val secret = "The safe code is 7Q9X"

        val result = capture.onNotification(chat("Sam" to secret))

        assertThat(result).isEqualTo(CaptureResult(skipped = 1))
        assertThat(saved()).isEmpty()
        val everythingStored = buildString {
            database.query("SELECT * FROM message", null).use { cursor ->
                while (cursor.moveToNext()) for (i in 0 until cursor.columnCount) append(cursor.getString(i))
            }
            append(statusPreferences.all.values.joinToString())
            append(settingsPreferences.all.values.joinToString())
        }
        assertThat(everythingStored).doesNotContain("7Q9X")
    }

    @Test
    fun `the default keywords keep codes and passwords out from the very first message`() = runTest {
        capture.onNotification(chat("Bank" to "Your OTP is 4821", "Sam" to "my password is hunter2", "Rina" to "See you at 5"))

        assertThat(saved().map { it.text }).containsExactly("See you at 5")
    }

    @Test
    fun `a matching rule records when it last matched`() = runTest {
        val rule = rules.add(RuleType.SENDER, RuleEffect.EXCLUDE, "Sam", "Sam")!!

        capture.onNotification(chat("Sam" to "Hi"))

        val stored = database.exclusionRuleDao().getAll().single { it.id == rule.id }
        assertThat(stored.lastMatchedAt).isEqualTo(now)
    }

    @Test
    fun `while paused nothing is read, and afterwards reading continues`() = runTest {
        settings.update { it.copy(capturePause = CapturePause.Until(now.plus(Duration.ofHours(1)))) }

        capture.onNotification(chat("Sam" to "During the pause"))
        clock.advance(Duration.ofHours(2))
        capture.onNotification(chat("Sam" to "After the pause"))

        assertThat(saved().map { it.text }).containsExactly("After the pause")
    }

    @Test
    fun `an app switched off is not read`() = runTest {
        settings.update { it.copy(appCapture = it.appCapture + (SourceApp.WHATSAPP to AppCapture(enabled = false))) }

        assertThat(capture.onNotification(chat("Sam" to "Hi")).skipped).isEqualTo(1)
        assertThat(saved()).isEmpty()
    }

    @Test
    fun `messages older than the retention period are not saved`() = runTest {
        settings.update { it.copy(messageRetentionDays = 7) }

        val result = capture.onNotification(chat("Sam" to "From long ago", minutesAgo = Duration.ofDays(8).toMinutes()))

        assertThat(result.tooOld).isEqualTo(1)
        assertThat(saved()).isEmpty()
    }

    @Test
    fun `summaries and calls are noise and save nothing`() = runTest {
        val summary = chat("Sam" to "Hi").copy(isGroupSummary = true)

        assertThat(capture.onNotification(summary).noise).isTrue()
        assertThat(saved()).isEmpty()
    }

    @Test
    fun `the status keeps counts and times only`() = runTest {
        rules.add(RuleType.KEYWORD, RuleEffect.EXCLUDE, "secret", "secret")
        capture.onNotification(chat("Sam" to "Hi", "Rina" to "a secret thing"))
        capture.onNotification(chat("Sam" to "Hi"))

        val snapshot = status.snapshot()
        assertThat(snapshot.saved).isEqualTo(1)
        assertThat(snapshot.skipped).isEqualTo(1)
        assertThat(snapshot.duplicates).isEqualTo(1)
        assertThat(snapshot.lastSeenAt).isEqualTo(now)
        assertThat(snapshot.lastSavedAt).isEqualTo(now)
        assertThat(statusPreferences.all.values.all { it is Long }).isTrue()
    }

    @Test
    fun `times are stored exactly`() = runTest {
        capture.onNotification(chat("Sam" to "Hi"))

        assertThat(saved().single().postedAt).isEqualTo(MONDAY_10AM.atZone(TEST_ZONE).toInstant().minus(Duration.ofMinutes(1)))
    }
}

package dev.maahdi.mavick.capture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.MavickDatabase
import dev.maahdi.mavick.data.message.MessageRepository
import dev.maahdi.mavick.data.rules.ExclusionRepository
import dev.maahdi.mavick.data.rules.RuleEffect
import dev.maahdi.mavick.data.rules.RuleType
import dev.maahdi.mavick.data.security.AndroidKeystoreKeyWrapper
import dev.maahdi.mavick.data.security.DatabaseKeyRepository
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.testing.containsSequence
import java.io.File
import java.security.KeyStore
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on the phone: message capture into the real encrypted (SQLCipher) database, with its own
 * test database, key and settings.
 */
@RunWith(AndroidJUnit4::class)
class MessageCaptureDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val keyFile = File(context.noBackupFilesDir, "capture-test.key")
    private val settingsPreferences = context.getSharedPreferences("capture-device-test-settings", Context.MODE_PRIVATE)
    private val statusPreferences = context.getSharedPreferences("capture-device-test-status", Context.MODE_PRIVATE)
    private val clock = { Clock.systemDefaultZone() }

    @Before
    fun setUp() = cleanUp()

    @After
    fun tearDown() = cleanUp()

    private fun cleanUp() {
        context.deleteDatabase(DATABASE_NAME)
        keyFile.delete()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY_ALIAS)
        settingsPreferences.edit().clear().commit()
        statusPreferences.edit().clear().commit()
    }

    private fun openDatabase() = MavickDatabase.open(context, DatabaseKeyRepository(keyFile, AndroidKeystoreKeyWrapper(KEY_ALIAS)), DATABASE_NAME)

    private fun chat(title: String, text: String) = RawNotification(
        packageName = SourceApp.WHATSAPP.packageName,
        userId = 0,
        postedAt = Instant.now(),
        shortcutId = "s-$title",
        conversationTitle = title,
        isGroupConversation = true,
        messages = listOf(RawMessage(text, Instant.now(), "Sam")),
    )

    @Test
    fun messagesAreSavedEncryptedAndAnExcludedChatNeverReachesTheFile() = runTest {
        val database = openDatabase()
        val settings = SettingsRepository(settingsPreferences)
        val rules = ExclusionRepository(database.exclusionRuleDao(), settings, clock)
        val capture = MessageCapture(MessageRepository(database.messageDao()), rules, settings, CaptureStatusStore(statusPreferences), clock)
        rules.add(RuleType.CHAT, RuleEffect.EXCLUDE, "Secret chat", "Secret chat")

        capture.onNotification(chat("Family", VISIBLE))
        capture.onNotification(chat("Secret chat", HIDDEN))
        database.close()

        val reopened = openDatabase()
        val saved = reopened.messageDao().observeRecent(100).first().map { it.text }
        reopened.close()
        assertThat(saved).containsExactly(VISIBLE)

        val files = context.getDatabasePath(DATABASE_NAME).parentFile!!.listFiles { file -> file.name.startsWith(DATABASE_NAME) }!!
        files.forEach { file ->
            val bytes = file.readBytes()
            assertThat(bytes.containsSequence(VISIBLE.toByteArray(Charsets.UTF_8))).isFalse() // encrypted
            assertThat(bytes.containsSequence(HIDDEN.toByteArray(Charsets.UTF_8))).isFalse() // never stored
        }
    }

    private companion object {
        const val DATABASE_NAME = "capture-test.db"
        const val KEY_ALIAS = "mavick.test.capture"
        const val VISIBLE = "Visible message 4KQ2"
        const val HIDDEN = "Hidden message 9ZX7"
    }
}

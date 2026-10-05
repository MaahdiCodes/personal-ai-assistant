package dev.maahdi.mavick.capture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.MavickDatabase
import dev.maahdi.mavick.data.health.HealthEventEntity
import dev.maahdi.mavick.data.health.HealthEventType
import dev.maahdi.mavick.data.message.MessageRepository
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.MutableClock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureChoresTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = MutableClock(MONDAY_10AM)
    private val now: Instant get() = clock.instant()
    private val settingsPreferences = context.getSharedPreferences("chores-settings", Context.MODE_PRIVATE)
    private val statusPreferences = context.getSharedPreferences("chores-status", Context.MODE_PRIVATE)
    private lateinit var database: MavickDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var messages: MessageRepository
    private lateinit var status: CaptureStatusStore
    private var hasAccess = true
    private val warnings = mutableListOf<ReadingState>()
    private lateinit var chores: CaptureChores

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MavickDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(settingsPreferences)
        messages = MessageRepository(database.messageDao())
        status = CaptureStatusStore(statusPreferences)
        chores = CaptureChores(messages, database.healthEventDao(), settings, status, { hasAccess }, warnings::add, clock = { clock })
    }

    @After
    fun tearDown() {
        database.close()
        settingsPreferences.edit().clear().commit()
        statusPreferences.edit().clear().commit()
    }

    private suspend fun saveMessage(text: String, daysAgo: Long) = messages.save(
        IncomingMessage(SourceApp.WHATSAPP, "0", "s:sam", "Sam", "Sam", text, now.minus(Duration.ofDays(daysAgo)), isFromMe = false, isGroup = false, cutShort = false),
        receivedAt = now,
    )

    @Test
    fun `clean-up deletes messages older than the retention period and keeps the rest`() = runTest {
        settings.update { it.copy(messageRetentionDays = 14) }
        saveMessage("old", daysAgo = 15)
        saveMessage("recent", daysAgo = 13)

        chores.cleanUp()

        assertThat(database.messageDao().observeRecent(100).first().map { it.text }).containsExactly("recent")
    }

    @Test
    fun `a shorter retention period applies at the next clean-up`() = runTest {
        saveMessage("three days old", daysAgo = 3)
        settings.update { it.copy(messageRetentionDays = 1) }

        chores.cleanUp()

        assertThat(database.messageDao().count()).isEqualTo(0)
    }

    @Test
    fun `clean-up deletes listener events after 30 days`() = runTest {
        val dao = database.healthEventDao()
        dao.insert(HealthEventEntity(type = HealthEventType.LISTENER_CONNECTED, at = now.minus(Duration.ofDays(31))))
        dao.insert(HealthEventEntity(type = HealthEventType.LISTENER_CONNECTED, at = now.minus(Duration.ofDays(29))))

        chores.cleanUp()

        assertThat(dao.count(HealthEventType.LISTENER_CONNECTED, Instant.EPOCH)).isEqualTo(1)
    }

    @Test
    fun `the daily check warns when reading has gone quiet`() = runTest {
        status.markConnected(now.minus(Duration.ofDays(2)))

        chores.daily()

        assertThat(warnings).containsExactly(ReadingState.QUIET)
    }

    @Test
    fun `the daily check warns when Android disconnected reading`() = runTest {
        status.markConnected(now.minus(Duration.ofHours(2)))
        status.markDisconnected(now.minus(Duration.ofHours(1)))

        chores.daily()

        assertThat(warnings).containsExactly(ReadingState.NOT_CONNECTED)
    }

    @Test
    fun `no warning when reading works, when access was never given, or when warnings are off`() = runTest {
        status.markConnected(now.minus(Duration.ofDays(2)))
        status.record(now.minus(Duration.ofHours(1)), CaptureResult(saved = 1))
        chores.daily()

        hasAccess = false
        status.record(now.minus(Duration.ofDays(5)), CaptureResult())
        chores.daily()

        hasAccess = true
        settings.update { it.copy(readingWarningDays = 0) }
        status.markDisconnected(now)
        chores.daily()

        assertThat(warnings).isEmpty()
    }

    @Test
    fun `the warning waits for the chosen number of days`() = runTest {
        settings.update { it.copy(readingWarningDays = 3) }
        status.markConnected(now.minus(Duration.ofDays(2)))

        chores.daily()
        assertThat(warnings).isEmpty()

        clock.advance(Duration.ofDays(1))
        chores.daily()
        assertThat(warnings).containsExactly(ReadingState.QUIET)
    }
}

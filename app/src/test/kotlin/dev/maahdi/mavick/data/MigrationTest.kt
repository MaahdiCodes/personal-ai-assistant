package dev.maahdi.mavick.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.message.AiState
import dev.maahdi.mavick.data.task.TaskPriority
import dev.maahdi.mavick.data.task.TaskSource
import dev.maahdi.mavick.data.task.TaskStatus
import dev.maahdi.mavick.time.RepeatRule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Upgrading Mavick must never lose tasks. Each database version's schema is kept in app/schemas;
 * this test builds an old database, upgrades it, and checks the data.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseFile = context.getDatabasePath(DATABASE_NAME)

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = databaseFile,
        driver = AndroidSQLiteDriver(),
        databaseClass = MavickDatabase::class,
    )

    @Test
    fun `Phase 0 tasks survive the upgrade to version 2 unchanged`() {
        helper.createDatabase(1).apply {
            execSQL(
                """
                INSERT INTO task (id, title, notes, dueDate, dueTime, remindAt, priority, status, source,
                                  sourceExcerpt, createdAt, updatedAt, deletedAt)
                VALUES ('t1', 'Old task', 'Some notes', '2026-10-08', '17:00', '2026-10-08T16:30', 'HIGH',
                        'OPEN', 'MANUAL', NULL, 1000, 2000, NULL)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(2).close()

        val database = Room.databaseBuilder(context, MavickDatabase::class.java, databaseFile.absolutePath)
            .allowMainThreadQueries()
            .build()
        val task = runBlocking { database.taskDao().findById("t1") }!!
        database.close()

        assertThat(task.title).isEqualTo("Old task")
        assertThat(task.notes).isEqualTo("Some notes")
        assertThat(task.dueDate).isEqualTo(LocalDate.of(2026, 10, 8))
        assertThat(task.dueTime).isEqualTo(LocalTime.of(17, 0))
        assertThat(task.remindAt).isEqualTo(LocalDateTime.of(2026, 10, 8, 16, 30))
        assertThat(task.priority).isEqualTo(TaskPriority.HIGH)
        assertThat(task.status).isEqualTo(TaskStatus.OPEN)
        assertThat(task.createdAt).isEqualTo(Instant.ofEpochMilli(1000))
        // New in version 2, empty for old tasks:
        assertThat(task.reminderTime).isNull()
        assertThat(task.repeatRule).isNull()
        assertThat(task.completedAt).isNull()
    }

    @Test
    fun `Phase 1 tasks survive the upgrade to version 3, which adds empty message tables`() {
        helper.createDatabase(2).apply {
            execSQL(
                """
                INSERT INTO task (id, title, notes, dueDate, dueTime, remindAt, priority, status, source,
                                  sourceExcerpt, createdAt, updatedAt, deletedAt, reminderTime, repeatRule, completedAt)
                VALUES ('t2', 'Pay rent', NULL, '2026-11-01', '10:00', '2026-11-01T10:00', 'NORMAL',
                        'OPEN', 'MANUAL', NULL, 1000, 2000, NULL, '10:00', 'MONTHLY:1', NULL)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(3).close()

        val database = Room.databaseBuilder(context, MavickDatabase::class.java, databaseFile.absolutePath)
            .allowMainThreadQueries()
            .build()
        val task = runBlocking { database.taskDao().findById("t2") }!!
        val messageCount = runBlocking { database.messageDao().count() }
        val rules = runBlocking { database.exclusionRuleDao().getAll() }
        database.close()

        assertThat(task.title).isEqualTo("Pay rent")
        assertThat(task.reminderTime).isEqualTo(LocalTime.of(10, 0))
        assertThat(task.repeatRule).isEqualTo(RepeatRule.Monthly(1))
        assertThat(messageCount).isEqualTo(0)
        assertThat(rules).isEmpty()
    }

    @Test
    fun `Phase 2 tasks and messages survive the upgrade to version 4, which adds an empty suggestion table`() {
        helper.createDatabase(3).apply {
            execSQL(
                """
                INSERT INTO task (id, title, notes, dueDate, dueTime, remindAt, priority, status, source,
                                  sourceExcerpt, createdAt, updatedAt, deletedAt, reminderTime, repeatRule, completedAt)
                VALUES ('t3', 'Call the bank', NULL, '2026-10-06', NULL, NULL, 'NORMAL',
                        'OPEN', 'MESSAGE', 'Call the bank tomorrow', 1000, 2000, NULL, NULL, NULL, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO message (id, app, accountKey, conversationKey, conversationTitle, sender, text, postedAt,
                                     receivedAt, isFromMe, isGroup, cutShort, dedupHash, aiState)
                VALUES ('m1', 'WHATSAPP', '0', 's:family', 'Family', 'Sam', 'Bring the cake at 5', 3000,
                        4000, 0, 1, 0, 'hash-1', 'PENDING')
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(4).close()

        val database = Room.databaseBuilder(context, MavickDatabase::class.java, databaseFile.absolutePath)
            .allowMainThreadQueries()
            .build()
        val task = runBlocking { database.taskDao().findById("t3") }!!
        val message = runBlocking { database.messageDao().findById("m1") }!!
        val suggestionCount = runBlocking { database.suggestionDao().count() }
        database.close()

        assertThat(task.title).isEqualTo("Call the bank")
        assertThat(task.source).isEqualTo(TaskSource.MESSAGE)
        assertThat(message.text).isEqualTo("Bring the cake at 5")
        // Waiting for the AI, which arrives with version 4.
        assertThat(message.aiState).isEqualTo(AiState.PENDING)
        assertThat(suggestionCount).isEqualTo(0)
    }

    @Test
    fun `Phase 3 tasks and suggestions survive the upgrade to version 5, which adds an empty calendar link table`() {
        helper.createDatabase(4).apply {
            execSQL(
                """
                INSERT INTO task (id, title, notes, dueDate, dueTime, remindAt, priority, status, source,
                                  sourceExcerpt, createdAt, updatedAt, deletedAt, reminderTime, repeatRule, completedAt)
                VALUES ('t4', 'Send the form', NULL, '2026-10-08', '17:00', NULL, 'NORMAL',
                        'OPEN', 'MANUAL', NULL, 1000, 2000, NULL, NULL, NULL, NULL)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(5).close()

        val database = Room.databaseBuilder(context, MavickDatabase::class.java, databaseFile.absolutePath)
            .allowMainThreadQueries()
            .build()
        val task = runBlocking { database.taskDao().findById("t4") }!!
        val links = runBlocking { database.calendarLinkDao().all() }
        database.close()

        assertThat(task.title).isEqualTo("Send the form")
        assertThat(task.dueTime).isEqualTo(LocalTime.of(17, 0))
        // No event has been written yet: the first reconcile after switching the feature on does that.
        assertThat(links).isEmpty()
    }

    private companion object {
        const val DATABASE_NAME = "migration-test.db"
    }
}

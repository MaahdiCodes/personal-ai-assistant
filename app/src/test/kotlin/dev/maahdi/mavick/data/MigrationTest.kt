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
import dev.maahdi.mavick.data.task.TaskPriority
import dev.maahdi.mavick.data.task.TaskStatus
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

    private companion object {
        const val DATABASE_NAME = "migration-test.db"
    }
}

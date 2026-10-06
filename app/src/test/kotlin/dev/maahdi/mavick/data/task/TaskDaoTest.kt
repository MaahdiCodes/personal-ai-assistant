package dev.maahdi.mavick.data.task

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.MavickDatabase
import dev.maahdi.mavick.testing.TEST_NOW
import dev.maahdi.mavick.testing.task
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Query behaviour, tested on an in-memory database without SQLCipher (its native library only
 * runs on a phone). Encryption itself is tested on the phone by EncryptedDatabaseTest.
 */
@RunWith(AndroidJUnit4::class)
class TaskDaoTest {
    private lateinit var database: MavickDatabase
    private lateinit var dao: TaskDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MavickDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.taskDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a saved task comes back with every field intact`() = runTest {
        val saved = TaskEntity(
            id = "4f0c2a8e-0000-4000-8000-000000000001",
            title = "Send the signed form to Sam",
            notes = "Use the blue folder",
            dueDate = LocalDate.of(2026, 10, 8),
            dueTime = LocalTime.of(17, 0),
            remindAt = LocalDateTime.of(2026, 10, 8, 16, 30),
            priority = TaskPriority.HIGH,
            status = TaskStatus.OPEN,
            source = TaskSource.MESSAGE,
            sourceExcerpt = "can you send the signed form by Thu 5pm?",
            createdAt = TEST_NOW,
            updatedAt = TEST_NOW.plusSeconds(60),
            deletedAt = null,
        )

        dao.insert(saved)

        assertThat(dao.findById(saved.id)).isEqualTo(saved)
    }

    @Test
    fun `an unknown id finds nothing`() = runTest {
        assertThat(dao.findById("missing")).isNull()
    }

    @Test
    fun `saving the same id twice is rejected`() = runTest {
        val original = task(id = "same-id")
        dao.insert(original)

        val result = runCatching { dao.insert(original.copy(title = "Duplicate")) }

        assertThat(result.exceptionOrNull()).isInstanceOf(SQLiteConstraintException::class.java)
        assertThat(dao.findById("same-id")?.title).isEqualTo(original.title)
    }

    @Test
    fun `open list leaves out done, archived and deleted tasks`() = runTest {
        val open = task(title = "Open")
        dao.insert(open)
        dao.insert(task(title = "Done", status = TaskStatus.DONE))
        dao.insert(task(title = "Archived", status = TaskStatus.ARCHIVED))
        dao.insert(task(title = "Deleted", deletedAt = TEST_NOW))

        assertThat(dao.observeOpen().first()).containsExactly(open)
    }

    @Test
    fun `open list is sorted by date then time, with undated and all-day tasks after timed ones`() = runTest {
        val tomorrowMorning = task(title = "tomorrow 09:00", dueDate = LocalDate.of(2026, 10, 6), dueTime = LocalTime.of(9, 0))
        val tomorrowAllDay = task(title = "tomorrow, any time", dueDate = LocalDate.of(2026, 10, 6))
        val todayEvening = task(title = "today 18:00", dueDate = LocalDate.of(2026, 10, 5), dueTime = LocalTime.of(18, 0))
        val nextYear = task(title = "next year", dueDate = LocalDate.of(2027, 1, 2))
        val undatedOlder = task(title = "undated, older", createdAt = TEST_NOW)
        val undatedNewer = task(title = "undated, newer", createdAt = TEST_NOW.plusSeconds(1))
        listOf(undatedNewer, nextYear, tomorrowAllDay, undatedOlder, tomorrowMorning, todayEvening).forEach { dao.insert(it) }

        val titles = dao.observeOpen().first().map { it.title }

        assertThat(titles).containsExactly(
            "today 18:00",
            "tomorrow 09:00",
            "tomorrow, any time",
            "next year",
            "undated, older",
            "undated, newer",
        ).inOrder()
    }

    @Test
    fun `timed tasks are the open ones with both a date and a time`() = runTest {
        val timed = task(title = "timed", dueDate = LocalDate.of(2026, 10, 8), dueTime = LocalTime.of(17, 0))
        dao.insert(timed)
        dao.insert(task(title = "date only", dueDate = LocalDate.of(2026, 10, 8)))
        dao.insert(task(title = "undated"))
        dao.insert(task(title = "done", dueDate = LocalDate.of(2026, 10, 8), dueTime = LocalTime.of(9, 0), status = TaskStatus.DONE))
        dao.insert(task(title = "archived", dueDate = LocalDate.of(2026, 10, 8), dueTime = LocalTime.of(9, 0), status = TaskStatus.ARCHIVED))
        dao.insert(task(title = "deleted", dueDate = LocalDate.of(2026, 10, 8), dueTime = LocalTime.of(9, 0), deletedAt = TEST_NOW))

        assertThat(dao.getOpenTimed()).containsExactly(timed)
    }

    @Test
    fun `count includes done tasks but not deleted ones`() = runTest {
        dao.insert(task())
        dao.insert(task(status = TaskStatus.DONE))
        dao.insert(task(deletedAt = TEST_NOW))

        assertThat(dao.countActive()).isEqualTo(2)
    }

    @Test
    fun `empty database counts zero`() = runTest {
        assertThat(dao.countActive()).isEqualTo(0)
    }
}

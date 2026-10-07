package dev.maahdi.mavick.data.task

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.MavickDatabase
import dev.maahdi.mavick.testing.FakeReminderScheduler
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.MutableClock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** What follows the tasks (the calendar, the widget) is told after each change, and never gets in its way. */
@RunWith(AndroidJUnit4::class)
class TaskRepositoryListenersTest {
    private lateinit var database: MavickDatabase
    private val clock = MutableClock(MONDAY_10AM)

    private class Recorder : TaskChangeListener {
        val told = mutableListOf<String>()

        override suspend fun taskChanged(taskId: String) {
            told += taskId
        }
    }

    private val first = Recorder()
    private val second = Recorder()
    private lateinit var repository: TaskRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MavickDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TaskRepository(database.taskDao(), FakeReminderScheduler(), clock = { clock }, listeners = listOf(first, second))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `every listener is told about a new task`() = runTest {
        val task = repository.create(TaskDraft(title = "Call"))

        assertThat(first.told).containsExactly(task.id)
        assertThat(second.told).containsExactly(task.id)
    }

    @Test
    fun `each kind of change tells the listeners once`() = runTest {
        val task = repository.create(TaskDraft(title = "Call"))
        first.told.clear()

        repository.update(task.id, TaskDraft(title = "Call again"))
        repository.complete(task.id)
        repository.reopen(task.id)
        repository.snooze(task.id, 10)
        repository.moveToTomorrow(task.id)
        val before = repository.find(task.id)!!
        repository.delete(task.id)
        repository.restore(before)

        assertThat(first.told).containsExactly(task.id, task.id, task.id, task.id, task.id, task.id, task.id)
    }

    @Test
    fun `a change that changes nothing tells nobody`() = runTest {
        val task = repository.create(TaskDraft(title = "Call"))
        repository.complete(task.id)
        first.told.clear()

        repository.complete(task.id) // already done
        repository.update("no-such-task", TaskDraft(title = "x"))

        assertThat(first.told).isEmpty()
    }

    @Test
    fun `a listener that fails does not stop the others or undo the change`() = runTest {
        val failing = object : TaskChangeListener {
            override suspend fun taskChanged(taskId: String) = throw IllegalStateException("broken")
        }
        val withFailure = TaskRepository(database.taskDao(), FakeReminderScheduler(), clock = { clock }, listeners = listOf(failing, second))

        val task = withFailure.create(TaskDraft(title = "Call"))

        assertThat(withFailure.find(task.id)).isNotNull()
        assertThat(second.told).containsExactly(task.id)
    }

    @Test
    fun `a listener is told after the change is saved, so it can read it`() = runTest {
        var seenTitle: String? = null
        val reading = object : TaskChangeListener {
            override suspend fun taskChanged(taskId: String) {
                seenTitle = database.taskDao().findById(taskId)?.title
            }
        }
        val reader = TaskRepository(database.taskDao(), FakeReminderScheduler(), clock = { clock }, listeners = listOf(reading))

        reader.create(TaskDraft(title = "Call the bank"))

        assertThat(seenTitle).isEqualTo("Call the bank")
    }

    @Test
    fun `a repository with no listeners works as before`() = runTest {
        val plain = TaskRepository(database.taskDao(), FakeReminderScheduler(), clock = { clock })

        assertThat(plain.create(TaskDraft(title = "Call")).title).isEqualTo("Call")
    }
}

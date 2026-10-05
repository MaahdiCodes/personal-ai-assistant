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
import dev.maahdi.mavick.time.DEFAULT_WORK_DAYS
import dev.maahdi.mavick.time.RepeatRule
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskRepositoryTest {
    private lateinit var database: MavickDatabase
    private val scheduler = FakeReminderScheduler()
    private val clock = MutableClock(MONDAY_10AM)
    private lateinit var repository: TaskRepository

    private val today: LocalDate = MONDAY_10AM.toLocalDate()
    private val tomorrow: LocalDate = today.plusDays(1)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MavickDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TaskRepository(database.taskDao(), scheduler, clock = { clock })
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun createAt(time: LocalTime, date: LocalDate = today, repeat: RepeatRule? = null, title: String = "Call") =
        repository.create(TaskDraft(title = title, dueDate = date, dueTime = time, reminderTime = time, repeatRule = repeat))

    // --- Creating ---

    @Test
    fun `a task with a reminder sets its alarm`() = runTest {
        val task = createAt(LocalTime.of(17, 0))

        assertThat(task.remindAt).isEqualTo(today.atTime(17, 0))
        assertThat(scheduler.alarms).containsExactly(task.id, today.atTime(17, 0))
    }

    @Test
    fun `a date-only task has no alarm`() = runTest {
        val task = repository.create(TaskDraft(title = "Pay rent", dueDate = tomorrow))

        assertThat(task.remindAt).isNull()
        assertThat(scheduler.alarms).isEmpty()
    }

    @Test
    fun `a reminder time already past sets no alarm`() = runTest {
        val task = createAt(LocalTime.of(8, 0))

        assertThat(task.remindAt).isNull()
        assertThat(scheduler.alarms).isEmpty()
    }

    @Test
    fun `a blank title is refused`() = runTest {
        val result = runCatching { repository.create(TaskDraft(title = " ")) }

        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `new tasks get unique ids`() = runTest {
        val first = repository.create(TaskDraft(title = "a"))
        val second = repository.create(TaskDraft(title = "b"))

        assertThat(first.id).isNotEqualTo(second.id)
    }

    // --- Completing ---

    @Test
    fun `completing a one-off task marks it done and cancels its alarm`() = runTest {
        val task = createAt(LocalTime.of(17, 0))

        val done = repository.complete(task.id)!!

        assertThat(done.status).isEqualTo(TaskStatus.DONE)
        assertThat(done.completedAt).isNotNull()
        assertThat(done.remindAt).isNull()
        assertThat(scheduler.alarms).isEmpty()
        assertThat(repository.observeOpen().first()).isEmpty()
        assertThat(repository.observeDone().first().map { it.id }).containsExactly(task.id)
    }

    @Test
    fun `completing a daily task moves it to tomorrow with its reminder`() = runTest {
        val task = createAt(LocalTime.of(21, 0), repeat = RepeatRule.Daily())

        val next = repository.complete(task.id)!!

        assertThat(next.status).isEqualTo(TaskStatus.OPEN)
        assertThat(next.dueDate).isEqualTo(tomorrow)
        assertThat(next.remindAt).isEqualTo(tomorrow.atTime(21, 0))
        assertThat(scheduler.alarms).containsExactly(task.id, tomorrow.atTime(21, 0))
    }

    @Test
    fun `completing an overdue repeating task skips to the next occurrence after today`() = runTest {
        val task = createAt(LocalTime.of(21, 0), date = today.minusDays(3), repeat = RepeatRule.Daily())

        val next = repository.complete(task.id)!!

        assertThat(next.dueDate).isEqualTo(tomorrow)
    }

    @Test
    fun `completing a repeating task early moves to the occurrence after its due date`() = runTest {
        val thursday = LocalDate.of(2026, 10, 8)
        val task = createAt(LocalTime.of(9, 0), date = thursday, repeat = RepeatRule.Weekly(setOf(thursday.dayOfWeek)))

        val next = repository.complete(task.id)!!

        assertThat(next.dueDate).isEqualTo(thursday.plusWeeks(1))
    }

    @Test
    fun `completing a monthly task on the 31st lands on the last day of a short month`() = runTest {
        clock.setLocal(LocalDateTime.of(2026, 10, 31, 8, 0))
        val task = createAt(LocalTime.of(9, 0), date = LocalDate.of(2026, 10, 31), repeat = RepeatRule.Monthly(31))

        val next = repository.complete(task.id)!!

        assertThat(next.dueDate).isEqualTo(LocalDate.of(2026, 11, 30))
    }

    @Test
    fun `completing a work-day task on Thursday moves it to Sunday`() = runTest {
        val thursday = LocalDate.of(2026, 10, 8)
        clock.setLocal(thursday.atTime(10, 0))
        val task = createAt(LocalTime.of(10, 30), date = thursday, repeat = RepeatRule.Weekly(DEFAULT_WORK_DAYS))

        val next = repository.complete(task.id)!!

        assertThat(next.dueDate).isEqualTo(LocalDate.of(2026, 10, 11))
    }

    @Test
    fun `completing a done task changes nothing`() = runTest {
        val task = repository.create(TaskDraft(title = "a"))
        val done = repository.complete(task.id)!!

        assertThat(repository.complete(task.id)).isEqualTo(done)
    }

    @Test
    fun `reopening a done task brings back a reminder that is still ahead`() = runTest {
        val task = createAt(LocalTime.of(17, 0))
        repository.complete(task.id)

        val reopened = repository.reopen(task.id)!!

        assertThat(reopened.status).isEqualTo(TaskStatus.OPEN)
        assertThat(reopened.completedAt).isNull()
        assertThat(scheduler.alarms).containsExactly(task.id, today.atTime(17, 0))
    }

    // --- Deleting and undo ---

    @Test
    fun `deleting hides the task and cancels its alarm`() = runTest {
        val task = createAt(LocalTime.of(17, 0))

        repository.delete(task.id)

        assertThat(repository.observeOpen().first()).isEmpty()
        assertThat(repository.find(task.id)).isNull()
        assertThat(scheduler.alarms).isEmpty()
    }

    @Test
    fun `undo restores the task and its alarm`() = runTest {
        val task = createAt(LocalTime.of(17, 0))
        repository.complete(task.id)

        repository.restore(task)

        assertThat(repository.find(task.id)?.status).isEqualTo(TaskStatus.OPEN)
        assertThat(scheduler.alarms).containsExactly(task.id, today.atTime(17, 0))
    }

    @Test
    fun `undo does not bring back a reminder whose time has passed`() = runTest {
        val task = createAt(LocalTime.of(10, 30))
        repository.complete(task.id)
        clock.advance(Duration.ofHours(1))

        repository.restore(task)

        assertThat(repository.find(task.id)?.remindAt).isNull()
        assertThat(scheduler.alarms).isEmpty()
    }

    @Test
    fun `tasks deleted more than 30 days ago are purged, recent ones kept`() = runTest {
        val old = repository.create(TaskDraft(title = "old"))
        repository.delete(old.id)
        clock.advance(Duration.ofDays(31))
        val recent = repository.create(TaskDraft(title = "recent"))
        repository.delete(recent.id)

        assertThat(repository.purgeOldDeleted()).isEqualTo(1)
        assertThat(database.taskDao().findById(old.id)).isNull()
        assertThat(database.taskDao().findById(recent.id)).isNotNull()
    }

    // --- Reminder buttons ---

    @Test
    fun `snooze moves only the reminder, ten minutes from now`() = runTest {
        val task = createAt(LocalTime.of(10, 5))
        clock.setLocal(today.atTime(10, 5))

        val snoozed = repository.snooze(task.id, 10)!!

        assertThat(snoozed.remindAt).isEqualTo(today.atTime(10, 15))
        assertThat(snoozed.dueTime).isEqualTo(LocalTime.of(10, 5))
        assertThat(scheduler.alarms).containsExactly(task.id, today.atTime(10, 15))
    }

    @Test
    fun `tomorrow moves the task and reminds at its usual time`() = runTest {
        val task = createAt(LocalTime.of(17, 0))

        val moved = repository.moveToTomorrow(task.id)!!

        assertThat(moved.dueDate).isEqualTo(tomorrow)
        assertThat(moved.remindAt).isEqualTo(tomorrow.atTime(17, 0))
    }

    @Test
    fun `tomorrow on a task without a time reminds at 9 am`() = runTest {
        val task = repository.create(TaskDraft(title = "Pay rent", dueDate = today))

        val moved = repository.moveToTomorrow(task.id)!!

        assertThat(moved.remindAt).isEqualTo(tomorrow.atTime(9, 0))
    }

    // --- Alarms going off ---

    @Test
    fun `a due alarm hands over the task once`() = runTest {
        val task = createAt(LocalTime.of(10, 30))
        clock.setLocal(today.atTime(10, 30))

        assertThat(repository.takeDueReminder(task.id)?.id).isEqualTo(task.id)
        assertThat(repository.takeDueReminder(task.id)).isNull()
    }

    @Test
    fun `an alarm for a reminder that moved later sets the alarm again instead`() = runTest {
        val task = createAt(LocalTime.of(10, 30))
        clock.setLocal(today.atTime(10, 30))
        repository.snooze(task.id, 10)
        scheduler.alarms.clear()

        assertThat(repository.takeDueReminder(task.id)).isNull()
        assertThat(scheduler.alarms).containsExactly(task.id, today.atTime(10, 40))
    }

    @Test
    fun `an alarm for a done or deleted task does nothing`() = runTest {
        val done = createAt(LocalTime.of(10, 30), title = "done")
        val deleted = createAt(LocalTime.of(10, 30), title = "deleted")
        repository.complete(done.id)
        repository.delete(deleted.id)
        clock.setLocal(today.atTime(10, 30))

        assertThat(repository.takeDueReminder(done.id)).isNull()
        assertThat(repository.takeDueReminder(deleted.id)).isNull()
        assertThat(repository.takeDueReminder("no-such-task")).isNull()
    }

    @Test
    fun `after a restart, future alarms are set again and missed ones reported once`() = runTest {
        val missed = createAt(LocalTime.of(11, 0), title = "missed")
        val future = createAt(LocalTime.of(18, 0), title = "future")
        scheduler.alarms.clear()
        clock.setLocal(today.atTime(12, 0))

        val reported = repository.rescheduleAll()

        assertThat(reported.map { it.id }).containsExactly(missed.id)
        assertThat(scheduler.alarms).containsExactly(future.id, today.atTime(18, 0))
        assertThat(repository.rescheduleAll()).isEmpty()
    }

    // --- Editing ---

    @Test
    fun `editing replaces the details and keeps where the task came from`() = runTest {
        val original = repository.create(TaskDraft(title = "Old", source = TaskSource.KEEP, sourceExcerpt = "note"))

        val edited = repository.update(
            original.id,
            TaskDraft(title = "New", dueDate = tomorrow, dueTime = LocalTime.of(9, 0), reminderTime = LocalTime.of(8, 30)),
        )!!

        assertThat(edited.title).isEqualTo("New")
        assertThat(edited.source).isEqualTo(TaskSource.KEEP)
        assertThat(edited.sourceExcerpt).isEqualTo("note")
        assertThat(edited.createdAt).isEqualTo(original.createdAt)
        assertThat(scheduler.alarms).containsExactly(original.id, tomorrow.atTime(8, 30))
    }

    @Test
    fun `editing a task that no longer exists does nothing`() = runTest {
        assertThat(repository.update("missing", TaskDraft(title = "x"))).isNull()
    }

    // --- Morning briefing ---

    @Test
    fun `the briefing lists overdue and today's open tasks only`() = runTest {
        val overdue = repository.create(TaskDraft(title = "overdue", dueDate = today.minusDays(1)))
        val dueToday = repository.create(TaskDraft(title = "today", dueDate = today))
        repository.create(TaskDraft(title = "tomorrow", dueDate = tomorrow))
        repository.create(TaskDraft(title = "undated"))
        val done = repository.create(TaskDraft(title = "done", dueDate = today))
        repository.complete(done.id)

        val briefing = repository.briefing(today)

        assertThat(briefing.overdue.map { it.id }).containsExactly(overdue.id)
        assertThat(briefing.today.map { it.id }).containsExactly(dueToday.id)
    }

    @Test
    fun `an empty day gives an empty briefing`() = runTest {
        assertThat(repository.briefing(today).isEmpty).isTrue()
    }
}

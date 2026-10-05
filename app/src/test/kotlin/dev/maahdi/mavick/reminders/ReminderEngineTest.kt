package dev.maahdi.mavick.reminders

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.MavickDatabase
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.data.task.TaskRepository
import dev.maahdi.mavick.data.task.TaskStatus
import dev.maahdi.mavick.testing.FakeDailyChores
import dev.maahdi.mavick.testing.FakeNotifier
import dev.maahdi.mavick.testing.FakeReminderScheduler
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.MutableClock
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderEngineTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: MavickDatabase
    private val scheduler = FakeReminderScheduler()
    private val notifier = FakeNotifier()
    private val clock = MutableClock(MONDAY_10AM)
    private val chores = FakeDailyChores()
    private lateinit var settings: SettingsRepository
    private lateinit var tasks: TaskRepository
    private lateinit var engine: ReminderEngine

    private val today = MONDAY_10AM.toLocalDate()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MavickDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(context.getSharedPreferences("engine-test", Context.MODE_PRIVATE))
        tasks = TaskRepository(database.taskDao(), scheduler, clock = { clock })
        engine = ReminderEngine(tasks, notifier, scheduler, settings, clock = { clock }, chores = chores)
    }

    @After
    fun tearDown() {
        database.close()
        context.getSharedPreferences("engine-test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private suspend fun taskAt(time: LocalTime, title: String = "Call") =
        tasks.create(TaskDraft(title = title, dueDate = today, dueTime = time, reminderTime = time))

    @Test
    fun `an alarm shows the reminder`() = runTest {
        val task = taskAt(LocalTime.of(10, 30))
        clock.setLocal(today.atTime(10, 30))

        engine.onReminderAlarm(task.id)

        assertThat(notifier.reminders).containsExactly(FakeNotifier.Shown(task.id, "Call", missed = false))
    }

    @Test
    fun `an alarm for a task already done shows nothing`() = runTest {
        val task = taskAt(LocalTime.of(10, 30))
        tasks.complete(task.id)
        clock.setLocal(today.atTime(10, 30))

        engine.onReminderAlarm(task.id)

        assertThat(notifier.reminders).isEmpty()
    }

    @Test
    fun `done on the notification completes the task and removes the notification`() = runTest {
        val task = taskAt(LocalTime.of(10, 30))

        engine.onNotificationAction(task.id, ReminderAction.DONE)

        assertThat(tasks.find(task.id)?.status).isEqualTo(TaskStatus.DONE)
        assertThat(notifier.cancelled).containsExactly(task.id)
    }

    @Test
    fun `snooze on the notification reminds again in ten minutes`() = runTest {
        val task = taskAt(LocalTime.of(10, 30))
        clock.setLocal(today.atTime(10, 30))

        engine.onNotificationAction(task.id, ReminderAction.SNOOZE)

        assertThat(scheduler.alarms[task.id]).isEqualTo(today.atTime(10, 40))
    }

    @Test
    fun `tomorrow on the notification moves the task`() = runTest {
        val task = taskAt(LocalTime.of(10, 30))

        engine.onNotificationAction(task.id, ReminderAction.TOMORROW)

        assertThat(tasks.find(task.id)?.dueDate).isEqualTo(today.plusDays(1))
    }

    @Test
    fun `resync shows missed reminders and sets the briefing`() = runTest {
        val missed = taskAt(LocalTime.of(10, 30), title = "missed")
        clock.setLocal(today.atTime(11, 0))

        engine.resync()

        assertThat(notifier.reminders).containsExactly(FakeNotifier.Shown(missed.id, "missed", missed = true))
        assertThat(scheduler.dailyAt).isEqualTo(today.plusDays(1).atTime(8, 0))
    }

    @Test
    fun `resync runs only once per app start`() = runTest {
        taskAt(LocalTime.of(10, 30))
        clock.setLocal(today.atTime(11, 0))

        engine.resyncOncePerProcess()
        engine.resyncOncePerProcess()

        assertThat(notifier.reminders).hasSize(1)
    }

    @Test
    fun `the briefing is set for today when its time is still ahead`() {
        clock.setLocal(today.atTime(7, 0))

        engine.scheduleDailyAlarm()

        assertThat(scheduler.dailyAt).isEqualTo(today.atTime(8, 0))
    }

    @Test
    fun `the briefing follows the chosen time`() {
        settings.update { it.copy(briefingTime = LocalTime.of(6, 30)) }

        engine.scheduleDailyAlarm()

        assertThat(scheduler.dailyAt).isEqualTo(today.plusDays(1).atTime(6, 30))
    }

    @Test
    fun `turning the briefing off keeps the daily alarm, which also cleans up`() {
        settings.update { it.copy(briefingEnabled = false) }

        engine.scheduleDailyAlarm()

        assertThat(scheduler.dailyAt).isEqualTo(today.plusDays(1).atTime(8, 0))
    }

    @Test
    fun `with the briefing off, the daily alarm shows nothing but still does the chores`() = runTest {
        settings.update { it.copy(briefingEnabled = false) }
        tasks.create(TaskDraft(title = "Pay rent", dueDate = today))
        clock.setLocal(today.atTime(8, 0))

        engine.onDailyAlarm()

        assertThat(notifier.briefings).isEmpty()
        assertThat(chores.cleanUps).isEqualTo(1)
        assertThat(chores.dailies).isEqualTo(1)
        assertThat(scheduler.dailyAt).isEqualTo(today.plusDays(1).atTime(8, 0))
    }

    @Test
    fun `a daily alarm that fires a moment early still sets tomorrow's, not another for today`() = runTest {
        clock.setLocal(today.atTime(7, 59, 59))

        engine.onDailyAlarm()

        assertThat(scheduler.dailyAt).isEqualTo(today.plusDays(1).atTime(8, 0))
    }

    @Test
    fun `a failing chore can't stop tomorrow's daily alarm`() = runTest {
        val failing = ReminderEngine(tasks, notifier, scheduler, settings, clock = { clock }, chores = FakeDailyChores(failCleanUp = true))
        clock.setLocal(today.atTime(8, 0))

        runCatching { failing.onDailyAlarm() }

        assertThat(scheduler.dailyAt).isEqualTo(today.plusDays(1).atTime(8, 0))
    }

    @Test
    fun `resync cleans up, but leaves the daily warnings to the daily alarm`() = runTest {
        engine.resync()

        assertThat(chores.cleanUps).isEqualTo(1)
        assertThat(chores.dailies).isEqualTo(0)
    }

    @Test
    fun `the briefing appears when something is due and is set again for tomorrow`() = runTest {
        tasks.create(TaskDraft(title = "Pay rent", dueDate = today))
        clock.setLocal(today.atTime(8, 0))

        engine.onDailyAlarm()

        assertThat(notifier.briefings).hasSize(1)
        assertThat(notifier.briefings.single().today.map { it.title }).containsExactly("Pay rent")
        assertThat(scheduler.dailyAt).isEqualTo(today.plusDays(1).atTime(8, 0))
    }

    @Test
    fun `a quiet day gets no briefing, but the next one is still set`() = runTest {
        clock.setLocal(today.atTime(8, 0))

        engine.onDailyAlarm()

        assertThat(notifier.briefings).isEmpty()
        assertThat(scheduler.dailyAt).isEqualTo(today.plusDays(1).atTime(8, 0))
    }

    @Test
    fun `the briefing runs on weekends too`() = runTest {
        val saturday = today.plusDays(5)
        tasks.create(TaskDraft(title = "Weekend errand", dueDate = saturday))
        clock.setLocal(saturday.atTime(8, 0))

        engine.onDailyAlarm()

        assertThat(notifier.briefings).hasSize(1)
    }
}

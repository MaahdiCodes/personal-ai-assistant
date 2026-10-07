package dev.maahdi.mavick.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.R
import dev.maahdi.mavick.data.MavickDatabase
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.data.task.TaskRepository
import dev.maahdi.mavick.testing.FakeReminderScheduler
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.MutableClock
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Stands in for Mavick's widget receiver, which would start real work of its own when a test creates a widget. */
class TestWidgetProvider : AppWidgetProvider()

@RunWith(AndroidJUnit4::class)
class WidgetUpdaterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("widget-updater-test", Context.MODE_PRIVATE)
    private val clock = MutableClock(MONDAY_10AM)
    private val today = MONDAY_10AM.toLocalDate()
    private lateinit var database: MavickDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var tasks: TaskRepository
    private lateinit var updater: WidgetUpdater
    private var briefingReads = 0
    private var briefingFailure: Exception? = null
    private val manager = AppWidgetManager.getInstance(context)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MavickDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(preferences)
        val plainTasks = TaskRepository(database.taskDao(), FakeReminderScheduler(), clock = { clock })
        updater = WidgetUpdater(
            context = context,
            briefing = { date ->
                briefingReads++
                briefingFailure?.let { throw it }
                plainTasks.briefing(date)
            },
            settings = settings,
            clock = { clock },
            dispatcher = Dispatchers.Unconfined,
            provider = ComponentName(context, TestWidgetProvider::class.java),
        )
        tasks = TaskRepository(database.taskDao(), FakeReminderScheduler(), clock = { clock }, listeners = listOf(updater))
    }

    @After
    fun tearDown() {
        database.close()
        preferences.edit().clear().commit()
    }

    private fun addWidget(): Int = shadowOf(manager).createWidget(TestWidgetProvider::class.java, R.layout.widget_tasks)

    private fun View.text(id: Int) = findViewById<TextView>(id).text.toString()

    private fun viewOf(widgetId: Int): View = shadowOf(manager).getViewFor(widgetId)

    private suspend fun dueToday(title: String, hour: Int) =
        tasks.create(TaskDraft(title = title, dueDate = today, dueTime = LocalTime.of(hour, 0)))

    @Test
    fun `with no widget on the home screen nothing is read at all`() = runTest {
        dueToday("Call the bank", 17)

        updater.update()

        assertThat(briefingReads).isEqualTo(0)
    }

    @Test
    fun `a widget shows today's tasks`() = runTest {
        val widget = addWidget()
        dueToday("Call the bank", 17)

        updater.update()

        val view = viewOf(widget)
        assertThat(view.text(R.id.widget_date)).isEqualTo("Mon 5 Oct")
        assertThat(view.text(R.id.widget_row_0)).endsWith("Call the bank")
        assertThat(view.text(R.id.widget_summary)).isEqualTo("1 task due today")
    }

    @Test
    fun `every widget on the home screen is updated`() = runTest {
        val first = addWidget()
        val second = addWidget()
        dueToday("Call the bank", 17)

        updater.update()

        assertThat(viewOf(first).text(R.id.widget_row_0)).endsWith("Call the bank")
        assertThat(viewOf(second).text(R.id.widget_row_0)).endsWith("Call the bank")
    }

    @Test
    fun `the widget follows every change to a task`() = runTest {
        val widget = addWidget()
        val task = dueToday("Call the bank", 17)
        assertThat(viewOf(widget).text(R.id.widget_row_0)).endsWith("Call the bank")

        tasks.update(task.id, TaskDraft(title = "Call the bank again", dueDate = today, dueTime = LocalTime.of(17, 0)))
        assertThat(viewOf(widget).text(R.id.widget_row_0)).endsWith("Call the bank again")

        tasks.complete(task.id)
        assertThat(viewOf(widget).text(R.id.widget_message)).isEqualTo("Nothing due today")

        tasks.reopen(task.id)
        assertThat(viewOf(widget).text(R.id.widget_row_0)).endsWith("Call the bank again")

        tasks.delete(task.id)
        assertThat(viewOf(widget).text(R.id.widget_message)).isEqualTo("Nothing due today")
    }

    @Test
    fun `hiding the titles takes them off the widget at once`() = runTest {
        val widget = addWidget()
        dueToday("Secret plan", 17)
        assertThat(viewOf(widget).text(R.id.widget_row_0)).endsWith("Secret plan")

        settings.update { it.copy(widgetShowTitles = false) }
        updater.update()

        val view = viewOf(widget)
        assertThat(view.findViewById<View>(R.id.widget_row_0).visibility).isEqualTo(View.GONE)
        assertThat(view.text(R.id.widget_summary)).isEqualTo("1 task due today")
    }

    @Test
    fun `a new day shows the new date and the tasks due then`() = runTest {
        val widget = addWidget()
        dueToday("Today's task", 17)
        tasks.create(TaskDraft(title = "Tomorrow's task", dueDate = today.plusDays(1), dueTime = LocalTime.of(9, 0)))

        clock.setLocal(today.plusDays(1).atTime(7, 0))
        updater.update()

        val view = viewOf(widget)
        assertThat(view.text(R.id.widget_date)).isEqualTo("Tue 6 Oct")
        assertThat(view.text(R.id.widget_row_0)).isEqualTo("Overdue: Today's task")
        assertThat(view.text(R.id.widget_row_1)).endsWith("Tomorrow's task")
    }

    @Test
    fun `when the tasks cannot be read the widget asks to open Mavick and nothing crashes`() = runTest {
        val widget = addWidget()
        briefingFailure = IllegalStateException("database locked until the first unlock")

        updater.update()

        assertThat(viewOf(widget).text(R.id.widget_message)).isEqualTo("Open Mavick to see your tasks")
    }

    @Test
    fun `the widget recovers once the tasks can be read again`() = runTest {
        val widget = addWidget()
        dueToday("Call the bank", 17)
        briefingFailure = IllegalStateException("locked")
        updater.update()

        briefingFailure = null
        updater.update()

        assertThat(viewOf(widget).text(R.id.widget_row_0)).endsWith("Call the bank")
    }

    @Test
    fun `a failure of the widget never fails a task change`() = runTest {
        addWidget()
        briefingFailure = IllegalStateException("locked")

        val task = dueToday("Call the bank", 17)

        assertThat(tasks.find(task.id)).isNotNull()
    }
}

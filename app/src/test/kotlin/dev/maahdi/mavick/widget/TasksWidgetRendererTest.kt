package dev.maahdi.mavick.widget

import android.app.Application
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.R
import dev.maahdi.mavick.reminders.ReminderIntents
import dev.maahdi.mavick.testing.task
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class TasksWidgetRendererTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val renderer = TasksWidgetRenderer(context)

    // Monday 5 October 2026.
    private val date = LocalDate.of(2026, 10, 5)

    private fun timed(title: String, hour: Int, minute: Int = 0) = task(title = title, dueDate = date, dueTime = LocalTime.of(hour, minute))

    private fun plan(
        today: List<dev.maahdi.mavick.data.task.TaskEntity> = emptyList(),
        overdue: List<dev.maahdi.mavick.data.task.TaskEntity> = emptyList(),
        showTitles: Boolean = true,
    ) = WidgetPlanner.plan(dev.maahdi.mavick.data.task.Briefing(overdue, today), date, showTitles)

    private fun view(plan: WidgetPlan?): View = renderer.render(plan, use24Hour = true).apply(context, FrameLayout(context))

    private fun View.text(id: Int): String = findViewById<TextView>(id).text.toString()

    private fun View.visible(id: Int): Boolean = findViewById<View>(id).visibility == View.VISIBLE

    private val rows = listOf(R.id.widget_row_0, R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3, R.id.widget_row_4)

    @Test
    fun `the header shows the date and how many tasks are due`() {
        val view = view(plan(today = listOf(timed("Call the bank", 17), timed("Pay rent", 18)), overdue = listOf(task(title = "Old", dueDate = date.minusDays(1)))))

        assertThat(view.text(R.id.widget_date)).isEqualTo("Mon 5 Oct")
        assertThat(view.text(R.id.widget_summary)).isEqualTo("2 tasks due today · 1 overdue")
    }

    @Test
    fun `each task is a line with its time, and overdue ones say so`() {
        val view = view(plan(today = listOf(timed("Call the bank", 17, 15), task(title = "Pay rent", dueDate = date)), overdue = listOf(task(title = "Old", dueDate = date.minusDays(1)))))

        assertThat(view.text(R.id.widget_row_0)).isEqualTo("Overdue: Old")
        assertThat(view.text(R.id.widget_row_1)).isEqualTo("17:15  Call the bank")
        assertThat(view.text(R.id.widget_row_2)).isEqualTo("Pay rent")
        assertThat(view.visible(R.id.widget_row_2)).isTrue()
        assertThat(view.visible(R.id.widget_row_3)).isFalse()
        assertThat(view.visible(R.id.widget_row_4)).isFalse()
        assertThat(view.visible(R.id.widget_message)).isFalse()
        assertThat(view.visible(R.id.widget_more)).isFalse()
    }

    @Test
    fun `an overdue line has its own colour`() {
        val view = view(plan(today = listOf(timed("Call", 17)), overdue = listOf(task(title = "Old", dueDate = date.minusDays(1)))))

        assertThat(view.findViewById<TextView>(R.id.widget_row_0).currentTextColor).isEqualTo(ContextCompat.getColor(context, R.color.widget_overdue))
        assertThat(view.findViewById<TextView>(R.id.widget_row_1).currentTextColor).isEqualTo(ContextCompat.getColor(context, R.color.widget_text))
    }

    @Test
    fun `tasks that do not fit are counted`() {
        val view = view(plan(today = (1..8).map { timed("Task $it", 8 + it) }))

        rows.forEach { assertThat(view.visible(it)).isTrue() }
        assertThat(view.text(R.id.widget_more)).isEqualTo("+3 more")
        assertThat(view.visible(R.id.widget_more)).isTrue()
    }

    @Test
    fun `with titles hidden no title appears anywhere, only the counts`() {
        val view = view(plan(today = listOf(timed("Secret plan", 17)), overdue = listOf(task(title = "Hidden debt", dueDate = date.minusDays(1))), showTitles = false))

        rows.forEach { assertThat(view.visible(it)).isFalse() }
        assertThat(view.text(R.id.widget_summary)).isEqualTo("1 task due today · 1 overdue")
        assertThat(view.text(R.id.widget_message)).isEqualTo("Titles are hidden. Open Mavick to see them.")
        val everything = listOf(R.id.widget_date, R.id.widget_summary, R.id.widget_message, R.id.widget_more).joinToString { view.text(it) }
        assertThat(everything).doesNotContain("Secret")
        assertThat(everything).doesNotContain("Hidden debt")
    }

    @Test
    fun `a day with nothing due says so`() {
        val view = view(plan())

        assertThat(view.text(R.id.widget_message)).isEqualTo("Nothing due today")
        assertThat(view.text(R.id.widget_summary)).isEmpty()
        rows.forEach { assertThat(view.visible(it)).isFalse() }
    }

    @Test
    fun `when the tasks cannot be read the widget asks to open Mavick`() {
        val view = view(null)

        assertThat(view.text(R.id.widget_message)).isEqualTo("Open Mavick to see your tasks")
        assertThat(view.text(R.id.widget_date)).isEqualTo(context.getString(R.string.app_name))
        rows.forEach { assertThat(view.visible(it)).isFalse() }
    }

    @Test
    fun `lines from an earlier draw never linger`() {
        // A widget is drawn again onto the same views: what was shown must be hidden again.
        val view = view(plan(today = listOf(timed("One", 9))))
        val redrawn = renderer.render(plan(), use24Hour = true)
        redrawn.reapply(context, view)

        assertThat(view.visible(R.id.widget_row_0)).isFalse()
        assertThat(view.visible(R.id.widget_message)).isTrue()
    }

    // --- Taps ---

    private fun tapped(view: View, id: Int): Intent {
        view.findViewById<View>(id).performClick()
        return shadowOf(context as Application).nextStartedActivity
    }

    @Test
    fun `a line opens its task`() {
        val bank = timed("Call the bank", 17)
        val view = view(plan(today = listOf(bank)))

        val intent = tapped(view, R.id.widget_row_0)

        assertThat(intent.action).isEqualTo(ReminderIntents.ACTION_OPEN_TASK)
        assertThat(ReminderIntents.taskIdOf(intent)).isEqualTo(bank.id)
        assertThat(intent.component!!.className).isEqualTo("dev.maahdi.mavick.MainActivity")
    }

    @Test
    fun `each line opens its own task`() {
        val first = timed("First", 9)
        val second = timed("Second", 10)
        val view = view(plan(today = listOf(first, second)))

        assertThat(ReminderIntents.taskIdOf(tapped(view, R.id.widget_row_0))).isEqualTo(first.id)
        assertThat(ReminderIntents.taskIdOf(tapped(view, R.id.widget_row_1))).isEqualTo(second.id)
    }

    @Test
    fun `the plus opens a new task`() {
        val intent = tapped(view(plan()), R.id.widget_add)

        assertThat(intent.action).isEqualTo(ReminderIntents.ACTION_NEW_TASK)
    }

    @Test
    fun `the heading opens Mavick`() {
        val intent = tapped(view(plan()), R.id.widget_title_block)

        assertThat(intent.component!!.className).isEqualTo("dev.maahdi.mavick.MainActivity")
        assertThat(intent.action).isNull()
    }

    @Test
    fun `the plus and the heading work when the tasks cannot be read too`() {
        val view = view(null)

        assertThat(tapped(view, R.id.widget_add).action).isEqualTo(ReminderIntents.ACTION_NEW_TASK)
        assertThat(tapped(view, R.id.widget_title_block).component!!.className).isEqualTo("dev.maahdi.mavick.MainActivity")
    }
}

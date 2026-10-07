package dev.maahdi.mavick.widget

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.task.Briefing
import dev.maahdi.mavick.testing.task
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Test

class WidgetPlannerTest {
    private val date = LocalDate.of(2026, 10, 5)

    private fun todayTask(title: String, hour: Int) = task(title = title, dueDate = date, dueTime = LocalTime.of(hour, 0))

    private fun overdueTask(title: String) = task(title = title, dueDate = date.minusDays(1))

    @Test
    fun `overdue tasks come first, then today's, each in the order given`() {
        val briefing = Briefing(
            overdue = listOf(overdueTask("Old 1"), overdueTask("Old 2")),
            today = listOf(todayTask("Morning", 9), todayTask("Evening", 18)),
        )

        val plan = WidgetPlanner.plan(briefing, date, showTitles = true)

        assertThat(plan.rows.map { it.task.title }).containsExactly("Old 1", "Old 2", "Morning", "Evening").inOrder()
        assertThat(plan.rows.map { it.overdue }).containsExactly(true, true, false, false).inOrder()
        assertThat(plan.overdue).isEqualTo(2)
        assertThat(plan.dueToday).isEqualTo(2)
        assertThat(plan.moreCount).isEqualTo(0)
        assertThat(plan.date).isEqualTo(date)
    }

    @Test
    fun `no more than five lines show, and the rest are counted`() {
        val briefing = Briefing(
            overdue = listOf(overdueTask("Old")),
            today = (1..7).map { todayTask("Task $it", 8 + it) },
        )

        val plan = WidgetPlanner.plan(briefing, date, showTitles = true)

        assertThat(plan.rows).hasSize(WidgetPlanner.MAX_ROWS)
        assertThat(plan.rows.first().task.title).isEqualTo("Old")
        assertThat(plan.moreCount).isEqualTo(3)
        // The counts always cover everything, not only what fits.
        assertThat(plan.dueToday).isEqualTo(7)
        assertThat(plan.overdue).isEqualTo(1)
    }

    @Test
    fun `exactly five tasks leave nothing over`() {
        val plan = WidgetPlanner.plan(Briefing(emptyList(), (1..5).map { todayTask("Task $it", 8 + it) }), date, showTitles = true)

        assertThat(plan.rows).hasSize(5)
        assertThat(plan.moreCount).isEqualTo(0)
    }

    @Test
    fun `with titles hidden there are no lines and nothing left over, only the counts`() {
        val briefing = Briefing(overdue = listOf(overdueTask("Old")), today = listOf(todayTask("Secret plan", 9)))

        val plan = WidgetPlanner.plan(briefing, date, showTitles = false)

        assertThat(plan.rows).isEmpty()
        assertThat(plan.moreCount).isEqualTo(0)
        assertThat(plan.overdue).isEqualTo(1)
        assertThat(plan.dueToday).isEqualTo(1)
        assertThat(plan.showTitles).isFalse()
        assertThat(plan.isEmpty).isFalse()
    }

    @Test
    fun `a day with nothing due is empty`() {
        val plan = WidgetPlanner.plan(Briefing(emptyList(), emptyList()), date, showTitles = true)

        assertThat(plan.isEmpty).isTrue()
        assertThat(plan.rows).isEmpty()
    }

    @Test
    fun `suggestions and clashes in the briefing do not add lines`() {
        val plan = WidgetPlanner.plan(Briefing(emptyList(), emptyList(), suggestionsWaiting = 4), date, showTitles = true)

        assertThat(plan.isEmpty).isTrue()
    }
}

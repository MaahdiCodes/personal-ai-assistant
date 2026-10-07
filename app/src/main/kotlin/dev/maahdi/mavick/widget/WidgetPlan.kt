package dev.maahdi.mavick.widget

import dev.maahdi.mavick.data.task.Briefing
import dev.maahdi.mavick.data.task.TaskEntity
import java.time.LocalDate

/** One line on the widget: [overdue] tasks come before today's. */
data class WidgetRow(val task: TaskEntity, val overdue: Boolean)

/**
 * What the home-screen widget shows (docs/PLAN.md §5.9). With [showTitles] off there are no rows,
 * only the counts, because a widget sits on the home screen, outside the app lock.
 */
data class WidgetPlan(
    val date: LocalDate,
    val dueToday: Int,
    val overdue: Int,
    val showTitles: Boolean,
    val rows: List<WidgetRow>,
    /** Tasks that didn't fit in [rows]. */
    val moreCount: Int,
) {
    val isEmpty: Boolean get() = dueToday == 0 && overdue == 0
}

object WidgetPlanner {
    /** The widget has room for this many lines. */
    const val MAX_ROWS = 5

    /** Today's and overdue tasks, overdue first, each group in time order as the briefing has them. */
    fun plan(briefing: Briefing, date: LocalDate, showTitles: Boolean): WidgetPlan {
        val all = briefing.overdue.map { WidgetRow(it, overdue = true) } + briefing.today.map { WidgetRow(it, overdue = false) }
        val rows = if (showTitles) all.take(MAX_ROWS) else emptyList()
        return WidgetPlan(
            date = date,
            dueToday = briefing.today.size,
            overdue = briefing.overdue.size,
            showTitles = showTitles,
            rows = rows,
            moreCount = if (showTitles) all.size - rows.size else 0,
        )
    }
}

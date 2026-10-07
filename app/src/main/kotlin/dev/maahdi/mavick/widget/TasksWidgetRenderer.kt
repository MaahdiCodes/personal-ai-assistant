package dev.maahdi.mavick.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import dev.maahdi.mavick.R
import dev.maahdi.mavick.reminders.BriefingText
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Turns a [WidgetPlan] into the widget's views. Plain RemoteViews: no list service, so no service at all. */
class TasksWidgetRenderer(private val context: Context) {
    /** [plan] null means the tasks could not be read (the phone is still locked after a restart, say). */
    fun render(plan: WidgetPlan?, use24Hour: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_tasks)
        views.setOnClickPendingIntent(R.id.widget_title_block, WidgetIntents.pending(context, WidgetIntents.openApp(context)))
        views.setOnClickPendingIntent(R.id.widget_add, WidgetIntents.pending(context, WidgetIntents.newTask(context)))
        ROW_IDS.forEach { views.setViewVisibility(it, GONE) }
        views.setViewVisibility(R.id.widget_more, GONE)
        views.setViewVisibility(R.id.widget_message, GONE)

        if (plan == null) {
            views.setTextViewText(R.id.widget_date, context.getString(R.string.app_name))
            views.setTextViewText(R.id.widget_summary, "")
            showMessage(views, context.getString(R.string.widget_open_to_see))
            return views
        }

        val resources = context.resources
        views.setTextViewText(R.id.widget_date, DATE_FORMAT.format(plan.date))
        views.setTextViewText(R.id.widget_summary, BriefingText.dueParts(resources, plan.dueToday, plan.overdue).joinToString(" · "))
        if (plan.isEmpty) {
            showMessage(views, context.getString(R.string.widget_nothing_due))
            return views
        }
        plan.rows.forEachIndexed { index, row ->
            val id = ROW_IDS[index]
            views.setTextViewText(id, BriefingText.taskLine(resources, row.task, row.overdue, use24Hour))
            views.setTextColor(id, ContextCompat.getColor(context, if (row.overdue) R.color.widget_overdue else R.color.widget_text))
            views.setOnClickPendingIntent(id, WidgetIntents.pending(context, WidgetIntents.openTask(context, row.task.id)))
            views.setViewVisibility(id, VISIBLE)
        }
        if (plan.moreCount > 0) {
            views.setTextViewText(R.id.widget_more, resources.getQuantityString(R.plurals.widget_more, plan.moreCount, plan.moreCount))
            views.setViewVisibility(R.id.widget_more, VISIBLE)
        }
        if (!plan.showTitles) showMessage(views, context.getString(R.string.widget_titles_hidden))
        return views
    }

    private fun showMessage(views: RemoteViews, text: String) {
        views.setTextViewText(R.id.widget_message, text)
        views.setViewVisibility(R.id.widget_message, VISIBLE)
    }

    private companion object {
        const val VISIBLE = android.view.View.VISIBLE
        const val GONE = android.view.View.GONE
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
        val ROW_IDS = intArrayOf(R.id.widget_row_0, R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3, R.id.widget_row_4)
    }
}

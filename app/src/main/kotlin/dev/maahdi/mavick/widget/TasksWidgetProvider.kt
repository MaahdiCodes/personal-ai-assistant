package dev.maahdi.mavick.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import dev.maahdi.mavick.MavickApp
import dev.maahdi.mavick.reminders.runInBackground

/** The widget's receiver. Android calls it when the widget is added, resized or after a restart. */
class TasksWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val updater = (context.applicationContext as MavickApp).container.widgets
        runInBackground { updater.update() }
    }
}

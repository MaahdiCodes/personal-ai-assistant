package dev.maahdi.mavick.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.maahdi.mavick.MainActivity
import dev.maahdi.mavick.reminders.ReminderIntents

/** What tapping the widget or the Quick Settings tile does. Everything opens Mavick, behind its app lock. */
object WidgetIntents {
    private const val FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

    fun openApp(context: Context): Intent = Intent(context, MainActivity::class.java).addFlags(FLAGS)

    fun openTask(context: Context, taskId: String): Intent = openApp(context)
        .setAction(ReminderIntents.ACTION_OPEN_TASK)
        .setData(ReminderIntents.taskUri(taskId))

    fun newTask(context: Context): Intent = openApp(context).setAction(ReminderIntents.ACTION_NEW_TASK)

    /** A tap target for [intent]. Intents with different data (one per task) stay separate from each other. */
    fun pending(context: Context, intent: Intent): PendingIntent =
        PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

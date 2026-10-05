package dev.maahdi.mavick.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.text.format.DateFormat
import dev.maahdi.mavick.MainActivity
import dev.maahdi.mavick.R
import dev.maahdi.mavick.data.task.Briefing
import dev.maahdi.mavick.data.task.TaskEntity
import dev.maahdi.mavick.time.DueFormatter
import java.time.Clock
import java.time.LocalDate

/**
 * Android notifications for reminders and the morning briefing.
 *
 * Privacy: on the lock screen only "Mavick reminder" shows; task titles appear once the phone is
 * unlocked. The buttons also need the phone unlocked before they act.
 */
class SystemNotifier(
    private val context: Context,
    private val clock: () -> Clock,
) : Notifier {
    private val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_REMINDERS,
                    context.getString(R.string.channel_reminders),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = context.getString(R.string.channel_reminders_description) },
                NotificationChannel(
                    CHANNEL_BRIEFING,
                    context.getString(R.string.channel_briefing),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.channel_briefing_description) },
            ),
        )
    }

    override fun showReminder(task: TaskEntity, missed: Boolean) {
        createChannels()
        val due = DueFormatter.dueLabel(task.dueDate, task.dueTime, LocalDate.now(clock()), DateFormat.is24HourFormat(context))
        val text = when {
            missed && due.isNotEmpty() -> context.getString(R.string.reminder_missed_due, due)
            missed -> context.getString(R.string.reminder_missed)
            due.isNotEmpty() -> due
            else -> task.notes.orEmpty()
        }
        val lockScreenVersion = Notification.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_public_title))
            .build()
        val notification = Notification.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreenVersion)
            .setAutoCancel(true)
            .setContentIntent(openTaskIntent(task.id))
            .addAction(actionButton(task.id, ReminderAction.DONE, R.string.action_done))
            .addAction(actionButton(task.id, ReminderAction.SNOOZE, R.string.action_snooze))
            .addAction(actionButton(task.id, ReminderAction.TOMORROW, R.string.action_tomorrow))
            .build()
        manager.notify(task.id, REMINDER_NOTIFICATION_ID, notification)
    }

    override fun cancelReminder(taskId: String) = manager.cancel(taskId, REMINDER_NOTIFICATION_ID)

    override fun showBriefing(briefing: Briefing) {
        createChannels()
        val resources = context.resources
        val summary = listOfNotNull(
            briefing.today.size.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.briefing_due_today, it, it) },
            briefing.overdue.size.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.briefing_overdue, it, it) },
        ).joinToString(" · ")
        val use24Hour = DateFormat.is24HourFormat(context)
        val lines = briefing.overdue.map { context.getString(R.string.briefing_overdue_line, it.title) } +
            briefing.today.map { task -> task.dueTime?.let { "${DueFormatter.time(it, use24Hour)}  ${task.title}" } ?: task.title }
        val style = Notification.InboxStyle().setSummaryText(summary)
        lines.take(MAX_BRIEFING_LINES).forEach { style.addLine(it) }

        val lockScreenVersion = Notification.Builder(context, CHANNEL_BRIEFING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.briefing_public_title))
            .build()
        val notification = Notification.Builder(context, CHANNEL_BRIEFING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.briefing_title))
            .setContentText(summary)
            .setStyle(style)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreenVersion)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        manager.notify(BRIEFING_TAG, BRIEFING_NOTIFICATION_ID, notification)
    }

    private fun actionButton(taskId: String, action: ReminderAction, label: Int): Notification.Action {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(action.intentAction)
            .setData(ReminderIntents.taskUri(taskId))
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_notification), context.getString(label), pendingIntent)
            .setAuthenticationRequired(true)
            .build()
    }

    private fun openTaskIntent(taskId: String): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setAction(ReminderIntents.ACTION_OPEN_TASK)
            .setData(ReminderIntents.taskUri(taskId))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_BRIEFING = "briefing"
        const val REMINDER_NOTIFICATION_ID = 1
        const val BRIEFING_NOTIFICATION_ID = 2
        const val BRIEFING_TAG = "briefing"
        private const val MAX_BRIEFING_LINES = 6
    }
}

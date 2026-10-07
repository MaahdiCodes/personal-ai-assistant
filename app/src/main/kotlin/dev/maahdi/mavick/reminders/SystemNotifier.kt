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
import dev.maahdi.mavick.calendar.clashEventsLabel
import dev.maahdi.mavick.capture.ReadingState
import dev.maahdi.mavick.data.task.Briefing
import dev.maahdi.mavick.data.task.TaskEntity
import dev.maahdi.mavick.time.DueFormatter
import java.time.Clock
import java.time.LocalDate

/**
 * Android notifications for reminders, the morning briefing, reading warnings and suggested tasks.
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
                NotificationChannel(
                    CHANNEL_HEALTH,
                    context.getString(R.string.channel_health),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.channel_health_description) },
                // Quiet: no sound or pop-up. Suggestions can wait until you look.
                NotificationChannel(
                    CHANNEL_SUGGESTIONS,
                    context.getString(R.string.channel_suggestions),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = context.getString(R.string.channel_suggestions_description) },
            ),
        )
    }

    /**
     * Suggested tasks are waiting: one quiet notification, replaced as more arrive. [titles] are
     * the newest; on the lock screen only "Mavick suggestions" shows. Tapping opens the list.
     */
    fun showSuggestions(count: Int, titles: List<String>) {
        if (count <= 0) {
            cancelSuggestions()
            return
        }
        createChannels()
        val summary = context.resources.getQuantityString(R.plurals.suggestions_waiting, count, count)
        val style = Notification.InboxStyle().setSummaryText(summary)
        titles.take(MAX_SUGGESTION_LINES).forEach { style.addLine(it) }
        val lockScreenVersion = Notification.Builder(context, CHANNEL_SUGGESTIONS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.suggestions_public_title))
            .build()
        val notification = Notification.Builder(context, CHANNEL_SUGGESTIONS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(summary)
            .setContentText(titles.firstOrNull().orEmpty())
            .setStyle(style)
            .setNumber(count)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreenVersion)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java)
                        .setAction(ReminderIntents.ACTION_OPEN_SUGGESTIONS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
        manager.notify(SUGGESTIONS_TAG, SUGGESTIONS_NOTIFICATION_ID, notification)
    }

    fun cancelSuggestions() = manager.cancel(SUGGESTIONS_TAG, SUGGESTIONS_NOTIFICATION_ID)

    /** Message reading stopped or went quiet. Tapping opens Settings, where Health is. */
    fun showReadingWarning(state: ReadingState) {
        createChannels()
        val text = when (state) {
            ReadingState.QUIET -> R.string.reading_warning_quiet
            else -> R.string.reading_warning_stopped
        }
        val notification = Notification.Builder(context, CHANNEL_HEALTH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reading_warning_title))
            .setContentText(context.getString(text))
            .setStyle(Notification.BigTextStyle().bigText(context.getString(text)))
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java)
                        .setAction(ReminderIntents.ACTION_OPEN_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
        manager.notify(HEALTH_TAG, HEALTH_NOTIFICATION_ID, notification)
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
        val summary = (
            BriefingText.dueParts(resources, briefing.today.size, briefing.overdue.size) +
                listOfNotNull(
                    briefing.suggestionsWaiting.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.suggestions_waiting, it, it) },
                    briefing.clashes.size.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.briefing_clashes, it, it) },
                )
            ).joinToString(" · ")
        val use24Hour = DateFormat.is24HourFormat(context)
        // Clashes first: they are the ones to act on.
        val clashLines = briefing.clashes.map { clash ->
            val time = clash.task.dueTime?.let { DueFormatter.time(it, use24Hour) }.orEmpty()
            context.getString(R.string.briefing_clash_line, time, clash.task.title, clashEventsLabel(resources, clash.events))
        }
        val lines = clashLines +
            briefing.overdue.map { BriefingText.taskLine(resources, it, overdue = true, use24Hour = use24Hour) } +
            briefing.today.map { BriefingText.taskLine(resources, it, overdue = false, use24Hour = use24Hour) }
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
        const val CHANNEL_HEALTH = "health"
        const val CHANNEL_SUGGESTIONS = "suggestions"
        const val REMINDER_NOTIFICATION_ID = 1
        const val BRIEFING_NOTIFICATION_ID = 2
        const val HEALTH_NOTIFICATION_ID = 3
        const val SUGGESTIONS_NOTIFICATION_ID = 4
        const val BRIEFING_TAG = "briefing"
        const val HEALTH_TAG = "health"
        const val SUGGESTIONS_TAG = "suggestions"
        private const val MAX_BRIEFING_LINES = 6
        private const val MAX_SUGGESTION_LINES = 5
    }
}

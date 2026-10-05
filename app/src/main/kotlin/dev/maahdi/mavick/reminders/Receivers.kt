package dev.maahdi.mavick.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.maahdi.mavick.MavickApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A reminder or briefing alarm went off. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val engine = engineOf(context)
        when (intent.action) {
            ReminderIntents.ACTION_REMINDER -> {
                val taskId = ReminderIntents.taskIdOf(intent) ?: return
                runInBackground { engine.onReminderAlarm(taskId) }
            }
            ReminderIntents.ACTION_BRIEFING -> runInBackground { engine.onBriefingAlarm() }
        }
    }
}

/** Done / Snooze / Tomorrow was tapped on a reminder. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = ReminderIntents.taskIdOf(intent) ?: return
        val action = ReminderAction.fromIntentAction(intent.action) ?: return
        val engine = engineOf(context)
        runInBackground { engine.onNotificationAction(taskId, action) }
    }
}

/** The phone restarted, its clock or time zone changed, or Mavick was updated: set alarms again. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val engine = engineOf(context)
        runInBackground { engine.resync() }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}

private const val TAG = "MavickReminders"
private const val WORK_TIMEOUT_MS = 8_000L
private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun engineOf(context: Context): ReminderEngine = (context.applicationContext as MavickApp).container.reminderEngine

/**
 * Does the receiver's work off the main thread while keeping the broadcast alive until it is
 * finished (Android allows about 10 seconds). Failures are logged without any task content.
 */
private fun BroadcastReceiver.runInBackground(work: suspend () -> Unit) {
    val pendingResult = goAsync()
    receiverScope.launch {
        try {
            if (withTimeoutOrNull(WORK_TIMEOUT_MS) { work() } == null) Log.w(TAG, "Reminder work timed out")
        } catch (e: Exception) {
            Log.w(TAG, "Reminder work failed: ${e.javaClass.simpleName}")
        } catch (e: LinkageError) {
            // The encryption library failed to load; nothing more can be done from a receiver.
            Log.w(TAG, "Reminder work failed: ${e.javaClass.simpleName}")
        } finally {
            pendingResult.finish()
        }
    }
}

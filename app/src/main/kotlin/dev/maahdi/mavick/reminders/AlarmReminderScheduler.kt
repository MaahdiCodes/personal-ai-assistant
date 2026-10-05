package dev.maahdi.mavick.reminders

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Uses Android's AlarmManager: one alarm per pending reminder plus one for the morning briefing.
 * Nothing polls; the phone wakes Mavick only at those times.
 *
 * Times are local ("09:00"), converted with the phone's current time zone when the alarm is set.
 * After a time-zone change, RescheduleReceiver sets every alarm again.
 */
class AlarmReminderScheduler(
    private val context: Context,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ReminderScheduler {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(taskId: String, at: LocalDateTime) = setAlarm(at, reminderIntent(taskId))

    override fun cancel(taskId: String) = alarmManager.cancel(reminderIntent(taskId))

    override fun scheduleBriefing(at: LocalDateTime) = setAlarm(at, briefingIntent())

    override fun cancelBriefing() = alarmManager.cancel(briefingIntent())

    // Lint looks only for SCHEDULE_EXACT_ALARM. Mavick declares USE_EXACT_ALARM, the reminder-app
    // equivalent on Android 13+, and checks canScheduleExactAlarms() before every exact alarm.
    @SuppressLint("MissingPermission")
    private fun setAlarm(at: LocalDateTime, operation: PendingIntent) {
        val triggerAtMillis = at.atZone(zone()).toInstant().toEpochMilli()
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        } else {
            // Exact alarms are granted to reminder apps (USE_EXACT_ALARM); this is only a safety net.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }

    private fun reminderIntent(taskId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(ReminderIntents.ACTION_REMINDER)
            .setData(ReminderIntents.taskUri(taskId)),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun briefingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderAlarmReceiver::class.java).setAction(ReminderIntents.ACTION_BRIEFING),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

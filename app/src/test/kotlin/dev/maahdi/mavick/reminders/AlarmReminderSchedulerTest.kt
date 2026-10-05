package dev.maahdi.mavick.reminders

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.testing.TEST_ZONE
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(AndroidJUnit4::class)
class AlarmReminderSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val scheduler = AlarmReminderScheduler(context, zone = { TEST_ZONE })

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    private fun alarms() = shadowOf(alarmManager).scheduledAlarms

    // Robolectric offers no getter for an alarm's PendingIntent, only this deprecated field.
    @Suppress("DEPRECATION")
    private fun taskIdOf(alarm: ShadowAlarmManager.ScheduledAlarm): String? =
        ReminderIntents.taskIdOf(shadowOf(alarm.operation).savedIntent)

    @Test
    fun `a reminder becomes one wake-up alarm at its local time, allowed while the phone sleeps`() {
        scheduler.schedule("task-1", LocalDateTime.of(2026, 10, 5, 17, 0))

        val alarm = alarms().single()
        assertThat(alarm.getType()).isEqualTo(AlarmManager.RTC_WAKEUP)
        assertThat(alarm.isAllowWhileIdle()).isTrue()
        // 17:00 in Dhaka (UTC+6) is 11:00 UTC.
        assertThat(alarm.getTriggerAtMs()).isEqualTo(Instant.parse("2026-10-05T11:00:00Z").toEpochMilli())
        assertThat(taskIdOf(alarm)).isEqualTo("task-1")
    }

    @Test
    fun `setting a task's reminder again replaces its alarm`() {
        scheduler.schedule("task-1", LocalDateTime.of(2026, 10, 5, 17, 0))
        scheduler.schedule("task-1", LocalDateTime.of(2026, 10, 5, 18, 0))

        val alarm = alarms().single()
        assertThat(alarm.getTriggerAtMs()).isEqualTo(Instant.parse("2026-10-05T12:00:00Z").toEpochMilli())
    }

    @Test
    fun `each task has its own alarm`() {
        scheduler.schedule("task-1", LocalDateTime.of(2026, 10, 5, 17, 0))
        scheduler.schedule("task-2", LocalDateTime.of(2026, 10, 5, 17, 0))

        assertThat(alarms().map(::taskIdOf)).containsExactly("task-1", "task-2")
    }

    @Test
    fun `cancelling removes only that task's alarm`() {
        scheduler.schedule("task-1", LocalDateTime.of(2026, 10, 5, 17, 0))
        scheduler.schedule("task-2", LocalDateTime.of(2026, 10, 5, 18, 0))

        scheduler.cancel("task-1")

        assertThat(alarms().map(::taskIdOf)).containsExactly("task-2")
    }

    @Test
    fun `the daily alarm is separate from task alarms, and setting it again replaces it`() {
        scheduler.schedule("task-1", LocalDateTime.of(2026, 10, 5, 17, 0))
        scheduler.scheduleDaily(LocalDateTime.of(2026, 10, 6, 8, 0))
        scheduler.scheduleDaily(LocalDateTime.of(2026, 10, 7, 8, 0))

        scheduler.cancel("task-1")

        val daily = alarms().single()
        assertThat(taskIdOf(daily)).isNull()
        // 08:00 in Dhaka is 02:00 UTC.
        assertThat(daily.getTriggerAtMs()).isEqualTo(Instant.parse("2026-10-07T02:00:00Z").toEpochMilli())
    }

    @Test
    fun `the daily alarm keeps the action of the old briefing alarm, so an update replaces it`() {
        scheduler.scheduleDaily(LocalDateTime.of(2026, 10, 6, 8, 0))

        @Suppress("DEPRECATION")
        val intent = shadowOf(alarms().single().operation).savedIntent
        assertThat(intent.action).isEqualTo("dev.maahdi.mavick.action.BRIEFING")
    }

    @Test
    fun `a time skipped by a daylight-saving change moves to just after it`() {
        val newYork = AlarmReminderScheduler(context, zone = { ZoneId.of("America/New_York") })

        // Clocks jump from 02:00 to 03:00 on 8 March 2026, so 02:30 doesn't exist: it becomes 03:30 EDT.
        newYork.schedule("task-1", LocalDateTime.of(2026, 3, 8, 2, 30))

        assertThat(alarms().single().getTriggerAtMs()).isEqualTo(Instant.parse("2026-03-08T07:30:00Z").toEpochMilli())
    }
}

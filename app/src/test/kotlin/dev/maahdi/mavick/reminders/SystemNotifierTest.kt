package dev.maahdi.mavick.reminders

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.capture.ReadingState
import dev.maahdi.mavick.data.task.Briefing
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.MutableClock
import dev.maahdi.mavick.testing.task
import java.time.LocalTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class SystemNotifierTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notifier = SystemNotifier(context, clock = { MutableClock(MONDAY_10AM) })
    private val today = MONDAY_10AM.toLocalDate()

    private fun posted(tag: String, id: Int): Notification? = shadowOf(manager).getNotification(tag, id)

    private fun Notification.text(key: String) = extras.getCharSequence(key)?.toString()

    @Test
    fun `a reminder shows the task with Done, Snooze and Tomorrow`() {
        val task = task(title = "Call the bank", dueDate = today, dueTime = LocalTime.of(17, 0))

        notifier.showReminder(task, missed = false)

        val notification = posted(task.id, SystemNotifier.REMINDER_NOTIFICATION_ID)!!
        assertThat(notification.channelId).isEqualTo(SystemNotifier.CHANNEL_REMINDERS)
        assertThat(notification.text(Notification.EXTRA_TITLE)).isEqualTo("Call the bank")
        assertThat(notification.text(Notification.EXTRA_TEXT)).startsWith("Today · ")
        assertThat(notification.actions.map { it.title.toString() }).containsExactly("Done", "Snooze 10 min", "Tomorrow").inOrder()
    }

    @Test
    fun `the lock screen shows only that there is a reminder`() {
        val task = task(title = "Private appointment", dueDate = today)

        notifier.showReminder(task, missed = false)

        val notification = posted(task.id, SystemNotifier.REMINDER_NOTIFICATION_ID)!!
        assertThat(notification.visibility).isEqualTo(Notification.VISIBILITY_PRIVATE)
        assertThat(notification.publicVersion.text(Notification.EXTRA_TITLE)).isEqualTo("Mavick reminder")
    }

    @Test
    fun `a missed reminder says so`() {
        val task = task(title = "Call", dueDate = today, dueTime = LocalTime.of(9, 0))

        notifier.showReminder(task, missed = true)

        assertThat(posted(task.id, SystemNotifier.REMINDER_NOTIFICATION_ID)!!.text(Notification.EXTRA_TEXT)).startsWith("Missed reminder · Today")
    }

    @Test
    fun `cancelling removes the reminder`() {
        val task = task(title = "Call", dueDate = today)
        notifier.showReminder(task, missed = false)

        notifier.cancelReminder(task.id)

        assertThat(posted(task.id, SystemNotifier.REMINDER_NOTIFICATION_ID)).isNull()
    }

    @Test
    fun `the briefing counts today's and overdue tasks`() {
        val briefing = Briefing(
            overdue = listOf(task(title = "Old 1"), task(title = "Old 2")),
            today = listOf(task(title = "Pay rent")),
        )

        notifier.showBriefing(briefing)

        val notification = posted(SystemNotifier.BRIEFING_TAG, SystemNotifier.BRIEFING_NOTIFICATION_ID)!!
        assertThat(notification.channelId).isEqualTo(SystemNotifier.CHANNEL_BRIEFING)
        assertThat(notification.text(Notification.EXTRA_TEXT)).isEqualTo("1 task due today · 2 overdue")
        assertThat(notification.publicVersion.text(Notification.EXTRA_TITLE)).isEqualTo("Mavick: your day")
    }

    @Test
    fun `reminders use a high-importance channel so they pop up`() {
        notifier.createChannels()

        assertThat(manager.getNotificationChannel(SystemNotifier.CHANNEL_REMINDERS).importance).isEqualTo(NotificationManager.IMPORTANCE_HIGH)
    }

    @Test
    fun `a reading warning says what is wrong and opens Settings, with no message content`() {
        notifier.showReadingWarning(ReadingState.NOT_CONNECTED)

        val notification = posted(SystemNotifier.HEALTH_TAG, SystemNotifier.HEALTH_NOTIFICATION_ID)!!
        assertThat(notification.channelId).isEqualTo(SystemNotifier.CHANNEL_HEALTH)
        assertThat(notification.text(Notification.EXTRA_TITLE)).isEqualTo("Mavick isn't reading messages")
        assertThat(notification.text(Notification.EXTRA_TEXT)).contains("Open Mavick to restart it")
        assertThat(shadowOf(notification.contentIntent).savedIntent.action).isEqualTo(ReminderIntents.ACTION_OPEN_SETTINGS)
    }

    @Test
    fun `a quiet warning replaces an earlier warning instead of piling up`() {
        notifier.showReadingWarning(ReadingState.NOT_CONNECTED)
        notifier.showReadingWarning(ReadingState.QUIET)

        assertThat(shadowOf(manager).allNotifications).hasSize(1)
        assertThat(posted(SystemNotifier.HEALTH_TAG, SystemNotifier.HEALTH_NOTIFICATION_ID)!!.text(Notification.EXTRA_TEXT))
            .contains("No WhatsApp, Messenger or Gmail message")
    }
}

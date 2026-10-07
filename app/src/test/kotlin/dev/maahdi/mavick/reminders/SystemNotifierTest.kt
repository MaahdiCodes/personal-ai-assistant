package dev.maahdi.mavick.reminders

import java.time.Instant
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.capture.ReadingState
import dev.maahdi.mavick.calendar.CalendarOccurrence
import dev.maahdi.mavick.calendar.Clash
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
    fun `the briefing mentions suggested tasks waiting`() {
        notifier.showBriefing(Briefing(overdue = emptyList(), today = listOf(task(title = "Pay rent")), suggestionsWaiting = 2))

        val notification = posted(SystemNotifier.BRIEFING_TAG, SystemNotifier.BRIEFING_NOTIFICATION_ID)!!
        assertThat(notification.text(Notification.EXTRA_TEXT)).isEqualTo("1 task due today · 2 suggested tasks to review")
    }

    private fun occurrence(title: String) = CalendarOccurrence(
        eventId = 1,
        calendarId = 1,
        title = title,
        start = Instant.parse("2026-10-05T11:00:00Z"),
        end = Instant.parse("2026-10-05T12:00:00Z"),
        allDay = false,
        busy = true,
    )

    @Test
    fun `the briefing counts clashes and lists them before everything else`() {
        val bank = task(title = "Call the bank", dueDate = today, dueTime = LocalTime.of(17, 0))
        val briefing = Briefing(
            overdue = listOf(task(title = "Old")),
            today = listOf(bank, task(title = "Pay rent", dueDate = today)),
            clashes = listOf(Clash(bank, listOf(occurrence("Dentist")))),
        )

        notifier.showBriefing(briefing)

        val notification = posted(SystemNotifier.BRIEFING_TAG, SystemNotifier.BRIEFING_NOTIFICATION_ID)!!
        assertThat(notification.text(Notification.EXTRA_TEXT)).isEqualTo("2 tasks due today · 1 overdue · 1 clash with your calendar")
        val lines = notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)!!.map { it.toString() }
        assertThat(lines.first()).startsWith("Clash: ")
        assertThat(lines.first()).endsWith("Call the bank, with Dentist")
        assertThat(lines).hasSize(4)
    }

    @Test
    fun `a clash with several or untitled events says so briefly`() {
        val bank = task(title = "Call the bank", dueDate = today, dueTime = LocalTime.of(17, 0))
        val rent = task(title = "Pay rent", dueDate = today, dueTime = LocalTime.of(18, 0))
        val briefing = Briefing(
            overdue = emptyList(),
            today = listOf(bank, rent),
            clashes = listOf(
                Clash(bank, listOf(occurrence("Dentist"), occurrence("Standup"), occurrence("Lunch"))),
                Clash(rent, listOf(occurrence(""))),
            ),
        )

        notifier.showBriefing(briefing)

        val notification = posted(SystemNotifier.BRIEFING_TAG, SystemNotifier.BRIEFING_NOTIFICATION_ID)!!
        assertThat(notification.text(Notification.EXTRA_TEXT)).contains("2 clashes with your calendar")
        val lines = notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)!!.map { it.toString() }
        assertThat(lines[0]).endsWith("Call the bank, with Dentist and 2 more")
        assertThat(lines[1]).endsWith("Pay rent, with an event")
    }

    @Test
    fun `the lock screen version of the briefing says nothing about clashes`() {
        val bank = task(title = "Call the bank", dueDate = today, dueTime = LocalTime.of(17, 0))

        notifier.showBriefing(Briefing(emptyList(), listOf(bank), clashes = listOf(Clash(bank, listOf(occurrence("Dentist"))))))

        val publicVersion = posted(SystemNotifier.BRIEFING_TAG, SystemNotifier.BRIEFING_NOTIFICATION_ID)!!.publicVersion
        assertThat(publicVersion.text(Notification.EXTRA_TITLE)).isEqualTo("Mavick: your day")
        assertThat(publicVersion.text(Notification.EXTRA_TEXT)).isNull()
    }

    @Test
    fun `waiting suggestions get one quiet notification, private on the lock screen, that opens them`() {
        notifier.showSuggestions(3, listOf("Pay the rent", "Bring the cake"))

        val notification = posted(SystemNotifier.SUGGESTIONS_TAG, SystemNotifier.SUGGESTIONS_NOTIFICATION_ID)!!
        assertThat(notification.channelId).isEqualTo(SystemNotifier.CHANNEL_SUGGESTIONS)
        assertThat(manager.getNotificationChannel(SystemNotifier.CHANNEL_SUGGESTIONS).importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
        assertThat(notification.text(Notification.EXTRA_TITLE)).isEqualTo("3 suggested tasks to review")
        assertThat(notification.text(Notification.EXTRA_TEXT)).isEqualTo("Pay the rent")
        assertThat(notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)!!.map { it.toString() })
            .containsExactly("Pay the rent", "Bring the cake").inOrder()
        assertThat(notification.visibility).isEqualTo(Notification.VISIBILITY_PRIVATE)
        assertThat(notification.publicVersion.text(Notification.EXTRA_TITLE)).isEqualTo("Mavick suggestions")
        assertThat(shadowOf(notification.contentIntent).savedIntent.action).isEqualTo(ReminderIntents.ACTION_OPEN_SUGGESTIONS)
    }

    @Test
    fun `more suggestions replace the notification, and none left removes it`() {
        notifier.showSuggestions(1, listOf("Pay the rent"))
        notifier.showSuggestions(2, listOf("Bring the cake", "Pay the rent"))
        assertThat(shadowOf(manager).allNotifications).hasSize(1)

        notifier.showSuggestions(0, emptyList())

        assertThat(posted(SystemNotifier.SUGGESTIONS_TAG, SystemNotifier.SUGGESTIONS_NOTIFICATION_ID)).isNull()
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

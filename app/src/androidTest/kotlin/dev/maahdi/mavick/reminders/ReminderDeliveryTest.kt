package dev.maahdi.mavick.reminders

import android.Manifest
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.MavickApp
import dev.maahdi.mavick.data.task.TaskDraft
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on the phone, end to end: a real exact alarm wakes Mavick, which shows a real notification.
 * This is the path every reminder takes (alarm -> receiver -> encrypted database -> notification).
 */
@RunWith(AndroidJUnit4::class)
class ReminderDeliveryTest {
    @get:Rule
    val notificationPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val app = ApplicationProvider.getApplicationContext<MavickApp>()
    private val notifications = app.getSystemService(NotificationManager::class.java)
    private var taskId: String? = null

    @After
    fun cleanUp() {
        taskId?.let { id ->
            runBlocking { app.container.tasks.delete(id) }
            notifications.cancel(id, SystemNotifier.REMINDER_NOTIFICATION_ID)
        }
    }

    @Test
    fun aReminderAlarmShowsItsNotification() {
        val remindAt = LocalDateTime.now().plusSeconds(5).withNano(0)
        val task = runBlocking {
            app.container.tasks.create(
                TaskDraft(
                    title = "Mavick reminder test",
                    dueDate = remindAt.toLocalDate(),
                    dueTime = remindAt.toLocalTime(),
                    reminderTime = remindAt.toLocalTime(),
                ),
            )
        }
        taskId = task.id

        val shown = waitUntil(timeoutMillis = 30_000) {
            notifications.activeNotifications.any { it.tag == task.id }
        }

        assertThat(shown).isTrue()
    }

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(250)
        }
        return condition()
    }
}

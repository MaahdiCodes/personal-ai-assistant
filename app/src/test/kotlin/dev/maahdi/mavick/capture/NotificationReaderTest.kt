package dev.maahdi.mavick.capture

import android.app.Notification
import android.app.Person
import android.content.Context
import android.os.Process
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reads real Android notifications, built with the same APIs WhatsApp, Messenger and Gmail use,
 * on Robolectric's copy of Android 16's own notification code.
 */
@RunWith(AndroidJUnit4::class)
class NotificationReaderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val me = Person.Builder().setName("Me").setKey("me-key").build()
    private val sam = Person.Builder().setName("Sam").setKey("sam-key").build()
    private val longText = "x".repeat(3000)

    private fun posted(
        notification: Notification,
        packageName: String = SourceApp.WHATSAPP.packageName,
        user: UserHandle = Process.myUserHandle(),
    ) = StatusBarNotification(packageName, packageName, 7, "tag", 10_123, 0, 0, notification, user, POST_TIME)

    private fun chat(style: Notification.MessagingStyle, shortcutId: String? = "8801700000000@s.whatsapp.net") =
        Notification.Builder(context, "chats")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setStyle(style)
            .apply { if (shortcutId != null) setShortcutId(shortcutId) }
            .build()

    @Test
    fun `a chat notification gives each message with its sender, time and the chat's shortcut`() {
        val style = Notification.MessagingStyle(me)
            .addMessage(Notification.MessagingStyle.Message("Call me today at 12", 1_000L, sam))
            .addMessage(Notification.MessagingStyle.Message("Sure", 2_000L, null as Person?))

        val raw = NotificationReader.read(posted(chat(style)))

        assertThat(raw.packageName).isEqualTo("com.whatsapp")
        assertThat(raw.userId).isEqualTo(0)
        assertThat(raw.postedAt).isEqualTo(Instant.ofEpochMilli(POST_TIME))
        assertThat(raw.shortcutId).isEqualTo("8801700000000@s.whatsapp.net")
        assertThat(raw.selfName).isEqualTo("Me")
        assertThat(raw.selfKey).isEqualTo("me-key")
        assertThat(raw.messages).containsExactly(
            RawMessage("Call me today at 12", Instant.ofEpochMilli(1_000L), "Sam", "sam-key", hasSender = true),
            // Your own reply: Android stores it without a sender.
            RawMessage("Sure", Instant.ofEpochMilli(2_000L), null, null, hasSender = false),
        ).inOrder()
    }

    @Test
    fun `a group chat gives its name and says it is a group`() {
        val style = Notification.MessagingStyle(me)
            .setConversationTitle("Family")
            .setGroupConversation(true)
            .addMessage(Notification.MessagingStyle.Message("Dinner at 8?", 1_000L, sam))

        val raw = NotificationReader.read(posted(chat(style)))

        assertThat(raw.conversationTitle).isEqualTo("Family")
        assertThat(raw.isGroupConversation).isTrue()
    }

    @Test
    fun `a long message from an app built with AndroidX arrives whole, though the screen shows only part`() {
        val notification = NotificationCompat.Builder(context, "chats")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentText(longText)
            .setStyle(
                NotificationCompat.MessagingStyle(androidx.core.app.Person.Builder().setName("Me").build())
                    .addMessage(longText, 1_000L, androidx.core.app.Person.Builder().setName("Sam").build()),
            )
            .build()

        val raw = NotificationReader.read(posted(notification))

        assertThat(raw.text).hasLength(MessageParser.ANDROID_TEXT_LIMIT)
        assertThat(raw.messages.single().text).hasLength(3000)
    }

    @Test
    fun `a long message from an app built with Android's own builder is cut at 1,024 characters`() {
        val style = Notification.MessagingStyle(me).addMessage(Notification.MessagingStyle.Message(longText, 1_000L, sam))

        val raw = NotificationReader.read(posted(chat(style)))

        assertThat(raw.messages.single().text).hasLength(MessageParser.ANDROID_TEXT_LIMIT)
    }

    @Test
    fun `an email notification gives its sender, subject, preview and receiving address`() {
        val notification = Notification.Builder(context, "mail")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Rahim")
            .setContentText("Invoice")
            .setSubText("you@gmail.com")
            .setWhen(5_000L)
            .setStyle(Notification.BigTextStyle().bigText("Invoice\nPlease pay by Thursday"))
            .build()

        val raw = NotificationReader.read(posted(notification, packageName = SourceApp.GMAIL.packageName))

        assertThat(raw.title).isEqualTo("Rahim")
        assertThat(raw.text).isEqualTo("Invoice")
        assertThat(raw.subText).isEqualTo("you@gmail.com")
        assertThat(raw.bigText).isEqualTo("Invoice\nPlease pay by Thursday")
        assertThat(raw.shownTime).isEqualTo(Instant.ofEpochMilli(5_000L))
        assertThat(raw.messages).isEmpty()
    }

    @Test
    fun `a list-style notification gives its lines`() {
        val notification = Notification.Builder(context, "mail")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setStyle(Notification.InboxStyle().addLine("First").addLine("Second"))
            .build()

        assertThat(NotificationReader.read(posted(notification)).textLines).containsExactly("First", "Second").inOrder()
    }

    @Test
    fun `summaries and ongoing notifications are flagged`() {
        val summary = Notification.Builder(context, "chats")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setGroup("chats")
            .setGroupSummary(true)
            .build()
        val ongoing = Notification.Builder(context, "chats")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_CALL)
            .build()

        assertThat(NotificationReader.read(posted(summary)).isGroupSummary).isTrue()
        with(NotificationReader.read(posted(ongoing))) {
            assertThat(isOngoing).isTrue()
            assertThat(category).isEqualTo("call")
        }
    }

    @Test
    fun `a cloned app's notification carries its Android user`() {
        val clone = UserHandle.getUserHandleForUid(999 * 100_000 + 10_123)
        val style = Notification.MessagingStyle(me).addMessage(Notification.MessagingStyle.Message("Hi", 1_000L, sam))

        assertThat(NotificationReader.read(posted(chat(style), user = clone)).userId).isEqualTo(999)
    }

    @Test
    fun `a notification without a shortcut or time gives none`() {
        val notification = Notification.Builder(context, "chats")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Sam")
            .setContentText("Hi")
            .setShowWhen(false)
            .setWhen(0)
            .build()

        val raw = NotificationReader.read(posted(notification))

        assertThat(raw.shortcutId).isNull()
        assertThat(raw.shownTime).isNull()
        assertThat(raw.isGroupSummary).isFalse()
        assertThat(raw.isOngoing).isFalse()
    }

    private companion object {
        const val POST_TIME = 1_759_640_400_000L
    }
}

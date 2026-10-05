package dev.maahdi.mavick.capture

import android.app.Notification
import android.app.Person
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.os.BundleCompat
import java.time.Instant

/**
 * Copies what Mavick needs out of a notification. Read-only: it never changes, answers or opens
 * the notification.
 *
 * Each message's text is read straight from the notification's data. Android's own helper
 * (MessagingStyle.Message) would cut it to 1,024 characters again, while apps built with AndroidX
 * keep the whole message there even though the screen shows only part of it.
 */
object NotificationReader {
    // Keys of each message's bundle in Notification.EXTRA_MESSAGES (Android and AndroidX use the same).
    private const val KEY_TEXT = "text"
    private const val KEY_TIME = "time"
    private const val KEY_SENDER = "sender"
    private const val KEY_SENDER_PERSON = "sender_person"

    fun read(sbn: StatusBarNotification): RawNotification {
        val notification = sbn.notification
        val extras = notification.extras
        val self = BundleCompat.getParcelable(extras, Notification.EXTRA_MESSAGING_PERSON, Person::class.java)
        return RawNotification(
            packageName = sbn.packageName,
            userId = sbn.userNumber(),
            postedAt = Instant.ofEpochMilli(sbn.postTime),
            shownTime = notification.`when`.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
            isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            isOngoing = (notification.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE)) != 0,
            category = notification.category,
            shortcutId = notification.shortcutId,
            title = extras.text(Notification.EXTRA_TITLE),
            text = extras.text(Notification.EXTRA_TEXT),
            subText = extras.text(Notification.EXTRA_SUB_TEXT),
            bigText = extras.text(Notification.EXTRA_BIG_TEXT),
            textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().map { it.toString() },
            conversationTitle = extras.text(Notification.EXTRA_CONVERSATION_TITLE),
            isGroupConversation = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION),
            selfName = self?.name?.toString() ?: extras.legacySelfName(),
            selfKey = self?.key,
            messages = BundleCompat.getParcelableArray(extras, Notification.EXTRA_MESSAGES, Bundle::class.java)
                .orEmpty()
                .filterIsInstance<Bundle>()
                .map(::readMessage),
        )
    }

    private fun readMessage(bundle: Bundle): RawMessage {
        val person = BundleCompat.getParcelable(bundle, KEY_SENDER_PERSON, Person::class.java)
        return RawMessage(
            text = bundle.text(KEY_TEXT),
            timestamp = bundle.getLong(KEY_TIME).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
            senderName = person?.name?.toString() ?: bundle.text(KEY_SENDER),
            senderKey = person?.key,
            hasSender = person != null || bundle.containsKey(KEY_SENDER),
        )
    }

    private fun Bundle.text(key: String): String? = getCharSequence(key)?.toString()

    /** Apps written for Android 8 and older name you here instead of in EXTRA_MESSAGING_PERSON. */
    @Suppress("DEPRECATION")
    private fun Bundle.legacySelfName(): String? = text(Notification.EXTRA_SELF_DISPLAY_NAME)

    /** getUser().getIdentifier() is hidden API; getUserId() is public and gives the same number. */
    @Suppress("DEPRECATION")
    private fun StatusBarNotification.userNumber(): Int = userId
}

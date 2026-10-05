package dev.maahdi.mavick.capture

import java.time.Instant

/**
 * What Mavick reads from one notification, copied out of Android's objects (NotificationReader)
 * so that parsing and filtering are plain Kotlin and can be tested anywhere.
 */
data class RawNotification(
    val packageName: String,
    /** The Android user it was posted for: 0 is the phone's main user, clones are another user. */
    val userId: Int,
    /** When Android received the notification. */
    val postedAt: Instant,
    /** The time the app set on the notification (an email's arrival, a reminder's time), if any. */
    val shownTime: Instant? = null,
    val isGroupSummary: Boolean = false,
    /** Ongoing or foreground-service notifications: progress, calls, "checking for messages". */
    val isOngoing: Boolean = false,
    val category: String? = null,
    /** The conversation's shortcut ID; stays the same when a chat is renamed. */
    val shortcutId: String? = null,
    val title: String? = null,
    val text: String? = null,
    val subText: String? = null,
    val bigText: String? = null,
    val textLines: List<String> = emptyList(),
    val conversationTitle: String? = null,
    val isGroupConversation: Boolean = false,
    /** The phone owner's name and ID in the conversation, used to spot your own messages. */
    val selfName: String? = null,
    val selfKey: String? = null,
    /** One entry per message, for chat apps that use Android's conversation style. */
    val messages: List<RawMessage> = emptyList(),
)

data class RawMessage(
    val text: String?,
    val timestamp: Instant?,
    val senderName: String?,
    val senderKey: String? = null,
    /** False for your own messages: Android stores them without a sender. */
    val hasSender: Boolean = senderName != null,
)

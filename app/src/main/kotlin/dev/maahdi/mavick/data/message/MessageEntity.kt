package dev.maahdi.mavick.data.message

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.maahdi.mavick.capture.SourceApp
import java.time.Instant

/**
 * A message read from a notification (Phase 2). Deleted after the retention period (14 days by
 * default); a task made from it keeps its own short excerpt.
 *
 * A chat is identified by its app, account and [conversationKey] together, so two WhatsApp
 * accounts never mix.
 */
@Entity(
    tableName = "message",
    indices = [
        Index(value = ["dedupHash"], unique = true),
        Index("postedAt"),
        Index(value = ["app", "accountKey", "conversationKey"]),
    ],
)
data class MessageEntity(
    /** A UUID. */
    @PrimaryKey val id: String,
    val app: SourceApp,
    /** Which of the app's accounts received it (see capture/Accounts.kt). */
    val accountKey: String,
    /** The chat within its app and account: "s:<shortcut ID>" (survives renames) or "t:<chat name>". */
    val conversationKey: String,
    /** The chat or group name, or an email's sender; empty when the app gives none. */
    val conversationTitle: String,
    /** Who wrote it; null for your own messages and for Keep reminders. */
    val sender: String?,
    val text: String,
    /** When it was sent, as the notification says. */
    val postedAt: Instant,
    /** When Mavick saved it. */
    val receivedAt: Instant,
    val isFromMe: Boolean,
    val isGroup: Boolean,
    /** Android cut the text at its notification limit; the app itself has the rest. */
    val cutShort: Boolean,
    /** The message's fingerprint, so a message posted again is saved only once. */
    val dedupHash: String,
    val aiState: AiState = AiState.PENDING,
)

/** How far the AI (Phase 3) got with a message. Stored by name: never rename a constant. */
enum class AiState { PENDING, SKIPPED, DONE, FAILED }

/** A chat with saved messages, for picking a chat rule. */
data class ConversationRef(
    val app: SourceApp,
    val accountKey: String,
    val conversationKey: String,
    val conversationTitle: String,
    val lastAt: Instant,
)

/** Someone who wrote saved messages, for picking a person rule. */
data class SenderRef(val app: SourceApp, val name: String, val lastAt: Instant)

/** An app account with saved messages, for picking an account rule. */
data class AccountRef(val app: SourceApp, val accountKey: String)

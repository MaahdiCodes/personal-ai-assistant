package dev.maahdi.mavick.data.suggestion

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.message.MessageEntity
import dev.maahdi.mavick.time.RepeatRule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * A task Mavick found in a message (Phase 3), waiting for you to add, edit or ignore it.
 *
 * It lives exactly as long as its message: deleting the message (after the retention period, with
 * "Never read this chat", or "Delete all saved messages") deletes its suggestions too. A task made
 * from a suggestion keeps its own short excerpt.
 *
 * The message's chat and sender are copied here, so the list needs no join and a suggestion can be
 * matched against others from the same chat.
 */
@Entity(
    tableName = "suggestion",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("messageId"),
        Index("state"),
        Index(value = ["app", "accountKey", "conversationKey"]),
    ],
)
data class SuggestionEntity(
    /** A UUID. */
    @PrimaryKey val id: String,
    val messageId: String,
    val app: SourceApp,
    val accountKey: String,
    val conversationKey: String,
    /** The chat or group name, or an email's sender, when the message arrived. */
    val chatTitle: String,
    /** Who wrote the message; null for your own messages and Keep reminders. */
    val sender: String?,
    val isFromMe: Boolean,
    val isGroup: Boolean,
    /** The start of the message, for the card and the task's excerpt. */
    val excerpt: String,
    /** When the message was sent; dates in it were counted from here. */
    val messagePostedAt: Instant,
    val kind: SuggestionKind,
    val title: String,
    /** The words that said when ("Thursday 5pm"), or null. */
    val whenText: String?,
    val dueDate: LocalDate?,
    val dueTime: LocalTime?,
    val repeatRule: RepeatRule?,
    /** [whenText] couldn't be read as a date or time, so you pick one when adding. */
    val needsTime: Boolean,
    /** Who the task involves, as the AI saw it. */
    val person: String?,
    /** From 0 to 1: how sure the AI was. */
    val confidence: Double,
    val source: SuggestionSource,
    val state: SuggestionState = SuggestionState.NEW,
    val createdAt: Instant,
    /** When it was added or ignored. */
    val decidedAt: Instant? = null,
    /** The task made from it, once added. */
    val taskId: String? = null,
)

// Stored by name: never rename a constant, or saved suggestions become unreadable.

enum class SuggestionKind { TASK, EVENT, REMINDER }

/** Which engine found it: simple rules (no AI model imported) or the AI model. */
enum class SuggestionSource { RULES, MODEL }

enum class SuggestionState { NEW, ACCEPTED, IGNORED }

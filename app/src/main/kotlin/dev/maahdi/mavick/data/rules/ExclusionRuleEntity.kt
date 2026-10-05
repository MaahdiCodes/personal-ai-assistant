package dev.maahdi.mavick.data.rules

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.maahdi.mavick.capture.SourceApp
import java.time.Instant

/**
 * A rule about what Mavick reads (docs/PLAN.md §5.2). "Never read" rules drop matching messages
 * before anything is stored; "Only read" rules list the chats or people read from an app set to
 * "Only listed chats".
 */
@Entity(tableName = "exclusion_rule")
data class ExclusionRuleEntity(
    /** A UUID. */
    @PrimaryKey val id: String,
    val type: RuleType,
    val effect: RuleEffect,
    /**
     * What to match. Account: the account key. Chat: the chat's key, or its name. Person: their
     * name. Keyword: the word or phrase (whole words, any case).
     */
    val value: String,
    /** The app it applies to; null for every app. */
    val app: SourceApp?,
    /** The account it applies to; null for every account. */
    val accountKey: String?,
    /** What the rules screen shows. */
    val displayName: String,
    val createdAt: Instant,
    /** The last time the rule matched a message, so a rule that no longer works is easy to spot. */
    val lastMatchedAt: Instant? = null,
)

// Stored by name: never rename a constant. Rules are checked in this order.
enum class RuleType { ACCOUNT, CHAT, SENDER, KEYWORD }

enum class RuleEffect {
    /** "Never read": always wins. */
    EXCLUDE,

    /** "Only read": used by apps set to "Only listed chats". */
    ALLOW,
}

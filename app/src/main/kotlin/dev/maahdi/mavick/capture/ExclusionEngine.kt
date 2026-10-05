package dev.maahdi.mavick.capture

import dev.maahdi.mavick.data.rules.ExclusionRuleEntity
import dev.maahdi.mavick.data.rules.RuleEffect
import dev.maahdi.mavick.data.rules.RuleType
import java.time.Instant

/** Why a message was not read. */
enum class SkipReason { PAUSED, APP_OFF, ACCOUNT, CHAT, SENDER, KEYWORD, NOT_LISTED }

sealed interface CaptureDecision {
    /** Read it. [ruleId] is the "Only read" rule that let it through, if any. */
    data class Read(val ruleId: String? = null) : CaptureDecision

    /** Drop it unseen. [ruleId] is the "Never read" rule that matched, if any. */
    data class Skip(val reason: SkipReason, val ruleId: String? = null) : CaptureDecision
}

/**
 * Decides, in memory, whether a message may be read. A skipped message is dropped before anything
 * is stored, shown to the AI or logged (docs/PLAN.md §5.2).
 *
 * Checked in this order: a pause, the app's switch, the "Never read" rules (account, chat, person,
 * keyword), and, for an app set to "Only listed chats", the "Only read" rules. "Never read" always
 * wins over "Only read".
 */
object ExclusionEngine {
    fun decide(
        message: IncomingMessage,
        rules: List<ExclusionRuleEntity>,
        appCapture: (SourceApp) -> AppCapture,
        pause: CapturePause,
        now: Instant,
    ): CaptureDecision {
        if (pause.isActive(now)) return CaptureDecision.Skip(SkipReason.PAUSED)
        val capture = appCapture(message.app)
        if (!capture.enabled) return CaptureDecision.Skip(SkipReason.APP_OFF)
        for (type in RuleType.entries) {
            val excluding = rules.firstOrNull { it.effect == RuleEffect.EXCLUDE && it.type == type && matches(it, message) }
            if (excluding != null) return CaptureDecision.Skip(skipReason(type), excluding.id)
        }
        if (capture.mode == CaptureMode.ONLY_LISTED) {
            val allowing = rules.firstOrNull { it.effect == RuleEffect.ALLOW && matches(it, message) }
                ?: return CaptureDecision.Skip(SkipReason.NOT_LISTED)
            return CaptureDecision.Read(allowing.id)
        }
        return CaptureDecision.Read()
    }

    fun matches(rule: ExclusionRuleEntity, message: IncomingMessage): Boolean {
        if (rule.app != null && rule.app != message.app) return false
        if (rule.accountKey != null && rule.accountKey != message.accountKey) return false
        val value = rule.value.trim()
        if (value.isEmpty()) return false
        return when (rule.type) {
            RuleType.ACCOUNT -> message.accountKey == value
            // A chat picked from saved messages matches by key (survives renames); a typed one by name.
            RuleType.CHAT -> message.conversationKey == value || message.conversationTitle.equals(value, ignoreCase = true)
            RuleType.SENDER -> !message.isFromMe && message.sender.equals(value, ignoreCase = true)
            RuleType.KEYWORD -> keywordPattern(value)?.containsMatchIn(message.text) == true
        }
    }

    /**
     * Whole words or phrases in any case: "PIN" matches "my pin is 1234" but not "spinning", and
     * "verification code" matches across any spaces or line breaks.
     */
    fun keywordPattern(keyword: String): Regex? {
        val words = keyword.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        val phrase = words.joinToString("\\s+") { Regex.escape(it) }
        // (?iu): case-insensitive for every alphabet, not just English.
        return Regex("(?iu)(?<!$WORD_CHARACTER)$phrase(?!$WORD_CHARACTER)")
    }

    private fun skipReason(type: RuleType) = when (type) {
        RuleType.ACCOUNT -> SkipReason.ACCOUNT
        RuleType.CHAT -> SkipReason.CHAT
        RuleType.SENDER -> SkipReason.SENDER
        RuleType.KEYWORD -> SkipReason.KEYWORD
    }

    private val WHITESPACE = Regex("\\s+")

    /** Letters (with their accent and vowel marks), digits and underscores make up words. */
    private const val WORD_CHARACTER = "[\\p{L}\\p{M}\\p{N}_]"
}

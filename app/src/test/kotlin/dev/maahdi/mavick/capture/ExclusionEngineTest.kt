package dev.maahdi.mavick.capture

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.rules.ExclusionRuleEntity
import dev.maahdi.mavick.data.rules.RuleEffect
import dev.maahdi.mavick.data.rules.RuleType
import java.time.Instant
import org.junit.Test

class ExclusionEngineTest {
    private val now = Instant.parse("2026-10-05T04:00:00Z")

    private val fromSam = IncomingMessage(
        app = SourceApp.WHATSAPP,
        accountKey = "0",
        conversationKey = "s:family@g.us",
        conversationTitle = "Family",
        sender = "Sam",
        text = "Pay the electricity bill by Thursday",
        postedAt = now,
        isFromMe = false,
        isGroup = true,
        cutShort = false,
    )

    private var nextId = 0

    private fun rule(
        type: RuleType,
        value: String,
        effect: RuleEffect = RuleEffect.EXCLUDE,
        app: SourceApp? = null,
        accountKey: String? = null,
    ) = ExclusionRuleEntity("rule-${nextId++}", type, effect, value, app, accountKey, value, now)

    private fun decide(
        message: IncomingMessage = fromSam,
        rules: List<ExclusionRuleEntity> = emptyList(),
        apps: Map<SourceApp, AppCapture> = emptyMap(),
        pause: CapturePause = CapturePause.Off,
    ) = ExclusionEngine.decide(message, rules, { apps[it] ?: AppCapture() }, pause, now)

    private fun skipReason(decision: CaptureDecision) = (decision as? CaptureDecision.Skip)?.reason

    @Test
    fun `with no rules, every message is read`() {
        assertThat(decide()).isEqualTo(CaptureDecision.Read())
    }

    // --- Pause and app switches ---------------------------------------------------------------

    @Test
    fun `a pause skips everything until it ends, or until resumed`() {
        assertThat(skipReason(decide(pause = CapturePause.UntilResumed))).isEqualTo(SkipReason.PAUSED)
        assertThat(skipReason(decide(pause = CapturePause.Until(now.plusSeconds(1))))).isEqualTo(SkipReason.PAUSED)
        assertThat(decide(pause = CapturePause.Until(now))).isEqualTo(CaptureDecision.Read())
        assertThat(decide(pause = CapturePause.Until(now.minusSeconds(60)))).isEqualTo(CaptureDecision.Read())
    }

    @Test
    fun `an app switched off is never read, other apps still are`() {
        val apps = mapOf(SourceApp.WHATSAPP to AppCapture(enabled = false))

        assertThat(skipReason(decide(apps = apps))).isEqualTo(SkipReason.APP_OFF)
        assertThat(decide(message = fromSam.copy(app = SourceApp.MESSENGER), apps = apps)).isEqualTo(CaptureDecision.Read())
    }

    @Test
    fun `the pause is checked before anything else`() {
        val apps = mapOf(SourceApp.WHATSAPP to AppCapture(enabled = false))

        assertThat(skipReason(decide(apps = apps, pause = CapturePause.UntilResumed))).isEqualTo(SkipReason.PAUSED)
    }

    // --- Each rule type -------------------------------------------------------------------------

    @Test
    fun `an account rule skips everything that account receives`() {
        val second = rule(RuleType.ACCOUNT, "999", app = SourceApp.WHATSAPP)

        assertThat(decide(fromSam.copy(accountKey = "999"), listOf(second))).isEqualTo(CaptureDecision.Skip(SkipReason.ACCOUNT, second.id))
        assertThat(decide(fromSam, listOf(second))).isEqualTo(CaptureDecision.Read())
        assertThat(decide(fromSam.copy(app = SourceApp.MESSENGER, accountKey = "999"), listOf(second))).isEqualTo(CaptureDecision.Read())
    }

    @Test
    fun `a chat rule matches the chat's key, or its name in any case`() {
        val byKey = rule(RuleType.CHAT, "s:family@g.us", app = SourceApp.WHATSAPP, accountKey = "0")
        val byName = rule(RuleType.CHAT, "  family ")

        assertThat(skipReason(decide(rules = listOf(byKey)))).isEqualTo(SkipReason.CHAT)
        assertThat(skipReason(decide(rules = listOf(byName)))).isEqualTo(SkipReason.CHAT)
        // Renamed, but the key still matches:
        assertThat(skipReason(decide(fromSam.copy(conversationTitle = "Family 2026"), listOf(byKey)))).isEqualTo(SkipReason.CHAT)
        assertThat(decide(fromSam.copy(conversationKey = "s:work@g.us", conversationTitle = "Work"), listOf(byKey, byName)))
            .isEqualTo(CaptureDecision.Read())
    }

    @Test
    fun `a person rule skips what they write, in any chat, but never your own messages`() {
        val sam = rule(RuleType.SENDER, "sam")

        assertThat(skipReason(decide(rules = listOf(sam)))).isEqualTo(SkipReason.SENDER)
        assertThat(skipReason(decide(fromSam.copy(conversationKey = "s:sam", isGroup = false), listOf(sam)))).isEqualTo(SkipReason.SENDER)
        assertThat(decide(fromSam.copy(sender = "Rina"), listOf(sam))).isEqualTo(CaptureDecision.Read())
        assertThat(decide(fromSam.copy(sender = null, isFromMe = true), listOf(sam))).isEqualTo(CaptureDecision.Read())
    }

    @Test
    fun `a keyword matches whole words or phrases in any case`() {
        fun skippedBy(keyword: String, text: String) =
            skipReason(decide(fromSam.copy(text = text), listOf(rule(RuleType.KEYWORD, keyword)))) == SkipReason.KEYWORD

        assertThat(skippedBy("PIN", "my pin is 1234")).isTrue()
        assertThat(skippedBy("PIN", "PIN: 1234")).isTrue()
        assertThat(skippedBy("pin", "Your PIN.")).isTrue()
        assertThat(skippedBy("PIN", "spinning class at 6")).isFalse()
        assertThat(skippedBy("PIN", "pinned the location")).isFalse()
        assertThat(skippedBy("OTP", "Your OTP is 4821")).isTrue()
        assertThat(skippedBy("OTP", "OTPs expire fast")).isFalse()
        assertThat(skippedBy("verification code", "Your verification\ncode: 99")).isTrue()
        assertThat(skippedBy("verification code", "verification of the code")).isFalse()
        assertThat(skippedBy("a.b", "a.b")).isTrue()
        assertThat(skippedBy("a.b", "axb")).isFalse() // special characters are matched literally
        assertThat(skippedBy("টাকা", "৫০০ টাকা দাও")).isTrue()
        assertThat(skippedBy("টাকা", "টাকার হিসাব")).isFalse()
        assertThat(skippedBy("ÉTÉ", "l'été arrive")).isTrue() // case-insensitive beyond English
    }

    @Test
    fun `a blank rule never matches`() {
        listOf(RuleType.ACCOUNT, RuleType.CHAT, RuleType.SENDER, RuleType.KEYWORD).forEach { type ->
            assertThat(decide(rules = listOf(rule(type, "   ")))).isEqualTo(CaptureDecision.Read())
        }
        assertThat(ExclusionEngine.keywordPattern(" ")).isNull()
    }

    // --- Scope: every app and account, or just one ----------------------------------------------

    @Test
    fun `a rule for one app or account leaves the others alone`() {
        val whatsAppOnly = rule(RuleType.KEYWORD, "bill", app = SourceApp.WHATSAPP)
        val mainAccountOnly = rule(RuleType.SENDER, "Sam", accountKey = "0")

        assertThat(skipReason(decide(rules = listOf(whatsAppOnly)))).isEqualTo(SkipReason.KEYWORD)
        assertThat(decide(fromSam.copy(app = SourceApp.GMAIL), listOf(whatsAppOnly))).isEqualTo(CaptureDecision.Read())
        assertThat(skipReason(decide(rules = listOf(mainAccountOnly)))).isEqualTo(SkipReason.SENDER)
        assertThat(decide(fromSam.copy(accountKey = "999"), listOf(mainAccountOnly))).isEqualTo(CaptureDecision.Read())
    }

    @Test
    fun `when several rules match, the reason follows the order account, chat, person, keyword`() {
        val keyword = rule(RuleType.KEYWORD, "bill")
        val sender = rule(RuleType.SENDER, "Sam")
        val chat = rule(RuleType.CHAT, "Family")
        val account = rule(RuleType.ACCOUNT, "0")

        assertThat(decide(rules = listOf(keyword, sender, chat, account))).isEqualTo(CaptureDecision.Skip(SkipReason.ACCOUNT, account.id))
        assertThat(decide(rules = listOf(keyword, sender, chat))).isEqualTo(CaptureDecision.Skip(SkipReason.CHAT, chat.id))
        assertThat(decide(rules = listOf(keyword, sender))).isEqualTo(CaptureDecision.Skip(SkipReason.SENDER, sender.id))
    }

    // --- "Only listed chats" ----------------------------------------------------------------------

    private val onlyListed = mapOf(SourceApp.WHATSAPP to AppCapture(mode = CaptureMode.ONLY_LISTED))

    @Test
    fun `an app set to only listed chats reads a chat or person on the list`() {
        val family = rule(RuleType.CHAT, "Family", effect = RuleEffect.ALLOW)
        val rina = rule(RuleType.SENDER, "Rina", effect = RuleEffect.ALLOW)

        assertThat(decide(rules = listOf(family, rina), apps = onlyListed)).isEqualTo(CaptureDecision.Read(family.id))
        assertThat(decide(fromSam.copy(conversationKey = "s:x", conversationTitle = "Other", sender = "Rina"), listOf(family, rina), onlyListed))
            .isEqualTo(CaptureDecision.Read(rina.id))
    }

    @Test
    fun `an app set to only listed chats skips everything else, even with an empty list`() {
        val work = rule(RuleType.CHAT, "Work", effect = RuleEffect.ALLOW)

        assertThat(skipReason(decide(rules = listOf(work), apps = onlyListed))).isEqualTo(SkipReason.NOT_LISTED)
        assertThat(skipReason(decide(apps = onlyListed))).isEqualTo(SkipReason.NOT_LISTED)
    }

    @Test
    fun `never read wins over only read`() {
        val family = rule(RuleType.CHAT, "Family", effect = RuleEffect.ALLOW)
        val bill = rule(RuleType.KEYWORD, "bill")

        assertThat(decide(rules = listOf(family, bill), apps = onlyListed)).isEqualTo(CaptureDecision.Skip(SkipReason.KEYWORD, bill.id))
    }

    @Test
    fun `only-read rules do nothing for apps that read all chats`() {
        val work = rule(RuleType.CHAT, "Work", effect = RuleEffect.ALLOW)

        assertThat(decide(rules = listOf(work))).isEqualTo(CaptureDecision.Read())
    }

    @Test
    fun `another app's mode doesn't change this app`() {
        val gmailOnlyListed = mapOf(SourceApp.GMAIL to AppCapture(mode = CaptureMode.ONLY_LISTED))

        assertThat(decide(apps = gmailOnlyListed)).isEqualTo(CaptureDecision.Read())
    }
}

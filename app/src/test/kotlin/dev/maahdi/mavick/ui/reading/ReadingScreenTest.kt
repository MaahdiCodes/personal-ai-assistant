package dev.maahdi.mavick.ui.reading

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.capture.AppCapture
import dev.maahdi.mavick.capture.CaptureMode
import dev.maahdi.mavick.capture.CapturePause
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.message.AccountRef
import dev.maahdi.mavick.data.message.ConversationRef
import dev.maahdi.mavick.data.rules.ExclusionRuleEntity
import dev.maahdi.mavick.data.rules.RuleEffect
import dev.maahdi.mavick.data.rules.RuleType
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-10-05T04:00:00Z")
    private val added = mutableListOf<Triple<RuleType, RuleEffect, RuleTarget>>()
    private val removed = mutableListOf<String>()
    private val appChanges = mutableListOf<Pair<SourceApp, AppCapture>>()

    private fun rule(id: String, type: RuleType, name: String, effect: RuleEffect = RuleEffect.EXCLUDE, app: SourceApp? = null, matchedAgo: Duration? = null) =
        ExclusionRuleEntity(id, type, effect, name, app, null, name, now.minus(Duration.ofDays(10)), matchedAgo?.let { now.minus(it) })

    private fun show(
        rules: List<ExclusionRuleEntity> = emptyList(),
        apps: Map<SourceApp, AppCapture> = emptyMap(),
        suggestions: RuleSuggestions = RuleSuggestions(),
    ) {
        compose.setContent {
            ReadingScreen(
                state = ReadingUiState(rules = rules, suggestions = suggestions, loading = false),
                apps = apps,
                pause = CapturePause.Off,
                now = now,
                zone = ZoneId.of("Asia/Dhaka"),
                use24Hour = true,
                onAppChange = { app, capture -> appChanges += app to capture },
                onPause = {},
                onResume = {},
                onAddRule = { type, effect, target -> added += Triple(type, effect, target) },
                onRemoveRule = { removed += it.id },
                onBack = {},
            )
        }
    }

    @Test
    fun `rules show what they match, where, and when they last matched`() {
        show(
            listOf(
                rule("1", RuleType.KEYWORD, "OTP", matchedAgo = Duration.ofHours(3)),
                rule("2", RuleType.CHAT, "Family", app = SourceApp.WHATSAPP),
            ),
        )

        compose.onNodeWithText("Word: OTP").assertExists()
        compose.onNodeWithText("All apps · last matched 3 h ago").assertExists()
        compose.onNodeWithText("Chat: Family").assertExists()
        compose.onNodeWithText("WhatsApp · not matched yet").assertExists()
    }

    @Test
    fun `a rule can be deleted`() {
        show(listOf(rule("1", RuleType.KEYWORD, "OTP")))

        compose.onNodeWithContentDescription("Delete Word: OTP").performScrollTo().performClick()

        assertThat(removed).containsExactly("1")
    }

    @Test
    fun `an app can be switched off, or set to only listed chats`() {
        show()

        // WhatsApp comes first: its switch, then its "Only listed chats" chip.
        compose.onAllNodes(isToggleable())[0].performClick()
        compose.onAllNodesWithText("Only listed chats")[0].performClick()

        assertThat(appChanges).containsExactly(
            SourceApp.WHATSAPP to AppCapture(enabled = false),
            SourceApp.WHATSAPP to AppCapture(mode = CaptureMode.ONLY_LISTED),
        ).inOrder()
    }

    @Test
    fun `a switched-off app hides its mode`() {
        show(apps = SourceApp.entries.associateWith { AppCapture(enabled = false) })

        compose.onNodeWithText("All chats").assertDoesNotExist()
    }

    @Test
    fun `a typed word becomes a rule for every app`() {
        show()

        compose.onNodeWithText("+ Word").performScrollTo().performClick()
        compose.onNodeWithText("Add").assertIsNotEnabled()
        compose.onNodeWithTag(RULE_FIELD_TAG).performTextInput("  salary ")
        compose.onNodeWithText("Add").performClick()

        assertThat(added).containsExactly(Triple(RuleType.KEYWORD, RuleEffect.EXCLUDE, RuleTarget("salary", "salary")))
    }

    @Test
    fun `a chat picked from saved messages applies to that app and account`() {
        val family = ConversationRef(SourceApp.WHATSAPP, "999", "s:family@g.us", "Family", now)
        show(suggestions = RuleSuggestions(chats = listOf(family)))

        compose.onNodeWithText("+ Chat").performScrollTo().performClick()
        compose.onNodeWithText("Family · WhatsApp · Clone (user 999)").performClick()

        assertThat(added).containsExactly(
            Triple(RuleType.CHAT, RuleEffect.EXCLUDE, RuleTarget("s:family@g.us", "Family", SourceApp.WHATSAPP, "999")),
        )
    }

    @Test
    fun `an account can only be picked, from accounts seen so far`() {
        show(suggestions = RuleSuggestions(accounts = listOf(AccountRef(SourceApp.GMAIL, "0/work@company.com"))))

        compose.onNodeWithText("+ Account").performScrollTo().performClick()
        compose.onNodeWithTag(RULE_FIELD_TAG).assertDoesNotExist()
        compose.onNodeWithText("Gmail · work@company.com").performClick()

        assertThat(added.single().third).isEqualTo(RuleTarget("0/work@company.com", "Gmail · work@company.com", SourceApp.GMAIL, "0/work@company.com"))
    }

    @Test
    fun `with nothing saved yet, the dialog says where names will come from`() {
        show()

        compose.onNodeWithText("+ Person").performScrollTo().performClick()

        compose.onNodeWithText("Names to pick from appear here once messages have been read.").assertExists()
    }

    @Test
    fun `an app set to only listed chats with no list warns that nothing is read`() {
        show(apps = mapOf(SourceApp.MESSENGER to AppCapture(mode = CaptureMode.ONLY_LISTED)))

        compose.onNodeWithText("Nothing is read from Messenger until you add a chat or person here.").performScrollTo().assertExists()
    }

    @Test
    fun `the only-read list adds chats as only-read rules`() {
        show(apps = mapOf(SourceApp.WHATSAPP to AppCapture(mode = CaptureMode.ONLY_LISTED)))

        compose.onAllNodesWithText("+ Chat")[1].performScrollTo().performClick()
        compose.onNodeWithTag(RULE_FIELD_TAG).performTextInput("Work")
        compose.onNodeWithText("Add").performClick()

        assertThat(added).containsExactly(Triple(RuleType.CHAT, RuleEffect.ALLOW, RuleTarget("Work", "Work")))
    }
}

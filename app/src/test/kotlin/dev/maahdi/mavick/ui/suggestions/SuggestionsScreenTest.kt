package dev.maahdi.mavick.ui.suggestions

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.suggestion.SuggestionEntity
import dev.maahdi.mavick.data.suggestion.SuggestionKind
import dev.maahdi.mavick.data.suggestion.SuggestionSource
import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.data.task.TaskSource
import dev.maahdi.mavick.testing.suggestion
import dev.maahdi.mavick.time.DEFAULT_WORK_DAYS
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SuggestionsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    /** Monday 5 October 2026. */
    private val today = LocalDate.of(2026, 10, 5)
    private val calls = mutableListOf<String>()
    private val drafts = mutableListOf<TaskDraft>()

    private fun show(
        suggestions: List<SuggestionEntity>,
        engine: SuggestionEngine = SuggestionEngine.MODEL,
        prompt: NeverReadPrompt? = null,
    ) {
        compose.setContent {
            SuggestionsScreen(
                state = SuggestionsUiState(suggestions = suggestions, loading = false),
                engine = engine,
                neverReadPrompt = prompt,
                today = today,
                workDays = DEFAULT_WORK_DAYS,
                use24Hour = true,
                onAdd = { suggestion, draft ->
                    calls += "add ${suggestion.title}"
                    drafts += draft
                },
                onEdit = { suggestion, draft ->
                    calls += "edit ${suggestion.title}"
                    drafts += draft
                },
                onIgnore = { calls += "ignore ${it.title}" },
                onAskNeverRead = { calls += "never read? ${it.chatTitle}" },
                onConfirmNeverRead = { calls += "never read $it" },
                onDismissNeverRead = { calls += "keep reading" },
                onOpenSettings = { calls += "settings" },
                onBack = {},
                snackbarHostState = SnackbarHostState(),
            )
        }
    }

    private val party = suggestion(
        title = "Go to Sam's party",
        kind = SuggestionKind.EVENT,
        dueDate = LocalDate.of(2026, 10, 8),
        dueTime = LocalTime.of(17, 0),
        excerpt = "Party at mine on Thursday 5pm, come!",
    )

    @Test
    fun `a card shows the task, when, what kind, and the message it came from`() {
        show(listOf(party))

        compose.onNodeWithText("WhatsApp").assertExists()
        compose.onNodeWithText("Go to Sam's party").assertExists()
        compose.onNodeWithText("Thursday · 17:00 · Event").assertExists()
        compose.onNodeWithText("Family · Sam: Party at mine on Thursday 5pm, come!").assertExists()
    }

    @Test
    fun `Add gives the task as a draft that says where it came from`() {
        show(listOf(party))

        compose.onNodeWithText("Add").performClick()

        assertThat(calls).containsExactly("add Go to Sam's party")
        with(drafts.single()) {
            assertThat(title).isEqualTo("Go to Sam's party")
            assertThat(notes).isEqualTo("From Sam in Family (WhatsApp)")
            assertThat(dueDate).isEqualTo(LocalDate.of(2026, 10, 8))
            assertThat(reminderTime).isEqualTo(LocalTime.of(17, 0))
            assertThat(source).isEqualTo(TaskSource.MESSAGE)
            assertThat(sourceExcerpt).isEqualTo("Party at mine on Thursday 5pm, come!")
        }
    }

    @Test
    fun `Edit and Ignore act on their card`() {
        show(listOf(party))

        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Ignore").performClick()

        assertThat(calls).containsExactly("edit Go to Sam's party", "ignore Go to Sam's party").inOrder()
    }

    @Test
    fun `when words that couldn't be read are shown, and Add asks for a time`() {
        show(listOf(suggestion(title = "Pay the school fees", whenText = "end of the month", needsTime = true)))

        compose.onNodeWithText("When: \"end of the month\" (pick a time)").assertExists()
        compose.onNodeWithText("Add…").performClick()

        assertThat(drafts.single().notes).isEqualTo("From Sam in Family (WhatsApp)\n\nWhen: end of the month")
    }

    @Test
    fun `an unsure AI suggestion and a rules suggestion say so`() {
        show(
            listOf(
                suggestion(title = "Maybe call Rina", confidence = 0.4, isGroup = false, chatTitle = "Rina", sender = "Rina"),
                suggestion(title = "Pay the rent", source = SuggestionSource.RULES, confidence = 0.5, isGroup = false, chatTitle = "Sam"),
            ),
        )

        compose.onNodeWithText("Not sure").assertExists()
        compose.onNodeWithText("Simple rules").assertExists()
    }

    @Test
    fun `your own message is quoted as yours, and a one-to-one chat without its name`() {
        show(listOf(suggestion(title = "Send the report", isFromMe = true, sender = null, isGroup = false, chatTitle = "Boss", excerpt = "I'll send it tonight")))

        compose.onNodeWithText("You: I'll send it tonight").assertExists()
    }

    @Test
    fun `another account is named on the card`() {
        show(listOf(party.copy(app = SourceApp.WHATSAPP, accountKey = "999")))

        compose.onNodeWithText("WhatsApp · Clone (user 999)").assertExists()
    }

    @Test
    fun `Never read this chat asks first, then stops reading it`() {
        show(listOf(party))
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Never read this chat").performClick()
        assertThat(calls).containsExactly("never read? Family")
    }

    @Test
    fun `the never-read question names the chat and how many messages go`() {
        show(listOf(party), prompt = NeverReadPrompt(party, messageCount = 4))

        compose.onNodeWithText("Stop reading Family?").assertExists()
        compose.onNodeWithText("Mavick will skip new messages from this chat and delete its 4 saved messages.").assertExists()
        compose.onNode(hasText("Stop reading") and hasAnyAncestor(isDialog())).performClick()

        assertThat(calls).containsExactly("never read Family")
    }

    @Test
    fun `with nothing waiting it explains, and without a model it says rules are used`() {
        show(emptyList(), engine = SuggestionEngine.RULES)

        compose.onNodeWithText("Nothing to review", substring = true).assertExists()
        compose.onNodeWithText("No AI model yet", substring = true).assertExists()
        compose.onNodeWithText("Settings").performClick()
        assertThat(calls).containsExactly("settings")
    }

    @Test
    fun `with suggestions off it says so`() {
        show(emptyList(), engine = SuggestionEngine.OFF)

        compose.onNodeWithText("Suggestions are off.").assertExists()
    }
}

package dev.maahdi.mavick.ui.inbox

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.capture.CapturePause
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.message.MessageEntity
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Inbox and the single-message screen. */
@RunWith(AndroidJUnit4::class)
class InboxScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val dhaka = ZoneId.of("Asia/Dhaka")
    private val now = Instant.parse("2026-10-05T04:00:00Z") // 10:00 in Dhaka
    private val today = LocalDate.of(2026, 10, 5)

    private fun message(
        id: String,
        text: String,
        sender: String? = "Sam",
        chat: String = "Family",
        app: SourceApp = SourceApp.WHATSAPP,
        account: String = "0",
        minutesAgo: Long = 5,
        isGroup: Boolean = true,
        isFromMe: Boolean = false,
        cutShort: Boolean = false,
    ) = MessageEntity(
        id = id,
        app = app,
        accountKey = account,
        conversationKey = "s:$chat",
        conversationTitle = chat,
        sender = sender,
        text = text,
        postedAt = now.minus(Duration.ofMinutes(minutesAgo)),
        receivedAt = now,
        isFromMe = isFromMe,
        isGroup = isGroup,
        cutShort = cutShort,
        dedupHash = id,
    )

    private fun showInbox(
        messages: List<MessageEntity>,
        hasAccess: Boolean = true,
        pause: CapturePause = CapturePause.Off,
        onOpenMessage: (MessageEntity) -> Unit = {},
        onTurnOnAccess: () -> Unit = {},
        onResume: () -> Unit = {},
        onPause: (PauseChoice) -> Unit = {},
    ) {
        compose.setContent {
            InboxScreen(
                state = InboxUiState(messages = messages, loading = false),
                hasAccess = hasAccess,
                pause = pause,
                now = now,
                zone = dhaka,
                use24Hour = true,
                onOpenMessage = onOpenMessage,
                onOpenReading = {},
                onPause = onPause,
                onResume = onResume,
                onTurnOnAccess = onTurnOnAccess,
                onBack = {},
            )
        }
    }

    @Test
    fun `messages show their app, chat, sender and time, grouped by day`() {
        var opened: String? = null
        showInbox(
            listOf(
                message("1", "Dinner at 8?", minutesAgo = 5),
                message("2", "Invoice attached", sender = "Rahim", chat = "Rahim", app = SourceApp.GMAIL, account = "0/you@gmail.com", isGroup = false, minutesAgo = 60 * 24),
                message("3", "On my way", sender = null, isFromMe = true, minutesAgo = 6),
            ),
            onOpenMessage = { opened = it.id },
        )

        compose.onNodeWithText("Today").assertExists()
        compose.onNodeWithText("Yesterday").assertExists()
        compose.onNodeWithText("Sam: Dinner at 8?").assertExists()
        compose.onNodeWithText("You: On my way").assertExists()
        compose.onNodeWithText("09:55").assertExists()
        compose.onNodeWithText("Gmail · you@gmail.com").assertExists()
        compose.onNodeWithText("Invoice attached").assertExists() // not a group: no sender prefix

        compose.onNodeWithText("Sam: Dinner at 8?").performClick()
        assertThat(opened).isEqualTo("1")
    }

    @Test
    fun `a cut-short message is labelled`() {
        showInbox(listOf(message("1", "Long story", cutShort = true)))

        compose.onNodeWithText("Cut short").assertExists()
    }

    @Test
    fun `without notification access, the inbox says so and offers to turn it on`() {
        var turnedOn = 0
        showInbox(emptyList(), hasAccess = false, onTurnOnAccess = { turnedOn++ })

        compose.onNodeWithText("Mavick can't read notifications yet.").assertExists()
        compose.onNodeWithText("Turn on").performClick()

        assertThat(turnedOn).isEqualTo(1)
    }

    @Test
    fun `while paused, the inbox says until when and offers to resume`() {
        var resumed = 0
        showInbox(emptyList(), pause = CapturePause.Until(now.plus(Duration.ofHours(1))), onResume = { resumed++ })

        compose.onNodeWithText("Reading is paused until Today · 11:00.").assertExists()
        compose.onNodeWithText("Resume reading").performClick()

        assertThat(resumed).isEqualTo(1)
    }

    @Test
    fun `an ended pause shows nothing`() {
        showInbox(emptyList(), pause = CapturePause.Until(now.minusSeconds(1)))

        compose.onNodeWithText("Resume reading").assertDoesNotExist()
    }

    @Test
    fun `reading can be paused from the menu`() {
        var paused: PauseChoice? = null
        showInbox(emptyList(), onPause = { paused = it })

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Pause until 6:00 tomorrow").performClick()

        assertThat(paused).isEqualTo(PauseChoice.UNTIL_TOMORROW_MORNING)
    }

    @Test
    fun `an empty inbox explains what will appear`() {
        showInbox(emptyList())

        compose.onNodeWithText("No messages yet. New WhatsApp, Messenger and Gmail messages, and Keep reminders, appear here.").assertExists()
    }

    private fun showMessage(state: MessageUiState, onAddTask: (MessageEntity) -> Unit = {}, onNeverRead: (MessageEntity) -> Unit = {}) {
        compose.setContent {
            MessageScreen(state, today, dhaka, use24Hour = true, onAddTask = onAddTask, onNeverReadChat = onNeverRead, onBack = {})
        }
    }

    @Test
    fun `a message shows in full with who sent it and when`() {
        showMessage(MessageUiState(message("1", "Please pay the bill by Thursday"), chatMessageCount = 3, loading = false))

        compose.onNodeWithText("Please pay the bill by Thursday").assertExists()
        compose.onNodeWithText("From Sam · Today · 09:55").assertExists()
        compose.onNodeWithText("Android cut this message short. Open WhatsApp for the rest.").assertDoesNotExist()
    }

    @Test
    fun `a cut-short message and a Gmail preview say where the rest is`() {
        showMessage(MessageUiState(message("1", "Long story", cutShort = true), loading = false))
        compose.onNodeWithText("Android cut this message short. Open WhatsApp for the rest.").assertExists()
    }

    @Test
    fun `an email always says it is a preview`() {
        showMessage(MessageUiState(message("1", "Invoice", app = SourceApp.GMAIL, isGroup = false), loading = false))

        compose.onNodeWithText("Gmail shows only a preview of each email. Open Gmail for all of it.").assertExists()
    }

    @Test
    fun `add as task hands over the message`() {
        var added: String? = null
        showMessage(MessageUiState(message("1", "Call me at 5"), loading = false), onAddTask = { added = it.id })

        compose.onNodeWithText("Add as task").performClick()

        assertThat(added).isEqualTo("1")
    }

    @Test
    fun `never reading a chat asks first, saying how many saved messages go`() {
        var excluded: String? = null
        showMessage(MessageUiState(message("1", "Hi"), chatMessageCount = 3, loading = false), onNeverRead = { excluded = it.conversationKey })

        compose.onNodeWithText("Never read this chat").performClick()
        compose.onNodeWithText("Stop reading Family?").assertExists()
        compose.onNodeWithText("Mavick will skip new messages from this chat and delete its 3 saved messages.").assertExists()
        assertThat(excluded).isNull()

        compose.onNodeWithText("Stop reading").performClick()

        assertThat(excluded).isEqualTo("s:Family")
    }

    @Test
    fun `cancelling keeps reading the chat`() {
        var excluded = false
        showMessage(MessageUiState(message("1", "Hi"), chatMessageCount = 1, loading = false), onNeverRead = { excluded = true })

        compose.onNodeWithText("Never read this chat").performClick()
        compose.onNodeWithText("Cancel").performClick()

        assertThat(excluded).isFalse()
    }

    @Test
    fun `a message deleted meanwhile says so`() {
        showMessage(MessageUiState(loading = false, missing = true))

        compose.onNodeWithText("This message is no longer saved.").assertExists()
    }
}

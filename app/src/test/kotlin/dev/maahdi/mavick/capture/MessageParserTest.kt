package dev.maahdi.mavick.capture

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class MessageParserTest {
    private val parser = MessageParser()
    private val posted = Instant.parse("2026-10-05T04:00:00Z")

    private fun whatsApp(vararg messages: RawMessage, title: String? = "Sam", conversationTitle: String? = null, group: Boolean = false, shortcutId: String? = "sam@s.whatsapp.net", userId: Int = 0, selfName: String? = "Me", selfKey: String? = null) =
        RawNotification(
            packageName = SourceApp.WHATSAPP.packageName,
            userId = userId,
            postedAt = posted,
            shortcutId = shortcutId,
            title = title,
            conversationTitle = conversationTitle,
            isGroupConversation = group,
            selfName = selfName,
            selfKey = selfKey,
            messages = messages.toList(),
        )

    private fun message(text: String?, sender: String? = "Sam", at: Long = 1_000, key: String? = null) =
        RawMessage(text, Instant.ofEpochMilli(at), sender, key)

    private fun parse(app: SourceApp, raw: RawNotification) = parser.parse(app, raw)

    @Test
    fun `each chat message becomes a message with its sender and time`() {
        val messages = parse(SourceApp.WHATSAPP, whatsApp(message("Call me today at 12", at = 1_000), message("Thanks", at = 2_000)))

        assertThat(messages).containsExactly(
            IncomingMessage(SourceApp.WHATSAPP, "0", "s:sam@s.whatsapp.net", "Sam", "Sam", "Call me today at 12", Instant.ofEpochMilli(1_000), isFromMe = false, isGroup = false, cutShort = false),
            IncomingMessage(SourceApp.WHATSAPP, "0", "s:sam@s.whatsapp.net", "Sam", "Sam", "Thanks", Instant.ofEpochMilli(2_000), isFromMe = false, isGroup = false, cutShort = false),
        ).inOrder()
    }

    @Test
    fun `a group keeps the group's name and each sender`() {
        val messages = parse(
            SourceApp.WHATSAPP,
            whatsApp(message("Dinner at 8?", sender = "Rina"), message("Yes", sender = "Karim"), title = "Family: Karim", conversationTitle = "Family", group = true),
        )

        assertThat(messages.map { it.conversationTitle }.distinct()).containsExactly("Family")
        assertThat(messages.map { it.sender }).containsExactly("Rina", "Karim").inOrder()
        assertThat(messages.all { it.isGroup }).isTrue()
    }

    @Test
    fun `a chat name loses invisible direction marks and a message count, so it stays the same`() {
        val raw = whatsApp(message("Hi"), conversationTitle = "‎Family (3 messages)", group = true, shortcutId = null)

        val message = parse(SourceApp.WHATSAPP, raw).single()

        assertThat(message.conversationTitle).isEqualTo("Family")
        assertThat(message.conversationKey).isEqualTo("t:Family")
    }

    @Test
    fun `without a shortcut, the chat is known by its name`() {
        assertThat(parse(SourceApp.WHATSAPP, whatsApp(message("Hi"), shortcutId = null)).single().conversationKey).isEqualTo("t:Sam")
    }

    @Test
    fun `your own reply has no sender and is marked as yours`() {
        val mine = RawMessage("On my way", Instant.ofEpochMilli(3_000), senderName = null, hasSender = false)

        val message = parse(SourceApp.WHATSAPP, whatsApp(mine)).single()

        assertThat(message.isFromMe).isTrue()
        assertThat(message.sender).isNull()
    }

    @Test
    fun `an app that names you as the sender is recognised by your key, or else your name`() {
        val byKey = whatsApp(message("Done", sender = "Me", key = "me-key"), selfKey = "me-key")
        val byName = whatsApp(message("Done", sender = "Me"), selfName = "Me", selfKey = null)
        val someoneElse = whatsApp(message("Done", sender = "Me", key = "other"), selfKey = "me-key")

        assertThat(parse(SourceApp.WHATSAPP, byKey).single().isFromMe).isTrue()
        assertThat(parse(SourceApp.WHATSAPP, byName).single().isFromMe).isTrue()
        assertThat(parse(SourceApp.WHATSAPP, someoneElse).single().isFromMe).isFalse()
    }

    @Test
    fun `deleted messages, call notices, placeholders and empty texts are dropped`() {
        val raw = whatsApp(
            message("This message was deleted"),
            message("Missed voice call"),
            message("Waiting for this message. This may take a while."),
            message("   "),
            message(null),
            message("Real one"),
        )

        assertThat(parse(SourceApp.WHATSAPP, raw).map { it.text }).containsExactly("Real one")
    }

    @Test
    fun `a message without its own time takes the notification's`() {
        val raw = whatsApp(RawMessage("Hi", timestamp = null, senderName = "Sam"))

        assertThat(parse(SourceApp.WHATSAPP, raw).single().postedAt).isEqualTo(posted)
    }

    @Test
    fun `text cut at Android's limit is marked cut short, but a whole long text is not`() {
        val cut = "x".repeat(MessageParser.ANDROID_TEXT_LIMIT)
        val whole = "y".repeat(3000)

        val messages = parse(SourceApp.WHATSAPP, whatsApp(message(cut, at = 1), message(whole, at = 2)))

        assertThat(messages.map { it.cutShort }).containsExactly(true, false).inOrder()
        assertThat(messages[1].text).hasLength(3000)
    }

    @Test
    fun `a huge message is stored up to Mavick's own limit`() {
        val huge = "z".repeat(MessageParser.MAX_TEXT_LENGTH + 500)

        assertThat(parse(SourceApp.WHATSAPP, whatsApp(message(huge))).single().text).hasLength(MessageParser.MAX_TEXT_LENGTH)
    }

    @Test
    fun `a cloned app is told apart by its Android user`() {
        assertThat(parse(SourceApp.WHATSAPP, whatsApp(message("Hi"), userId = 999)).single().accountKey).isEqualTo("999")
    }

    @Test
    fun `an email is one message from its sender, with the receiving address as its account`() {
        val raw = RawNotification(
            packageName = SourceApp.GMAIL.packageName,
            userId = 0,
            postedAt = posted,
            shownTime = Instant.ofEpochMilli(5_000),
            title = "Rahim",
            text = "Invoice",
            subText = "you@gmail.com",
            bigText = "Invoice\nPlease pay by Thursday",
        )

        val email = parse(SourceApp.GMAIL, raw).single()

        assertThat(email).isEqualTo(
            IncomingMessage(SourceApp.GMAIL, "0/you@gmail.com", "t:Rahim", "Rahim", "Rahim", "Invoice\nPlease pay by Thursday", Instant.ofEpochMilli(5_000), isFromMe = false, isGroup = false, cutShort = false),
        )
    }

    @Test
    fun `an email without a preview uses its subject, and a sub-text that isn't an address is no account`() {
        val raw = RawNotification(SourceApp.GMAIL.packageName, 0, posted, title = "Rahim", text = "Invoice", subText = "Updates")

        val email = parse(SourceApp.GMAIL, raw).single()

        assertThat(email.text).isEqualTo("Invoice")
        assertThat(email.accountKey).isEqualTo("0")
        assertThat(email.postedAt).isEqualTo(posted)
    }

    @Test
    fun `a Keep reminder keeps the note's title with its text, once`() {
        val withText = RawNotification(SourceApp.KEEP.packageName, 0, posted, title = "Dentist", text = "Bring the card")
        val titleOnly = RawNotification(SourceApp.KEEP.packageName, 0, posted, title = "Dentist", text = "Dentist")

        val reminder = parse(SourceApp.KEEP, withText).single()

        assertThat(reminder.text).isEqualTo("Dentist\nBring the card")
        assertThat(reminder.sender).isNull()
        assertThat(reminder.conversationTitle).isEmpty()
        assertThat(parse(SourceApp.KEEP, titleOnly).single().text).isEqualTo("Dentist")
    }

    @Test
    fun `list-style text becomes one message`() {
        val raw = RawNotification(SourceApp.MESSENGER.packageName, 0, posted, title = "Sam", textLines = listOf("Hi", "Are you free?"))

        assertThat(parse(SourceApp.MESSENGER, raw).single().text).isEqualTo("Hi\nAre you free?")
    }

    @Test
    fun `a notification with no text gives nothing`() {
        assertThat(parse(SourceApp.MESSENGER, RawNotification(SourceApp.MESSENGER.packageName, 0, posted, title = "Sam"))).isEmpty()
    }

    @Test
    fun `cleaning a name trims it and drops what changes between notifications`() {
        assertThat(MessageParser.cleanName("  Work ‫team‬ (12 new messages) ")).isEqualTo("Work team")
        assertThat(MessageParser.cleanName("‎")).isNull()
        assertThat(MessageParser.cleanName(null)).isNull()
        assertThat(MessageParser.cleanName("Room (2 people)")).isEqualTo("Room (2 people)")
    }
}

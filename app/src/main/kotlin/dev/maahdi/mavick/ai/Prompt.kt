package dev.maahdi.mavick.ai

import dev.maahdi.mavick.capture.SourceApp

/**
 * What the AI model is told (docs/PLAN.md §5.3). Kept short: a small model on a phone reads every
 * word of it for every message, so each sentence costs time.
 *
 * Dates stay as words ("Thursday 5pm"): small models are bad at date arithmetic, so [WhenResolver]
 * turns the words into a date, counting from when the message was sent.
 */
object Prompt {
    /** Longer messages are cut here and marked partial: tasks are nearly always near the start. */
    const val MAX_MESSAGE_CHARS = 1_500
    const val MAX_CONTEXT_CHARS = 300
    const val MAX_NAME_CHARS = 60

    val SYSTEM: String = """
        You read one new message from the phone owner's chats or email and list what the owner needs to do.
        Reply with JSON only, like this:
        {"actionable": true, "items": [{"kind": "task", "title": "Send the signed form to Sam", "when_text": "Thursday 5pm", "person": "Sam", "confidence": 0.9}]}
        Rules:
        - An item is something the owner must do, attend, pay, bring, buy, reply to or remember, or a promise the owner made. Lines from "Me" are the owner's own.
        - kind: "event" for a meeting or appointment at a set time, "reminder" for a Keep reminder or something to remember, otherwise "task".
        - title: a short to-do starting with a verb, 3 to 80 characters.
        - when_text: the words of the message that say when, copied exactly, or null.
        - person: who the item involves, or null.
        - confidence: from 0 to 1, how sure you are that this is a real to-do.
        - At most 3 items. Take items only from the new message; earlier messages just explain it.
        - Greetings, small talk, news, adverts, jokes and things already done are not actionable: {"actionable": false, "items": []}
    """.trimIndent()

    /** The message and its context, as the model reads them. */
    fun forMessage(input: ExtractionInput): String = buildString {
        appendLine(sourceLine(input))
        if (input.earlier.isNotEmpty()) {
            appendLine("Earlier messages:")
            input.earlier.forEach { appendLine(line(it, input, MAX_CONTEXT_CHARS)) }
        }
        val cut = input.message.text.trim().length > MAX_MESSAGE_CHARS
        when {
            input.app == SourceApp.GMAIL -> appendLine("Only the start of this email is shown.")
            input.partial || cut -> appendLine("The new message was cut short; only its start is shown.")
        }
        appendLine("New message:")
        append(line(input.message, input, MAX_MESSAGE_CHARS))
    }

    private fun sourceLine(input: ExtractionInput): String {
        val chat = name(input.chatTitle)
        return when (input.app) {
            SourceApp.GMAIL -> if (chat.isEmpty()) "An email." else "An email from \"$chat\"."
            SourceApp.KEEP -> "A Google Keep reminder."
            SourceApp.WHATSAPP, SourceApp.WHATSAPP_BUSINESS, SourceApp.MESSENGER -> {
                val app = if (input.app == SourceApp.MESSENGER) "Messenger" else "WhatsApp"
                when {
                    chat.isEmpty() -> "A $app chat."
                    input.isGroup -> "The $app group \"$chat\"."
                    else -> "A $app chat with \"$chat\"."
                }
            }
        }
    }

    /** "Sam: text", "Me: text", or the bare text when the app names no one. */
    private fun line(chatLine: ChatLine, input: ExtractionInput, maxChars: Int): String {
        val text = cut(chatLine.text.trim(), maxChars)
        val speaker = when {
            chatLine.isFromMe -> "Me"
            chatLine.sender != null -> name(chatLine.sender)
            // In a one-to-one chat, the other person is the chat.
            !input.isGroup && input.app != SourceApp.KEEP -> name(input.chatTitle)
            else -> ""
        }
        return if (speaker.isEmpty()) text else "$speaker: $text"
    }

    /** Names on one line, so a name can't pose as another line of the chat. */
    private fun name(text: String): String = cut(text.replace(WHITESPACE, " ").trim(), MAX_NAME_CHARS)

    private fun cut(text: String, maxChars: Int): String = if (text.length <= maxChars) text else text.take(maxChars).trimEnd() + "…"

    private val WHITESPACE = Regex("\\s+")
}

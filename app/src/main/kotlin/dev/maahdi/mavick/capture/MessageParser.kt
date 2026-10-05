package dev.maahdi.mavick.capture

/**
 * Turns a supported app's notification into messages.
 *
 * - Chat apps (WhatsApp, Messenger) use Android's conversation style: one entry per message, with
 *   its sender and time. A notification repeats a chat's recent messages; dedup keeps each once.
 * - Gmail posts one email per notification: the sender is the title, the subject and a preview
 *   are the text, and the receiving address is the sub-text.
 * - Keep posts a reminder: the note's title and text.
 *
 * Built against Android's standard notification formats; the recorder (docs/PLAN.md §0.3) checks
 * them against what the apps really post.
 */
class MessageParser {
    fun parse(app: SourceApp, raw: RawNotification): List<IncomingMessage> {
        val accountKey = Accounts.key(raw.userId, accountLabel(app, raw))
        return if (raw.messages.isNotEmpty()) conversation(app, raw, accountKey) else single(app, raw, accountKey)
    }

    private fun accountLabel(app: SourceApp, raw: RawNotification): String? = when (app) {
        SourceApp.GMAIL -> cleanName(raw.subText)?.takeIf { '@' in it }
        else -> null
    }

    private fun conversation(app: SourceApp, raw: RawNotification, accountKey: String): List<IncomingMessage> {
        val title = cleanName(raw.conversationTitle) ?: cleanName(raw.title).orEmpty()
        val conversationKey = conversationKey(raw.shortcutId, title)
        return raw.messages.mapNotNull { message ->
            val text = message.text ?: return@mapNotNull null
            if (Noise.isNoiseText(text)) return@mapNotNull null
            val fromMe = isFromMe(message, raw)
            IncomingMessage(
                app = app,
                accountKey = accountKey,
                conversationKey = conversationKey,
                conversationTitle = title,
                sender = if (fromMe) null else cleanName(message.senderName) ?: title.ifEmpty { null },
                text = text.trim().take(MAX_TEXT_LENGTH),
                postedAt = message.timestamp ?: raw.postedAt,
                isFromMe = fromMe,
                isGroup = raw.isGroupConversation,
                cutShort = text.length == ANDROID_TEXT_LIMIT,
            )
        }
    }

    private fun single(app: SourceApp, raw: RawNotification, accountKey: String): List<IncomingMessage> {
        val body = raw.bigText ?: raw.textLines.takeIf { it.isNotEmpty() }?.joinToString("\n") ?: raw.text ?: return emptyList()
        val title = cleanName(raw.title)
        val isKeep = app == SourceApp.KEEP
        // A Keep reminder's title is the note's title, part of what to remember.
        val text = if (isKeep) listOfNotNull(title, body.trim().takeIf { it.isNotEmpty() && it != title }).joinToString("\n") else body
        if (Noise.isNoiseText(text)) return emptyList()
        val conversationTitle = if (isKeep) "" else title.orEmpty()
        return listOf(
            IncomingMessage(
                app = app,
                accountKey = accountKey,
                conversationKey = conversationKey(raw.shortcutId, conversationTitle),
                conversationTitle = conversationTitle,
                sender = if (isKeep) null else title,
                text = text.trim().take(MAX_TEXT_LENGTH),
                postedAt = raw.shownTime ?: raw.postedAt,
                isFromMe = false,
                isGroup = false,
                cutShort = body.length == ANDROID_TEXT_LIMIT,
            ),
        )
    }

    /** Android stores your own messages without a sender; some apps name you instead. */
    private fun isFromMe(message: RawMessage, raw: RawNotification): Boolean = when {
        !message.hasSender -> true
        raw.selfKey != null && message.senderKey != null -> message.senderKey == raw.selfKey
        raw.selfKey == null && raw.selfName != null -> message.senderName == raw.selfName
        else -> false
    }

    private fun conversationKey(shortcutId: String?, title: String): String =
        shortcutId?.takeIf { it.isNotBlank() }?.let { "s:$it" } ?: "t:$title"

    companion object {
        /** Android cuts notification text at this length (Notification.MAX_CHARSEQUENCE_LENGTH). */
        const val ANDROID_TEXT_LIMIT = 1024

        /** The most Mavick stores of one message. */
        const val MAX_TEXT_LENGTH = 10_000

        private val INVISIBLE = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]")
        private val COUNT_SUFFIX = Regex("\\s*\\(\\d+\\s+(?:new\\s+)?messages?\\)\\s*$", RegexOption.IGNORE_CASE)

        /**
         * A chat or person's name without invisible direction marks and without a trailing
         * "(3 messages)", so the name stays the same from one notification to the next.
         */
        fun cleanName(text: String?): String? =
            text?.replace(INVISIBLE, "")?.replace(COUNT_SUFFIX, "")?.trim()?.takeIf { it.isNotEmpty() }
    }
}

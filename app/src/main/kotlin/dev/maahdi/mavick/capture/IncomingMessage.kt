package dev.maahdi.mavick.capture

import dev.maahdi.mavick.data.toHex
import java.security.MessageDigest
import java.time.Instant

/** One message read from a notification, before Mavick decides whether to keep it. */
data class IncomingMessage(
    val app: SourceApp,
    val accountKey: String,
    /** The chat within its app and account: "s:<shortcut ID>" or "t:<chat name>". */
    val conversationKey: String,
    /** The chat or group name, or an email's sender; empty when the app gives none. */
    val conversationTitle: String,
    /** Who wrote it; null for your own messages and for Keep reminders. */
    val sender: String?,
    val text: String,
    val postedAt: Instant,
    val isFromMe: Boolean,
    val isGroup: Boolean,
    /** Android cut the text at its notification limit; the app itself has the rest. */
    val cutShort: Boolean,
) {
    /**
     * The message's fingerprint. Chat apps post a chat's recent messages again with every new
     * one, so this keeps each message once. The account is part of it, so accounts never mix.
     */
    fun dedupHash(): String {
        val parts = listOf(
            app.name,
            accountKey,
            conversationKey,
            sender.orEmpty(),
            if (isFromMe) "me" else "",
            text,
            postedAt.toEpochMilli().toString(),
        )
        return MessageDigest.getInstance("SHA-256").digest(parts.joinToString(SEPARATOR).toByteArray(Charsets.UTF_8)).toHex()
    }

    private companion object {
        /** Never appears in text, so "a" + "bc" and "ab" + "c" can't give the same fingerprint. */
        const val SEPARATOR = "\u0000"
    }
}

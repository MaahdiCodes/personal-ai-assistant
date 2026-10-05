package dev.maahdi.mavick.capture

/** Notifications and texts that are never messages: summaries, calls, progress, placeholders. */
object Noise {
    /** Notification.CATEGORY_* values, as text so this file needs no Android. */
    private val CATEGORIES = setOf("call", "missed_call", "progress", "service", "sys", "transport", "status")

    private val TEXTS = listOf(
        "this message was deleted",
        "you deleted this message",
        "waiting for this message\\. this may take a while",
        "(?:missed|incoming|ongoing) (?:voice|video) call",
        "checking for new messages",
        "\\d+ (?:new )?messages?(?: from \\d+ chats?)?",
    ).map { Regex("^(?:$it)\\.?$", RegexOption.IGNORE_CASE) }

    /** Group summaries ("3 new messages"), ongoing notifications (calls, backups) and the like. */
    fun isNoise(raw: RawNotification): Boolean = raw.isGroupSummary || raw.isOngoing || raw.category in CATEGORIES

    /** A message whose text says nothing: empty, deleted, a call, or a placeholder. */
    fun isNoiseText(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.isEmpty() || TEXTS.any { it.matches(trimmed) }
    }
}

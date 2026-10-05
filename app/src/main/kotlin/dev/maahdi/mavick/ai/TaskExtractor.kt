package dev.maahdi.mavick.ai

import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.suggestion.SuggestionKind
import dev.maahdi.mavick.data.suggestion.SuggestionSource
import java.time.LocalDateTime

/** One line of a chat as the AI sees it: who wrote it and what it says, nothing else. */
data class ChatLine(
    /** Null for Keep reminders and when the app names no one. */
    val sender: String?,
    val isFromMe: Boolean,
    val text: String,
)

/** A message to look for tasks in, with the chat it came from. */
data class ExtractionInput(
    val app: SourceApp,
    /** The chat or group name, or an email's sender. */
    val chatTitle: String,
    val isGroup: Boolean,
    /** Up to three earlier messages of the same chat, oldest first: context only. */
    val earlier: List<ChatLine>,
    val message: ChatLine,
    /** Only the start of the message is known (cut short by Android, or an email preview). */
    val partial: Boolean,
    /** When it was sent, in the phone's time zone. */
    val sentAt: LocalDateTime,
)

/** One thing to do, as an extractor found it. Dates are still words; [WhenResolver] reads them. */
data class ExtractedItem(
    val kind: SuggestionKind,
    val title: String,
    /** The words that say when ("Thursday 5pm"), or null. */
    val whenText: String?,
    val person: String?,
    /** From 0 to 1. */
    val confidence: Double,
)

sealed interface ExtractionResult {
    /** The message was read; [items] is empty when there's nothing to do. */
    data class Found(val items: List<ExtractedItem>) : ExtractionResult

    /** The AI's answer was unusable, even after a retry. [reason] is a short code, never content. */
    data class Failed(val reason: String) : ExtractionResult
}

/**
 * Finds tasks in one message. Swappable (docs/PLAN.md §5.3): simple rules without an AI model,
 * or the AI model once imported.
 */
interface TaskExtractor {
    val source: SuggestionSource

    /** Blocking: the AI model takes seconds, so call this on the AI thread. Throws if the model itself fails. */
    fun extract(input: ExtractionInput): ExtractionResult
}

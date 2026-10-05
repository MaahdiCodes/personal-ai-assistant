package dev.maahdi.mavick.ai

import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.suggestion.SuggestionKind
import dev.maahdi.mavick.data.suggestion.SuggestionSource
import dev.maahdi.mavick.time.WhenParser

/**
 * Suggestions without an AI model ("Mavick also works with no model at all", docs/PLAN.md §2).
 *
 * A message with both a date or time and a to-do word becomes one task, titled with the sentence
 * that holds the to-do once the date words are taken out: "Can you send me the report by Friday?"
 * becomes "Can you send me the report", due Friday. A Keep reminder becomes a reminder as it is.
 * Plainer than the AI, so it is used only until a model is imported (or if the model fails).
 */
class RuleExtractor(private val parser: () -> WhenParser) : TaskExtractor {
    override val source = SuggestionSource.RULES

    override fun extract(input: ExtractionInput): ExtractionResult {
        val text = input.message.text.trim().take(MAX_TEXT_LENGTH)
        if (input.app == SourceApp.KEEP) {
            val title = titleFrom(text) ?: return ExtractionResult.Found(emptyList())
            return ExtractionResult.Found(listOf(ExtractedItem(SuggestionKind.REMINDER, title, whenText = null, person = null, confidence = CONFIDENCE)))
        }
        val parsed = parser().parse(text, input.sentAt)
        val hasWhen = parsed.dueDate != null || parsed.dueTime != null || parsed.repeatRule != null
        if (!hasWhen || !Prefilter.hasTaskWord(text)) return ExtractionResult.Found(emptyList())
        val title = titleFrom(parsed.title) ?: return ExtractionResult.Found(emptyList())
        val item = ExtractedItem(
            kind = if (EVENT_WORDS.containsMatchIn(text)) SuggestionKind.EVENT else SuggestionKind.TASK,
            title = title,
            // The whole text: WhenResolver reads it again from the message's time and finds the same date.
            whenText = text,
            person = if (input.message.isFromMe) null else input.message.sender,
            confidence = CONFIDENCE,
        )
        return ExtractionResult.Found(listOf(item))
    }

    /** The sentence holding a to-do word (else the first), shortened at a word to fit a title. */
    private fun titleFrom(text: String): String? {
        val sentences = SENTENCE_END.split(text).map { it.replace(WHITESPACE, " ").trim(*EDGE_PUNCTUATION).trim() }.filter { it.isNotEmpty() }
        val sentence = sentences.firstOrNull(Prefilter::hasTaskWord) ?: sentences.firstOrNull() ?: return null
        val title = if (sentence.length <= ExtractionJson.MAX_TITLE_LENGTH) {
            sentence
        } else {
            sentence.take(ExtractionJson.MAX_TITLE_LENGTH).substringBeforeLast(' ').trimEnd(*EDGE_PUNCTUATION) + "…"
        }
        return title.takeIf { it.length >= ExtractionJson.MIN_TITLE_LENGTH }
    }

    companion object {
        /** Rules look at the start of a message only, like the AI (dates come early). */
        const val MAX_TEXT_LENGTH = 500

        /** Rules can't tell how sure they are; this marks them as a middling guess. */
        const val CONFIDENCE = 0.5

        private const val WORD = "[\\p{L}\\p{M}\\p{N}]"
        private val EVENT_WORDS = Regex(
            "(?iu)(?<!$WORD)(?:meet|meeting|appointment|interview|party|dinner|lunch|wedding|ceremony|flight)(?!$WORD)",
        )
        /** After ".", "!" or "?" and a space, or at a line break; so "Pay 10.50" stays one sentence. */
        private val SENTENCE_END = Regex("(?<=[.!?])\\s+|\\n+")
        private val WHITESPACE = Regex("\\s+")
        private val EDGE_PUNCTUATION = charArrayOf(',', ';', ':', '-', '–', '—', '.', '!', '?', ' ')
    }
}

package dev.maahdi.mavick.ai.eval

import dev.maahdi.mavick.ai.ExtractedItem
import dev.maahdi.mavick.ai.ExtractionResult
import dev.maahdi.mavick.ai.Prefilter
import dev.maahdi.mavick.ai.ResolvedWhen
import dev.maahdi.mavick.ai.TaskExtractor
import dev.maahdi.mavick.ai.WhenResolver
import dev.maahdi.mavick.time.WhenParser

/** What the pipeline did with one labelled message. */
data class EvalOutcome(
    val row: EvalRow,
    val passedPrefilter: Boolean,
    /** Null when the prefilter stopped it. */
    val result: ExtractionResult?,
    /** Each found item with its date, as a suggestion would have it. */
    val items: List<Pair<ExtractedItem, ResolvedWhen>> = emptyList(),
    /** How long the extractor took; 0 when it didn't run. */
    val millis: Long = 0,
) {
    /** A suggestion would have been made. */
    val suggested: Boolean get() = items.isNotEmpty()
}

/**
 * Runs labelled messages through the same steps as SuggestionQueue: prefilter, extractor, dates from
 * the message's time. Unlabelled messages are skipped, saving the phone the work.
 */
class EvalRunner(
    private val extractor: TaskExtractor,
    private val parser: WhenParser,
    private val elapsedMillis: () -> Long,
) {
    fun run(rows: List<EvalRow>, onProgress: (done: Int, of: Int) -> Unit = { _, _ -> }): List<EvalOutcome> {
        val labelled = rows.filter { it.expected.actionable != null }
        return labelled.mapIndexed { index, row ->
            outcome(row).also { onProgress(index + 1, labelled.size) }
        }
    }

    private fun outcome(row: EvalRow): EvalOutcome {
        if (!Prefilter.passes(row.input, parser)) return EvalOutcome(row, passedPrefilter = false, result = null)
        val started = elapsedMillis()
        val result = try {
            extractor.extract(row.input)
        } catch (e: Exception) {
            ExtractionResult.Failed(e.javaClass.simpleName)
        }
        val millis = elapsedMillis() - started
        val items = (result as? ExtractionResult.Found)?.items.orEmpty().map { item ->
            item to WhenResolver.resolve(item.whenText, row.input.sentAt, parser)
        }
        return EvalOutcome(row, passedPrefilter = true, result = result, items = items, millis = millis)
    }
}

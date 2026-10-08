package dev.maahdi.mavick.ai.eval

import dev.maahdi.mavick.ai.ModelTimings
import java.util.Locale

/**
 * Where the model's time goes, for the accuracy check's report: reading the prompt (instructions,
 * context and message) or writing the answer. Medians over every model call, retries included.
 * Numbers only, so the report stays safe to share.
 */
object EvalTimings {
    fun summary(timings: List<ModelTimings>): String = buildString {
        if (timings.isEmpty()) {
            appendLine("Where the time goes: the runtime gave no timings")
            return@buildString
        }
        appendLine("Where the time goes (medians of ${timings.size} model calls, retries included):")
        appendLine("  Reading the prompt: ${part(timings, ModelTimings::promptTokens, ModelTimings::promptTokensPerSecond)}")
        appendLine("  Writing the answer: ${part(timings, ModelTimings::answerTokens, ModelTimings::answerTokensPerSecond)}")
        val firstToken = median(timings.map { it.firstTokenSeconds }.filter { it > 0 })
        appendLine("  First word of the answer after: ${firstToken?.let { "${decimal(it)} s" } ?: "not measured"}")
    }

    /** "420 tokens at 18.3 tokens/s, 23.0 s"; seconds only from calls whose speed was measured. */
    private fun part(timings: List<ModelTimings>, tokens: (ModelTimings) -> Int, perSecond: (ModelTimings) -> Double): String {
        val count = median(timings.map { tokens(it).toDouble() })!!
        val speeds = timings.map(perSecond).filter { it > 0 }
        if (speeds.isEmpty()) return "${count.toInt()} tokens, speed not measured"
        val seconds = timings.filter { perSecond(it) > 0 }.map { tokens(it) / perSecond(it) }
        return "${count.toInt()} tokens at ${decimal(median(speeds)!!)} tokens/s, ${decimal(median(seconds)!!)} s"
    }

    /** The middle value, or the mean of the two middle ones; null for no values. */
    internal fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }

    private fun decimal(value: Double) = String.format(Locale.ENGLISH, "%.1f", value)
}

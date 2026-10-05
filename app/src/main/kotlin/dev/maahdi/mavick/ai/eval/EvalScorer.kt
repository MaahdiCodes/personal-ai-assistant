package dev.maahdi.mavick.ai.eval

import dev.maahdi.mavick.ai.ExtractionResult
import dev.maahdi.mavick.ai.TitleSimilarity
import java.util.Locale

/**
 * How well suggestions match your labels (docs/PLAN.md §7, Phase 3 targets: precision ≥ 85%,
 * recall ≥ 70%, ≤ 10 s per message). Counted per message: did a message with a task get a
 * suggestion, and did a message without one stay quiet.
 */
data class EvalReport(
    val labelled: Int,
    val truePositives: Int,
    val falsePositives: Int,
    val falseNegatives: Int,
    val trueNegatives: Int,
    /** Messages the prefilter stopped. */
    val prefilterSkipped: Int,
    /** Of those, messages you said have a task: the prefilter's misses. */
    val prefilterMissed: Int,
    /** Unusable answers, after the retry, and model errors. */
    val failures: Int,
    val titlesChecked: Int,
    val titlesMatched: Int,
    val datesChecked: Int,
    val datesMatched: Int,
    /** Times of the extractor's runs, in milliseconds, shortest first. */
    val millis: List<Long>,
) {
    /** Of the messages that got a suggestion, the share that has a task; null with no suggestions. */
    val precision: Double? get() = ratio(truePositives, truePositives + falsePositives)

    /** Of the messages with a task, the share that got a suggestion; null with no such messages. */
    val recall: Double? get() = ratio(truePositives, truePositives + falseNegatives)

    val medianMillis: Long? get() = percentile(50)

    val p90Millis: Long? get() = percentile(90)

    val maxMillis: Long? get() = millis.lastOrNull()

    private fun percentile(percent: Int): Long? =
        if (millis.isEmpty()) null else millis[((millis.size - 1) * percent / 100.0).toInt()]

    private fun ratio(part: Int, whole: Int): Double? = if (whole == 0) null else part.toDouble() / whole
}

object EvalScorer {
    const val PRECISION_TARGET = 0.85
    const val RECALL_TARGET = 0.70
    const val MILLIS_TARGET = 10_000L

    fun score(outcomes: List<EvalOutcome>): EvalReport {
        val labelled = outcomes.filter { it.row.expected.actionable != null }
        val hasTask = { outcome: EvalOutcome -> outcome.row.expected.actionable == true }
        val titleChecks = labelled.filter { hasTask(it) && it.suggested && it.row.expected.title != null }
        val dateChecks = labelled.filter { hasTask(it) && it.suggested && it.row.expected.whenDate != null }
        return EvalReport(
            labelled = labelled.size,
            truePositives = labelled.count { hasTask(it) && it.suggested },
            falsePositives = labelled.count { !hasTask(it) && it.suggested },
            falseNegatives = labelled.count { hasTask(it) && !it.suggested },
            trueNegatives = labelled.count { !hasTask(it) && !it.suggested },
            prefilterSkipped = labelled.count { !it.passedPrefilter },
            prefilterMissed = labelled.count { !it.passedPrefilter && hasTask(it) },
            failures = labelled.count { it.result is ExtractionResult.Failed },
            titlesChecked = titleChecks.size,
            titlesMatched = titleChecks.count { outcome -> outcome.items.any { (item, _) -> TitleSimilarity.similar(item.title, outcome.row.expected.title!!) } },
            datesChecked = dateChecks.size,
            datesMatched = dateChecks.count(::dateMatches),
            millis = labelled.filter { it.passedPrefilter }.map { it.millis }.sorted(),
        )
    }

    /** One of the message's items falls on the day you gave (at the time you gave, if any). */
    private fun dateMatches(outcome: EvalOutcome): Boolean {
        val expected = outcome.row.expected
        return outcome.items.any { (_, resolved) ->
            resolved.dueDate == expected.whenDate && (expected.whenTime == null || resolved.dueTime == expected.whenTime)
        }
    }

    /** The report as plain text: numbers only, never message content, so it is safe to share. */
    fun summary(report: EvalReport, modelName: String, loadMillis: Long?): String = buildString {
        appendLine("Mavick accuracy check: $modelName")
        appendLine("Labelled messages: ${report.labelled}")
        appendLine(
            "Prefilter: ${report.prefilterSkipped} stopped" +
                if (report.prefilterMissed > 0) ", of which ${report.prefilterMissed} had a task (prefilter misses)" else "",
        )
        appendLine(line("Precision", report.precision, "${report.truePositives} of ${report.truePositives + report.falsePositives} suggested had a task", PRECISION_TARGET))
        appendLine(line("Recall", report.recall, "${report.truePositives} of ${report.truePositives + report.falseNegatives} with a task got a suggestion", RECALL_TARGET))
        appendLine("Titles close to yours: ${report.titlesMatched} of ${report.titlesChecked}")
        appendLine("Dates right: ${report.datesMatched} of ${report.datesChecked}")
        appendLine("Unusable answers or errors: ${report.failures}")
        loadMillis?.let { appendLine("Model load: ${seconds(it)} s") }
        val median = report.medianMillis
        if (median == null) {
            appendLine("Time per message: nothing reached the AI")
        } else {
            val target = if ((report.p90Millis ?: 0) <= MILLIS_TARGET) "meets" else "misses"
            appendLine(
                "Time per message (AI only): median ${seconds(median)} s, 90% within ${seconds(report.p90Millis!!)} s, " +
                    "slowest ${seconds(report.maxMillis!!)} s ($target the ${seconds(MILLIS_TARGET)} s target)",
            )
        }
    }

    /**
     * One line per message: what you said and what the pipeline did, to look up misses by id in
     * your labelled file. Holds the AI's titles, which come from your messages: keep it private.
     */
    fun details(outcomes: List<EvalOutcome>): String = Csv.write(
        listOf(listOf("id", "expected_actionable", "suggested", "passed_prefilter", "problem", "titles", "when", "millis")) +
            outcomes.map { outcome ->
                listOf(
                    outcome.row.id,
                    when (outcome.row.expected.actionable) {
                        true -> "y"
                        false -> "n"
                        null -> ""
                    },
                    if (outcome.suggested) "y" else "n",
                    if (outcome.passedPrefilter) "y" else "n",
                    (outcome.result as? ExtractionResult.Failed)?.reason.orEmpty(),
                    outcome.items.joinToString(" | ") { it.first.title },
                    outcome.items.joinToString(" | ") { (_, resolved) -> listOfNotNull(resolved.dueDate, resolved.dueTime).joinToString(" ") },
                    outcome.millis.toString(),
                )
            },
    )

    private fun line(name: String, value: Double?, detail: String, target: Double): String {
        if (value == null) return "$name: not measurable ($detail)"
        val verdict = if (value >= target) "meets" else "misses"
        return "$name: ${percent(value)} ($detail; $verdict the ${percent(target)} target)"
    }

    private fun percent(value: Double) = String.format(Locale.ENGLISH, "%.1f%%", value * 100)

    private fun seconds(millis: Long) = String.format(Locale.ENGLISH, "%.1f", millis / 1000.0)
}

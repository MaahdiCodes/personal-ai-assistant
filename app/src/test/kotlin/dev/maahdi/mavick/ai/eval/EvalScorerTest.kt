package dev.maahdi.mavick.ai.eval

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.ai.ExtractedItem
import dev.maahdi.mavick.ai.ExtractionResult
import dev.maahdi.mavick.ai.ResolvedWhen
import dev.maahdi.mavick.data.suggestion.SuggestionKind
import dev.maahdi.mavick.testing.extractionInput
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Test

class EvalScorerTest {
    private val thursday = LocalDate.of(2026, 10, 8)

    private fun outcome(
        id: String,
        expected: Boolean?,
        suggestedTitle: String? = null,
        expectedTitle: String? = null,
        due: LocalDate? = null,
        dueTime: LocalTime? = null,
        expectedDate: LocalDate? = null,
        expectedTime: LocalTime? = null,
        prefilter: Boolean = true,
        failed: Boolean = false,
        millis: Long = 1_000,
    ): EvalOutcome {
        val row = EvalRow(id, extractionInput("text $id"), EvalExpectation(expected, expectedTitle, expectedDate, expectedTime))
        if (!prefilter) return EvalOutcome(row, passedPrefilter = false, result = null)
        if (failed) return EvalOutcome(row, true, ExtractionResult.Failed("bad_json"), millis = millis)
        val items = suggestedTitle?.let { listOf(ExtractedItem(SuggestionKind.TASK, it, null, null, 0.9) to ResolvedWhen(due, dueTime)) }.orEmpty()
        return EvalOutcome(row, true, ExtractionResult.Found(items.map { it.first }), items, millis)
    }

    @Test
    fun `precision and recall count messages with and without a task`() {
        val report = EvalScorer.score(
            listOf(
                outcome("tp1", true, "Pay rent"),
                outcome("tp2", true, "Call Sam"),
                outcome("tp3", true, "Buy milk"),
                outcome("fp", false, "Not a task"),
                outcome("fn", true),
                outcome("tn", false),
                outcome("unlabelled", null, "Whatever"),
            ),
        )

        assertThat(report.labelled).isEqualTo(6)
        assertThat(report.truePositives).isEqualTo(3)
        assertThat(report.falsePositives).isEqualTo(1)
        assertThat(report.falseNegatives).isEqualTo(1)
        assertThat(report.trueNegatives).isEqualTo(1)
        assertThat(report.precision).isEqualTo(0.75)
        assertThat(report.recall).isEqualTo(0.75)
    }

    @Test
    fun `with nothing suggested or nothing to find, the ratios can't be measured`() {
        val report = EvalScorer.score(listOf(outcome("tn", false)))

        assertThat(report.precision).isNull()
        assertThat(report.recall).isNull()
    }

    @Test
    fun `the prefilter's misses and the failures are counted`() {
        val report = EvalScorer.score(
            listOf(
                outcome("missed", true, prefilter = false),
                outcome("right to skip", false, prefilter = false),
                outcome("failed", true, failed = true),
            ),
        )

        assertThat(report.prefilterSkipped).isEqualTo(2)
        assertThat(report.prefilterMissed).isEqualTo(1)
        assertThat(report.failures).isEqualTo(1)
        assertThat(report.falseNegatives).isEqualTo(2)
    }

    @Test
    fun `titles are matched by their words, and dates by day and, when given, time`() {
        val report = EvalScorer.score(
            listOf(
                outcome("a", true, "Send the form to Sam", expectedTitle = "send form to sam", due = thursday, dueTime = LocalTime.of(17, 0), expectedDate = thursday, expectedTime = LocalTime.of(17, 0)),
                outcome("b", true, "Buy milk", expectedTitle = "Pay rent", due = thursday, expectedDate = thursday),
                outcome("c", true, "Call Rina", due = thursday, dueTime = LocalTime.of(9, 0), expectedDate = thursday, expectedTime = LocalTime.of(17, 0)),
                outcome("d", true, expectedTitle = "Not suggested at all"),
            ),
        )

        assertThat(report.titlesChecked).isEqualTo(2)
        assertThat(report.titlesMatched).isEqualTo(1)
        assertThat(report.datesChecked).isEqualTo(3)
        assertThat(report.datesMatched).isEqualTo(2)
    }

    @Test
    fun `times are the extractor's runs only, as median, 90 percent and slowest`() {
        val report = EvalScorer.score(
            (1..10).map { outcome("m$it", false, millis = it * 1_000L) } + outcome("skipped", false, prefilter = false),
        )

        assertThat(report.millis).hasSize(10)
        assertThat(report.medianMillis).isEqualTo(5_000)
        assertThat(report.p90Millis).isEqualTo(9_000)
        assertThat(report.maxMillis).isEqualTo(10_000)
    }

    @Test
    fun `the summary gives the numbers against the targets, and no message text`() {
        val report = EvalScorer.score(
            listOf(
                outcome("tp", true, "Send Sam the secret form", millis = 6_100),
                outcome("fn", true, millis = 7_000),
                outcome("tn", false, millis = 800),
            ),
        )

        val summary = EvalScorer.summary(report, "gemma3-1b-it-int4.litertlm", loadMillis = 4_200)

        assertThat(summary).contains("Mavick accuracy check: gemma3-1b-it-int4.litertlm")
        assertThat(summary).contains("Precision: 100.0% (1 of 1 suggested had a task; meets the 85.0% target)")
        assertThat(summary).contains("Recall: 50.0% (1 of 2 with a task got a suggestion; misses the 70.0% target)")
        assertThat(summary).contains("Model load: 4.2 s")
        assertThat(summary).contains("median 6.1 s")
        assertThat(summary).doesNotContain("secret")
        assertThat(summary).doesNotContain("text tp")
    }

    @Test
    fun `the details list each message's result for looking up misses`() {
        val details = Csv.read(EvalScorer.details(listOf(outcome("a", true, "Pay rent", due = thursday), outcome("b", false, prefilter = false))))

        assertThat(details[0]).containsExactly("id", "expected_actionable", "suggested", "passed_prefilter", "problem", "titles", "when", "millis").inOrder()
        assertThat(details[1]).containsExactly("a", "y", "y", "y", "", "Pay rent", "2026-10-08", "1000").inOrder()
        assertThat(details[2]).containsExactly("b", "n", "n", "n", "", "", "", "0").inOrder()
    }
}

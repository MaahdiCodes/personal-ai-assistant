package dev.maahdi.mavick.ai.eval

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.ai.ExtractionResult
import dev.maahdi.mavick.ai.GemmaExtractor
import dev.maahdi.mavick.ai.GenerationRequest
import dev.maahdi.mavick.ai.LanguageModel
import dev.maahdi.mavick.ai.RuleExtractor
import dev.maahdi.mavick.testing.FakeElapsed
import dev.maahdi.mavick.testing.FakeLanguageModel
import dev.maahdi.mavick.testing.extractionInput
import dev.maahdi.mavick.time.WhenParser
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Test

class EvalRunnerTest {
    private val parser = WhenParser()
    private val elapsed = FakeElapsed()

    private fun row(id: String, text: String, actionable: Boolean?) = EvalRow(id, extractionInput(text), EvalExpectation(actionable))

    @Test
    fun `labelled messages go through the prefilter, the extractor and the dates, timed`() {
        val model = object : LanguageModel {
            override fun generate(request: GenerationRequest): String {
                elapsed.millis += 6_000
                return """{"actionable": true, "items": [{"kind": "task", "title": "Send Sam the form", "when_text": "Thursday 5pm", "person": "Sam", "confidence": 0.9}]}"""
            }

            override fun close() = Unit
        }
        val runner = EvalRunner(GemmaExtractor(model), parser, elapsed)

        val outcome = runner.run(listOf(row("a", "Can you send me the form by Thursday 5pm?", true))).single()

        assertThat(outcome.passedPrefilter).isTrue()
        assertThat(outcome.suggested).isTrue()
        assertThat(outcome.millis).isEqualTo(6_000)
        val (item, resolved) = outcome.items.single()
        assertThat(item.title).isEqualTo("Send Sam the form")
        assertThat(resolved.dueDate).isEqualTo(LocalDate.of(2026, 10, 8))
        assertThat(resolved.dueTime).isEqualTo(LocalTime.of(17, 0))
    }

    @Test
    fun `the prefilter stops small talk before the extractor, and unlabelled messages are skipped`() {
        val model = FakeLanguageModel()
        val runner = EvalRunner(GemmaExtractor(model), parser, elapsed)
        val progress = mutableListOf<Pair<Int, Int>>()

        val outcomes = runner.run(listOf(row("a", "haha nice", false), row("b", "Pay the rent tomorrow", null))) { done, of -> progress += done to of }

        assertThat(outcomes.map { it.row.id }).containsExactly("a")
        assertThat(outcomes.single().passedPrefilter).isFalse()
        assertThat(outcomes.single().result).isNull()
        assertThat(model.requests).isEmpty()
        assertThat(progress).containsExactly(1 to 1)
    }

    @Test
    fun `a model error counts as a failed message, not a crash of the check`() {
        val model = FakeLanguageModel().apply { failure = IllegalStateException("engine broke") }

        val outcome = EvalRunner(GemmaExtractor(model), parser, elapsed).run(listOf(row("a", "Pay the rent tomorrow", true))).single()

        assertThat(outcome.result).isEqualTo(ExtractionResult.Failed("IllegalStateException"))
        assertThat(outcome.suggested).isFalse()
    }

    @Test
    fun `the rules can be checked the same way`() {
        val outcome = EvalRunner(RuleExtractor { parser }, parser, elapsed).run(listOf(row("a", "Pay the rent tomorrow", true))).single()

        assertThat(outcome.suggested).isTrue()
        assertThat(outcome.items.single().second.dueDate).isEqualTo(LocalDate.of(2026, 10, 6))
    }
}

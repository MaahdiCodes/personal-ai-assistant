package dev.maahdi.mavick.ai

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.suggestion.SuggestionKind
import dev.maahdi.mavick.data.suggestion.SuggestionSource
import dev.maahdi.mavick.testing.FakeLanguageModel
import dev.maahdi.mavick.testing.extractionInput
import org.junit.Assert.assertThrows
import org.junit.Test

class GemmaExtractorTest {
    private val valid = """{"actionable": true, "items": [{"kind": "event", "title": "Go to the party", "when_text": "5pm", "person": "Sam", "confidence": 0.9}]}"""
    private val input = extractionInput("Party starts at 5pm, come!")

    @Test
    fun `a valid first answer becomes the items`() {
        val model = FakeLanguageModel(valid)

        val result = GemmaExtractor(model).extract(input)

        assertThat(result).isEqualTo(
            ExtractionResult.Found(listOf(ExtractedItem(SuggestionKind.EVENT, "Go to the party", "5pm", "Sam", 0.9))),
        )
        val request = model.requests.single()
        assertThat(request.systemInstruction).isEqualTo(Prompt.SYSTEM)
        assertThat(request.prompt).isEqualTo(Prompt.forMessage(input))
        assertThat(request.jsonSchema).isEqualTo(ExtractionJson.SCHEMA)
        assertThat(request.varied).isFalse()
    }

    @Test
    fun `an unusable answer is retried once, with some variety`() {
        val model = FakeLanguageModel("{\"actionable\": true, \"items\": [{\"kind\": \"todo\"", valid)

        val result = GemmaExtractor(model).extract(input)

        assertThat(result).isInstanceOf(ExtractionResult.Found::class.java)
        assertThat(model.requests.map { it.varied }).containsExactly(false, true).inOrder()
        assertThat(model.requests.map { it.prompt }.distinct()).hasSize(1)
    }

    @Test
    fun `two unusable answers fail with the last problem, and no more tries`() {
        val model = FakeLanguageModel("not JSON", """{"actionable": true, "items": [{"kind": "todo", "title": "x", "confidence": 1}]}""")

        val result = GemmaExtractor(model).extract(input)

        assertThat(result).isEqualTo(ExtractionResult.Failed("bad_kind"))
        assertThat(model.requests).hasSize(GemmaExtractor.MAX_ATTEMPTS)
    }

    @Test
    fun `nothing to do is a valid answer, not a failure`() {
        val model = FakeLanguageModel("""{"actionable": false, "items": []}""")

        assertThat(GemmaExtractor(model).extract(input)).isEqualTo(ExtractionResult.Found(emptyList()))
        assertThat(model.requests).hasSize(1)
    }

    @Test
    fun `a failing model is reported to the caller, not hidden`() {
        val model = FakeLanguageModel().apply { failure = IllegalStateException("engine broke") }

        assertThrows(IllegalStateException::class.java) { GemmaExtractor(model).extract(input) }
    }

    @Test
    fun `its suggestions are marked as the model's`() {
        assertThat(GemmaExtractor(FakeLanguageModel()).source).isEqualTo(SuggestionSource.MODEL)
    }
}

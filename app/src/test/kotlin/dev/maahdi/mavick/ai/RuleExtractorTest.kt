package dev.maahdi.mavick.ai

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.suggestion.SuggestionKind
import dev.maahdi.mavick.data.suggestion.SuggestionSource
import dev.maahdi.mavick.testing.extractionInput
import dev.maahdi.mavick.time.WhenParser
import org.junit.Test

class RuleExtractorTest {
    private val extractor = RuleExtractor { WhenParser() }

    private fun items(text: String, app: SourceApp = SourceApp.WHATSAPP, isFromMe: Boolean = false): List<ExtractedItem> =
        (extractor.extract(extractionInput(text, app = app, isFromMe = isFromMe)) as ExtractionResult.Found).items

    @Test
    fun `a date and a to-do word make a task, titled without the date words`() {
        val item = items("Can you send me the report by Friday?").single()

        assertThat(item.kind).isEqualTo(SuggestionKind.TASK)
        assertThat(item.title).isEqualTo("Can you send me the report")
        // WhenResolver reads the whole text again, from the message's time.
        assertThat(item.whenText).isEqualTo("Can you send me the report by Friday?")
        assertThat(item.person).isEqualTo("Sam")
        assertThat(item.confidence).isEqualTo(RuleExtractor.CONFIDENCE)
    }

    @Test
    fun `a meeting at a time is an event`() {
        val item = items("Meeting tomorrow at 5pm").single()

        assertThat(item.kind).isEqualTo(SuggestionKind.EVENT)
        assertThat(item.title).isEqualTo("Meeting")
    }

    @Test
    fun `without a date, or without a to-do word, nothing is suggested`() {
        assertThat(items("Please send the report")).isEmpty()
        assertThat(items("I was there on Friday")).isEmpty()
        assertThat(items("ok")).isEmpty()
    }

    @Test
    fun `the title is the sentence with the to-do, not a greeting before it`() {
        assertThat(items("Hi Sam! Can you pay the rent tomorrow?").single().title).isEqualTo("Can you pay the rent")
    }

    @Test
    fun `a price stays in the title`() {
        assertThat(items("Pay 10.50 tomorrow").single().title).isEqualTo("Pay 10.50")
    }

    @Test
    fun `your own promise has no other person`() {
        val item = items("I'll call you tomorrow", isFromMe = true).single()

        assertThat(item.title).isEqualTo("I'll call you")
        assertThat(item.person).isNull()
    }

    @Test
    fun `a Keep reminder becomes a reminder titled with its first line`() {
        val item = items("Buy milk\nand eggs", app = SourceApp.KEEP).single()

        assertThat(item.kind).isEqualTo(SuggestionKind.REMINDER)
        assertThat(item.title).isEqualTo("Buy milk")
        assertThat(item.whenText).isNull()
        assertThat(item.person).isNull()
    }

    @Test
    fun `a long title is cut at a word and marked`() {
        val words = List(40) { "word$it" }.joinToString(" ")

        val title = items("Please $words tomorrow").single().title

        assertThat(title.length).isAtMost(ExtractionJson.MAX_TITLE_LENGTH + 1)
        assertThat(title).endsWith("…")
        assertThat(title.removeSuffix("…").split(" ").last()).matches("word\\d+")
    }

    @Test
    fun `its suggestions are marked as the rules'`() {
        assertThat(extractor.source).isEqualTo(SuggestionSource.RULES)
    }
}

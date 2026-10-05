package dev.maahdi.mavick.ai

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.ai.ExtractionJson.Parsed.Invalid
import dev.maahdi.mavick.ai.ExtractionJson.Parsed.Valid
import dev.maahdi.mavick.data.suggestion.SuggestionKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Test

class ExtractionJsonTest {
    private fun item(
        kind: String = "\"task\"",
        title: String = "\"Send the signed form to Sam\"",
        whenText: String = "\"Thursday 5pm\"",
        person: String = "\"Sam\"",
        confidence: String = "0.86",
    ) = """{"kind": $kind, "title": $title, "when_text": $whenText, "person": $person, "confidence": $confidence}"""

    private fun answer(vararg items: String, actionable: Boolean = true) =
        """{"actionable": $actionable, "items": [${items.joinToString(", ")}]}"""

    @Test
    fun `a valid answer becomes items`() {
        val parsed = ExtractionJson.parse(answer(item()))

        assertThat(parsed).isEqualTo(
            Valid(listOf(ExtractedItem(SuggestionKind.TASK, "Send the signed form to Sam", "Thursday 5pm", "Sam", 0.86))),
        )
    }

    @Test
    fun `not actionable means no items, even if some are listed`() {
        assertThat(ExtractionJson.parse(answer(item(), actionable = false))).isEqualTo(Valid(emptyList()))
        assertThat(ExtractionJson.parse("""{"actionable": true, "items": []}""")).isEqualTo(Valid(emptyList()))
        assertThat(ExtractionJson.parse("""{"actionable": true}""")).isEqualTo(Valid(emptyList()))
    }

    @Test
    fun `text around the JSON is ignored`() {
        val fenced = "```json\n${answer(item())}\n```"
        val chatty = "Sure! Here it is: ${answer(item())} Hope that helps."

        assertThat(ExtractionJson.parse(fenced)).isInstanceOf(Valid::class.java)
        assertThat(ExtractionJson.parse(chatty)).isInstanceOf(Valid::class.java)
    }

    @Test
    fun `no JSON or broken JSON is invalid`() {
        assertThat(ExtractionJson.parse("I don't know")).isEqualTo(Invalid("no_json"))
        assertThat(ExtractionJson.parse("""{"actionable": true, "items": [""")).isEqualTo(Invalid("no_json"))
        assertThat(ExtractionJson.parse("""{"actionable" true}""")).isEqualTo(Invalid("bad_json"))
        assertThat(ExtractionJson.parse("""{ not json at all }""")).isEqualTo(Invalid("bad_json"))
    }

    @Test
    fun `actionable must be a yes or no`() {
        assertThat(ExtractionJson.parse("""{"items": []}""")).isEqualTo(Invalid("bad_actionable"))
        assertThat(ExtractionJson.parse("""{"actionable": "maybe", "items": []}""")).isEqualTo(Invalid("bad_actionable"))
    }

    @Test
    fun `more than three items is invalid`() {
        val four = answer(item(title = "\"One thing\""), item(title = "\"Two thing\""), item(title = "\"Three thing\""), item(title = "\"Four thing\""))

        assertThat(ExtractionJson.parse(four)).isEqualTo(Invalid("too_many_items"))
    }

    @Test
    fun `items must be a list of objects`() {
        assertThat(ExtractionJson.parse("""{"actionable": true, "items": "Send it"}""")).isEqualTo(Invalid("bad_items"))
        assertThat(ExtractionJson.parse("""{"actionable": true, "items": ["Send it"]}""")).isEqualTo(Invalid("bad_item"))
    }

    @Test
    fun `the kind must be task, event or reminder, in any case`() {
        assertThat(ExtractionJson.parse(answer(item(kind = "\"todo\"")))).isEqualTo(Invalid("bad_kind"))
        assertThat(ExtractionJson.parse(answer(item(kind = "\"EVENT\""))).items().single().kind).isEqualTo(SuggestionKind.EVENT)
    }

    @Test
    fun `the title must be 3 to 120 characters, with spaces tidied`() {
        assertThat(ExtractionJson.parse(answer(item(title = "\"Go\"")))).isEqualTo(Invalid("bad_title"))
        assertThat(ExtractionJson.parse(answer(item(title = "\"${"a".repeat(121)}\"")))).isEqualTo(Invalid("bad_title"))
        assertThat(ExtractionJson.parse(answer(item(title = "null")))).isEqualTo(Invalid("bad_title"))
        assertThat(ExtractionJson.parse(answer(item(title = "\"  Send   the\\nform \""))).items().single().title).isEqualTo("Send the form")
        assertThat(ExtractionJson.parse(answer(item(title = "\"${"a".repeat(120)}\""))).items()).hasSize(1)
    }

    @Test
    fun `when and person may be missing, null, or words meaning none`() {
        val items = (ExtractionJson.parse(
            answer(
                item(title = "\"First thing\"", whenText = "null", person = "\"none\""),
                item(title = "\"Second thing\"", whenText = "\"null\"", person = "\"\""),
                """{"kind": "task", "title": "Third thing", "confidence": 0.5}""",
            ),
        ) as Valid).items

        assertThat(items.map { it.whenText }).containsExactly(null, null, null)
        assertThat(items.map { it.person }).containsExactly(null, null, null)
    }

    @Test
    fun `when and person that are too long or not text are invalid`() {
        assertThat(ExtractionJson.parse(answer(item(whenText = "\"${"x".repeat(101)}\"")))).isEqualTo(Invalid("bad_when"))
        assertThat(ExtractionJson.parse(answer(item(whenText = "5")))).isEqualTo(Invalid("bad_when"))
        assertThat(ExtractionJson.parse(answer(item(person = "\"${"x".repeat(81)}\"")))).isEqualTo(Invalid("bad_person"))
    }

    @Test
    fun `confidence must be a number from 0 to 1, also when written as text`() {
        assertThat(ExtractionJson.parse(answer(item(confidence = "1.2")))).isEqualTo(Invalid("bad_confidence"))
        assertThat(ExtractionJson.parse(answer(item(confidence = "-0.1")))).isEqualTo(Invalid("bad_confidence"))
        assertThat(ExtractionJson.parse(answer(item(confidence = "\"high\"")))).isEqualTo(Invalid("bad_confidence"))
        assertThat(ExtractionJson.parse("""{"actionable": true, "items": [{"kind": "task", "title": "Do it"}]}""")).isEqualTo(Invalid("bad_confidence"))
        assertThat(ExtractionJson.parse(answer(item(confidence = "\"0.8\""))).items().single().confidence).isEqualTo(0.8)
        assertThat(ExtractionJson.parse(answer(item(confidence = "0"))).items().single().confidence).isEqualTo(0.0)
        assertThat(ExtractionJson.parse(answer(item(confidence = "1"))).items().single().confidence).isEqualTo(1.0)
    }

    @Test
    fun `the same title twice is one item, and extra fields are ignored`() {
        val parsed = ExtractionJson.parse(
            answer(
                item(title = "\"Pay the rent\""),
                """{"kind": "task", "title": "pay the rent", "when_text": null, "person": null, "confidence": 0.4, "priority": "high"}""",
            ),
        )

        assertThat(parsed.items().map { it.title }).containsExactly("Pay the rent")
    }

    @Test
    fun `the schema given to the model is valid JSON with the same limits`() {
        val schema = Json.parseToJsonElement(ExtractionJson.SCHEMA)

        assertThat(schema).isInstanceOf(JsonObject::class.java)
        assertThat(ExtractionJson.SCHEMA).contains("\"maxItems\": ${ExtractionJson.MAX_ITEMS}")
        assertThat(ExtractionJson.SCHEMA).contains("\"maxLength\": ${ExtractionJson.MAX_TITLE_LENGTH}")
    }

    private fun ExtractionJson.Parsed.items(): List<ExtractedItem> = (this as Valid).items
}

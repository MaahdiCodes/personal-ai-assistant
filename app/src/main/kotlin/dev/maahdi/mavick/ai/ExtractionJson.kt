package dev.maahdi.mavick.ai

import dev.maahdi.mavick.data.suggestion.SuggestionKind
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * The AI's answer format, and the checks every answer must pass before it becomes a suggestion
 * (docs/PLAN.md §5.3, step 4). An answer that fails is retried once, then dropped and counted.
 *
 * [SCHEMA] also goes to the model as a constraint, so a working model can only produce this shape;
 * the checks stay because constrained decoding can still stop mid-answer.
 */
object ExtractionJson {
    const val MAX_ITEMS = 3
    const val MIN_TITLE_LENGTH = 3
    const val MAX_TITLE_LENGTH = 120
    const val MAX_WHEN_LENGTH = 100
    const val MAX_PERSON_LENGTH = 80

    val SCHEMA: String = """
        {"type": "object",
         "properties": {
           "actionable": {"type": "boolean"},
           "items": {"type": "array", "maxItems": $MAX_ITEMS,
             "items": {"type": "object",
               "properties": {
                 "kind": {"type": "string", "enum": ["task", "event", "reminder"]},
                 "title": {"type": "string", "minLength": $MIN_TITLE_LENGTH, "maxLength": $MAX_TITLE_LENGTH},
                 "when_text": {"type": ["string", "null"], "maxLength": $MAX_WHEN_LENGTH},
                 "person": {"type": ["string", "null"], "maxLength": $MAX_PERSON_LENGTH},
                 "confidence": {"type": "number", "minimum": 0, "maximum": 1}
               },
               "required": ["kind", "title", "when_text", "person", "confidence"],
               "additionalProperties": false}}
         },
         "required": ["actionable", "items"],
         "additionalProperties": false}
    """.trimIndent()

    sealed interface Parsed {
        data class Valid(val items: List<ExtractedItem>) : Parsed

        /** [reason] is a short code for the counters, never content. */
        data class Invalid(val reason: String) : Parsed
    }

    /**
     * Reads the model's reply. Text around the JSON (such as a ```json fence) is ignored, and so are
     * extra fields; missing or wrong values make the whole answer invalid.
     */
    fun parse(reply: String): Parsed {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return Parsed.Invalid("no_json")
        val root = try {
            JSON.parseToJsonElement(reply.substring(start, end + 1)) as? JsonObject
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } ?: return Parsed.Invalid("bad_json")

        val actionable = (root["actionable"] as? JsonPrimitive)?.booleanOrNull ?: return Parsed.Invalid("bad_actionable")
        if (!actionable) return Parsed.Valid(emptyList())
        val items = when (val value = root["items"]) {
            null, JsonNull -> return Parsed.Valid(emptyList())
            is JsonArray -> value
            else -> return Parsed.Invalid("bad_items")
        }
        if (items.size > MAX_ITEMS) return Parsed.Invalid("too_many_items")
        val parsed = items.map { element -> item(element) ?: return Parsed.Invalid(problem(element)) }
        return Parsed.Valid(parsed.distinctBy { it.title.lowercase() })
    }

    private fun item(element: JsonElement): ExtractedItem? {
        val fields = element as? JsonObject ?: return null
        val kind = text(fields["kind"])?.let(::kindOf) ?: return null
        val title = text(fields["title"])?.takeIf { it.length in MIN_TITLE_LENGTH..MAX_TITLE_LENGTH } ?: return null
        val whenText = optionalText(fields["when_text"], MAX_WHEN_LENGTH) ?: return null
        val person = optionalText(fields["person"], MAX_PERSON_LENGTH) ?: return null
        val confidence = number(fields["confidence"])?.takeIf { it.isFinite() && it in 0.0..1.0 } ?: return null
        return ExtractedItem(kind, title, whenText.value, person.value, confidence)
    }

    /** Which check an item failed, for the counters. */
    private fun problem(element: JsonElement): String {
        val fields = element as? JsonObject ?: return "bad_item"
        return when {
            text(fields["kind"])?.let(::kindOf) == null -> "bad_kind"
            text(fields["title"])?.length !in MIN_TITLE_LENGTH..MAX_TITLE_LENGTH -> "bad_title"
            optionalText(fields["when_text"], MAX_WHEN_LENGTH) == null -> "bad_when"
            optionalText(fields["person"], MAX_PERSON_LENGTH) == null -> "bad_person"
            else -> "bad_confidence"
        }
    }

    private fun kindOf(text: String): SuggestionKind? = SuggestionKind.entries.firstOrNull { it.name.equals(text, ignoreCase = true) }

    /** A string with its spaces tidied, or null if it isn't one. */
    private fun text(element: JsonElement?): String? {
        val primitive = element as? JsonPrimitive ?: return null
        if (!primitive.isString) return null
        return primitive.content.replace(WHITESPACE, " ").trim()
    }

    /** A present-or-null value: null itself, or a string "null" or "none", counts as absent. */
    private class Optional(val value: String?)

    /** Null when the value is invalid (not a string, or too long). */
    private fun optionalText(element: JsonElement?, maxLength: Int): Optional? {
        if (element == null || element is JsonNull) return Optional(null)
        val value = text(element) ?: return null
        if (value.isEmpty() || value.lowercase() in ABSENT_WORDS) return Optional(null)
        return if (value.length <= maxLength) Optional(value) else null
    }

    /** A number, also when written as a string ("0.8"). */
    private fun number(element: JsonElement?): Double? {
        val primitive = element as? JsonPrimitive ?: return null
        return primitive.doubleOrNull ?: primitive.content.trim().toDoubleOrNull()
    }

    private val ABSENT_WORDS = setOf("null", "none", "n/a", "na", "unknown")
    private val WHITESPACE = Regex("\\s+")
    private val JSON = Json { isLenient = true }
}

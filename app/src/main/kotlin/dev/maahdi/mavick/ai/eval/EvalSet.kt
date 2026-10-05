package dev.maahdi.mavick.ai.eval

import dev.maahdi.mavick.ai.ChatLine
import dev.maahdi.mavick.ai.ExtractionInput
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.message.MessageEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** What you said a message holds; null fields weren't filled in. */
data class EvalExpectation(
    /** Null: not labelled yet, so left out of the scores. */
    val actionable: Boolean?,
    val title: String? = null,
    val whenDate: LocalDate? = null,
    /** Only with [whenDate]; null means "any time that day". */
    val whenTime: LocalTime? = null,
)

/** One labelled message of the accuracy check. */
data class EvalRow(val id: String, val input: ExtractionInput, val expected: EvalExpectation)

/** A labelled file, and the lines that couldn't be read (line number and what's wrong, never content). */
data class EvalSetRead(val rows: List<EvalRow>, val problems: List<String>)

/**
 * The accuracy check's file (docs/PLAN.md §8, AI eval): your messages exported by Mavick, labelled by
 * you on the PC, and read again by the on-phone runner. One message per line, with up to three
 * earlier messages of its chat as context, as the AI sees them.
 *
 * Holds real messages, so it lives only in the git-ignored eval/private folder (eval/README.md).
 */
object EvalSet {
    const val CONTEXT_COLUMNS = 3

    val HEADER: List<String> = listOf(
        "id", "expected_actionable", "expected_title", "expected_when", "label_note",
        "app", "chat", "is_group", "sender", "from_me", "partial", "posted_at", "text",
    ) + (1..CONTEXT_COLUMNS).flatMap { listOf("context_${it}_sender", "context_${it}_from_me", "context_${it}_text") }

    /** Messages (each with its earlier ones, oldest first) as an unlabelled file. */
    fun write(messages: List<Pair<MessageEntity, List<MessageEntity>>>, zone: ZoneId): String {
        val rows = messages.map { (message, earlier) ->
            listOf(
                message.id, "", "", "", "",
                message.app.name,
                message.conversationTitle,
                yesNo(message.isGroup),
                message.sender.orEmpty(),
                yesNo(message.isFromMe),
                yesNo(message.cutShort),
                message.postedAt.atZone(zone).toOffsetDateTime().toString(),
                message.text,
            ) + earlier.takeLast(CONTEXT_COLUMNS).let { lines ->
                (0 until CONTEXT_COLUMNS).flatMap { index ->
                    lines.getOrNull(index)?.let { listOf(it.sender.orEmpty(), yesNo(it.isFromMe), it.text) } ?: listOf("", "", "")
                }
            }
        }
        return Csv.write(listOf(HEADER) + rows)
    }

    /** Reads a labelled file; times are read in [zone]. */
    fun read(text: String, zone: ZoneId): EvalSetRead {
        val lines = Csv.read(text)
        val header = lines.firstOrNull() ?: return EvalSetRead(emptyList(), listOf("The file is empty."))
        val column = HEADER.associateWith { name -> header.indexOf(name) }
        val missing = column.filterValues { it < 0 }.keys
        if (missing.isNotEmpty()) return EvalSetRead(emptyList(), listOf("Missing columns: ${missing.joinToString()}"))

        val rows = mutableListOf<EvalRow>()
        val problems = mutableListOf<String>()
        lines.drop(1).forEachIndexed { index, cells ->
            if (cells.all { it.isBlank() }) return@forEachIndexed
            val lineNumber = index + 2
            val get = { name: String -> cells.getOrNull(column.getValue(name)).orEmpty().trim() }
            try {
                rows += row(get, zone)
            } catch (e: IllegalArgumentException) {
                problems += "Line $lineNumber: ${e.message}"
            }
        }
        return EvalSetRead(rows, problems)
    }

    private fun row(get: (String) -> String, zone: ZoneId): EvalRow {
        val app = SourceApp.entries.firstOrNull { it.name == get("app") } ?: throw IllegalArgumentException("unknown app")
        val sentAt = try {
            OffsetDateTime.parse(get("posted_at")).atZoneSameInstant(zone).toLocalDateTime()
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("posted_at must be a time like 2026-10-05T10:00+06:00")
        }
        val (whenDate, whenTime) = whenOf(get("expected_when"))
        val fromMe = flag(get("from_me"), "from_me") == true
        val earlier = (1..CONTEXT_COLUMNS).mapNotNull { index ->
            val text = get("context_${index}_text")
            if (text.isEmpty()) return@mapNotNull null
            val contextFromMe = flag(get("context_${index}_from_me"), "context_${index}_from_me") == true
            ChatLine(sender = get("context_${index}_sender").takeUnless { it.isEmpty() || contextFromMe }, isFromMe = contextFromMe, text = text)
        }
        return EvalRow(
            id = get("id").ifEmpty { throw IllegalArgumentException("no id") },
            input = ExtractionInput(
                app = app,
                chatTitle = get("chat"),
                isGroup = flag(get("is_group"), "is_group") == true,
                earlier = earlier,
                message = ChatLine(sender = get("sender").takeUnless { it.isEmpty() || fromMe }, isFromMe = fromMe, text = get("text")),
                partial = flag(get("partial"), "partial") == true,
                sentAt = sentAt,
            ),
            expected = EvalExpectation(
                actionable = flag(get("expected_actionable"), "expected_actionable"),
                title = get("expected_title").ifEmpty { null },
                whenDate = whenDate,
                whenTime = whenTime,
            ),
        )
    }

    /** y / yes / 1 / true / x, or n / no / 0 / false; blank is "not said". */
    private fun flag(value: String, column: String): Boolean? = when (value.lowercase()) {
        "" -> null
        "y", "yes", "1", "true", "x" -> true
        "n", "no", "0", "false" -> false
        else -> throw IllegalArgumentException("$column must be y or n")
    }

    /** "2026-10-08", "2026-10-08 17:00" or "2026-10-08T17:00". */
    private fun whenOf(value: String): Pair<LocalDate?, LocalTime?> {
        if (value.isEmpty()) return null to null
        return try {
            if (value.length <= DATE_LENGTH) {
                LocalDate.parse(value) to null
            } else {
                LocalDateTime.parse(value.replace(' ', 'T')).let { it.toLocalDate() to it.toLocalTime() }
            }
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("expected_when must look like 2026-10-08 or 2026-10-08 17:00")
        }
    }

    private fun yesNo(value: Boolean) = if (value) "y" else "n"

    private const val DATE_LENGTH = 10
}

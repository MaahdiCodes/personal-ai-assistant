package dev.maahdi.mavick.importers

import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/** One checklist line of a Keep note. */
data class ChecklistItem(val text: String, val checked: Boolean)

/** A Google Keep note from a Takeout export. */
data class KeepNote(
    /** The note's file in the export: unique, so it identifies the note on the import screen. */
    val fileName: String,
    val title: String,
    val text: String,
    val checklist: List<ChecklistItem>,
    val labels: List<String>,
    val editedAt: Instant?,
    val pinned: Boolean,
    val archived: Boolean,
) {
    /** A checklist with every item ticked: probably done already. */
    val allChecked: Boolean get() = checklist.isNotEmpty() && checklist.all { it.checked }

    /** The body as plain text: the note's text, or its checklist as "☐ item" / "☑ item" lines. */
    fun body(): String = listOf(
        text.trim(),
        checklist.joinToString("\n") { item -> (if (item.checked) "☑ " else "☐ ") + item.text.trim() },
    ).filter { it.isNotEmpty() }.joinToString("\n")
}

sealed interface TakeoutResult {
    /** [skipped]: notes in the bin, empty notes, and files that couldn't be read. */
    data class Notes(val notes: List<KeepNote>, val skipped: Int) : TakeoutResult

    /** The file isn't a readable .zip, or it is far bigger than a Keep export. */
    data class Failed(val problem: TakeoutProblem) : TakeoutResult
}

enum class TakeoutProblem { NOT_A_ZIP, TOO_BIG }

/**
 * Reads Google Keep notes from a Google Takeout export (docs/PLAN.md §3, Keep route B). Google
 * offers no Keep API for personal accounts; Takeout gives each note as a JSON file in a "Keep"
 * folder of a .zip. Everything else in the export (other Google services, attachments) is skipped.
 *
 * Built against Takeout's documented layout and checked against made-up exports. The reminder times
 * of notes are not in the export, so dates come from the notes' words, as with sharing.
 *
 * Limits keep a damaged or hostile file from filling the phone's memory: [MAX_NOTE_BYTES] per
 * note, [MAX_NOTES] notes and [MAX_TOTAL_BYTES] read in all.
 */
object KeepTakeout {
    const val MAX_NOTE_BYTES = 1 * 1024 * 1024
    const val MAX_NOTES = 5_000
    const val MAX_TOTAL_BYTES = 64L * 1024 * 1024

    /** The limits are parameters only so tests can reach them with small files. */
    fun read(
        zip: InputStream,
        maxNotes: Int = MAX_NOTES,
        maxNoteBytes: Int = MAX_NOTE_BYTES,
        maxTotalBytes: Long = MAX_TOTAL_BYTES,
    ): TakeoutResult {
        val notes = mutableListOf<KeepNote>()
        var skipped = 0
        var total = 0L
        var sawEntry = false
        try {
            ZipInputStream(zip.buffered()).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    sawEntry = true
                    if (entry.isDirectory || !isKeepNote(entry.name)) continue
                    if (notes.size >= maxNotes) return TakeoutResult.Failed(TakeoutProblem.TOO_BIG)
                    val bytes = readLimited(input, maxNoteBytes)
                    if (bytes == null) {
                        skipped++
                        continue
                    }
                    total += bytes.size
                    if (total > maxTotalBytes) return TakeoutResult.Failed(TakeoutProblem.TOO_BIG)
                    val note = parseNote(bytes.toString(Charsets.UTF_8), entry.name.substringAfterLast('/'))
                    if (note == null) skipped++ else notes += note
                }
            }
        } catch (e: ZipException) {
            return TakeoutResult.Failed(TakeoutProblem.NOT_A_ZIP)
        } catch (e: IOException) {
            return TakeoutResult.Failed(TakeoutProblem.NOT_A_ZIP)
        }
        if (!sawEntry) return TakeoutResult.Failed(TakeoutProblem.NOT_A_ZIP)
        return TakeoutResult.Notes(notes.sortedWith(compareByDescending<KeepNote> { it.pinned }.thenByDescending { it.editedAt }), skipped)
    }

    /**
     * One note's JSON. Null for a note in the bin, an empty note, or JSON that isn't a note.
     * Missing fields are fine: Keep leaves out what a note doesn't have.
     */
    fun parseNote(json: String, fileName: String): KeepNote? {
        val fields = try {
            JSON.parseToJsonElement(json) as? JsonObject
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } ?: return null
        if (boolean(fields["isTrashed"])) return null
        val note = KeepNote(
            fileName = fileName,
            title = string(fields["title"]),
            text = string(fields["textContent"]),
            checklist = (fields["listContent"] as? JsonArray).orEmpty().mapNotNull { item ->
                val line = item as? JsonObject ?: return@mapNotNull null
                ChecklistItem(string(line["text"]), boolean(line["isChecked"])).takeIf { it.text.isNotBlank() }
            },
            labels = (fields["labels"] as? JsonArray).orEmpty().mapNotNull { label -> (label as? JsonObject)?.let { string(it["name"]) }?.takeIf { it.isNotBlank() } },
            editedAt = microseconds(fields["userEditedTimestampUsec"]) ?: microseconds(fields["createdTimestampUsec"]),
            pinned = boolean(fields["isPinned"]),
            archived = boolean(fields["isArchived"]),
        )
        return note.takeIf { it.title.isNotBlank() || it.body().isNotEmpty() }
    }

    /** "Takeout/Keep/Shopping.json": a JSON file directly in a folder called Keep. */
    private fun isKeepNote(path: String): Boolean {
        val parts = path.replace('\\', '/').split('/')
        return parts.size >= 2 && parts[parts.size - 2].equals("Keep", ignoreCase = true) && parts.last().endsWith(".json", ignoreCase = true)
    }

    /** The entry's bytes, or null (skipping it) when it is bigger than [limit]. */
    private fun readLimited(input: InputStream, limit: Int): ByteArray? {
        val bytes = input.readNBytes(limit + 1)
        return if (bytes.size > limit) null else bytes
    }

    private fun string(element: JsonElement?): String = (element as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

    private fun boolean(element: JsonElement?): Boolean = (element as? JsonPrimitive)?.booleanOrNull == true

    private fun microseconds(element: JsonElement?): Instant? {
        val value = (element as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 } ?: return null
        return Instant.ofEpochSecond(value / 1_000_000, (value % 1_000_000) * 1_000)
    }

    private val JSON = Json { isLenient = true }
}

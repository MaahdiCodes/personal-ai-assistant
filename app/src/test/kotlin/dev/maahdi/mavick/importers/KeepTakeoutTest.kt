package dev.maahdi.mavick.importers

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Test

/** Made-up Takeout exports, laid out as Google Takeout writes them. */
class KeepTakeoutTest {
    private fun zip(vararg files: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out ->
            files.forEach { (name, content) ->
                out.putNextEntry(ZipEntry(name))
                out.write(content.toByteArray())
                out.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun read(vararg files: Pair<String, String>) = KeepTakeout.read(zip(*files).inputStream())

    private fun notes(vararg files: Pair<String, String>) = (read(*files) as TakeoutResult.Notes).notes

    private val textNote = """
        {"color": "DEFAULT", "isTrashed": false, "isPinned": false, "isArchived": false,
         "textContent": "Dentist on Thursday 4pm", "title": "Dentist",
         "userEditedTimestampUsec": 1759651200000000, "createdTimestampUsec": 1759600000000000,
         "labels": [{"name": "Health"}], "annotations": [], "attachments": []}
    """.trimIndent()

    private val listNote = """
        {"title": "Shopping", "isPinned": true, "userEditedTimestampUsec": 1759000000000000,
         "listContent": [{"text": "Milk", "isChecked": false, "textHtml": "Milk"}, {"text": "Eggs", "isChecked": true}]}
    """.trimIndent()

    @Test
    fun `a text note is read with its title, text, labels and edit time`() {
        val note = notes("Takeout/Keep/Dentist.json" to textNote).single()

        assertThat(note.fileName).isEqualTo("Dentist.json")
        assertThat(note.title).isEqualTo("Dentist")
        assertThat(note.text).isEqualTo("Dentist on Thursday 4pm")
        assertThat(note.labels).containsExactly("Health")
        assertThat(note.editedAt).isEqualTo(Instant.ofEpochSecond(1_759_651_200))
        assertThat(note.archived).isFalse()
        assertThat(note.body()).isEqualTo("Dentist on Thursday 4pm")
    }

    @Test
    fun `a checklist becomes ticked and unticked lines`() {
        val note = notes("Takeout/Keep/Shopping.json" to listNote).single()

        assertThat(note.checklist).containsExactly(ChecklistItem("Milk", false), ChecklistItem("Eggs", true)).inOrder()
        assertThat(note.body()).isEqualTo("☐ Milk\n☑ Eggs")
        assertThat(note.allChecked).isFalse()
        assertThat(note.pinned).isTrue()
    }

    @Test
    fun `pinned notes come first, then the most recently edited`() {
        val older = """{"title": "Older", "textContent": "a", "userEditedTimestampUsec": 1000000}"""
        val newer = """{"title": "Newer", "textContent": "b", "userEditedTimestampUsec": 2000000}"""

        val titles = notes(
            "Takeout/Keep/Older.json" to older,
            "Takeout/Keep/Newer.json" to newer,
            "Takeout/Keep/Shopping.json" to listNote,
        ).map { it.title }

        assertThat(titles).containsExactly("Shopping", "Newer", "Older").inOrder()
    }

    @Test
    fun `notes in the bin, empty notes and broken files are left out and counted`() {
        val result = read(
            "Takeout/Keep/Dentist.json" to textNote,
            "Takeout/Keep/Binned.json" to """{"title": "Old", "textContent": "x", "isTrashed": true}""",
            "Takeout/Keep/Empty.json" to """{"title": "", "textContent": "  "}""",
            "Takeout/Keep/Broken.json" to """{"title": "Half""",
            "Takeout/Keep/List.json" to """["not", "a", "note"]""",
        ) as TakeoutResult.Notes

        assertThat(result.notes.map { it.title }).containsExactly("Dentist")
        assertThat(result.skipped).isEqualTo(4)
    }

    @Test
    fun `only JSON files in the Keep folder are read, whatever else the export holds`() {
        val notes = notes(
            "Takeout/Keep/Dentist.json" to textNote,
            "Takeout/Keep/Dentist.html" to "<html>Dentist</html>",
            "Takeout/Keep/photo.jpg" to "not really a photo",
            "Takeout/Calendar/events.json" to """{"title": "Calendar", "textContent": "not Keep"}""",
            "Takeout/archive_browser.html" to "<html></html>",
        )

        assertThat(notes.map { it.title }).containsExactly("Dentist")
    }

    @Test
    fun `exports made on Windows, or with a lower-case folder, are read too`() {
        assertThat(notes("Takeout\\Keep\\Dentist.json" to textNote)).hasSize(1)
        assertThat(notes("takeout/keep/Dentist.json" to textNote)).hasSize(1)
    }

    @Test
    fun `missing fields are fine, and a note without times has none`() {
        val note = notes("Takeout/Keep/Bare.json" to """{"textContent": "Call the plumber"}""").single()

        assertThat(note.title).isEmpty()
        assertThat(note.editedAt).isNull()
        assertThat(note.labels).isEmpty()
        assertThat(note.checklist).isEmpty()
    }

    @Test
    fun `the creation time stands in when the edit time is missing`() {
        val note = notes("Takeout/Keep/Created.json" to """{"textContent": "x", "createdTimestampUsec": 1500000}""").single()

        assertThat(note.editedAt).isEqualTo(Instant.ofEpochSecond(1, 500_000_000))
    }

    @Test
    fun `a file that isn't a zip, or an empty one, is refused`() {
        assertThat(KeepTakeout.read("just some text, not a zip".byteInputStream())).isEqualTo(TakeoutResult.Failed(TakeoutProblem.NOT_A_ZIP))
        assertThat(KeepTakeout.read(ByteArray(0).inputStream())).isEqualTo(TakeoutResult.Failed(TakeoutProblem.NOT_A_ZIP))
    }

    @Test
    fun `a zip without Keep notes gives no notes`() {
        assertThat(read("Takeout/Calendar/events.json" to "{}")).isEqualTo(TakeoutResult.Notes(emptyList(), skipped = 0))
    }

    @Test
    fun `a note bigger than the limit is skipped, not read into memory`() {
        val big = """{"title": "Big", "textContent": "${"x".repeat(2_000)}"}"""

        val result = KeepTakeout.read(zip("Takeout/Keep/Big.json" to big, "Takeout/Keep/Dentist.json" to textNote).inputStream(), maxNoteBytes = 1_000)

        assertThat((result as TakeoutResult.Notes).notes.map { it.title }).containsExactly("Dentist")
        assertThat(result.skipped).isEqualTo(1)
    }

    @Test
    fun `an export with too many notes, or too much text, is refused`() {
        val three = arrayOf(
            "Takeout/Keep/a.json" to """{"title": "a", "textContent": "a"}""",
            "Takeout/Keep/b.json" to """{"title": "b", "textContent": "b"}""",
            "Takeout/Keep/c.json" to """{"title": "c", "textContent": "c"}""",
        )

        assertThat(KeepTakeout.read(zip(*three).inputStream(), maxNotes = 2)).isEqualTo(TakeoutResult.Failed(TakeoutProblem.TOO_BIG))
        assertThat(KeepTakeout.read(zip(*three).inputStream(), maxTotalBytes = 60)).isEqualTo(TakeoutResult.Failed(TakeoutProblem.TOO_BIG))
        assertThat((KeepTakeout.read(zip(*three).inputStream(), maxNotes = 3) as TakeoutResult.Notes).notes).hasSize(3)
    }

    @Test
    fun `a checklist with every item ticked says so`() {
        val done = """{"title": "Done list", "listContent": [{"text": "A", "isChecked": true}, {"text": " ", "isChecked": false}]}"""

        val note = notes("Takeout/Keep/Done.json" to done).single()

        assertThat(note.checklist).containsExactly(ChecklistItem("A", true))
        assertThat(note.allChecked).isTrue()
    }
}

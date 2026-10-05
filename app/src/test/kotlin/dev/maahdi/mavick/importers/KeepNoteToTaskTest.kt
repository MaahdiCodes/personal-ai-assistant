package dev.maahdi.mavick.importers

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.task.TaskSource
import dev.maahdi.mavick.testing.TEST_ZONE
import dev.maahdi.mavick.time.WhenParser
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Test

class KeepNoteToTaskTest {
    private val parser = WhenParser()

    /** Monday 5 October 2026, 10:00 in Dhaka. */
    private val now = LocalDateTime.of(2026, 10, 5, 10, 0)

    private fun note(
        title: String = "",
        text: String = "",
        checklist: List<ChecklistItem> = emptyList(),
        editedAt: LocalDateTime? = null,
    ) = KeepNote("n.json", title, text, checklist, emptyList(), editedAt?.atZone(TEST_ZONE)?.toInstant(), pinned = false, archived = false)

    @Test
    fun `the title becomes the task and the text its notes, as when sharing from Keep`() {
        val draft = KeepNoteToTask.draft(note(title = "Dentist", text = "Bring the X-rays"), parser, TEST_ZONE, now)!!

        assertThat(draft.title).isEqualTo("Dentist")
        assertThat(draft.notes).isEqualTo("Bring the X-rays")
        assertThat(draft.source).isEqualTo(TaskSource.KEEP)
        assertThat(draft.sourceExcerpt).isEqualTo("Bring the X-rays")
    }

    @Test
    fun `dates count from when the note was last edited`() {
        // Edited on Friday 2 October: "Thursday" then meant 8 October.
        val draft = KeepNoteToTask.draft(note(title = "Dentist Thursday 4pm", editedAt = LocalDateTime.of(2026, 10, 2, 9, 0)), parser, TEST_ZONE, now)!!

        assertThat(draft.title).isEqualTo("Dentist")
        assertThat(draft.dueDate).isEqualTo(LocalDate.of(2026, 10, 8))
        assertThat(draft.dueTime).isEqualTo(LocalTime.of(16, 0))
        assertThat(draft.reminderTime).isEqualTo(LocalTime.of(16, 0))
    }

    @Test
    fun `a note without a time counts from now`() {
        val draft = KeepNoteToTask.draft(note(title = "Pay rent tomorrow"), parser, TEST_ZONE, now)!!

        assertThat(draft.dueDate).isEqualTo(LocalDate.of(2026, 10, 6))
    }

    @Test
    fun `without a title, the first line is the task`() {
        val draft = KeepNoteToTask.draft(note(text = "Call the plumber\nabout the sink"), parser, TEST_ZONE, now)!!

        assertThat(draft.title).isEqualTo("Call the plumber")
        assertThat(draft.notes).isEqualTo("about the sink")
    }

    @Test
    fun `a checklist goes into the notes with its ticks`() {
        val draft = KeepNoteToTask.draft(
            note(title = "Shopping", checklist = listOf(ChecklistItem("Milk", false), ChecklistItem("Eggs", true))),
            parser,
            TEST_ZONE,
            now,
        )!!

        assertThat(draft.notes).isEqualTo("☐ Milk\n☑ Eggs")
    }
}

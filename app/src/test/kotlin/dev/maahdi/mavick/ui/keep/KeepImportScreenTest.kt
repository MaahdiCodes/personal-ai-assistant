package dev.maahdi.mavick.ui.keep

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.importers.ChecklistItem
import dev.maahdi.mavick.importers.KeepNote
import dev.maahdi.mavick.importers.TakeoutProblem
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeepImportScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 5)
    private val calls = mutableListOf<String>()

    private fun show(state: KeepImportState) {
        compose.setContent {
            KeepImportScreen(
                state = state,
                today = today,
                use24Hour = true,
                onToggle = { calls += "toggle $it" },
                onSelectAll = { calls += "all" },
                onSelectNone = { calls += "none" },
                onAdd = { calls += "add" },
                onBack = {},
            )
        }
    }

    private val dentist = KeepImportItem(
        KeepNote("a.json", "Dentist", "Bring the X-rays", emptyList(), listOf("Health"), null, pinned = false, archived = true),
        TaskDraft(title = "Dentist", notes = "Bring the X-rays", dueDate = LocalDate.of(2026, 10, 8), dueTime = LocalTime.of(16, 0)),
    )
    private val shopping = KeepImportItem(
        KeepNote("b.json", "Shopping", "", listOf(ChecklistItem("Milk", true)), emptyList(), null, pinned = false, archived = false),
        TaskDraft(title = "Shopping", notes = "☑ Milk"),
    )

    @Test
    fun `each note shows the task it would become, with its date, labels and state`() {
        show(KeepImportState.Ready(listOf(dentist, shopping), skipped = 1))

        compose.onNodeWithText("2 notes found. Tick the ones that should become tasks.").assertExists()
        compose.onNodeWithText("1 note left out: in the bin, empty or unreadable.").assertExists()
        compose.onNodeWithText("Thursday · 16:00 · Health · Archived").assertExists()
        compose.onNodeWithText("All ticked").assertExists()
        compose.onNodeWithText("Bring the X-rays").assertExists()
    }

    @Test
    fun `tapping a note ticks it, and Add counts the chosen ones`() {
        show(KeepImportState.Ready(listOf(dentist, shopping), skipped = 0, selected = setOf("a.json")))

        compose.onNodeWithText("Shopping").performClick()
        compose.onNodeWithText("Add 1 task").performClick()
        compose.onNodeWithText("Select all").performClick()
        compose.onNodeWithText("Select none").performClick()

        assertThat(calls).containsExactly("toggle b.json", "add", "all", "none").inOrder()
    }

    @Test
    fun `with nothing chosen, Add waits`() {
        show(KeepImportState.Ready(listOf(dentist), skipped = 0))

        compose.onNodeWithText("Add 0 tasks").assertIsNotEnabled()
    }

    @Test
    fun `problems and the result are explained`() {
        show(KeepImportState.Failed(TakeoutProblem.NOT_A_ZIP))
        compose.onNodeWithText("This isn't a Google Takeout .zip", substring = true).assertExists()
    }

    @Test
    fun `the result says how many tasks were added`() {
        show(KeepImportState.Done(added = 3))
        compose.onNodeWithText("3 tasks added from Keep.").assertExists()
    }

    @Test
    fun `an export without notes says so`() {
        show(KeepImportState.Ready(emptyList(), skipped = 0))
        compose.onNodeWithText("No Keep notes in this export.").assertExists()
    }
}

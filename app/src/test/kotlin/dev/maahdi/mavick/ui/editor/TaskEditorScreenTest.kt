package dev.maahdi.mavick.ui.editor

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.maahdi.mavick.calendar.CalendarOccurrence
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.TEST_ZONE
import dev.maahdi.mavick.time.DEFAULT_WORK_DAYS
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskEditorScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = MONDAY_10AM.toLocalDate()

    private fun event(title: String, hour: Int) = CalendarOccurrence(
        1, 1, title,
        today.atTime(hour, 0).atZone(TEST_ZONE).toInstant(), today.atTime(hour + 1, 0).atZone(TEST_ZONE).toInstant(),
        allDay = false, busy = true,
    )

    private fun show(state: EditorState) {
        compose.setContent {
            TaskEditorScreen(
                state = state,
                today = today,
                workDays = DEFAULT_WORK_DAYS,
                use24Hour = true,
                onChange = {},
                onSave = {},
                onDelete = {},
                onClose = {},
                zone = TEST_ZONE,
            )
        }
    }

    private val timed = EditorState(title = "Call the bank", dueDate = today, dueTime = LocalTime.of(17, 0))

    @Test
    fun `a clash is said under the time`() {
        show(timed.copy(clashes = listOf(event("Dentist", 17))))

        compose.onNodeWithTag(CLASH_WARNING_TAG).assertTextEquals("Clashes with Dentist at 17:00")
    }

    @Test
    fun `several clashes name the first and count the rest`() {
        show(timed.copy(clashes = listOf(event("Dentist", 17), event("Standup", 17), event("Lunch", 17))))

        compose.onNodeWithTag(CLASH_WARNING_TAG).assertTextEquals("Clashes with Dentist and 2 more at 17:00")
    }

    @Test
    fun `no clash means no warning`() {
        show(timed)

        compose.onNodeWithTag(CLASH_WARNING_TAG).assertDoesNotExist()
        compose.onNodeWithText("Clashes with", substring = true).assertDoesNotExist()
    }
}

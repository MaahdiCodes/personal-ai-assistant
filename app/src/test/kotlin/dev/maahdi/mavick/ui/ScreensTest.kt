package dev.maahdi.mavick.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.task
import dev.maahdi.mavick.time.DEFAULT_WORK_DAYS
import dev.maahdi.mavick.time.RepeatRule
import dev.maahdi.mavick.time.WhenParser
import dev.maahdi.mavick.ui.lock.LockScreen
import dev.maahdi.mavick.ui.tasks.QUICK_ADD_FIELD_TAG
import dev.maahdi.mavick.ui.tasks.QUICK_ADD_PREVIEW_TAG
import dev.maahdi.mavick.ui.tasks.QuickAddBar
import dev.maahdi.mavick.ui.tasks.TaskRow
import dev.maahdi.mavick.ui.tasks.TasksScreen
import dev.maahdi.mavick.ui.tasks.TasksUiState
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = MONDAY_10AM.toLocalDate()
    private val parser = WhenParser()

    @Test
    fun `quick-add shows what it understood, then adds and clears`() {
        var added: String? = null
        compose.setContent {
            QuickAddBar(
                preview = { parser.parse(it, MONDAY_10AM) },
                onSubmit = {
                    added = it
                    true
                },
                today = today,
                workDays = DEFAULT_WORK_DAYS,
                use24Hour = true,
            )
        }

        compose.onNodeWithTag(QUICK_ADD_FIELD_TAG).performTextInput("Call the bank tomorrow 5pm")
        compose.onNodeWithTag(QUICK_ADD_PREVIEW_TAG).assertTextEquals("Tomorrow · 17:00")
        compose.onNodeWithText("Add").performClick()

        assertThat(added).isEqualTo("Call the bank tomorrow 5pm")
        compose.onNodeWithText("Call the bank tomorrow 5pm").assertDoesNotExist()
    }

    @Test
    fun `quick-add can't add a task without a title`() {
        compose.setContent {
            QuickAddBar({ parser.parse(it, MONDAY_10AM) }, { true }, today, DEFAULT_WORK_DAYS, use24Hour = true)
        }

        compose.onNodeWithTag(QUICK_ADD_FIELD_TAG).performTextInput("tomorrow 5pm")

        compose.onNodeWithText("Add").assertIsNotEnabled()
    }

    @Test
    fun `a task row shows when and how often, and its checkbox marks it done`() {
        var toggled = 0
        var opened = 0
        val rent = task(title = "Pay rent", dueDate = today.plusDays(1), dueTime = LocalTime.of(17, 0), repeatRule = RepeatRule.Daily())
        compose.setContent {
            TaskRow(rent, today, DEFAULT_WORK_DAYS, use24Hour = true, highlightDue = false, onToggleDone = { toggled++ }, onClick = { opened++ })
        }

        compose.onNodeWithText("Tomorrow · 17:00 · Every day").assertExists()
        compose.onNodeWithContentDescription("Mark \"Pay rent\" done").performClick()
        compose.onNodeWithText("Pay rent").performClick()

        assertThat(toggled).isEqualTo(1)
        assertThat(opened).isEqualTo(1)
    }

    @Test
    fun `the task list groups today's work and switches tabs`() {
        val state = TasksUiState.build(
            open = listOf(
                task(title = "Late report", dueDate = today.minusDays(1)),
                task(title = "Buy milk", dueDate = today),
                task(title = "Dentist", dueDate = today.plusDays(3)),
            ),
            done = emptyList(),
            today = today,
            workDays = DEFAULT_WORK_DAYS,
        )
        compose.setContent {
            TasksScreen(
                state = state,
                use24Hour = true,
                preview = { parser.parse(it, MONDAY_10AM) },
                onQuickAdd = { true },
                onToggleDone = {},
                onOpenTask = {},
                onNewTask = {},
                onOpenSettings = {},
                onOpenInbox = {},
                notificationsBlocked = false,
                onFixNotifications = {},
                snackbarHostState = SnackbarHostState(),
            )
        }

        compose.onNodeWithText("Overdue").assertExists()
        compose.onNodeWithText("Late report").assertExists()
        compose.onNodeWithText("Buy milk").assertExists()
        compose.onNodeWithText("Dentist").assertDoesNotExist()

        compose.onNodeWithText("Upcoming").performClick()

        compose.onNodeWithText("Dentist").assertExists()
        compose.onNodeWithText("Buy milk").assertDoesNotExist()
    }

    @Test
    fun `the lock screen asks to unlock straight away, and again on request`() {
        var requests = 0
        compose.setContent { LockScreen(onUnlock = { requests++ }) }
        compose.waitForIdle()
        assertThat(requests).isEqualTo(1)

        compose.onNodeWithText("Unlock").performClick()

        assertThat(requests).isEqualTo(2)
    }
}

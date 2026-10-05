package dev.maahdi.mavick.ui.tasks

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.testing.task
import dev.maahdi.mavick.time.DEFAULT_WORK_DAYS
import dev.maahdi.mavick.time.ParsedTask
import dev.maahdi.mavick.time.RepeatRule
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Test

class TasksUiStateTest {
    private val today = LocalDate.of(2026, 10, 5)

    @Test
    fun `open tasks are split into overdue, today, upcoming and no date, keeping their order`() {
        val overdue = task(title = "overdue", dueDate = today.minusDays(2))
        val dueToday = task(title = "today", dueDate = today)
        val tomorrow = task(title = "tomorrow", dueDate = today.plusDays(1))
        val later = task(title = "later", dueDate = today.plusDays(9))
        val undated = task(title = "undated")

        val state = TasksUiState.build(listOf(overdue, dueToday, tomorrow, later, undated), emptyList(), today, DEFAULT_WORK_DAYS)

        assertThat(state.overdue).containsExactly(overdue)
        assertThat(state.dueToday).containsExactly(dueToday)
        assertThat(state.upcoming).containsExactly(tomorrow, later).inOrder()
        assertThat(state.noDate).containsExactly(undated)
        assertThat(state.loading).isFalse()
    }

    @Test
    fun `quick-add preview shows what was understood`() {
        val parsed = ParsedTask("Call", today.plusDays(1), LocalTime.of(17, 0), RepeatRule.Daily())

        assertThat(previewSummary(parsed, today, DEFAULT_WORK_DAYS, use24Hour = true)).isEqualTo("Tomorrow · 17:00 · Every day")
        assertThat(previewSummary(ParsedTask("Just a note"), today, DEFAULT_WORK_DAYS, use24Hour = true)).isEmpty()
    }
}

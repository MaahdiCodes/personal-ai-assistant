package dev.maahdi.mavick.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.maahdi.mavick.AppContainer
import dev.maahdi.mavick.calendar.CalendarOccurrence
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.data.suggestion.SuggestionRepository
import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.data.task.TaskRepository
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Edits an existing task ([taskId]) or a new one, optionally pre-filled ([draft], e.g. from a
 * Keep note). Nothing is saved until [save]. A draft from a suggestion ([suggestionId]) marks that
 * suggestion as added once saved.
 */
class EditorViewModel(
    private val openTasks: suspend () -> TaskRepository,
    private val openSuggestions: suspend () -> SuggestionRepository,
    private val settings: SettingsRepository,
    private val clock: () -> Clock,
    taskId: String?,
    draft: TaskDraft?,
    private val suggestionId: String? = null,
    /** The calendar events that overlap a task at this date and time (Phase 4); none while the check is off. */
    private val findClashesAt: suspend (LocalDate, LocalTime) -> List<CalendarOccurrence> = { _, _ -> emptyList() },
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        when {
            taskId != null -> EditorState(taskId = taskId, loading = true)
            draft != null -> EditorState.fromDraft(draft, settings.current.workDays)
            else -> EditorState()
        },
    )

    val state: StateFlow<EditorState> = mutableState.asStateFlow()

    private var clashCheck: Job? = null

    init {
        if (taskId != null) {
            viewModelScope.launch {
                val task = openTasks().find(taskId)
                mutableState.value = if (task == null) {
                    EditorState(taskId = taskId, missing = true)
                } else {
                    EditorState.fromTask(task, settings.current.workDays)
                }
                checkClashes()
            }
        } else {
            checkClashes()
        }
    }

    fun edit(change: (EditorState) -> EditorState) {
        val before = mutableState.value
        mutableState.update { change(it).copy(showTitleError = false) }
        val after = mutableState.value
        if (before.dueDate != after.dueDate || before.dueTime != after.dueTime) checkClashes()
    }

    /** Looks for calendar events at the chosen date and time. A newer check replaces an older one still running. */
    private fun checkClashes() {
        clashCheck?.cancel()
        val date = mutableState.value.dueDate
        val time = mutableState.value.dueTime
        if (date == null || time == null) {
            mutableState.update { it.copy(clashes = emptyList()) }
            return
        }
        clashCheck = viewModelScope.launch {
            val found = findClashesAt(date, time)
            // Only if the date and time are still the ones asked about.
            mutableState.update { if (it.dueDate == date && it.dueTime == time) it.copy(clashes = found) else it }
        }
    }

    /** Saves, then calls [onSaved]. With an empty title it shows an error instead. */
    fun save(onSaved: () -> Unit) {
        val current = mutableState.value
        if (current.loading || current.missing) return
        if (current.title.isBlank()) {
            mutableState.update { it.copy(showTitleError = true) }
            return
        }
        val draft = current.toDraft(LocalDate.now(clock()), settings.current.workDays)
        viewModelScope.launch {
            val repository = openTasks()
            val taskId = current.taskId
            if (taskId == null) {
                val task = repository.create(draft)
                suggestionId?.let { openSuggestions().accept(it, task.id) }
            } else {
                repository.update(taskId, draft)
            }
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val taskId = mutableState.value.taskId ?: return
        viewModelScope.launch {
            openTasks().delete(taskId)
            onDeleted()
        }
    }

    companion object {
        fun factory(container: AppContainer, taskId: String?, draft: TaskDraft?, suggestionId: String?) = viewModelFactory {
            initializer {
                EditorViewModel(
                    container::openTasks,
                    container::openSuggestions,
                    container.settings,
                    container.clock,
                    taskId,
                    draft,
                    suggestionId,
                    findClashesAt = { date, time -> container.openClashes().clashesAt(date, time) },
                )
            }
        }
    }
}

package dev.maahdi.mavick.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.maahdi.mavick.AppContainer
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.data.task.TaskEntity
import dev.maahdi.mavick.data.task.TaskRepository
import dev.maahdi.mavick.data.task.TaskStatus
import dev.maahdi.mavick.time.DEFAULT_WORK_DAYS
import dev.maahdi.mavick.time.ParsedTask
import dev.maahdi.mavick.time.WhenParser
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the task lists show, already split into sections. */
data class TasksUiState(
    val today: LocalDate,
    val overdue: List<TaskEntity> = emptyList(),
    val dueToday: List<TaskEntity> = emptyList(),
    val upcoming: List<TaskEntity> = emptyList(),
    val noDate: List<TaskEntity> = emptyList(),
    val done: List<TaskEntity> = emptyList(),
    val workDays: Set<DayOfWeek> = DEFAULT_WORK_DAYS,
    val loading: Boolean = true,
    /** Set when the encrypted database can't be opened; the screen explains instead of crashing. */
    val storageError: String? = null,
) {
    companion object {
        /** [open] arrives sorted by date and time; each section keeps that order. */
        fun build(open: List<TaskEntity>, done: List<TaskEntity>, today: LocalDate, workDays: Set<DayOfWeek>) = TasksUiState(
            today = today,
            overdue = open.filter { it.dueDate?.isBefore(today) == true },
            dueToday = open.filter { it.dueDate == today },
            upcoming = open.filter { it.dueDate?.isAfter(today) == true },
            noDate = open.filter { it.dueDate == null },
            done = done,
            workDays = workDays,
            loading = false,
        )
    }
}

/** A task was marked done; [snapshot] is how it was before, for the snackbar's Undo. */
data class UndoEvent(val snapshot: TaskEntity)

class TasksViewModel(
    private val openTasks: suspend () -> TaskRepository,
    private val settings: SettingsRepository,
    private val parser: () -> WhenParser,
    private val clock: () -> Clock,
) : ViewModel() {
    private val today = MutableStateFlow(LocalDate.now(clock()))
    private val undoEvents = MutableSharedFlow<UndoEvent>(extraBufferCapacity = 1)

    /** Watches the database only while the screen is visible (WhileSubscribed), to save battery. */
    val state: StateFlow<TasksUiState> = flow {
        val repository = openTasks()
        emitAll(
            combine(repository.observeOpen(), repository.observeDone(), today, settings.settings) { open, done, day, current ->
                TasksUiState.build(open, done, day, current.workDays)
            },
        )
    }
        .catch { error -> emit(TasksUiState(today = today.value, loading = false, storageError = error.javaClass.simpleName)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_WATCHING_AFTER_MS), TasksUiState(today = today.value))

    val undo: SharedFlow<UndoEvent> = undoEvents.asSharedFlow()

    /** Call when the screen comes back, in case midnight passed. */
    fun refreshToday() {
        today.value = LocalDate.now(clock())
    }

    fun preview(text: String): ParsedTask = parser().parse(text, LocalDateTime.now(clock()))

    /** Adds a task from the quick-add text. Returns false (and adds nothing) if there's no title. */
    fun quickAdd(text: String): Boolean {
        val parsed = preview(text)
        if (parsed.title.isBlank()) return false
        viewModelScope.launch {
            openTasks().create(
                TaskDraft(
                    title = parsed.title,
                    dueDate = parsed.dueDate,
                    dueTime = parsed.dueTime,
                    // A task with a time is reminded at that time; date-only tasks are in the briefing.
                    reminderTime = parsed.dueTime,
                    repeatRule = parsed.repeatRule,
                ),
            )
        }
        return true
    }

    fun toggleDone(task: TaskEntity) {
        viewModelScope.launch {
            val repository = openTasks()
            if (task.status == TaskStatus.OPEN) {
                repository.complete(task.id)
                undoEvents.emit(UndoEvent(task))
            } else {
                repository.reopen(task.id)
            }
        }
    }

    fun undo(event: UndoEvent) {
        viewModelScope.launch { openTasks().restore(event.snapshot) }
    }

    companion object {
        private const val STOP_WATCHING_AFTER_MS = 5_000L

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { TasksViewModel(container::openTasks, container.settings, container::whenParser, container.clock) }
        }
    }
}

package dev.maahdi.mavick.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.maahdi.mavick.AppContainer
import dev.maahdi.mavick.calendar.CalendarOccurrence
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.data.suggestion.SuggestionRepository
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
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
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
    /** The calendar events each task overlaps, by task ID (Phase 4), for the warning on its row. */
    val clashes: Map<String, List<CalendarOccurrence>> = emptyMap(),
    /** The time zone clash times are shown in. */
    val zone: ZoneId = ZoneId.systemDefault(),
    /** Tasks found in messages, waiting for you (Phase 3). */
    val suggestionsWaiting: Int = 0,
    val loading: Boolean = true,
    /** Set when the encrypted database can't be opened; the screen explains instead of crashing. */
    val storageError: String? = null,
) {
    companion object {
        /** [open] arrives sorted by date and time; each section keeps that order. */
        fun build(
            open: List<TaskEntity>,
            done: List<TaskEntity>,
            today: LocalDate,
            workDays: Set<DayOfWeek>,
            suggestionsWaiting: Int = 0,
            zone: ZoneId = ZoneId.systemDefault(),
        ) = TasksUiState(
            today = today,
            overdue = open.filter { it.dueDate?.isBefore(today) == true },
            dueToday = open.filter { it.dueDate == today },
            upcoming = open.filter { it.dueDate?.isAfter(today) == true },
            noDate = open.filter { it.dueDate == null },
            done = done,
            workDays = workDays,
            zone = zone,
            suggestionsWaiting = suggestionsWaiting,
            loading = false,
        )
    }
}

/** A task was marked done; [snapshot] is how it was before, for the snackbar's Undo. */
data class UndoEvent(val snapshot: TaskEntity)

class TasksViewModel(
    private val openTasks: suspend () -> TaskRepository,
    private val openSuggestions: suspend () -> SuggestionRepository,
    private val settings: SettingsRepository,
    private val parser: () -> WhenParser,
    private val clock: () -> Clock,
    /** Calendar events each of the open tasks overlaps, by task ID (Phase 4); nothing while the check is off. */
    private val findClashes: suspend (List<TaskEntity>) -> Map<String, List<CalendarOccurrence>> = { emptyMap() },
) : ViewModel() {
    private val today = MutableStateFlow(LocalDate.now(clock()))

    /** Counted up when the screen comes back, so the calendar is read again (it may have changed). */
    private val recheck = MutableStateFlow(0)
    private val undoEvents = MutableSharedFlow<UndoEvent>(extraBufferCapacity = 1)

    /** Watches the database only while the screen is visible (WhileSubscribed), to save battery. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TasksUiState> = flow {
        val repository = openTasks()
        val suggestions = openSuggestions()
        val open = repository.observeOpen()
        // The list shows at once; clash warnings follow when the calendar has been read.
        val clashes = combine(open, clashSettings(), recheck) { tasks, _, _ -> tasks }
            .mapLatest { tasks -> safeClashes(tasks) }
            .onStart { emit(emptyMap()) }
        val list = combine(
            open,
            repository.observeDone(),
            today,
            settings.settings,
            suggestions.observeNewCount(),
        ) { openTasks, done, day, current, waiting ->
            TasksUiState.build(
                openTasks,
                done,
                day,
                current.workDays,
                suggestionsWaiting = if (current.suggestionsEnabled) waiting else 0,
                zone = clock().zone,
            )
        }
        emitAll(combine(list, clashes) { state, found -> state.copy(clashes = found) })
    }
        .catch { error -> emit(TasksUiState(today = today.value, loading = false, storageError = error.javaClass.simpleName)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_WATCHING_AFTER_MS), TasksUiState(today = today.value))

    val undo: SharedFlow<UndoEvent> = undoEvents.asSharedFlow()

    /** Call when the screen comes back, in case midnight passed. */
    fun refreshToday() {
        today.value = LocalDate.now(clock())
        recheck.value++
    }

    /** Calendar trouble means no warnings; it must never turn the task list into an error. */
    private suspend fun safeClashes(tasks: List<TaskEntity>): Map<String, List<CalendarOccurrence>> = try {
        findClashes(tasks)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyMap()
    }

    /** What decides whether the calendar is read, and which calendars: a change reads it again. */
    private fun clashSettings() = settings.settings.map { it.clashCheckEnabled to it.clashCalendarIds }.distinctUntilChanged()

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
            initializer {
                TasksViewModel(
                    container::openTasks,
                    container::openSuggestions,
                    container.settings,
                    container::whenParser,
                    container.clock,
                    findClashes = { tasks -> container.openClashes().clashesFor(tasks) },
                )
            }
        }
    }
}

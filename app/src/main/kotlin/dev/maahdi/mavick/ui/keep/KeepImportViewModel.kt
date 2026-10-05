package dev.maahdi.mavick.ui.keep

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.maahdi.mavick.AppContainer
import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.data.task.TaskRepository
import dev.maahdi.mavick.importers.KeepNote
import dev.maahdi.mavick.importers.KeepNoteToTask
import dev.maahdi.mavick.importers.KeepTakeout
import dev.maahdi.mavick.importers.TakeoutProblem
import dev.maahdi.mavick.importers.TakeoutResult
import java.io.FileNotFoundException
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A note from the export and the task it would become. */
data class KeepImportItem(val note: KeepNote, val draft: TaskDraft)

sealed interface KeepImportState {
    data object Reading : KeepImportState

    /** [problem] is null when the picked file couldn't be opened at all. */
    data class Failed(val problem: TakeoutProblem?) : KeepImportState

    /**
     * Notes to choose from. None is chosen at first: Keep holds many notes that aren't tasks.
     * [skipped]: notes in the bin, empty notes and unreadable files.
     */
    data class Ready(
        val items: List<KeepImportItem>,
        val skipped: Int,
        val selected: Set<String> = emptySet(),
        val adding: Boolean = false,
    ) : KeepImportState

    data class Done(val added: Int) : KeepImportState
}

/**
 * Settings › Google Keep: reads a Takeout export once, in memory, and adds the notes you pick as
 * tasks. Nothing else from the export is kept.
 */
class KeepImportViewModel(
    /** Reads the picked export; null if it couldn't be opened. */
    private val readExport: suspend () -> TakeoutResult?,
    private val openTasks: suspend () -> TaskRepository,
    private val makeDraft: (KeepNote) -> TaskDraft?,
    /** Where the notes are read into drafts: off the main thread, as there may be hundreds. */
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val mutableState = MutableStateFlow<KeepImportState>(KeepImportState.Reading)

    val state: StateFlow<KeepImportState> = mutableState.asStateFlow()

    /** Reading the export, for tests to wait on. */
    val reading: Job = viewModelScope.launch {
        mutableState.value = when (val result = readExport()) {
            null -> KeepImportState.Failed(problem = null)
            is TakeoutResult.Failed -> KeepImportState.Failed(result.problem)
            is TakeoutResult.Notes -> {
                val items = withContext(work) { result.notes.mapNotNull { note -> makeDraft(note)?.let { KeepImportItem(note, it) } } }
                KeepImportState.Ready(items, skipped = result.skipped + (result.notes.size - items.size))
            }
        }
    }

    fun toggle(fileName: String) = updateReady { ready ->
        ready.copy(selected = if (fileName in ready.selected) ready.selected - fileName else ready.selected + fileName)
    }

    fun selectAll() = updateReady { ready -> ready.copy(selected = ready.items.map { it.note.fileName }.toSet()) }

    fun selectNone() = updateReady { ready -> ready.copy(selected = emptySet()) }

    /** Adds the chosen notes as tasks, in the order shown. */
    fun add(): Job? {
        val ready = mutableState.value as? KeepImportState.Ready ?: return null
        if (ready.adding || ready.selected.isEmpty()) return null
        mutableState.value = ready.copy(adding = true)
        return viewModelScope.launch {
            val tasks = openTasks()
            val chosen = ready.items.filter { it.note.fileName in ready.selected }
            chosen.forEach { tasks.create(it.draft) }
            mutableState.value = KeepImportState.Done(chosen.size)
        }
    }

    private fun updateReady(change: (KeepImportState.Ready) -> KeepImportState.Ready) {
        mutableState.update { current -> if (current is KeepImportState.Ready && !current.adding) change(current) else current }
    }

    companion object {
        fun factory(container: AppContainer, resolver: ContentResolver, uri: Uri) = viewModelFactory {
            initializer {
                KeepImportViewModel(
                    readExport = {
                        withContext(Dispatchers.IO) {
                            try {
                                resolver.openInputStream(uri)?.use(KeepTakeout::read)
                            } catch (e: FileNotFoundException) {
                                null
                            } catch (e: SecurityException) {
                                null
                            }
                        }
                    },
                    openTasks = container::openTasks,
                    makeDraft = { note ->
                        val clock = container.clock()
                        KeepNoteToTask.draft(note, container.whenParser(), clock.zone, LocalDateTime.now(clock))
                    },
                )
            }
        }
    }
}

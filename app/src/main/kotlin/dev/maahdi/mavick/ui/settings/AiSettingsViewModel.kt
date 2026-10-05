package dev.maahdi.mavick.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.maahdi.mavick.AppContainer
import dev.maahdi.mavick.ai.AiStatus
import dev.maahdi.mavick.ai.AiStatusStore
import dev.maahdi.mavick.ai.ImportProblem
import dev.maahdi.mavick.ai.ImportResult
import dev.maahdi.mavick.ai.ModelCheck
import dev.maahdi.mavick.ai.ModelInfo
import dev.maahdi.mavick.ai.ModelManager
import java.io.InputStream
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Bytes copied so far during an import, of [total] when the picker said. */
data class ImportProgress(val copied: Long, val total: Long?)

/** The result of the last thing done with the model, shown under it. */
sealed interface ModelOutcome {
    /** Imported, then checked with a test message. */
    data class Imported(val info: ModelInfo, val check: ModelCheck) : ModelOutcome

    data class Rejected(val problem: ImportProblem) : ModelOutcome

    data class Checked(val check: ModelCheck) : ModelOutcome

    data object Removed : ModelOutcome
}

data class AiSettingsState(
    val model: ModelInfo? = null,
    /** Imported and allowed to run now (not switched off, not waiting after a problem). */
    val usable: Boolean = false,
    val status: AiStatus = AiStatus(),
    val importing: ImportProgress? = null,
    val checking: Boolean = false,
    val outcome: ModelOutcome? = null,
) {
    val busy: Boolean get() = importing != null || checking
}

/** Settings › Suggestions: the AI model and what it has done (counts only). */
class AiSettingsViewModel(
    private val manager: ModelManager,
    private val status: AiStatusStore,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AiSettingsState())

    val state: StateFlow<AiSettingsState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    /** Reads the model and the counts again (the AI may have run meanwhile). */
    fun refresh(): Job = viewModelScope.launch {
        val model = manager.info()
        val usable = manager.isUsable()
        mutableState.update { it.copy(model = model, usable = usable, status = status.snapshot()) }
    }

    /** Imports a picked file, then checks the new model with a test message. */
    fun import(name: String, sizeBytes: Long?, open: () -> InputStream): Job? {
        if (mutableState.value.busy) return null
        mutableState.update { it.copy(importing = ImportProgress(0, sizeBytes), outcome = null) }
        return viewModelScope.launch {
            val result = manager.import(name, sizeBytes, open) { copied ->
                mutableState.update { it.copy(importing = ImportProgress(copied, sizeBytes)) }
            }
            mutableState.update { it.copy(importing = null) }
            when (result) {
                is ImportResult.Rejected -> mutableState.update { it.copy(outcome = ModelOutcome.Rejected(result.problem)) }
                is ImportResult.Imported -> {
                    mutableState.update { it.copy(checking = true) }
                    val check = manager.check()
                    mutableState.update { it.copy(checking = false, outcome = ModelOutcome.Imported(result.info, check)) }
                }
            }
            refresh().join()
        }
    }

    fun check(): Job? {
        if (mutableState.value.busy) return null
        mutableState.update { it.copy(checking = true, outcome = null) }
        return viewModelScope.launch {
            val check = manager.check()
            mutableState.update { it.copy(checking = false, outcome = ModelOutcome.Checked(check)) }
            refresh().join()
        }
    }

    fun remove(): Job? {
        if (mutableState.value.busy) return null
        return viewModelScope.launch {
            manager.remove()
            mutableState.update { it.copy(outcome = ModelOutcome.Removed) }
            refresh().join()
        }
    }

    /** After the model was switched off for stopping Mavick: try it again. */
    fun turnOnAgain(): Job {
        manager.turnOnAgain()
        mutableState.update { it.copy(outcome = null) }
        return refresh()
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { AiSettingsViewModel(container.modelManager, container.aiStatus) }
        }
    }
}

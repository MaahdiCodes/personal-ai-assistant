package dev.maahdi.mavick.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.maahdi.mavick.AppContainer
import dev.maahdi.mavick.backup.BackupDocument
import dev.maahdi.mavick.backup.BackupException
import dev.maahdi.mavick.backup.BackupFile
import dev.maahdi.mavick.backup.BackupProblem
import dev.maahdi.mavick.backup.BackupService
import dev.maahdi.mavick.backup.RestorePreview
import dev.maahdi.mavick.backup.RestoreReport
import java.time.Clock
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the backup or restore dialog flow is. */
sealed interface BackupStep {
    data object Idle : BackupStep

    /** Choosing the password for a new backup. */
    data object NewPassword : BackupStep

    /** Making the key from the password, encrypting, or reading: a moment of work. */
    data object Working : BackupStep

    /** The file is made; the screen asks Android where to save it. */
    data object ChoosingPlace : BackupStep

    /** A backup file was picked; its password is needed to open it. */
    data object RestorePassword : BackupStep

    /** The backup is open and nothing has changed yet: what restoring would do. */
    data class Previewing(val preview: RestorePreview, val restoreSettings: Boolean = true) : BackupStep

    data class Finished(val result: BackupResult) : BackupStep
}

/** How a backup or restore ended, said once in a dialog. */
sealed interface BackupResult {
    data class Saved(val tasks: Int) : BackupResult

    data object SaveFailed : BackupResult

    data class Restored(val report: RestoreReport) : BackupResult

    /** The file could not be opened for a reason other than the password. */
    data class Problem(val problem: BackupProblem) : BackupResult

    data object ReadFailed : BackupResult

    data object TooBig : BackupResult
}

/** What is wrong with the password just typed, shown under the field. */
enum class PasswordProblem { TOO_SHORT, MISMATCH, WRONG }

data class BackupUiState(
    val step: BackupStep = BackupStep.Idle,
    val passwordProblem: PasswordProblem? = null,
)

/**
 * Settings › Backup: makes a backup file and restores one (docs/PLAN.md §5.7 A). Passwords go straight
 * to the service, which wipes them; the file (and, while you look at the preview, what is in it) is kept
 * in memory only until you finish or cancel.
 */
class BackupViewModel(
    private val openService: suspend () -> BackupService,
    private val afterRestore: suspend () -> Unit,
    private val clock: () -> Clock,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BackupUiState())

    val state: StateFlow<BackupUiState> = mutableState.asStateFlow()

    private val saveRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Once the file is made: its suggested name, for the screen to open Android's "Save to" with. */
    val askToSave: SharedFlow<String> = saveRequests.asSharedFlow()

    private var madeFile: BackupFile? = null
    private var pickedFile: ByteArray? = null
    private var openedBackup: BackupDocument? = null

    // --- Making a backup ---

    fun startBackup() {
        mutableState.value = BackupUiState(BackupStep.NewPassword)
    }

    /** Checks the new password, then makes the backup file. */
    fun submitNewPassword(password: String, confirm: String): Job? {
        val problem = when {
            password.length < MIN_PASSWORD_LENGTH -> PasswordProblem.TOO_SHORT
            password != confirm -> PasswordProblem.MISMATCH
            else -> null
        }
        if (problem != null) {
            mutableState.update { it.copy(passwordProblem = problem) }
            return null
        }
        mutableState.value = BackupUiState(BackupStep.Working)
        return viewModelScope.launch {
            val outcome = guarded { openService().create(password.toCharArray()) }
            val file = outcome.getOrNull()
            if (file == null) {
                mutableState.value = BackupUiState(BackupStep.Finished(BackupResult.SaveFailed))
                return@launch
            }
            madeFile = file
            mutableState.value = BackupUiState(BackupStep.ChoosingPlace)
            saveRequests.emit(suggestedName())
        }
    }

    /** The place was chosen: [write] puts the file there. */
    fun saveTo(write: (ByteArray) -> Unit): Job? {
        val file = madeFile ?: return null
        mutableState.value = BackupUiState(BackupStep.Working)
        return viewModelScope.launch {
            val saved = guarded { withContext(io) { write(file.bytes) } }
            madeFile = null
            if (saved.isSuccess) {
                guarded { openService().recordBackupSaved() }
                mutableState.value = BackupUiState(BackupStep.Finished(BackupResult.Saved(file.tasks)))
            } else {
                mutableState.value = BackupUiState(BackupStep.Finished(BackupResult.SaveFailed))
            }
        }
    }

    // --- Restoring ---

    /** A backup file was picked; [read] gives its bytes, or null when it is bigger than [MAX_FILE_BYTES]. */
    fun onFilePicked(read: () -> ByteArray?): Job {
        mutableState.value = BackupUiState(BackupStep.Working)
        return viewModelScope.launch {
            val outcome = guarded { withContext(io) { read() } }
            when {
                outcome.isFailure -> mutableState.value = BackupUiState(BackupStep.Finished(BackupResult.ReadFailed))
                outcome.getOrNull() == null -> mutableState.value = BackupUiState(BackupStep.Finished(BackupResult.TooBig))
                else -> {
                    pickedFile = outcome.getOrNull()
                    mutableState.value = BackupUiState(BackupStep.RestorePassword)
                }
            }
        }
    }

    /** Opens the picked file with the password. A wrong password lets you try again. */
    fun submitRestorePassword(password: String): Job? {
        val file = pickedFile ?: return null
        mutableState.value = BackupUiState(BackupStep.Working)
        return viewModelScope.launch {
            try {
                val service = openService()
                val document = service.open(file, password.toCharArray())
                openedBackup = document
                mutableState.value = BackupUiState(BackupStep.Previewing(service.preview(document)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                if (e.problem == BackupProblem.WRONG_PASSWORD_OR_DAMAGED) {
                    mutableState.value = BackupUiState(BackupStep.RestorePassword, PasswordProblem.WRONG)
                } else {
                    forget()
                    mutableState.value = BackupUiState(BackupStep.Finished(BackupResult.Problem(e.problem)))
                }
            } catch (e: Exception) {
                forget()
                mutableState.value = BackupUiState(BackupStep.Finished(BackupResult.ReadFailed))
            } catch (e: LinkageError) {
                forget()
                mutableState.value = BackupUiState(BackupStep.Finished(BackupResult.ReadFailed))
            }
        }
    }

    fun setRestoreSettings(restore: Boolean) {
        mutableState.update { current ->
            val step = current.step
            if (step is BackupStep.Previewing) current.copy(step = step.copy(restoreSettings = restore)) else current
        }
    }

    /** Merges the opened backup into this phone. */
    fun confirmRestore(): Job? {
        val document = openedBackup ?: return null
        val restoreSettings = (mutableState.value.step as? BackupStep.Previewing)?.restoreSettings ?: return null
        mutableState.value = BackupUiState(BackupStep.Working)
        return viewModelScope.launch {
            val outcome = guarded {
                val report = openService().restore(document, restoreSettings)
                afterRestore()
                report
            }
            forget()
            val report = outcome.getOrNull()
            mutableState.value = BackupUiState(
                BackupStep.Finished(if (report != null) BackupResult.Restored(report) else BackupResult.ReadFailed),
            )
        }
    }

    // --- Ending ---

    /** Closes whatever is open and forgets the file and its contents. */
    fun cancel() {
        forget()
        mutableState.value = BackupUiState()
    }

    private fun forget() {
        madeFile = null
        pickedFile = null
        openedBackup = null
    }

    private fun suggestedName(): String = "mavick-backup-${LocalDate.now(clock())}.mavickbackup"

    /** Runs [block]; a failure becomes a failed result, never a crash. Only the kind is of interest, never content. */
    private suspend fun <T> guarded(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    } catch (e: LinkageError) {
        Result.failure(e)
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 8

        /** A backup of thousands of tasks is far smaller than this; a bigger file is not one of ours. */
        const val MAX_FILE_BYTES = 50 * 1024 * 1024

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { BackupViewModel(container::openBackups, container::afterRestore, container.clock) }
        }
    }
}

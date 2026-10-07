package dev.maahdi.mavick.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.backup.BackupCrypto
import dev.maahdi.mavick.backup.BackupProblem
import dev.maahdi.mavick.backup.BackupService
import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.MainDispatcherRule
import dev.maahdi.mavick.testing.MutableClock
import dev.maahdi.mavick.testing.TestPhone
import java.io.IOException
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var old: TestPhone
    private lateinit var new: TestPhone
    private var restoredCalls = 0
    private var serviceFailure: Exception? = null
    private val password = "a long enough password"

    @Before
    fun setUp() {
        old = TestPhone("backup-vm-old", context, MutableClock(MONDAY_10AM))
        new = TestPhone("backup-vm-new", context, MutableClock(MONDAY_10AM.plusDays(1)))
    }

    @After
    fun tearDown() {
        old.close()
        new.close()
    }

    private fun viewModel(phone: TestPhone) = BackupViewModel(
        openService = { serviceFailure?.let { throw it }; phone.service },
        afterRestore = { restoredCalls++ },
        clock = { phone.clock },
        io = Dispatchers.Unconfined,
    )

    /** Walks the "back up now" flow to the point where Android is asked where to save, and gives the file's bytes. */
    private suspend fun BackupViewModel.makeBackup(pw: String = password) {
        startBackup()
        submitNewPassword(pw, pw)?.join()
    }

    private suspend fun BackupViewModel.savedBytes(): ByteArray {
        var bytes: ByteArray? = null
        saveTo { bytes = it }?.join()
        return bytes!!
    }

    // --- Making a backup ---

    @Test
    fun `back up now asks for a password first`() {
        val viewModel = viewModel(old)

        viewModel.startBackup()

        assertThat(viewModel.state.value.step).isEqualTo(BackupStep.NewPassword)
    }

    @Test
    fun `a short password, or two that differ, is refused with the reason and nothing is made`() = runTest {
        val viewModel = viewModel(old)
        viewModel.startBackup()

        assertThat(viewModel.submitNewPassword("short", "short")).isNull()
        assertThat(viewModel.state.value.passwordProblem).isEqualTo(PasswordProblem.TOO_SHORT)

        assertThat(viewModel.submitNewPassword(password, "$password!")).isNull()
        assertThat(viewModel.state.value.passwordProblem).isEqualTo(PasswordProblem.MISMATCH)
        assertThat(viewModel.state.value.step).isEqualTo(BackupStep.NewPassword)
    }

    @Test
    fun `eight characters are enough`() = runTest {
        val viewModel = viewModel(old)
        viewModel.startBackup()

        assertThat(viewModel.submitNewPassword("12345678", "12345678")).isNotNull()
    }

    @Test
    fun `a good password makes the file and asks the screen where to save it, named by the date`() = runTest {
        val viewModel = viewModel(old)
        val asked = mutableListOf<String>()
        backgroundScope.launch(Dispatchers.Unconfined) { viewModel.askToSave.collect { asked += it } }

        viewModel.makeBackup()

        assertThat(viewModel.state.value.step).isEqualTo(BackupStep.ChoosingPlace)
        assertThat(asked).containsExactly("mavick-backup-2026-10-05.mavickbackup")
    }

    @Test
    fun `saving writes a file that opens with the password and says how many tasks it holds`() = runTest {
        old.tasks.create(TaskDraft(title = "Call the bank", dueDate = MONDAY_10AM.toLocalDate(), dueTime = LocalTime.of(17, 0)))
        val viewModel = viewModel(old)
        viewModel.makeBackup()

        val bytes = viewModel.savedBytes()

        val finished = viewModel.state.value.step as BackupStep.Finished
        assertThat(finished.result).isEqualTo(BackupResult.Saved(tasks = 1))
        assertThat(String(BackupCrypto.decrypt(bytes, password.toCharArray()), Charsets.UTF_8)).contains("Call the bank")
        assertThat(old.settings.current.lastBackupAt).isNotNull()
    }

    @Test
    fun `a place that refuses the file is said, and the backup is not recorded`() = runTest {
        val viewModel = viewModel(old)
        viewModel.makeBackup()

        viewModel.saveTo { throw IOException("No space left") }?.join()

        assertThat((viewModel.state.value.step as BackupStep.Finished).result).isEqualTo(BackupResult.SaveFailed)
        assertThat(old.settings.current.lastBackupAt).isNull()
    }

    @Test
    fun `closing the save screen drops the file`() = runTest {
        val viewModel = viewModel(old)
        viewModel.makeBackup()

        viewModel.cancel()

        assertThat(viewModel.state.value.step).isEqualTo(BackupStep.Idle)
        assertThat(viewModel.saveTo { }).isNull()
        assertThat(old.settings.current.lastBackupAt).isNull()
    }

    @Test
    fun `saving with no file made does nothing`() {
        assertThat(viewModel(old).saveTo { }).isNull()
    }

    @Test
    fun `a database that can't be opened is said, not a crash`() = runTest {
        serviceFailure = IllegalStateException("cannot open")
        val viewModel = viewModel(old)
        viewModel.startBackup()

        viewModel.submitNewPassword(password, password)?.join()

        assertThat((viewModel.state.value.step as BackupStep.Finished).result).isEqualTo(BackupResult.SaveFailed)
    }

    // --- Restoring ---

    private suspend fun fileOf(phone: TestPhone = old): ByteArray {
        val viewModel = viewModel(phone)
        viewModel.makeBackup()
        return viewModel.savedBytes()
    }

    @Test
    fun `a picked file asks for its password`() = runTest {
        val file = fileOf()
        val viewModel = viewModel(new)

        viewModel.onFilePicked { file }.join()

        assertThat(viewModel.state.value.step).isEqualTo(BackupStep.RestorePassword)
    }

    @Test
    fun `a file that is too big, or can't be read, is said and nothing else happens`() = runTest {
        val viewModel = viewModel(new)

        viewModel.onFilePicked { null }.join()
        assertThat((viewModel.state.value.step as BackupStep.Finished).result).isEqualTo(BackupResult.TooBig)

        viewModel.onFilePicked { throw IOException("gone") }.join()
        assertThat((viewModel.state.value.step as BackupStep.Finished).result).isEqualTo(BackupResult.ReadFailed)
        assertThat(viewModel.submitRestorePassword(password)).isNull()
    }

    @Test
    fun `a wrong password lets you try again, and the right one shows what a restore would do`() = runTest {
        old.tasks.create(TaskDraft(title = "One"))
        old.tasks.create(TaskDraft(title = "Two"))
        val file = fileOf()
        val viewModel = viewModel(new)
        viewModel.onFilePicked { file }.join()

        viewModel.submitRestorePassword("not the password")?.join()
        assertThat(viewModel.state.value.step).isEqualTo(BackupStep.RestorePassword)
        assertThat(viewModel.state.value.passwordProblem).isEqualTo(PasswordProblem.WRONG)

        viewModel.submitRestorePassword(password)?.join()
        val previewing = viewModel.state.value.step as BackupStep.Previewing
        assertThat(previewing.preview.tasksAdded).isEqualTo(2)
        assertThat(previewing.restoreSettings).isTrue()
        assertThat(viewModel.state.value.passwordProblem).isNull()
        assertThat(new.database.taskDao().getAll()).isEmpty()
    }

    @Test
    fun `a file that is not a backup, or from a newer Mavick, ends the restore with the reason`() = runTest {
        val viewModel = viewModel(new)

        viewModel.onFilePicked { "just a text file with nothing in it at all, long enough".toByteArray() }.join()
        viewModel.submitRestorePassword(password)?.join()
        assertThat((viewModel.state.value.step as BackupStep.Finished).result).isEqualTo(BackupResult.Problem(BackupProblem.NOT_A_BACKUP))

        val newer = fileOf().also { it[8] = 2 }
        viewModel.onFilePicked { newer }.join()
        viewModel.submitRestorePassword(password)?.join()
        assertThat((viewModel.state.value.step as BackupStep.Finished).result).isEqualTo(BackupResult.Problem(BackupProblem.NEWER_FORMAT))
        // The file is forgotten: there is nothing left to try a password on.
        assertThat(viewModel.submitRestorePassword(password)).isNull()
    }

    @Test
    fun `confirming restores the tasks, tells the app, and says what was done`() = runTest {
        val task = old.tasks.create(TaskDraft(title = "Call the bank"))
        old.settings.update { it.copy(briefingTime = LocalTime.of(6, 30)) }
        val file = fileOf()
        val viewModel = viewModel(new)
        viewModel.onFilePicked { file }.join()
        viewModel.submitRestorePassword(password)?.join()

        viewModel.confirmRestore()?.join()

        val result = (viewModel.state.value.step as BackupStep.Finished).result as BackupResult.Restored
        assertThat(result.report.tasksAdded).isEqualTo(1)
        assertThat(result.report.settingsRestored).isTrue()
        assertThat(new.database.taskDao().findById(task.id)!!.title).isEqualTo("Call the bank")
        assertThat(new.settings.current.briefingTime).isEqualTo(LocalTime.of(6, 30))
        assertThat(restoredCalls).isEqualTo(1)
    }

    @Test
    fun `settings are left alone when the box is unticked`() = runTest {
        old.settings.update { it.copy(briefingTime = LocalTime.of(6, 30)) }
        val file = fileOf()
        val viewModel = viewModel(new)
        viewModel.onFilePicked { file }.join()
        viewModel.submitRestorePassword(password)?.join()

        viewModel.setRestoreSettings(false)
        viewModel.confirmRestore()?.join()

        assertThat(new.settings.current.briefingTime).isNotEqualTo(LocalTime.of(6, 30))
        assertThat(((viewModel.state.value.step as BackupStep.Finished).result as BackupResult.Restored).report.settingsRestored).isFalse()
    }

    @Test
    fun `the settings box only counts while a backup is being previewed`() {
        val viewModel = viewModel(new)

        viewModel.setRestoreSettings(false)

        assertThat(viewModel.state.value.step).isEqualTo(BackupStep.Idle)
    }

    @Test
    fun `cancelling the preview forgets the backup, so it can't be restored by accident`() = runTest {
        old.tasks.create(TaskDraft(title = "Call the bank"))
        val file = fileOf()
        val viewModel = viewModel(new)
        viewModel.onFilePicked { file }.join()
        viewModel.submitRestorePassword(password)?.join()

        viewModel.cancel()

        assertThat(viewModel.state.value.step).isEqualTo(BackupStep.Idle)
        assertThat(viewModel.confirmRestore()).isNull()
        assertThat(new.database.taskDao().getAll()).isEmpty()
    }

    @Test
    fun `a restore that fails halfway is said, not a crash`() = runTest {
        val file = fileOf()
        val viewModel = viewModel(new)
        viewModel.onFilePicked { file }.join()
        viewModel.submitRestorePassword(password)?.join()
        serviceFailure = IllegalStateException("database closed")

        viewModel.confirmRestore()?.join()

        assertThat((viewModel.state.value.step as BackupStep.Finished).result).isEqualTo(BackupResult.ReadFailed)
    }

    @Test
    fun `finishing returns to the start, ready for the next backup`() = runTest {
        val viewModel = viewModel(old)
        viewModel.makeBackup()
        viewModel.savedBytes()

        viewModel.cancel()

        assertThat(viewModel.state.value).isEqualTo(BackupUiState())
    }
}

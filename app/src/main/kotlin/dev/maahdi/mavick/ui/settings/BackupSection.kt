package dev.maahdi.mavick.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.maahdi.mavick.R
import dev.maahdi.mavick.backup.BackupProblem
import dev.maahdi.mavick.backup.RestorePreview
import dev.maahdi.mavick.time.DueFormatter
import java.time.Instant
import java.time.ZoneId

/** What the backup section can ask for; the screen's route connects each to the view model and Android. */
data class BackupActions(
    val onStartBackup: () -> Unit,
    val onNewPassword: (password: String, confirm: String) -> Unit,
    val onStartRestore: () -> Unit,
    val onRestorePassword: (password: String) -> Unit,
    val onRestoreSettings: (Boolean) -> Unit,
    val onConfirmRestore: () -> Unit,
    val onCancel: () -> Unit,
)

/** Settings › Backup: when the last backup was made, the two buttons, and the dialogs of each flow. */
@Composable
fun BackupSection(
    lastBackupAt: Instant?,
    state: BackupUiState,
    now: Instant,
    zone: ZoneId,
    use24Hour: Boolean,
    actions: BackupActions,
) {
    Text(stringResource(R.string.settings_backup), style = MaterialTheme.typography.titleMedium)
    Text(
        if (lastBackupAt == null) {
            stringResource(R.string.backup_never)
        } else {
            stringResource(R.string.backup_last, DueFormatter.ago(lastBackupAt, now))
        },
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        stringResource(R.string.backup_summary),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = actions.onStartBackup, enabled = state.step == BackupStep.Idle) { Text(stringResource(R.string.backup_now)) }
        OutlinedButton(onClick = actions.onStartRestore, enabled = state.step == BackupStep.Idle) { Text(stringResource(R.string.backup_restore)) }
    }

    when (val step = state.step) {
        BackupStep.Idle, BackupStep.ChoosingPlace -> Unit
        BackupStep.NewPassword -> NewPasswordDialog(state.passwordProblem, actions.onNewPassword, actions.onCancel)
        BackupStep.RestorePassword -> RestorePasswordDialog(state.passwordProblem, actions.onRestorePassword, actions.onCancel)
        BackupStep.Working -> WorkingDialog()
        is BackupStep.Previewing -> PreviewDialog(step, now, zone, use24Hour, actions)
        is BackupStep.Finished -> ResultDialog(step.result, actions.onCancel)
    }
}

@Composable
private fun NewPasswordDialog(problem: PasswordProblem?, onSubmit: (String, String) -> Unit, onCancel: () -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.backup_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_password_hint), style = MaterialTheme.typography.bodyMedium)
                PasswordField(password, { password = it }, stringResource(R.string.backup_password_label), isError = problem == PasswordProblem.TOO_SHORT)
                PasswordField(confirm, { confirm = it }, stringResource(R.string.backup_password_confirm), isError = problem == PasswordProblem.MISMATCH)
                problem?.let {
                    Text(
                        stringResource(
                            when (it) {
                                PasswordProblem.TOO_SHORT -> R.string.backup_password_short
                                PasswordProblem.MISMATCH -> R.string.backup_password_mismatch
                                PasswordProblem.WRONG -> R.string.backup_password_wrong
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(password, confirm) }) { Text(stringResource(R.string.backup_make)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}

@Composable
private fun RestorePasswordDialog(problem: PasswordProblem?, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.backup_open_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_open_hint), style = MaterialTheme.typography.bodyMedium)
                PasswordField(password, { password = it }, stringResource(R.string.backup_password_label), isError = problem == PasswordProblem.WRONG)
                if (problem == PasswordProblem.WRONG) {
                    Text(stringResource(R.string.backup_password_wrong), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(password) }) { Text(stringResource(R.string.backup_open)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String, isError: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Not dismissable: the work takes a moment and can't be taken back half-way. */
@Composable
private fun WorkingDialog() {
    AlertDialog(
        onDismissRequest = {},
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text(stringResource(R.string.backup_working))
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun PreviewDialog(step: BackupStep.Previewing, now: Instant, zone: ZoneId, use24Hour: Boolean, actions: BackupActions) {
    val preview = step.preview
    AlertDialog(
        onDismissRequest = actions.onCancel,
        title = { Text(stringResource(R.string.backup_preview_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        R.string.backup_preview_made,
                        DueFormatter.moment(preview.createdAt, zone, now.atZone(zone).toLocalDate(), use24Hour),
                    ),
                )
                Text(pluralStringResource(R.plurals.backup_preview_tasks, preview.tasksInBackup, preview.tasksInBackup))
                Text(previewChanges(preview), style = MaterialTheme.typography.bodyMedium)
                if (preview.skipped > 0) {
                    Text(
                        pluralStringResource(R.plurals.backup_preview_skipped, preview.skipped, preview.skipped),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(
                    Modifier.fillMaxWidth().toggleable(value = step.restoreSettings, role = Role.Checkbox, onValueChange = actions.onRestoreSettings),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = step.restoreSettings, onCheckedChange = null)
                    Text(stringResource(R.string.backup_preview_settings), modifier = Modifier.padding(start = 12.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = actions.onConfirmRestore) { Text(stringResource(R.string.backup_restore_confirm)) } },
        dismissButton = { TextButton(onClick = actions.onCancel) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}

@Composable
private fun previewChanges(preview: RestorePreview): String {
    if (!preview.changesAnything) return stringResource(R.string.backup_preview_nothing)
    val parts = listOfNotNull(
        preview.tasksAdded.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.backup_preview_added, it, it) },
        preview.tasksUpdated.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.backup_preview_updated, it, it) },
        preview.rulesAdded.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.backup_preview_rules, it, it) },
    )
    val kept = preview.tasksKeptNewer.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.backup_preview_kept, it, it) }
    return (parts + listOfNotNull(kept)).joinToString("\n")
}

@Composable
private fun ResultDialog(result: BackupResult, onDone: () -> Unit) {
    val text = when (result) {
        is BackupResult.Saved -> pluralStringResource(R.plurals.backup_saved, result.tasks, result.tasks)
        BackupResult.SaveFailed -> stringResource(R.string.backup_save_failed)
        is BackupResult.Restored -> restoredText(result)
        is BackupResult.Problem -> stringResource(
            when (result.problem) {
                BackupProblem.NOT_A_BACKUP -> R.string.backup_not_a_backup
                BackupProblem.NEWER_FORMAT -> R.string.backup_newer
                BackupProblem.WRONG_PASSWORD_OR_DAMAGED -> R.string.backup_password_wrong
            },
        )
        BackupResult.ReadFailed -> stringResource(R.string.backup_read_failed)
        BackupResult.TooBig -> stringResource(R.string.backup_too_big)
    }
    AlertDialog(
        onDismissRequest = onDone,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.dialog_ok)) } },
    )
}

@Composable
private fun restoredText(result: BackupResult.Restored): String {
    val report = result.report
    val changed = report.tasksAdded + report.tasksUpdated
    return if (changed == 0 && report.rulesAdded == 0 && !report.settingsRestored) {
        stringResource(R.string.backup_restored_nothing)
    } else {
        listOfNotNull(
            pluralStringResource(R.plurals.backup_restored_tasks, changed, changed),
            report.rulesAdded.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.backup_restored_rules, it, it) },
            stringResource(R.string.backup_restored_settings).takeIf { report.settingsRestored },
        ).joinToString("\n")
    }
}

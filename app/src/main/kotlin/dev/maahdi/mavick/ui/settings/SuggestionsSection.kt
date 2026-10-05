package dev.maahdi.mavick.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.maahdi.mavick.R
import dev.maahdi.mavick.ai.AiPause
import dev.maahdi.mavick.ai.AiPauseRules
import dev.maahdi.mavick.ai.AiStatus
import dev.maahdi.mavick.ai.ImportProblem
import dev.maahdi.mavick.ai.ModelCheck
import dev.maahdi.mavick.data.settings.AppSettings
import dev.maahdi.mavick.time.DueFormatter
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** How the last accuracy-check export went. */
sealed interface ExportOutcome {
    data class Saved(val count: Int) : ExportOutcome

    data object Failed : ExportOutcome
}

/**
 * Settings › Suggestions: the switch, the AI model (import, check, remove), what the AI did, and the
 * export for an accuracy check.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuggestionsSection(
    settings: AppSettings,
    ai: AiSettingsState,
    exportOutcome: ExportOutcome?,
    now: Instant,
    zone: ZoneId,
    use24Hour: Boolean,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onImportModel: () -> Unit,
    onCheckModel: () -> Unit,
    onRemoveModel: () -> Unit,
    onTurnModelOnAgain: () -> Unit,
    onExportMessages: () -> Unit,
) {
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmExport by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Text(stringResource(R.string.settings_suggestions), style = MaterialTheme.typography.titleMedium)
    SwitchRow(
        title = stringResource(R.string.settings_suggestions_switch),
        summary = stringResource(R.string.settings_suggestions_summary),
        checked = settings.suggestionsEnabled,
        onCheckedChange = { enabled -> onChange { it.copy(suggestionsEnabled = enabled) } },
    )

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.ai_model), style = MaterialTheme.typography.bodyLarge)
        val model = ai.model
        if (model == null) {
            Text(stringResource(R.string.ai_model_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(
                stringResource(R.string.ai_model_info, model.name, Formatter.formatShortFileSize(context, model.sizeBytes)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            modelState(ai.status, ai.usable, now, zone, use24Hour)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ai.importing?.let { progress ->
            val total = progress.total
            Text(
                if (total != null && total > 0) {
                    stringResource(R.string.ai_model_importing_percent, (progress.copied * 100 / total).toInt())
                } else {
                    stringResource(R.string.ai_model_importing, Formatter.formatShortFileSize(context, progress.copied))
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (total != null && total > 0) {
                LinearProgressIndicator(progress = { (progress.copied.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        if (ai.checking) {
            Text(stringResource(R.string.ai_model_checking), style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        ai.outcome?.let { outcome ->
            Text(outcomeText(outcome), style = MaterialTheme.typography.bodyMedium, color = outcomeColor(outcome))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onImportModel, enabled = !ai.busy) { Text(stringResource(R.string.ai_model_import)) }
            if (model != null) {
                if (ai.status.modelSwitchedOff) {
                    OutlinedButton(onClick = onTurnModelOnAgain, enabled = !ai.busy) { Text(stringResource(R.string.ai_model_turn_on)) }
                } else {
                    OutlinedButton(onClick = onCheckModel, enabled = !ai.busy) { Text(stringResource(R.string.ai_model_check)) }
                }
                TextButton(onClick = { confirmRemove = true }, enabled = !ai.busy) {
                    Text(stringResource(R.string.ai_model_remove), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (model == null) {
            Text(stringResource(R.string.ai_model_import_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    activity(ai.status, now)?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Column(Modifier.fillMaxWidth().clickable { confirmExport = true }.padding(vertical = 4.dp)) {
        Text(stringResource(R.string.ai_export), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.ai_export_summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (exportOutcome) {
            is ExportOutcome.Saved -> Text(
                pluralStringResource(R.plurals.ai_export_saved, exportOutcome.count, exportOutcome.count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            ExportOutcome.Failed -> Text(stringResource(R.string.ai_export_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            null -> Unit
        }
    }

    if (confirmExport) {
        AlertDialog(
            onDismissRequest = { confirmExport = false },
            title = { Text(stringResource(R.string.ai_export_confirm_title)) },
            text = { Text(stringResource(R.string.ai_export_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmExport = false
                    onExportMessages()
                }) { Text(stringResource(R.string.ai_export_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmExport = false }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }

    val model = ai.model
    if (confirmRemove && model != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.ai_model_remove_title)) },
            text = { Text(stringResource(R.string.ai_model_remove_text, Formatter.formatShortFileSize(context, model.sizeBytes))) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemoveModel()
                }) { Text(stringResource(R.string.ai_model_remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
}

/** "Answers in about 6.1 s", or why it isn't running. Null when there's nothing to say yet. */
@Composable
private fun modelState(status: AiStatus, usable: Boolean, now: Instant, zone: ZoneId, use24Hour: Boolean): String? {
    val retryAt = status.retryAt()
    val average = status.averageAnswerMillis
    return when {
        status.modelSwitchedOff -> stringResource(R.string.ai_model_switched_off)
        !usable && status.inProblemPause(now) && retryAt != null -> stringResource(
            R.string.ai_model_problem,
            status.problem.orEmpty(),
            DueFormatter.moment(retryAt, zone, now.atZone(zone).toLocalDate(), use24Hour),
        )
        average != null -> stringResource(R.string.ai_model_average, seconds(average))
        else -> null
    }
}

/** "120 messages checked · 4 suggestions · last looked 5 min ago", and any pause. */
@Composable
private fun activity(status: AiStatus, now: Instant): String? {
    if (status.checked == 0L && status.pause == null) return null
    val parts = listOfNotNull(
        pluralStringResource(R.plurals.ai_checked, status.checked.toInt(), status.checked.toInt()),
        pluralStringResource(R.plurals.ai_suggested, status.suggested.toInt(), status.suggested.toInt()),
        status.lastRunAt?.let { stringResource(R.string.ai_last_run, DueFormatter.ago(it, now)) },
        status.pause?.let { pauseText(it) },
    )
    return parts.joinToString(" · ")
}

@Composable
private fun pauseText(pause: AiPause): String = when (pause) {
    AiPause.BATTERY_LOW -> stringResource(R.string.ai_paused_battery_low, AiPauseRules.LOW_BATTERY_PERCENT)
    AiPause.BATTERY_SAVER -> stringResource(R.string.ai_paused_battery_saver)
    AiPause.HOT -> stringResource(R.string.ai_paused_hot)
}

@Composable
private fun outcomeText(outcome: ModelOutcome): String = when (outcome) {
    is ModelOutcome.Imported -> stringResource(R.string.ai_model_imported) + " " + checkText(outcome.check)
    is ModelOutcome.Checked -> checkText(outcome.check)
    is ModelOutcome.Rejected -> stringResource(
        when (outcome.problem) {
            ImportProblem.NOT_A_MODEL -> R.string.ai_import_not_a_model
            ImportProblem.TOO_SMALL -> R.string.ai_import_too_small
            ImportProblem.NO_SPACE -> R.string.ai_import_no_space
            ImportProblem.INCOMPLETE -> R.string.ai_import_incomplete
            ImportProblem.COPY_DAMAGED -> R.string.ai_import_damaged
            ImportProblem.READ_FAILED -> R.string.ai_import_read_failed
        },
    )
    ModelOutcome.Removed -> stringResource(R.string.ai_model_removed)
}

@Composable
private fun checkText(check: ModelCheck): String = when (check) {
    is ModelCheck.Worked -> stringResource(R.string.ai_check_worked, seconds(check.millis))
    ModelCheck.NotUsable -> stringResource(R.string.ai_check_not_usable)
    ModelCheck.BadAnswer -> stringResource(R.string.ai_check_bad_answer)
    is ModelCheck.Failed -> stringResource(R.string.ai_check_failed, check.error)
}

@Composable
private fun outcomeColor(outcome: ModelOutcome) = when {
    outcome is ModelOutcome.Rejected -> MaterialTheme.colorScheme.error
    outcome is ModelOutcome.Imported && outcome.check !is ModelCheck.Worked -> MaterialTheme.colorScheme.error
    outcome is ModelOutcome.Checked && outcome.check !is ModelCheck.Worked -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.primary
}

/** "6.1" from 6,100 milliseconds. */
private fun seconds(millis: Long): String = String.format(Locale.ENGLISH, "%.1f", millis / 1000.0)

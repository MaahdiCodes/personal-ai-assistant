@file:OptIn(ExperimentalMaterial3Api::class)

package dev.maahdi.mavick.ui.editor

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.maahdi.mavick.AppContainer
import dev.maahdi.mavick.R
import dev.maahdi.mavick.data.task.TaskPriority
import dev.maahdi.mavick.time.DueFormatter
import dev.maahdi.mavick.ui.Destination
import dev.maahdi.mavick.ui.components.TimePickerDialog
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun EditorRoute(container: AppContainer, destination: Destination.Editor, onClose: () -> Unit) {
    val viewModel: EditorViewModel = viewModel(
        key = "editor-${destination.sessionId}",
        factory = EditorViewModel.factory(container, destination.taskId, destination.draft),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)
    TaskEditorScreen(
        state = state,
        today = LocalDate.now(container.clock()),
        workDays = settings.workDays,
        use24Hour = DateFormat.is24HourFormat(LocalContext.current),
        onChange = viewModel::edit,
        onSave = { viewModel.save(onClose) },
        onDelete = { viewModel.delete(onClose) },
        onClose = onClose,
    )
}

@Composable
fun TaskEditorScreen(
    state: EditorState,
    today: LocalDate,
    workDays: Set<DayOfWeek>,
    use24Hour: Boolean,
    onChange: ((EditorState) -> EditorState) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var picker by remember { mutableStateOf(Picker.NONE) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.editor_new_title else R.string.editor_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = !state.loading && !state.missing) {
                        Text(stringResource(R.string.editor_save))
                    }
                },
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding).fillMaxSize()
        when {
            state.missing -> Text(stringResource(R.string.editor_task_missing), modifier = contentModifier.padding(24.dp))
            state.loading -> Text(stringResource(R.string.editor_loading), modifier = contentModifier.padding(24.dp))
            else -> EditorFields(state, today, workDays, use24Hour, onChange, onPick = { picker = it }, onDelete = { confirmDelete = true }, contentModifier)
        }
    }

    when (picker) {
        Picker.DATE -> DateDialog(
            initial = state.dueDate ?: today,
            onPicked = { date -> onChange { it.copy(dueDate = date) } },
            onDismiss = { picker = Picker.NONE },
        )
        Picker.TIME -> TimePickerDialog(
            initial = state.dueTime ?: LocalTime.of(9, 0),
            use24Hour = use24Hour,
            onPicked = { time -> onChange { it.copy(dueTime = time) } },
            onDismiss = { picker = Picker.NONE },
        )
        Picker.REMINDER -> TimePickerDialog(
            initial = state.effectiveReminderTime,
            use24Hour = use24Hour,
            onPicked = { time -> onChange { it.copy(reminderTime = time, reminderOn = true) } },
            onDismiss = { picker = Picker.NONE },
        )
        Picker.NONE -> Unit
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.editor_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.editor_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
}

private enum class Picker { NONE, DATE, TIME, REMINDER }

@Composable
private fun EditorFields(
    state: EditorState,
    today: LocalDate,
    workDays: Set<DayOfWeek>,
    use24Hour: Boolean,
    onChange: ((EditorState) -> EditorState) -> Unit,
    onPick: (Picker) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = state.title,
            onValueChange = { title -> onChange { it.copy(title = title) } },
            label = { Text(stringResource(R.string.editor_title_label)) },
            isError = state.showTitleError,
            supportingText = if (state.showTitleError) {
                { Text(stringResource(R.string.editor_title_required)) }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.notes,
            onValueChange = { notes -> onChange { it.copy(notes = notes) } },
            label = { Text(stringResource(R.string.editor_notes_label)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        val notSet = stringResource(R.string.editor_not_set)
        ValueRow(
            label = stringResource(R.string.editor_date),
            value = state.dueDate?.let { DueFormatter.dayLabel(it, today) } ?: notSet,
            onClick = { onPick(Picker.DATE) },
            onClear = if (state.dueDate != null) {
                { onChange { it.copy(dueDate = null, dueTime = null, reminderOn = false, repeatChoice = RepeatChoice.NONE) } }
            } else {
                null
            },
        )
        ValueRow(
            label = stringResource(R.string.editor_time),
            value = state.dueTime?.let { DueFormatter.time(it, use24Hour) } ?: notSet,
            onClick = { onPick(Picker.TIME) },
            onClear = if (state.dueTime != null) {
                { onChange { it.copy(dueTime = null) } }
            } else {
                null
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.editor_reminder), style = MaterialTheme.typography.titleMedium)
                if (state.reminderOn) {
                    Text(
                        DueFormatter.time(state.effectiveReminderTime, use24Hour),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onPick(Picker.REMINDER) }.padding(vertical = 4.dp),
                    )
                }
            }
            Switch(checked = state.reminderOn, onCheckedChange = { on -> onChange { it.copy(reminderOn = on) } })
        }

        RepeatRow(state, today, workDays, onChange)

        Text(stringResource(R.string.editor_priority), style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val labels = listOf(R.string.priority_low, R.string.priority_normal, R.string.priority_high)
            TaskPriority.entries.forEachIndexed { index, priority ->
                SegmentedButton(
                    selected = state.priority == priority,
                    onClick = { onChange { it.copy(priority = priority) } },
                    shape = SegmentedButtonDefaults.itemShape(index, TaskPriority.entries.size),
                ) { Text(stringResource(labels[index])) }
            }
        }

        if (!state.isNew) {
            OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.editor_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit, onClear: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(onClick = onClick).padding(vertical = 4.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(value, color = MaterialTheme.colorScheme.primary)
        }
        if (onClear != null) TextButton(onClick = onClear) { Text(stringResource(R.string.editor_clear)) }
    }
}

@Composable
private fun RepeatRow(state: EditorState, today: LocalDate, workDays: Set<DayOfWeek>, onChange: ((EditorState) -> EditorState) -> Unit) {
    val anchor = state.dueDate ?: today
    val customRule = state.customRule
    val customAvailable = customRule != null && EditorState.choiceFor(customRule, state.dueDate, workDays) == RepeatChoice.CUSTOM
    val choices = RepeatChoice.entries.filter { it != RepeatChoice.CUSTOM || customAvailable }
    val labels = choices.associateWith { choice ->
        when (choice) {
            RepeatChoice.NONE -> stringResource(R.string.repeat_none)
            RepeatChoice.DAILY -> stringResource(R.string.repeat_daily)
            RepeatChoice.WORK_DAYS -> stringResource(R.string.repeat_work_days)
            RepeatChoice.WEEKLY -> stringResource(R.string.repeat_weekly, anchor.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
            RepeatChoice.MONTHLY -> stringResource(R.string.repeat_monthly, DueFormatter.ordinal(anchor.dayOfMonth))
            RepeatChoice.YEARLY -> stringResource(
                R.string.repeat_yearly,
                "${anchor.dayOfMonth} ${anchor.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}",
            )
            RepeatChoice.CUSTOM -> customRule?.let { DueFormatter.repeatLabel(it, workDays) }.orEmpty()
        }
    }
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Column(Modifier.fillMaxWidth().clickable { menuOpen = true }.padding(vertical = 4.dp)) {
            Text(stringResource(R.string.editor_repeat), style = MaterialTheme.typography.titleMedium)
            Text(labels[state.repeatChoice].orEmpty(), color = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(labels.getValue(choice)) },
                    onClick = {
                        onChange { it.copy(repeatChoice = choice) }
                        menuOpen = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DateDialog(initial: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The Material date picker works in UTC milliseconds at midnight.
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text(stringResource(R.string.dialog_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    ) {
        DatePicker(state = pickerState)
    }
}

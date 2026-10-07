package dev.maahdi.mavick.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.maahdi.mavick.R
import dev.maahdi.mavick.calendar.DeviceCalendar
import dev.maahdi.mavick.data.settings.AppSettings

/**
 * Settings › Calendar: the switch, the calendar chosen, what is wrong (permission off, calendar
 * gone) and the picker. Switching on goes through the permission prompt and the picker, so the
 * switch only turns on once a calendar is chosen.
 */
@Composable
fun CalendarSection(
    settings: AppSettings,
    state: CalendarSettingsState,
    onSwitch: (Boolean) -> Unit,
    onChange: () -> Unit,
    onPick: (DeviceCalendar) -> Unit,
    onClosePicker: () -> Unit,
    onFixPermission: () -> Unit,
    onClashSwitch: (Boolean) -> Unit,
    onChooseCheckedCalendars: () -> Unit,
    onCheckedCalendars: (Set<Long>) -> Unit,
    onCloseCheckedPicker: () -> Unit,
) {
    Text(stringResource(R.string.settings_calendar), style = MaterialTheme.typography.titleMedium)
    SwitchRow(
        title = stringResource(R.string.settings_calendar_switch),
        summary = stringResource(R.string.settings_calendar_summary),
        checked = settings.calendarEnabled,
        onCheckedChange = onSwitch,
    )

    val chosenName = settings.calendarName
    if (settings.calendarEnabled && chosenName != null) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onChange).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.calendar_chosen), style = MaterialTheme.typography.bodyLarge)
                Text(chosenName, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = onChange) { Text(stringResource(R.string.calendar_change)) }
        }
    }

    // Events can outlive the switch: they stay until they could be removed (which needs the permission).
    val needsPermission = !state.permissionGranted &&
        (settings.calendarEnabled || settings.clashCheckEnabled || state.permissionDenied || state.eventCount > 0)
    if (needsPermission) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.calendar_permission_off),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onFixPermission) { Text(stringResource(R.string.status_fix)) }
        }
    } else if (settings.calendarEnabled && state.loaded && state.calendars.none { it.id == settings.calendarId }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.calendar_missing),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onChange) { Text(stringResource(R.string.calendar_change)) }
        }
    }
    if (state.working) {
        Text(stringResource(R.string.calendar_working), style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    } else if (state.eventCount > 0) {
        Text(
            pluralStringResource(R.plurals.calendar_events, state.eventCount, state.eventCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Text(
        stringResource(R.string.settings_calendar_privacy),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    SwitchRow(
        title = stringResource(R.string.settings_clashes_switch),
        summary = stringResource(R.string.settings_clashes_summary),
        checked = settings.clashCheckEnabled,
        onCheckedChange = onClashSwitch,
    )
    if (settings.clashCheckEnabled) {
        // Only calendars still there count; none left means every calendar is checked.
        val checked = state.allCalendars.count { it.id in settings.clashCalendarIds }.takeIf { state.loaded } ?: settings.clashCalendarIds.size
        Column(Modifier.fillMaxWidth().clickable(onClick = onChooseCheckedCalendars).padding(vertical = 4.dp)) {
            Text(stringResource(R.string.clash_calendars), style = MaterialTheme.typography.bodyLarge)
            Text(
                if (checked == 0) stringResource(R.string.clash_calendars_all) else pluralStringResource(R.plurals.clash_calendars_some, checked, checked),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    if (state.pickingChecked) {
        CheckedCalendarsPicker(state, selected = settings.clashCalendarIds, onDone = onCheckedCalendars, onDismiss = onCloseCheckedPicker)
    }
    if (state.picking) CalendarPicker(state, current = settings.calendarId, onPick = onPick, onDismiss = onClosePicker)
}

/** Which calendars to check for clashes. None ticked means every calendar. */
@Composable
private fun CheckedCalendarsPicker(state: CalendarSettingsState, selected: Set<Long>, onDone: (Set<Long>) -> Unit, onDismiss: () -> Unit) {
    var chosen by remember { mutableStateOf(selected.filter { id -> state.allCalendars.any { it.id == id } }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clash_pick_title)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                when {
                    !state.loaded -> Text(stringResource(R.string.calendar_pick_loading))
                    state.allCalendars.isEmpty() -> Text(stringResource(R.string.calendar_pick_none))
                    else -> {
                        CheckRow(label = stringResource(R.string.clash_pick_all), detail = null, checked = chosen.isEmpty(), onToggle = { chosen = emptySet() })
                        state.allCalendars.forEach { calendar ->
                            val account = calendar.account.takeIf { it.isNotBlank() && it != calendar.name }
                            CheckRow(
                                label = calendar.name,
                                detail = account,
                                checked = calendar.id in chosen,
                                onToggle = { chosen = if (calendar.id in chosen) chosen - calendar.id else chosen + calendar.id },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(chosen) }) { Text(stringResource(R.string.clash_pick_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}

@Composable
private fun CheckRow(label: String, detail: String?, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CalendarPicker(state: CalendarSettingsState, current: Long?, onPick: (DeviceCalendar) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calendar_pick_title)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    !state.loaded -> Text(stringResource(R.string.calendar_pick_loading))
                    state.calendars.isEmpty() -> Text(stringResource(R.string.calendar_pick_none))
                    else -> state.calendars.forEach { calendar ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = calendar.id == current, role = Role.RadioButton) { onPick(calendar) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = calendar.id == current, onClick = null)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(calendar.name, style = MaterialTheme.typography.bodyLarge)
                                if (calendar.account.isNotBlank() && calendar.account != calendar.name) {
                                    Text(calendar.account, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}

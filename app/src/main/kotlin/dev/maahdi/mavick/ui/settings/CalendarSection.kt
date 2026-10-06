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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    val needsPermission = !state.permissionGranted && (settings.calendarEnabled || state.permissionDenied || state.eventCount > 0)
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

    if (state.picking) CalendarPicker(state, current = settings.calendarId, onPick = onPick, onDismiss = onClosePicker)
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

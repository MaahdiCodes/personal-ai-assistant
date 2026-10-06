@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.maahdi.mavick.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import dev.maahdi.mavick.capture.ReadingState
import dev.maahdi.mavick.data.settings.AppSettings
import dev.maahdi.mavick.health.StorageStatus
import dev.maahdi.mavick.time.DateOrder
import dev.maahdi.mavick.time.DueFormatter
import dev.maahdi.mavick.ui.components.TimePickerDialog
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** What the Health section shows; measured on the phone each time Settings opens. */
data class HealthInfo(
    val storage: StorageStatus = StorageStatus.Checking,
    val hasInternetPermission: Boolean = false,
    val notificationsAllowed: Boolean = true,
    val exactAlarmsAllowed: Boolean = true,
    val batteryUnrestricted: Boolean = true,
    val appLockAvailable: Boolean = true,
    /** "Notification access", needed to read messages (Phase 2). */
    val notificationAccess: Boolean = false,
    val reading: ReadingState = ReadingState.NO_ACCESS,
    /** When a supported app's notification last arrived. */
    val lastSeenAt: Instant? = null,
    /** How often Android stopped message reading in the last 7 days. */
    val disconnectsThisWeek: Int = 0,
    val isXiaomi: Boolean = false,
    val versionName: String = "",
    val isDebugBuild: Boolean = false,
)

/** Days a saved message is kept, offered in Settings. */
private val RETENTION_CHOICES = listOf(1, 3, 7, 14, 30, 60, 90)

/** Days without messages before a warning; 0 means never. */
private val WARNING_CHOICES = listOf(0, 1, 2, 3, 7)

/** The week as shown in Settings, starting on Sunday. */
private val WEEK = listOf(
    DayOfWeek.SUNDAY,
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    health: HealthInfo,
    ai: AiSettingsState,
    calendar: CalendarSettingsState,
    exportOutcome: ExportOutcome?,
    now: Instant,
    zone: ZoneId,
    use24Hour: Boolean,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onCalendarSwitch: (Boolean) -> Unit,
    onChangeCalendar: () -> Unit,
    onPickCalendar: (DeviceCalendar) -> Unit,
    onClosePicker: () -> Unit,
    onFixCalendarPermission: () -> Unit,
    onFixNotifications: () -> Unit,
    onFixBattery: () -> Unit,
    onFixNotificationAccess: () -> Unit,
    onRestartReading: () -> Unit,
    onOpenAutostart: () -> Unit,
    onOpenReading: () -> Unit,
    onDeleteAllMessages: () -> Unit,
    onImportModel: () -> Unit,
    onCheckModel: () -> Unit,
    onRemoveModel: () -> Unit,
    onTurnModelOnAgain: () -> Unit,
    onExportMessages: () -> Unit,
    onImportKeep: () -> Unit,
    onBack: () -> Unit,
) {
    var pickingBriefingTime by remember { mutableStateOf(false) }
    var confirmDeleteMessages by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SwitchRow(
                title = stringResource(R.string.settings_briefing),
                summary = stringResource(R.string.settings_briefing_summary),
                checked = settings.briefingEnabled,
                onCheckedChange = { enabled -> onChange { it.copy(briefingEnabled = enabled) } },
            )
            if (settings.briefingEnabled) {
                Column(Modifier.fillMaxWidth().clickable { pickingBriefingTime = true }.padding(vertical = 4.dp)) {
                    Text(stringResource(R.string.settings_briefing_time), style = MaterialTheme.typography.titleMedium)
                    Text(DueFormatter.time(settings.briefingTime, use24Hour), color = MaterialTheme.colorScheme.primary)
                }
            }
            HorizontalDivider()

            SwitchRow(
                title = stringResource(R.string.settings_app_lock),
                summary = if (health.appLockAvailable) {
                    stringResource(R.string.settings_app_lock_summary)
                } else {
                    stringResource(R.string.settings_app_lock_unavailable)
                },
                checked = settings.appLockEnabled,
                onCheckedChange = { enabled -> onChange { it.copy(appLockEnabled = enabled) } },
            )
            HorizontalDivider()

            Text(stringResource(R.string.settings_work_days), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_work_days_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WEEK.forEach { day ->
                    val selected = day in settings.workDays
                    FilterChip(
                        selected = selected,
                        // The last work day can't be removed: "every work day" needs at least one.
                        onClick = { onChange { it.copy(workDays = if (selected) it.workDays - day else it.workDays + day) } },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)) },
                    )
                }
            }
            HorizontalDivider()

            Text(stringResource(R.string.settings_date_format), style = MaterialTheme.typography.titleMedium)
            listOf(
                DateOrder.DAY_MONTH to R.string.settings_date_day_month,
                DateOrder.MONTH_DAY to R.string.settings_date_month_day,
            ).forEach { (order, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = settings.dateOrder == order, role = Role.RadioButton) { onChange { it.copy(dateOrder = order) } },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.dateOrder == order, onClick = null)
                    Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
                }
            }
            HorizontalDivider()

            MessagesSection(settings, onChange, onOpenReading, onDeleteAllMessages = { confirmDeleteMessages = true })
            HorizontalDivider()

            SuggestionsSection(
                settings = settings,
                ai = ai,
                exportOutcome = exportOutcome,
                now = now,
                zone = zone,
                use24Hour = use24Hour,
                onChange = onChange,
                onImportModel = onImportModel,
                onCheckModel = onCheckModel,
                onRemoveModel = onRemoveModel,
                onTurnModelOnAgain = onTurnModelOnAgain,
                onExportMessages = onExportMessages,
            )
            HorizontalDivider()

            CalendarSection(
                settings = settings,
                state = calendar,
                onSwitch = onCalendarSwitch,
                onChange = onChangeCalendar,
                onPick = onPickCalendar,
                onClosePicker = onClosePicker,
                onFixPermission = onFixCalendarPermission,
            )
            HorizontalDivider()

            Text(stringResource(R.string.settings_keep), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.fillMaxWidth().clickable(onClick = onImportKeep).padding(vertical = 4.dp)) {
                Text(stringResource(R.string.settings_keep_import), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.settings_keep_import_summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()

            HealthSection(
                health,
                now,
                autostartOn = settings.xiaomiAutostartOn,
                onAutostartChange = { on -> onChange { it.copy(xiaomiAutostartOn = on) } },
                onFixNotifications = onFixNotifications,
                onFixBattery = onFixBattery,
                onFixNotificationAccess = onFixNotificationAccess,
                onRestartReading = onRestartReading,
                onOpenAutostart = onOpenAutostart,
            )
        }
    }

    if (pickingBriefingTime) {
        TimePickerDialog(
            initial = settings.briefingTime,
            use24Hour = use24Hour,
            onPicked = { time -> onChange { it.copy(briefingTime = time) } },
            onDismiss = { pickingBriefingTime = false },
        )
    }

    if (confirmDeleteMessages) {
        AlertDialog(
            onDismissRequest = { confirmDeleteMessages = false },
            title = { Text(stringResource(R.string.delete_messages_confirm_title)) },
            text = { Text(stringResource(R.string.delete_messages_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteMessages = false
                    onDeleteAllMessages()
                }) { Text(stringResource(R.string.delete_messages_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteMessages = false }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
}

@Composable
private fun MessagesSection(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onOpenReading: () -> Unit,
    onDeleteAllMessages: () -> Unit,
) {
    Text(stringResource(R.string.settings_messages), style = MaterialTheme.typography.titleMedium)
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpenReading).padding(vertical = 4.dp)) {
        Text(stringResource(R.string.reading_title), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.settings_reading_summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ChoiceRow(
        title = stringResource(R.string.settings_retention),
        current = settings.messageRetentionDays,
        choices = RETENTION_CHOICES,
        label = { days -> pluralStringResource(R.plurals.days, days, days) },
        onPick = { days -> onChange { it.copy(messageRetentionDays = days) } },
    )
    ChoiceRow(
        title = stringResource(R.string.settings_warning),
        current = settings.readingWarningDays,
        choices = WARNING_CHOICES,
        label = { days -> if (days == 0) stringResource(R.string.warning_never) else pluralStringResource(R.plurals.days, days, days) },
        onPick = { days -> onChange { it.copy(readingWarningDays = days) } },
    )
    TextButton(onClick = onDeleteAllMessages) {
        Text(stringResource(R.string.settings_delete_messages), color = MaterialTheme.colorScheme.error)
    }
}

/** A setting with a few choices, picked from a menu. */
@Composable
private fun <T> ChoiceRow(title: String, current: T, choices: List<T>, label: @Composable (T) -> String, onPick: (T) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Column(Modifier.fillMaxWidth().clickable { menuOpen = true }.padding(vertical = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(label(current), color = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(text = { Text(label(choice)) }, onClick = {
                    menuOpen = false
                    onPick(choice)
                })
            }
        }
    }
}

@Composable
private fun HealthSection(
    health: HealthInfo,
    now: Instant,
    autostartOn: Boolean,
    onAutostartChange: (Boolean) -> Unit,
    onFixNotifications: () -> Unit,
    onFixBattery: () -> Unit,
    onFixNotificationAccess: () -> Unit,
    onRestartReading: () -> Unit,
    onOpenAutostart: () -> Unit,
) {
    Text(stringResource(R.string.settings_health), style = MaterialTheme.typography.titleMedium)
    StatusRow(
        label = stringResource(R.string.status_storage),
        value = when (val storage = health.storage) {
            StorageStatus.Checking -> stringResource(R.string.status_storage_checking)
            is StorageStatus.Ready -> stringResource(R.string.status_storage_ready)
            is StorageStatus.Failed -> storage.reason
        },
        isOk = when (health.storage) {
            StorageStatus.Checking -> null
            is StorageStatus.Ready -> true
            is StorageStatus.Failed -> false
        },
    )
    StatusRow(
        label = stringResource(R.string.status_internet),
        value = stringResource(if (health.hasInternetPermission) R.string.status_internet_present else R.string.status_internet_none),
        isOk = !health.hasInternetPermission,
    )
    StatusRow(
        label = stringResource(R.string.status_notifications),
        value = stringResource(if (health.notificationsAllowed) R.string.status_allowed else R.string.status_notifications_blocked),
        isOk = health.notificationsAllowed,
        onFix = onFixNotifications.takeUnless { health.notificationsAllowed },
    )
    StatusRow(
        label = stringResource(R.string.status_exact_alarms),
        value = stringResource(if (health.exactAlarmsAllowed) R.string.status_allowed else R.string.status_exact_alarms_blocked),
        isOk = health.exactAlarmsAllowed,
    )
    StatusRow(
        label = stringResource(R.string.status_battery),
        value = stringResource(if (health.batteryUnrestricted) R.string.status_battery_unrestricted else R.string.status_battery_restricted),
        isOk = health.batteryUnrestricted,
        onFix = onFixBattery.takeUnless { health.batteryUnrestricted },
    )
    StatusRow(
        label = stringResource(R.string.status_access),
        value = stringResource(if (health.notificationAccess) R.string.status_allowed else R.string.status_access_off),
        isOk = health.notificationAccess,
        onFix = onFixNotificationAccess.takeUnless { health.notificationAccess },
        detail = stringResource(R.string.access_restricted_hint).takeUnless { health.notificationAccess },
    )
    StatusRow(
        label = stringResource(R.string.status_reading),
        value = readingText(health, now),
        isOk = health.reading == ReadingState.OK,
        onFix = onRestartReading.takeIf { health.reading == ReadingState.NOT_CONNECTED },
        fixLabel = R.string.status_restart,
        detail = health.disconnectsThisWeek.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.reading_disconnects, it, it) },
    )
    if (health.isXiaomi) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = autostartOn, onCheckedChange = onAutostartChange)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.status_autostart), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.status_autostart_summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onOpenAutostart) { Text(stringResource(R.string.status_open)) }
        }
    }
    StatusRow(
        label = stringResource(R.string.status_background),
        value = stringResource(if (health.notificationAccess) R.string.status_background_reading else R.string.status_background_alarms),
        isOk = true,
    )
    Text(
        stringResource(
            R.string.version_label,
            health.versionName,
            stringResource(if (health.isDebugBuild) R.string.build_debug else R.string.build_release),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A setting that is on or off. The whole row toggles it, not just the switch. */
@Composable
internal fun SwitchRow(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** "Working · last message 5 min ago", "Stopped by Android", and so on. */
@Composable
private fun readingText(health: HealthInfo, now: Instant): String = when (health.reading) {
    ReadingState.NO_ACCESS -> stringResource(R.string.reading_off)
    ReadingState.NOT_CONNECTED -> stringResource(R.string.reading_not_connected)
    ReadingState.QUIET -> health.lastSeenAt?.let { stringResource(R.string.reading_quiet, DueFormatter.ago(it, now)) }
        ?: stringResource(R.string.reading_quiet_never)
    ReadingState.OK -> health.lastSeenAt?.let { stringResource(R.string.reading_ok_last, DueFormatter.ago(it, now)) }
        ?: stringResource(R.string.reading_ok_nothing)
}

/** One health line. [isOk] null means "still checking". */
@Composable
private fun StatusRow(
    label: String,
    value: String,
    isOk: Boolean?,
    onFix: (() -> Unit)? = null,
    @StringRes fixLabel: Int = R.string.status_fix,
    detail: String? = null,
) {
    val marker = when (isOk) {
        true -> "✓"
        false -> "✗"
        null -> "…"
    }
    val markerColor = when (isOk) {
        true -> MaterialTheme.colorScheme.primary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(marker, color = markerColor, style = MaterialTheme.typography.titleMedium)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onFix != null) TextButton(onClick = onFix) { Text(stringResource(fixLabel)) }
    }
}

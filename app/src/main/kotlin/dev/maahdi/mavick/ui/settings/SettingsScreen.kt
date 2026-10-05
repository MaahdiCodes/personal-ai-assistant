@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.maahdi.mavick.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.maahdi.mavick.R
import dev.maahdi.mavick.data.settings.AppSettings
import dev.maahdi.mavick.health.StorageStatus
import dev.maahdi.mavick.time.DateOrder
import dev.maahdi.mavick.time.DueFormatter
import dev.maahdi.mavick.ui.components.TimePickerDialog
import java.time.DayOfWeek
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
    val versionName: String = "",
    val isDebugBuild: Boolean = false,
)

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
    use24Hour: Boolean,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onFixNotifications: () -> Unit,
    onFixBattery: () -> Unit,
    onBack: () -> Unit,
) {
    var pickingBriefingTime by remember { mutableStateOf(false) }
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

            HealthSection(health, onFixNotifications, onFixBattery)
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
}

@Composable
private fun HealthSection(health: HealthInfo, onFixNotifications: () -> Unit, onFixBattery: () -> Unit) {
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
        label = stringResource(R.string.status_background),
        value = stringResource(R.string.status_background_alarms),
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

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** One health line. [isOk] null means "still checking". */
@Composable
private fun StatusRow(label: String, value: String, isOk: Boolean?, onFix: (() -> Unit)? = null) {
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
        }
        if (onFix != null) TextButton(onClick = onFix) { Text(stringResource(R.string.status_fix)) }
    }
}

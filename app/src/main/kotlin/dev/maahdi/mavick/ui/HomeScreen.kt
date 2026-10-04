package dev.maahdi.mavick.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.maahdi.mavick.R
import dev.maahdi.mavick.health.StorageStatus
import dev.maahdi.mavick.ui.theme.MavickTheme

@Composable
fun HomeScreen(
    checkStorage: suspend () -> StorageStatus,
    hasInternetPermission: Boolean,
    versionName: String,
    isDebugBuild: Boolean,
) {
    var storageStatus by remember { mutableStateOf<StorageStatus>(StorageStatus.Checking) }
    LaunchedEffect(Unit) { storageStatus = checkStorage() }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
            Text(
                stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            StatusRow(
                label = stringResource(R.string.status_storage),
                value = when (val status = storageStatus) {
                    StorageStatus.Checking -> stringResource(R.string.status_storage_checking)
                    is StorageStatus.Ready -> stringResource(R.string.status_storage_ready)
                    is StorageStatus.Failed -> status.reason
                },
                isOk = when (storageStatus) {
                    StorageStatus.Checking -> null
                    is StorageStatus.Ready -> true
                    is StorageStatus.Failed -> false
                },
            )
            StatusRow(
                label = stringResource(R.string.status_internet),
                value = stringResource(
                    if (hasInternetPermission) R.string.status_internet_present else R.string.status_internet_none,
                ),
                isOk = !hasInternetPermission,
            )
            StatusRow(
                label = stringResource(R.string.status_background),
                value = stringResource(R.string.status_background_none),
                isOk = true,
            )

            Text(
                stringResource(
                    R.string.version_label,
                    versionName,
                    stringResource(if (isDebugBuild) R.string.build_debug else R.string.build_release),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One status line. [isOk] null means "still checking". */
@Composable
private fun StatusRow(label: String, value: String, isOk: Boolean?) {
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
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(marker, color = markerColor, style = MaterialTheme.typography.titleMedium)
        Column {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    MavickTheme {
        HomeScreen(
            checkStorage = { StorageStatus.Ready(taskCount = 0) },
            hasInternetPermission = false,
            versionName = "0.1.0",
            isDebugBuild = true,
        )
    }
}

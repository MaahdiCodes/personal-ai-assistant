package dev.maahdi.mavick.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.text.format.DateFormat
import android.text.format.Formatter
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.maahdi.mavick.AppContainer
import dev.maahdi.mavick.R
import dev.maahdi.mavick.capture.ListenerRestart
import dev.maahdi.mavick.capture.ReadingHealth
import dev.maahdi.mavick.data.health.HealthEventType
import dev.maahdi.mavick.data.settings.AppSettings
import dev.maahdi.mavick.health.StorageStatus
import dev.maahdi.mavick.security.canAuthenticate
import dev.maahdi.mavick.ui.PhoneSettings
import dev.maahdi.mavick.ui.PickedFile
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsRoute(container: AppContainer, onOpenReading: () -> Unit, onOpenKeepImport: (uri: String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val aiViewModel: AiSettingsViewModel = viewModel(factory = AiSettingsViewModel.factory(container))
    val ai by aiViewModel.state.collectAsStateWithLifecycle()
    var health by remember { mutableStateOf(readHealth(context, container)) }
    var storage by remember { mutableStateOf<StorageStatus>(StorageStatus.Checking) }
    var disconnects by remember { mutableIntStateOf(0) }
    // The model file picked last, offered for deletion once Mavick has its own copy.
    var pickedModel by remember { mutableStateOf<Uri?>(null) }
    var exportOutcome by remember { mutableStateOf<ExportOutcome?>(null) }
    LifecycleResumeEffect(Unit) {
        // Re-read when coming back from Android's settings, where the user may have fixed something.
        health = readHealth(context, container)
        aiViewModel.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        storage = container.storageHealthCheck.run()
        disconnects = countRecentDisconnects(container)
    }
    BackHandler(onBack = onBack)
    val pickTakeout = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onOpenKeepImport(uri.toString())
    }
    val saveExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { exportOutcome = writeAccuracyExport(container, context, uri) }
    }
    val pickModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val file = PickedFile.from(context.contentResolver, uri)
            pickedModel = uri
            aiViewModel.import(file.name, file.sizeBytes, file::open)
        }
    }

    SettingsScreen(
        settings = settings,
        health = health.copy(storage = storage, disconnectsThisWeek = disconnects),
        ai = ai,
        exportOutcome = exportOutcome,
        now = Instant.now(container.clock()),
        zone = container.clock().zone,
        use24Hour = DateFormat.is24HourFormat(context),
        onChange = { change: (AppSettings) -> AppSettings ->
            val wasSuggesting = container.settings.current.suggestionsEnabled
            container.settings.update(change)
            container.reminderEngine.scheduleDailyAlarm()
            // Turned on: look at the messages waiting since.
            if (!wasSuggesting && container.settings.current.suggestionsEnabled) container.suggestionWorker.wake()
        },
        onFixNotifications = { PhoneSettings.open(context, PhoneSettings.notifications(context)) },
        onFixBattery = { PhoneSettings.open(context, PhoneSettings.battery()) },
        onFixNotificationAccess = { PhoneSettings.open(context, *PhoneSettings.notificationAccess(container.listenerComponent)) },
        onRestartReading = {
            ListenerRestart.restart(context.packageManager, container.listenerComponent)
            health = readHealth(context, container)
        },
        onOpenAutostart = { PhoneSettings.open(context, PhoneSettings.xiaomiAutostart()) },
        onOpenReading = onOpenReading,
        onDeleteAllMessages = {
            scope.launch {
                container.openMessages().deleteAll()
                container.clearSuggestionsNotificationIfNone()
            }
        },
        // Any file: phones don't know a type for .litertlm. The import checks the file itself.
        onImportModel = { pickModel.launch(arrayOf("*/*")) },
        onCheckModel = aiViewModel::check,
        onRemoveModel = aiViewModel::remove,
        onTurnModelOnAgain = aiViewModel::turnOnAgain,
        onExportMessages = { saveExport.launch("mavick-messages-${LocalDate.now(container.clock())}.csv") },
        onImportKeep = { pickTakeout.launch(TAKEOUT_TYPES) },
        onBack = onBack,
    )

    LaunchedEffect(ai.outcome) {
        // A refused import, or anything done since, leaves no copy to offer for deletion.
        if (ai.outcome != null && ai.outcome !is ModelOutcome.Imported) pickedModel = null
    }
    val imported = ai.outcome as? ModelOutcome.Imported
    val source = pickedModel
    if (imported != null && source != null) {
        DeleteDownloadedCopyDialog(
            name = imported.info.name,
            size = Formatter.formatShortFileSize(context, imported.info.sizeBytes),
            onDelete = { PickedFile.delete(context.contentResolver, source).also { deleted -> if (deleted) pickedModel = null } },
            onKeep = { pickedModel = null },
        )
    }
}

/** Writes the accuracy-check export to the file you chose. Any failure (storage, the file) says only that it failed. */
private suspend fun writeAccuracyExport(container: AppContainer, context: Context, uri: Uri): ExportOutcome = try {
    val file = container.exportForAccuracyCheck()
    withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(uri) ?: throw IOException("No access to the chosen file")
        output.use { it.write(file.csv.toByteArray(Charsets.UTF_8)) }
    }
    ExportOutcome.Saved(file.count)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.w("Mavick", "Export failed: ${e.javaClass.simpleName}")
    ExportOutcome.Failed
} catch (e: LinkageError) {
    ExportOutcome.Failed // the encryption library failed to load; Health explains it
}

/** How phones label a Takeout .zip; the import checks the file itself. */
private val TAKEOUT_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

/** After an import: the copy in Downloads is no longer needed. Tells you if it couldn't be deleted. */
@Composable
private fun DeleteDownloadedCopyDialog(name: String, size: String, onDelete: () -> Boolean, onKeep: () -> Unit) {
    var failed by remember { mutableStateOf(false) }
    if (failed) {
        AlertDialog(
            onDismissRequest = onKeep,
            text = { Text(stringResource(R.string.ai_delete_source_failed, name)) },
            confirmButton = { TextButton(onClick = onKeep) { Text(stringResource(R.string.dialog_ok)) } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text(stringResource(R.string.ai_delete_source_title)) },
        text = { Text(stringResource(R.string.ai_delete_source_text, name, size)) },
        confirmButton = { TextButton(onClick = { if (!onDelete()) failed = true }) { Text(stringResource(R.string.ai_delete_source_confirm)) } },
        dismissButton = { TextButton(onClick = onKeep) { Text(stringResource(R.string.ai_delete_source_keep)) } },
    )
}

/** Everything in the Health section except what needs the database. */
private fun readHealth(context: Context, container: AppContainer): HealthInfo {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    val access = container.hasNotificationAccess()
    val status = container.captureStatus.snapshot()
    val quietAfter = Duration.ofDays(maxOf(1, container.settings.current.readingWarningDays).toLong())
    return HealthInfo(
        hasInternetPermission = context.checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED,
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        exactAlarmsAllowed = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
        batteryUnrestricted = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
        appLockAvailable = canAuthenticate(context),
        notificationAccess = access,
        reading = ReadingHealth.state(access, status, Instant.now(container.clock()), quietAfter),
        lastSeenAt = status.lastSeenAt,
        isXiaomi = PhoneSettings.isXiaomi(),
        versionName = packageInfo.versionName.orEmpty(),
        isDebugBuild = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
    )
}

/** How often Android stopped message reading in the last 7 days; 0 if storage can't be read. */
private suspend fun countRecentDisconnects(container: AppContainer): Int = withContext(Dispatchers.IO) {
    val since = Instant.now(container.clock()).minus(Duration.ofDays(7))
    try {
        container.healthEvents.count(HealthEventType.LISTENER_DISCONNECTED, since)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("Mavick", "Listener history unavailable: ${e.javaClass.simpleName}")
        0
    } catch (e: LinkageError) {
        0 // the encryption library failed to load; storage health explains it
    }
}

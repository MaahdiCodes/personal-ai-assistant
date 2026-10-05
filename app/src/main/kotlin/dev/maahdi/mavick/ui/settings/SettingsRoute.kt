package dev.maahdi.mavick.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.maahdi.mavick.AppContainer
import dev.maahdi.mavick.data.settings.AppSettings
import dev.maahdi.mavick.health.StorageStatus
import dev.maahdi.mavick.security.canAuthenticate

@Composable
fun SettingsRoute(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    var health by remember { mutableStateOf(readHealth(context)) }
    var storage by remember { mutableStateOf<StorageStatus>(StorageStatus.Checking) }
    LifecycleResumeEffect(Unit) {
        // Re-read when coming back from Android's settings, where the user may have fixed something.
        health = readHealth(context)
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) { storage = container.storageHealthCheck.run() }
    BackHandler(onBack = onBack)

    SettingsScreen(
        settings = settings,
        health = health.copy(storage = storage),
        use24Hour = DateFormat.is24HourFormat(context),
        onChange = { change: (AppSettings) -> AppSettings ->
            container.settings.update(change)
            container.reminderEngine.scheduleBriefing()
        },
        onFixNotifications = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        },
        // The list of apps' battery settings; picking "Unrestricted" needs no extra permission.
        onFixBattery = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
        onBack = onBack,
    )
}

/** Everything in the Health section except storage, which needs a database check. */
private fun readHealth(context: Context): HealthInfo {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    return HealthInfo(
        hasInternetPermission = context.checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED,
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        exactAlarmsAllowed = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
        batteryUnrestricted = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
        appLockAvailable = canAuthenticate(context),
        versionName = packageInfo.versionName.orEmpty(),
        isDebugBuild = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
    )
}

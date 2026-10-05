package dev.maahdi.mavick.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.maahdi.mavick.AppContainer
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.ui.editor.EditorRoute
import dev.maahdi.mavick.ui.inbox.InboxRoute
import dev.maahdi.mavick.ui.inbox.MessageRoute
import dev.maahdi.mavick.ui.lock.LockScreen
import dev.maahdi.mavick.ui.reading.ReadingRoute
import dev.maahdi.mavick.ui.settings.SettingsRoute
import dev.maahdi.mavick.ui.tasks.TasksRoute

/** Shows the lock screen or the current screen. */
@Composable
fun MavickRoot(
    container: AppContainer,
    navigation: NavigationViewModel,
    onUnlockRequest: () -> Unit,
    onFinish: () -> Unit,
) {
    val locked by container.appLock.locked.collectAsStateWithLifecycle()
    if (locked) {
        LockScreen(onUnlock = onUnlockRequest)
        return
    }

    RequestNotificationPermissionOnce(container.settings)
    val closeEditor: () -> Unit = {
        if (navigation.finishAfterEditor) {
            onFinish()
        } else {
            navigation.back()
        }
    }
    val back: () -> Unit = { navigation.back() }
    when (val destination = navigation.current) {
        Destination.Tasks -> TasksRoute(
            container = container,
            onOpenTask = { navigation.openEditor(taskId = it) },
            onNewTask = { navigation.openEditor() },
            onOpenSettings = navigation::openSettings,
            onOpenInbox = navigation::openInbox,
        )
        Destination.Settings -> SettingsRoute(container, onOpenReading = navigation::openReading, onBack = back)
        is Destination.Editor -> EditorRoute(container, destination, onClose = closeEditor)
        Destination.Inbox -> InboxRoute(container, onOpenMessage = navigation::openMessage, onOpenReading = navigation::openReading, onBack = back)
        is Destination.Message -> MessageRoute(container, destination.messageId, onAddTask = { navigation.openEditor(draft = it) }, onBack = back)
        Destination.Reading -> ReadingRoute(container, onBack = back)
    }
}

/**
 * Asks for notification permission the first time Mavick opens (reminders need it). After that,
 * Mavick doesn't ask again; the task list shows a "Turn on" banner instead if it was refused.
 */
@Composable
private fun RequestNotificationPermissionOnce(settings: SettingsRepository) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted && !settings.current.notificationPermissionRequested) {
            settings.update { it.copy(notificationPermissionRequested = true) }
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

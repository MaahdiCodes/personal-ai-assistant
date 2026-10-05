package dev.maahdi.mavick.ui.tasks

import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.maahdi.mavick.AppContainer
import dev.maahdi.mavick.R

@Composable
fun TasksRoute(
    container: AppContainer,
    onOpenTask: (String) -> Unit,
    onNewTask: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: TasksViewModel = viewModel(factory = TasksViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notificationsBlocked by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        notificationsBlocked = !NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose { }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val doneMessage = stringResource(R.string.task_completed)
    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(viewModel) {
        viewModel.undo.collect { event ->
            val result = snackbarHostState.showSnackbar(doneMessage, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(event)
        }
    }

    TasksScreen(
        state = state,
        use24Hour = DateFormat.is24HourFormat(context),
        preview = viewModel::preview,
        onQuickAdd = viewModel::quickAdd,
        onToggleDone = viewModel::toggleDone,
        onOpenTask = { onOpenTask(it.id) },
        onNewTask = onNewTask,
        onOpenSettings = onOpenSettings,
        notificationsBlocked = notificationsBlocked,
        onFixNotifications = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        },
        snackbarHostState = snackbarHostState,
    )
}

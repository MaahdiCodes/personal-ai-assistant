package dev.maahdi.mavick.ui.inbox

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.maahdi.mavick.AppContainer
import dev.maahdi.mavick.ui.PhoneSettings
import java.time.Instant

@Composable
fun InboxRoute(
    container: AppContainer,
    onOpenMessage: (String) -> Unit,
    onOpenReading: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: InboxViewModel = viewModel(factory = InboxViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(container.hasNotificationAccess()) }
    var now by remember { mutableStateOf(Instant.now(container.clock())) }
    LifecycleResumeEffect(Unit) {
        // Re-read when coming back from Android's settings, where access may have been granted.
        hasAccess = container.hasNotificationAccess()
        now = Instant.now(container.clock())
        onPauseOrDispose { }
    }
    BackHandler(onBack = onBack)

    InboxScreen(
        state = state,
        hasAccess = hasAccess,
        pause = settings.capturePause,
        now = now,
        zone = container.clock().zone,
        use24Hour = DateFormat.is24HourFormat(context),
        onOpenMessage = { onOpenMessage(it.id) },
        onOpenReading = onOpenReading,
        onPause = { choice ->
            now = Instant.now(container.clock())
            container.settings.pauseReading(choice, container.clock())
        },
        onResume = { container.settings.resumeReading() },
        onTurnOnAccess = { PhoneSettings.open(context, *PhoneSettings.notificationAccess(container.listenerComponent)) },
        onBack = onBack,
    )
}

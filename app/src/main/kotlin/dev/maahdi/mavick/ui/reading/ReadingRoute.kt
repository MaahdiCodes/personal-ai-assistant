package dev.maahdi.mavick.ui.reading

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.maahdi.mavick.AppContainer
import dev.maahdi.mavick.ui.inbox.pauseReading
import dev.maahdi.mavick.ui.inbox.resumeReading
import java.time.Instant

@Composable
fun ReadingRoute(container: AppContainer, onBack: () -> Unit) {
    val viewModel: ReadingViewModel = viewModel(factory = ReadingViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    ReadingScreen(
        state = state,
        apps = settings.appCapture,
        pause = settings.capturePause,
        now = Instant.now(container.clock()),
        zone = container.clock().zone,
        use24Hour = DateFormat.is24HourFormat(LocalContext.current),
        onAppChange = { app, capture -> container.settings.update { it.copy(appCapture = it.appCapture + (app to capture)) } },
        onPause = { container.settings.pauseReading(it, container.clock()) },
        onResume = { container.settings.resumeReading() },
        onAddRule = viewModel::addRule,
        onRemoveRule = viewModel::removeRule,
        onBack = onBack,
    )
}

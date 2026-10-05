package dev.maahdi.mavick.ui.keep

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.maahdi.mavick.AppContainer
import java.time.LocalDate

@Composable
fun KeepImportRoute(container: AppContainer, uri: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: KeepImportViewModel = viewModel(
        key = "keep-import-$uri",
        factory = KeepImportViewModel.factory(container, context.contentResolver, uri.toUri()),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    KeepImportScreen(
        state = state,
        today = LocalDate.now(container.clock()),
        use24Hour = DateFormat.is24HourFormat(context),
        onToggle = viewModel::toggle,
        onSelectAll = viewModel::selectAll,
        onSelectNone = viewModel::selectNone,
        onAdd = { viewModel.add() },
        onBack = onBack,
    )
}

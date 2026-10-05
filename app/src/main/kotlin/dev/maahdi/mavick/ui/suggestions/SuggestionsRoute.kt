package dev.maahdi.mavick.ui.suggestions

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.maahdi.mavick.AppContainer
import dev.maahdi.mavick.R
import dev.maahdi.mavick.data.task.TaskDraft
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SuggestionsRoute(
    container: AppContainer,
    onEdit: (draft: TaskDraft, suggestionId: String) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: SuggestionsViewModel = viewModel(factory = SuggestionsViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prompt by viewModel.neverReadPrompt.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    var modelUsable by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        // Seen now, so the notification about them goes.
        container.notifier.cancelSuggestions()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        modelUsable = withContext(Dispatchers.IO) { container.modelHost.isUsable() }
    }
    BackHandler(onBack = onBack)

    val snackbarHostState = remember { SnackbarHostState() }
    val addedText = stringResource(R.string.suggestion_added)
    val ignoredText = stringResource(R.string.suggestion_ignored)
    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(viewModel) {
        viewModel.undo.collect { event ->
            val text = if (event is SuggestionUndo.Added) addedText else ignoredText
            val result = snackbarHostState.showSnackbar(text, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(event)
        }
    }

    SuggestionsScreen(
        state = state,
        engine = when {
            !settings.suggestionsEnabled -> SuggestionEngine.OFF
            modelUsable -> SuggestionEngine.MODEL
            else -> SuggestionEngine.RULES
        },
        neverReadPrompt = prompt,
        today = LocalDate.now(container.clock()),
        workDays = settings.workDays,
        use24Hour = DateFormat.is24HourFormat(LocalContext.current),
        onAdd = { suggestion, draft ->
            // Words about when that couldn't be read: pick the time in the editor first.
            if (suggestion.needsTime) onEdit(draft, suggestion.id) else viewModel.add(suggestion, draft)
        },
        onEdit = { suggestion, draft -> onEdit(draft, suggestion.id) },
        onIgnore = viewModel::ignore,
        onAskNeverRead = viewModel::askNeverRead,
        onConfirmNeverRead = viewModel::neverReadChat,
        onDismissNeverRead = viewModel::dismissNeverRead,
        onOpenSettings = onOpenSettings,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    )
}

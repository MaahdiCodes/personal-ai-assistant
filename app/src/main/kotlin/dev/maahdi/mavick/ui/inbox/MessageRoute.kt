package dev.maahdi.mavick.ui.inbox

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.maahdi.mavick.AppContainer
import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.share.MessageToTask
import java.time.LocalDate

@Composable
fun MessageRoute(
    container: AppContainer,
    messageId: String,
    onAddTask: (TaskDraft) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: MessageViewModel = viewModel(key = "message-$messageId", factory = MessageViewModel.factory(container, messageId))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val zone = container.clock().zone
    val origin = state.message?.let { originLine(it) }
    val chatName = state.message?.let { chatName(it) }
    BackHandler(onBack = onBack)

    MessageScreen(
        state = state,
        today = LocalDate.now(container.clock()),
        zone = zone,
        use24Hour = DateFormat.is24HourFormat(LocalContext.current),
        onAddTask = { message -> onAddTask(MessageToTask.draft(message, origin.orEmpty(), container.whenParser(), zone)) },
        onNeverReadChat = { viewModel.neverReadChat(chatName.orEmpty(), onDone = onBack) },
        onBack = onBack,
    )
}

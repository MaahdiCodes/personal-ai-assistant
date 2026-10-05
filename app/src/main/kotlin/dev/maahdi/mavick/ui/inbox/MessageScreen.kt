@file:OptIn(ExperimentalMaterial3Api::class)

package dev.maahdi.mavick.ui.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.maahdi.mavick.R
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.message.MessageEntity
import dev.maahdi.mavick.time.DueFormatter
import java.time.LocalDate
import java.time.ZoneId

/** One message in full, with "Add as task" and "Never read this chat". */
@Composable
fun MessageScreen(
    state: MessageUiState,
    today: LocalDate,
    zone: ZoneId,
    use24Hour: Boolean,
    onAddTask: (MessageEntity) -> Unit,
    onNeverReadChat: (MessageEntity) -> Unit,
    onBack: () -> Unit,
) {
    val message = state.message
    var confirmNeverRead by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(message?.let { chatName(it) }.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding).fillMaxSize()
        when {
            state.missing -> Text(stringResource(R.string.message_missing), modifier = modifier.padding(24.dp))
            message == null -> Text(stringResource(R.string.editor_loading), modifier = modifier.padding(24.dp))
            else -> Column(
                modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(appAndAccount(message.app, message.accountKey), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(senderLine(message, today, zone, use24Hour), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Selectable, so part of a long message can be copied.
                SelectionContainer { Text(message.text, style = MaterialTheme.typography.bodyLarge) }
                completenessNote(message)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Button(onClick = { onAddTask(message) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.add_as_task))
                }
                OutlinedButton(onClick = { confirmNeverRead = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.never_read_chat))
                }
            }
        }
    }

    if (confirmNeverRead && message != null) {
        AlertDialog(
            onDismissRequest = { confirmNeverRead = false },
            title = { Text(stringResource(R.string.never_read_confirm_title, chatName(message))) },
            text = { Text(pluralStringResource(R.plurals.never_read_confirm_text, state.chatMessageCount, state.chatMessageCount)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmNeverRead = false
                    onNeverReadChat(message)
                }) { Text(stringResource(R.string.never_read_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmNeverRead = false }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
}

/** "From Sam · Today · 10:05", "You · Today · 10:05", or just the time. */
@Composable
private fun senderLine(message: MessageEntity, today: LocalDate, zone: ZoneId, use24Hour: Boolean): String {
    val time = DueFormatter.moment(message.postedAt, zone, today, use24Hour)
    return when {
        message.isFromMe -> stringResource(R.string.message_you_at, time)
        message.sender != null -> stringResource(R.string.message_from_at, message.sender, time)
        else -> time
    }
}

/** Says when the saved text isn't the whole message. */
@Composable
private fun completenessNote(message: MessageEntity): String? = when {
    message.app == SourceApp.GMAIL -> stringResource(R.string.gmail_preview_note)
    message.cutShort -> stringResource(R.string.cut_short_note, appName(message.app))
    else -> null
}

/** The first line of a task's notes: where the message came from. */
@Composable
fun originLine(message: MessageEntity): String {
    val app = appName(message.app)
    return when {
        message.isFromMe -> stringResource(R.string.origin_you, chatName(message), app)
        message.sender == null -> stringResource(R.string.origin_app, app)
        message.isGroup -> stringResource(R.string.origin_group, message.sender, chatName(message), app)
        else -> stringResource(R.string.origin_person, message.sender, app)
    }
}

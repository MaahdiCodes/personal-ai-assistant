@file:OptIn(ExperimentalMaterial3Api::class)

package dev.maahdi.mavick.ui.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.maahdi.mavick.R
import dev.maahdi.mavick.capture.CapturePause
import dev.maahdi.mavick.data.message.MessageEntity
import dev.maahdi.mavick.time.DueFormatter
import dev.maahdi.mavick.ui.components.Banner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Messages read from notifications, newest first, grouped by day. */
@Composable
fun InboxScreen(
    state: InboxUiState,
    hasAccess: Boolean,
    pause: CapturePause,
    now: Instant,
    zone: ZoneId,
    use24Hour: Boolean,
    onOpenMessage: (MessageEntity) -> Unit,
    onOpenReading: () -> Unit,
    onPause: (PauseChoice) -> Unit,
    onResume: () -> Unit,
    onTurnOnAccess: () -> Unit,
    onBack: () -> Unit,
) {
    val today = now.atZone(zone).toLocalDate()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inbox_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = { InboxMenu(paused = pause.isActive(now), onOpenReading, onPause, onResume) },
            )
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            if (!hasAccess) {
                Banner(
                    message = stringResource(R.string.access_off),
                    action = stringResource(R.string.turn_on),
                    onAction = onTurnOnAccess,
                    detail = stringResource(R.string.access_restricted_hint),
                )
            }
            pauseText(pause, now, zone, use24Hour)?.let { Banner(it, stringResource(R.string.resume_reading), onResume) }
            state.storageError?.let { Banner(stringResource(R.string.storage_problem, it), action = null, onAction = {}) }
            if (!state.loading && state.messages.isEmpty()) {
                Text(
                    stringResource(R.string.inbox_empty),
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                MessageList(state.messages, today, zone, use24Hour, onOpenMessage)
            }
        }
    }
}

@Composable
private fun InboxMenu(paused: Boolean, onOpenReading: () -> Unit, onPause: (PauseChoice) -> Unit, onResume: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.reading_title)) }, onClick = {
                open = false
                onOpenReading()
            })
            if (paused) {
                DropdownMenuItem(text = { Text(stringResource(R.string.resume_reading)) }, onClick = {
                    open = false
                    onResume()
                })
            } else {
                PauseChoice.entries.forEach { choice ->
                    DropdownMenuItem(text = { Text(pauseChoiceLabel(choice)) }, onClick = {
                        open = false
                        onPause(choice)
                    })
                }
            }
        }
    }
}

@Composable
fun pauseChoiceLabel(choice: PauseChoice): String = stringResource(
    when (choice) {
        PauseChoice.ONE_HOUR -> R.string.pause_one_hour
        PauseChoice.UNTIL_TOMORROW_MORNING -> R.string.pause_until_morning
        PauseChoice.UNTIL_RESUMED -> R.string.pause_until_resumed
    },
)

@Composable
private fun MessageList(
    messages: List<MessageEntity>,
    today: LocalDate,
    zone: ZoneId,
    use24Hour: Boolean,
    onOpenMessage: (MessageEntity) -> Unit,
) {
    val byDay = messages.groupBy { it.postedAt.atZone(zone).toLocalDate() }
    LazyColumn(Modifier.fillMaxSize()) {
        byDay.forEach { (day, dayMessages) ->
            item(key = "day:$day") {
                Text(
                    DueFormatter.dayLabel(day, today),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(dayMessages, key = { it.id }) { message ->
                MessageRow(message, zone, use24Hour, onClick = { onOpenMessage(message) })
            }
        }
    }
}

@Composable
fun MessageRow(message: MessageEntity, zone: ZoneId, use24Hour: Boolean, onClick: () -> Unit) {
    val time = DueFormatter.time(message.postedAt.atZone(zone).toLocalTime(), use24Hour)
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        overlineContent = { Text(appAndAccount(message.app, message.accountKey)) },
        headlineContent = { Text(chatName(message), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(messagePreview(message), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Column {
                Text(time, style = MaterialTheme.typography.labelMedium)
                if (message.cutShort) {
                    Text(stringResource(R.string.cut_short), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}

/** "Sam: text" in groups, "You: text" for your own messages, just the text otherwise. */
@Composable
private fun messagePreview(message: MessageEntity): String = when {
    message.isFromMe -> stringResource(R.string.message_from_you, message.text)
    message.isGroup && message.sender != null -> stringResource(R.string.message_from, message.sender, message.text)
    else -> message.text
}

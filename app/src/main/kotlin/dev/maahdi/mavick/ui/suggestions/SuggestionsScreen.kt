@file:OptIn(ExperimentalMaterial3Api::class)

package dev.maahdi.mavick.ui.suggestions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.maahdi.mavick.R
import dev.maahdi.mavick.data.suggestion.SuggestionEntity
import dev.maahdi.mavick.data.suggestion.SuggestionKind
import dev.maahdi.mavick.data.suggestion.SuggestionSource
import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.share.SuggestionToTask
import dev.maahdi.mavick.time.DueFormatter
import dev.maahdi.mavick.ui.components.Banner
import dev.maahdi.mavick.ui.inbox.NeverReadChatDialog
import dev.maahdi.mavick.ui.inbox.appAndAccount
import dev.maahdi.mavick.ui.inbox.chatName
import dev.maahdi.mavick.ui.inbox.originLine
import java.time.DayOfWeek
import java.time.LocalDate

/** Where suggestions come from right now, said at the top of the list. */
enum class SuggestionEngine { OFF, RULES, MODEL }

/** Tasks found in messages, newest first, each with Add, Edit, Ignore and "Never read this chat". */
@Composable
fun SuggestionsScreen(
    state: SuggestionsUiState,
    engine: SuggestionEngine,
    neverReadPrompt: NeverReadPrompt?,
    today: LocalDate,
    workDays: Set<DayOfWeek>,
    use24Hour: Boolean,
    onAdd: (SuggestionEntity, TaskDraft) -> Unit,
    onEdit: (SuggestionEntity, TaskDraft) -> Unit,
    onIgnore: (SuggestionEntity) -> Unit,
    onAskNeverRead: (SuggestionEntity) -> Unit,
    onConfirmNeverRead: (chatName: String) -> Unit,
    onDismissNeverRead: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.suggestions_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            state.storageError?.let { Banner(stringResource(R.string.storage_problem, it), action = null, onAction = {}) }
            when (engine) {
                SuggestionEngine.OFF -> Banner(stringResource(R.string.suggestions_off), stringResource(R.string.open_settings), onOpenSettings)
                SuggestionEngine.RULES -> Banner(
                    stringResource(R.string.suggestions_engine_rules),
                    stringResource(R.string.open_settings),
                    onOpenSettings,
                    problem = false,
                )
                SuggestionEngine.MODEL -> Unit
            }
            if (!state.loading && state.suggestions.isEmpty()) {
                Text(
                    stringResource(R.string.suggestions_empty),
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.suggestions, key = { it.id }) { suggestion ->
                        val draft = suggestionDraft(suggestion)
                        SuggestionCard(
                            suggestion = suggestion,
                            today = today,
                            workDays = workDays,
                            use24Hour = use24Hour,
                            onAdd = { onAdd(suggestion, draft) },
                            onEdit = { onEdit(suggestion, draft) },
                            onIgnore = { onIgnore(suggestion) },
                            onNeverRead = { onAskNeverRead(suggestion) },
                        )
                    }
                }
            }
        }
    }

    if (neverReadPrompt != null) {
        val name = chatName(neverReadPrompt.suggestion.chatTitle, neverReadPrompt.suggestion.app)
        NeverReadChatDialog(
            chatName = name,
            messageCount = neverReadPrompt.messageCount,
            onConfirm = { onConfirmNeverRead(name) },
            onDismiss = onDismissNeverRead,
        )
    }
}

@Composable
fun SuggestionCard(
    suggestion: SuggestionEntity,
    today: LocalDate,
    workDays: Set<DayOfWeek>,
    use24Hour: Boolean,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onIgnore: () -> Unit,
    onNeverRead: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(appAndAccount(suggestion.app, suggestion.accountKey), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(suggestion.title, style = MaterialTheme.typography.titleMedium)
            details(suggestion, today, workDays, use24Hour).takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }
            Text(
                quote(suggestion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdd) {
                    Text(stringResource(if (suggestion.needsTime) R.string.suggestion_add_pick_time else R.string.suggestion_add))
                }
                OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.suggestion_edit)) }
                TextButton(onClick = onIgnore) { Text(stringResource(R.string.suggestion_ignore)) }
                Spacer(Modifier.weight(1f))
                CardMenu(onNeverRead)
            }
        }
    }
}

@Composable
private fun CardMenu(onNeverRead: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.never_read_chat)) }, onClick = {
                open = false
                onNeverRead()
            })
        }
    }
}

/** The task a suggestion becomes, with notes saying where it came from and any unread "when" words. */
@Composable
fun suggestionDraft(suggestion: SuggestionEntity): TaskDraft {
    val origin = originLine(suggestion.app, suggestion.chatTitle, suggestion.sender, suggestion.isFromMe, suggestion.isGroup)
    val whenNote = suggestion.whenText?.takeIf { suggestion.needsTime }?.let { stringResource(R.string.suggestion_when_note, it) }
    return SuggestionToTask.draft(suggestion, origin, whenNote)
}

/** "Thu 8 Oct · 17:00 · Event · Not sure", or the words to pick a time from. */
@Composable
private fun details(suggestion: SuggestionEntity, today: LocalDate, workDays: Set<DayOfWeek>, use24Hour: Boolean): String {
    val whenText = suggestion.whenText
    val due = if (suggestion.needsTime && whenText != null) {
        stringResource(R.string.suggestion_when_unread, whenText)
    } else {
        DueFormatter.dueLabel(suggestion.dueDate, suggestion.dueTime, today, use24Hour).takeIf { it.isNotEmpty() }
    }
    return listOfNotNull(
        due,
        suggestion.repeatRule?.let { DueFormatter.repeatLabel(it, workDays) },
        when (suggestion.kind) {
            SuggestionKind.EVENT -> stringResource(R.string.suggestion_kind_event)
            SuggestionKind.REMINDER -> stringResource(R.string.suggestion_kind_reminder)
            SuggestionKind.TASK -> null
        },
        stringResource(R.string.suggestion_unsure).takeIf { suggestion.source == SuggestionSource.MODEL && suggestion.confidence < UNSURE_BELOW },
        stringResource(R.string.suggestion_by_rules).takeIf { suggestion.source == SuggestionSource.RULES },
    ).joinToString(" · ")
}

/** "Family · Sam: Can you…", "You: …", or just the text, as in the Inbox. */
@Composable
private fun quote(suggestion: SuggestionEntity): String {
    val text = suggestion.excerpt.replace(WHITESPACE, " ")
    val line = when {
        suggestion.isFromMe -> stringResource(R.string.message_from_you, text)
        suggestion.sender != null && suggestion.sender != suggestion.chatTitle -> stringResource(R.string.message_from, suggestion.sender, text)
        else -> text
    }
    return if (suggestion.isGroup) "${chatName(suggestion.chatTitle, suggestion.app)} · $line" else line
}

/** The AI's own confidence below this is shown as "Not sure". */
const val UNSURE_BELOW = 0.6

private val WHITESPACE = Regex("\\s+")

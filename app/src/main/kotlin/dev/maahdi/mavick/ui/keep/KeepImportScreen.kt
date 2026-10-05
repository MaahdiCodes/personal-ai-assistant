@file:OptIn(ExperimentalMaterial3Api::class)

package dev.maahdi.mavick.ui.keep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.maahdi.mavick.R
import dev.maahdi.mavick.importers.TakeoutProblem
import dev.maahdi.mavick.time.DueFormatter
import java.time.LocalDate

/** Notes from a Takeout export, each with a tick box; the ticked ones become tasks. */
@Composable
fun KeepImportScreen(
    state: KeepImportState,
    today: LocalDate,
    use24Hour: Boolean,
    onToggle: (fileName: String) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.keep_import_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        bottomBar = {
            if (state is KeepImportState.Ready && state.items.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = onSelectAll, enabled = !state.adding) { Text(stringResource(R.string.keep_import_select_all)) }
                        TextButton(onClick = onSelectNone, enabled = !state.adding) { Text(stringResource(R.string.keep_import_select_none)) }
                        Button(onClick = onAdd, enabled = state.selected.isNotEmpty() && !state.adding, modifier = Modifier.weight(1f)) {
                            Text(pluralStringResource(R.plurals.keep_import_add, state.selected.size, state.selected.size))
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding).fillMaxSize()
        when (state) {
            KeepImportState.Reading -> Message(stringResource(R.string.keep_import_reading), modifier)
            is KeepImportState.Failed -> Message(
                stringResource(
                    when (state.problem) {
                        null -> R.string.keep_import_unreadable
                        TakeoutProblem.NOT_A_ZIP -> R.string.keep_import_not_a_zip
                        TakeoutProblem.TOO_BIG -> R.string.keep_import_too_big
                    },
                ),
                modifier,
            )
            is KeepImportState.Done -> Message(pluralStringResource(R.plurals.keep_import_done, state.added, state.added), modifier)
            is KeepImportState.Ready -> if (state.items.isEmpty()) {
                Message(stringResource(R.string.keep_import_none), modifier)
            } else {
                LazyColumn(modifier) {
                    item(key = "summary") {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(pluralStringResource(R.plurals.keep_import_found, state.items.size, state.items.size), style = MaterialTheme.typography.bodyLarge)
                            if (state.skipped > 0) {
                                Text(
                                    pluralStringResource(R.plurals.keep_import_skipped, state.skipped, state.skipped),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    items(state.items, key = { it.note.fileName }) { item ->
                        NoteRow(item, selected = item.note.fileName in state.selected, enabled = !state.adding, today, use24Hour) { onToggle(item.note.fileName) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteRow(item: KeepImportItem, selected: Boolean, enabled: Boolean, today: LocalDate, use24Hour: Boolean, onToggle: () -> Unit) {
    val draft = item.draft
    val details = listOfNotNull(
        DueFormatter.dueLabel(draft.dueDate, draft.dueTime, today, use24Hour).takeIf { it.isNotEmpty() },
        item.note.labels.takeIf { it.isNotEmpty() }?.joinToString(", "),
        stringResource(R.string.keep_import_archived).takeIf { item.note.archived },
        stringResource(R.string.keep_import_all_checked).takeIf { item.note.allChecked },
    ).joinToString(" · ")
    ListItem(
        modifier = Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() }),
        leadingContent = { Checkbox(checked = selected, onCheckedChange = null, enabled = enabled) },
        headlineContent = { Text(draft.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                draft.notes?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
                if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        },
    )
}

@Composable
private fun Message(text: String, modifier: Modifier) {
    Text(text, modifier = modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

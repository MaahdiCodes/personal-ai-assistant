@file:OptIn(ExperimentalMaterial3Api::class)

package dev.maahdi.mavick.ui.tasks

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.maahdi.mavick.R
import dev.maahdi.mavick.data.task.TaskEntity
import dev.maahdi.mavick.calendar.clashSentence
import dev.maahdi.mavick.data.task.TaskPriority
import dev.maahdi.mavick.data.task.TaskStatus
import dev.maahdi.mavick.time.DueFormatter
import dev.maahdi.mavick.time.ParsedTask
import dev.maahdi.mavick.ui.components.Banner
import java.time.DayOfWeek
import java.time.LocalDate

enum class TasksTab(@param:StringRes val label: Int) {
    TODAY(R.string.tab_today),
    UPCOMING(R.string.tab_upcoming),
    DONE(R.string.tab_done),
}

/** A titled group of tasks in a list. [title] null means no header. */
data class TaskSection(val title: String?, val tasks: List<TaskEntity>, val highlightDue: Boolean = false)

@Composable
fun TasksScreen(
    state: TasksUiState,
    use24Hour: Boolean,
    preview: (String) -> ParsedTask,
    onQuickAdd: (String) -> Boolean,
    onToggleDone: (TaskEntity) -> Unit,
    onOpenTask: (TaskEntity) -> Unit,
    onNewTask: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenSuggestions: () -> Unit,
    notificationsBlocked: Boolean,
    onFixNotifications: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var tab by rememberSaveable { mutableStateOf(TasksTab.TODAY) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onNewTask) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.new_task))
                    }
                    IconButton(onClick = onOpenInbox) {
                        Icon(Icons.Filled.Email, contentDescription = stringResource(R.string.open_inbox))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.open_settings))
                    }
                },
            )
        },
        bottomBar = { QuickAddBar(preview, onQuickAdd, state.today, state.workDays, use24Hour) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            if (notificationsBlocked) Banner(stringResource(R.string.notifications_blocked), stringResource(R.string.turn_on), onFixNotifications)
            state.storageError?.let { Banner(stringResource(R.string.storage_problem, it), action = null, onAction = {}) }
            if (state.suggestionsWaiting > 0) {
                Banner(
                    message = pluralStringResource(R.plurals.suggestions_banner, state.suggestionsWaiting, state.suggestionsWaiting),
                    action = stringResource(R.string.suggestions_review),
                    onAction = onOpenSuggestions,
                    problem = false,
                )
            }
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                TasksTab.entries.forEach { entry ->
                    Tab(selected = tab == entry, onClick = { tab = entry }, text = { Text(stringResource(entry.label)) })
                }
            }
            val sections = when (tab) {
                TasksTab.TODAY -> listOf(
                    TaskSection(stringResource(R.string.section_overdue), state.overdue, highlightDue = true),
                    TaskSection(stringResource(R.string.section_today), state.dueToday),
                )
                TasksTab.UPCOMING ->
                    state.upcoming.groupBy { it.dueDate }.map { (date, tasks) ->
                        TaskSection(date?.let { DueFormatter.dayLabel(it, state.today) }, tasks)
                    } + TaskSection(stringResource(R.string.section_no_date), state.noDate)
                TasksTab.DONE -> listOf(TaskSection(null, state.done))
            }.filter { it.tasks.isNotEmpty() }
            val emptyText = when (tab) {
                TasksTab.TODAY -> R.string.empty_today
                TasksTab.UPCOMING -> R.string.empty_upcoming
                TasksTab.DONE -> R.string.empty_done
            }
            if (!state.loading && sections.isEmpty()) {
                Text(
                    stringResource(emptyText),
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                TaskList(sections, state, use24Hour, onToggleDone, onOpenTask)
            }
        }
    }
}

@Composable
private fun TaskList(
    sections: List<TaskSection>,
    state: TasksUiState,
    use24Hour: Boolean,
    onToggleDone: (TaskEntity) -> Unit,
    onOpenTask: (TaskEntity) -> Unit,
) {
    val resources = LocalResources.current
    LazyColumn(Modifier.fillMaxSize()) {
        sections.forEach { section ->
            section.title?.let { title ->
                item(key = "header:$title") {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
            }
            items(section.tasks, key = { it.id }) { task ->
                val clash = state.clashes[task.id]?.let { clashSentence(resources, it, state.zone, use24Hour) }
                TaskRow(
                    task,
                    state.today,
                    state.workDays,
                    use24Hour,
                    section.highlightDue,
                    onToggleDone = { onToggleDone(task) },
                    onClick = { onOpenTask(task) },
                    clash = clash,
                )
            }
        }
    }
}

@Composable
fun TaskRow(
    task: TaskEntity,
    today: LocalDate,
    workDays: Set<DayOfWeek>,
    use24Hour: Boolean,
    highlightDue: Boolean,
    onToggleDone: () -> Unit,
    onClick: () -> Unit,
    /** "Clashes with Dentist at 17:00", when the task overlaps a calendar event (Phase 4). */
    clash: String? = null,
) {
    val isDone = task.status == TaskStatus.DONE
    val details = listOfNotNull(
        DueFormatter.dueLabel(task.dueDate, task.dueTime, today, use24Hour).takeIf { it.isNotEmpty() },
        task.repeatRule?.let { DueFormatter.repeatLabel(it, workDays) },
        stringResource(R.string.high_priority).takeIf { task.priority == TaskPriority.HIGH },
    ).joinToString(" · ")
    val markDoneLabel = stringResource(R.string.mark_done, task.title)
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Checkbox(
                checked = isDone,
                onCheckedChange = { onToggleDone() },
                modifier = Modifier.semantics { contentDescription = markDoneLabel },
            )
        },
        headlineContent = {
            Text(
                task.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (isDone) TextDecoration.LineThrough else null,
            )
        },
        supportingContent = if (details.isEmpty() && clash == null) {
            null
        } else {
            {
                Column {
                    if (details.isNotEmpty()) {
                        Text(
                            details,
                            color = if (highlightDue && !isDone) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (clash != null && !isDone) Text(clash, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        trailingContent = if (task.remindAt != null && !isDone) {
            { Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.has_reminder)) }
        } else {
            null
        },
    )
}

/**
 * The always-visible quick-add box. As you type it shows what it understood
 * ("Tomorrow · 17:00 · Every day"), so you can check before adding.
 */
@Composable
fun QuickAddBar(
    preview: (String) -> ParsedTask,
    onSubmit: (String) -> Boolean,
    today: LocalDate,
    workDays: Set<DayOfWeek>,
    use24Hour: Boolean,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = remember(text) { if (text.isBlank()) null else preview(text) }
    val submit = { if (onSubmit(text)) text = "" }
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val summary = parsed?.let { previewSummary(it, today, workDays, use24Hour) }.orEmpty()
            if (summary.isNotEmpty()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp).testTag(QUICK_ADD_PREVIEW_TAG),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.quick_add_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f).testTag(QUICK_ADD_FIELD_TAG),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = submit, enabled = parsed?.title?.isNotBlank() == true) {
                    Text(stringResource(R.string.quick_add_button))
                }
            }
        }
    }
}

/** "Tomorrow · 17:00 · Every day", or empty when no date, time or repeat was found. */
fun previewSummary(parsed: ParsedTask, today: LocalDate, workDays: Set<DayOfWeek>, use24Hour: Boolean): String = listOfNotNull(
    DueFormatter.dueLabel(parsed.dueDate, parsed.dueTime, today, use24Hour).takeIf { it.isNotEmpty() },
    parsed.repeatRule?.let { DueFormatter.repeatLabel(it, workDays) },
).joinToString(" · ")

const val QUICK_ADD_FIELD_TAG = "quickAddField"
const val QUICK_ADD_PREVIEW_TAG = "quickAddPreview"

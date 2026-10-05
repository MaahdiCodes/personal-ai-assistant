@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.maahdi.mavick.ui.reading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import dev.maahdi.mavick.R
import dev.maahdi.mavick.capture.AppCapture
import dev.maahdi.mavick.capture.CaptureMode
import dev.maahdi.mavick.capture.CapturePause
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.rules.ExclusionRuleEntity
import dev.maahdi.mavick.data.rules.RuleEffect
import dev.maahdi.mavick.data.rules.RuleType
import dev.maahdi.mavick.time.DueFormatter
import dev.maahdi.mavick.ui.components.Banner
import dev.maahdi.mavick.ui.inbox.PauseChoice
import dev.maahdi.mavick.ui.inbox.accountName
import dev.maahdi.mavick.ui.inbox.appName
import dev.maahdi.mavick.ui.inbox.pauseChoiceLabel
import dev.maahdi.mavick.ui.inbox.pauseText
import java.time.Instant
import java.time.ZoneId

/** What Mavick reads: the pause, each app's switch and mode, and the rules. */
@Composable
fun ReadingScreen(
    state: ReadingUiState,
    apps: Map<SourceApp, AppCapture>,
    pause: CapturePause,
    now: Instant,
    zone: ZoneId,
    use24Hour: Boolean,
    onAppChange: (SourceApp, AppCapture) -> Unit,
    onPause: (PauseChoice) -> Unit,
    onResume: () -> Unit,
    onAddRule: (RuleType, RuleEffect, RuleTarget) -> Unit,
    onRemoveRule: (ExclusionRuleEntity) -> Unit,
    onBack: () -> Unit,
) {
    var adding by remember { mutableStateOf<Pair<RuleType, RuleEffect>?>(null) }
    val capture = { app: SourceApp -> apps[app] ?: AppCapture() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reading_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.storageError?.let { Banner(stringResource(R.string.storage_problem, it), action = null, onAction = {}) }
            PauseRow(pauseText(pause, now, zone, use24Hour), onPause, onResume)
            HorizontalDivider()

            SectionTitle(R.string.section_apps)
            SourceApp.entries.forEach { app -> AppRow(app, capture(app), onChange = { onAppChange(app, it) }) }
            HorizontalDivider()

            SectionTitle(R.string.section_never_read, R.string.section_never_read_summary)
            state.rules.filter { it.effect == RuleEffect.EXCLUDE }.forEach { rule -> RuleRow(rule, now, onRemove = { onRemoveRule(rule) }) }
            AddButtons(
                types = listOf(RuleType.KEYWORD, RuleType.CHAT, RuleType.SENDER, RuleType.ACCOUNT),
                onAdd = { adding = it to RuleEffect.EXCLUDE },
            )

            val allowRules = state.rules.filter { it.effect == RuleEffect.ALLOW }
            val onlyListedApps = SourceApp.entries.filter { capture(it).enabled && capture(it).mode == CaptureMode.ONLY_LISTED }
            if (onlyListedApps.isNotEmpty() || allowRules.isNotEmpty()) {
                HorizontalDivider()
                SectionTitle(R.string.section_only_read, R.string.section_only_read_summary)
                allowRules.forEach { rule -> RuleRow(rule, now, onRemove = { onRemoveRule(rule) }) }
                onlyListedApps.filter { app -> allowRules.none { it.app == null || it.app == app } }.forEach { app ->
                    Text(stringResource(R.string.only_read_empty_warning, appName(app)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                AddButtons(types = listOf(RuleType.CHAT, RuleType.SENDER), onAdd = { adding = it to RuleEffect.ALLOW })
            }
        }
    }

    adding?.let { (type, effect) ->
        AddRuleDialog(
            type = type,
            effect = effect,
            suggestions = state.suggestions,
            onAdd = { target ->
                adding = null
                onAddRule(type, effect, target)
            },
            onDismiss = { adding = null },
        )
    }
}

@Composable
private fun SectionTitle(title: Int, summary: Int? = null) {
    Column {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        if (summary != null) {
            Text(stringResource(summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PauseRow(pausedText: String?, onPause: (PauseChoice) -> Unit, onResume: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(pausedText ?: stringResource(R.string.reading_status_on), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (pausedText != null) {
            TextButton(onClick = onResume) { Text(stringResource(R.string.resume_reading)) }
        } else {
            Box {
                TextButton(onClick = { menuOpen = true }) { Text(stringResource(R.string.pause_button)) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    PauseChoice.entries.forEach { choice ->
                        DropdownMenuItem(text = { Text(pauseChoiceLabel(choice)) }, onClick = {
                            menuOpen = false
                            onPause(choice)
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: SourceApp, capture: AppCapture, onChange: (AppCapture) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(appName(app), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = capture.enabled, onCheckedChange = { onChange(capture.copy(enabled = it)) })
        }
        if (capture.enabled) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = capture.mode == CaptureMode.ALL_EXCEPT,
                    onClick = { onChange(capture.copy(mode = CaptureMode.ALL_EXCEPT)) },
                    label = { Text(stringResource(R.string.capture_all_chats)) },
                )
                FilterChip(
                    selected = capture.mode == CaptureMode.ONLY_LISTED,
                    onClick = { onChange(capture.copy(mode = CaptureMode.ONLY_LISTED)) },
                    label = { Text(stringResource(R.string.capture_only_listed)) },
                )
            }
        }
    }
}

@Composable
private fun RuleRow(rule: ExclusionRuleEntity, now: Instant, onRemove: () -> Unit) {
    val title = stringResource(
        when (rule.type) {
            RuleType.KEYWORD -> R.string.rule_keyword
            RuleType.CHAT -> R.string.rule_chat
            RuleType.SENDER -> R.string.rule_sender
            RuleType.ACCOUNT -> R.string.rule_account
        },
        rule.displayName,
    )
    val scope = when {
        rule.app == null -> stringResource(R.string.rule_all_apps)
        rule.accountKey == null || rule.type == RuleType.ACCOUNT -> appName(rule.app)
        else -> "${appName(rule.app)} · ${accountName(rule.accountKey)}"
    }
    val matched = rule.lastMatchedAt?.let { stringResource(R.string.rule_last_matched, DueFormatter.ago(it, now)) }
        ?: stringResource(R.string.rule_never_matched)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text("$scope · $matched", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_rule, title))
        }
    }
}

@Composable
private fun AddButtons(types: List<RuleType>, onAdd: (RuleType) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        types.forEach { type ->
            OutlinedButton(onClick = { onAdd(type) }) {
                Text(
                    stringResource(
                        when (type) {
                            RuleType.KEYWORD -> R.string.add_word
                            RuleType.CHAT -> R.string.add_chat
                            RuleType.SENDER -> R.string.add_person
                            RuleType.ACCOUNT -> R.string.add_account
                        },
                    ),
                )
            }
        }
    }
}

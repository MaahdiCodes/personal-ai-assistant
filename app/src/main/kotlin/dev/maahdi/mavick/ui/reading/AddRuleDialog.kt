package dev.maahdi.mavick.ui.reading

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.maahdi.mavick.R
import dev.maahdi.mavick.data.rules.RuleEffect
import dev.maahdi.mavick.data.rules.RuleType
import dev.maahdi.mavick.ui.inbox.accountName
import dev.maahdi.mavick.ui.inbox.appAndAccount
import dev.maahdi.mavick.ui.inbox.appName

/** One choice in the dialog: what it adds, and how it is shown. */
private data class Suggestion(val target: RuleTarget, val label: String)

/**
 * Adds a rule: type a word or name (applies everywhere), or pick a chat, person or account from
 * saved messages (applies to that app, and for chats that account).
 */
@Composable
fun AddRuleDialog(
    type: RuleType,
    effect: RuleEffect,
    suggestions: RuleSuggestions,
    onAdd: (RuleTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    val choices = suggestionsFor(type, suggestions)
    val fieldLabel = fieldLabel(type)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(dialogTitle(type, effect))) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (fieldLabel != null) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text(stringResource(fieldLabel)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag(RULE_FIELD_TAG),
                    )
                }
                if (type != RuleType.KEYWORD) {
                    Text(
                        stringResource(if (choices.isEmpty()) R.string.suggestions_none else R.string.typed_applies_everywhere),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                choices.forEach { choice ->
                    Text(
                        choice.label,
                        modifier = Modifier.fillMaxWidth().clickable { onAdd(choice.target) }.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
        confirmButton = {
            if (fieldLabel != null) {
                TextButton(onClick = { onAdd(RuleTarget(typed.trim(), typed.trim())) }, enabled = typed.isNotBlank()) {
                    Text(stringResource(R.string.dialog_add))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}

@Composable
private fun suggestionsFor(type: RuleType, suggestions: RuleSuggestions): List<Suggestion> = when (type) {
    RuleType.KEYWORD -> emptyList()
    RuleType.CHAT -> suggestions.chats.map { chat ->
        val name = chat.conversationTitle.ifBlank { appName(chat.app) }
        Suggestion(RuleTarget(chat.conversationKey, name, chat.app, chat.accountKey), "$name · ${appAndAccount(chat.app, chat.accountKey)}")
    }
    RuleType.SENDER -> suggestions.people.map { person ->
        Suggestion(RuleTarget(person.name, person.name, person.app), "${person.name} · ${appName(person.app)}")
    }
    RuleType.ACCOUNT -> suggestions.accounts.map { account ->
        val name = "${appName(account.app)} · ${accountName(account.accountKey)}"
        Suggestion(RuleTarget(account.accountKey, name, account.app, account.accountKey), name)
    }
}

@StringRes
private fun fieldLabel(type: RuleType): Int? = when (type) {
    RuleType.KEYWORD -> R.string.field_keyword
    RuleType.CHAT -> R.string.field_chat
    RuleType.SENDER -> R.string.field_person
    RuleType.ACCOUNT -> null // accounts can only be picked: their keys aren't names
}

@StringRes
private fun dialogTitle(type: RuleType, effect: RuleEffect): Int = when (effect) {
    RuleEffect.EXCLUDE -> when (type) {
        RuleType.KEYWORD -> R.string.add_title_keyword
        RuleType.CHAT -> R.string.add_title_chat_never
        RuleType.SENDER -> R.string.add_title_person_never
        RuleType.ACCOUNT -> R.string.add_title_account
    }
    RuleEffect.ALLOW -> if (type == RuleType.SENDER) R.string.add_title_person_only else R.string.add_title_chat_only
}

const val RULE_FIELD_TAG = "ruleField"

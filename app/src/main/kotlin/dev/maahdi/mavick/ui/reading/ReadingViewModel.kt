package dev.maahdi.mavick.ui.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.maahdi.mavick.AppContainer
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.message.AccountRef
import dev.maahdi.mavick.data.message.ConversationRef
import dev.maahdi.mavick.data.message.MessageRepository
import dev.maahdi.mavick.data.message.SenderRef
import dev.maahdi.mavick.data.rules.ExclusionRepository
import dev.maahdi.mavick.data.rules.ExclusionRuleEntity
import dev.maahdi.mavick.data.rules.RuleEffect
import dev.maahdi.mavick.data.rules.RuleType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Chats, people and accounts from saved messages, offered when adding a rule. */
data class RuleSuggestions(
    val chats: List<ConversationRef> = emptyList(),
    val people: List<SenderRef> = emptyList(),
    val accounts: List<AccountRef> = emptyList(),
)

/** What a new rule matches, and where: picked from saved messages (one app or account) or typed (everywhere). */
data class RuleTarget(val value: String, val displayName: String, val app: SourceApp? = null, val accountKey: String? = null)

data class ReadingUiState(
    val rules: List<ExclusionRuleEntity> = emptyList(),
    val suggestions: RuleSuggestions = RuleSuggestions(),
    val loading: Boolean = true,
    val storageError: String? = null,
)

class ReadingViewModel(
    private val openExclusions: suspend () -> ExclusionRepository,
    private val openMessages: suspend () -> MessageRepository,
) : ViewModel() {
    val state: StateFlow<ReadingUiState> = flow {
        val rules = openExclusions()
        rules.all() // adds the default keywords the first time
        val messages = openMessages()
        val suggestions = RuleSuggestions(messages.recentConversations(), messages.recentSenders(), messages.accounts())
        emitAll(rules.observe().map { ReadingUiState(rules = it, suggestions = suggestions, loading = false) })
    }
        .catch { error -> emit(ReadingUiState(loading = false, storageError = error.javaClass.simpleName)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_WATCHING_AFTER_MS), ReadingUiState())

    fun addRule(type: RuleType, effect: RuleEffect, target: RuleTarget) {
        viewModelScope.launch {
            openExclusions().add(type, effect, target.value, target.displayName, target.app, target.accountKey)
        }
    }

    fun removeRule(rule: ExclusionRuleEntity) {
        viewModelScope.launch { openExclusions().remove(rule.id) }
    }

    companion object {
        private const val STOP_WATCHING_AFTER_MS = 5_000L

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { ReadingViewModel(container::openExclusions, container::openMessages) }
        }
    }
}

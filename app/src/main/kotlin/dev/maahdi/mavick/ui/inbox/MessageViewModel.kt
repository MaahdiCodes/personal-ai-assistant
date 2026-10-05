package dev.maahdi.mavick.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.maahdi.mavick.AppContainer
import dev.maahdi.mavick.data.message.MessageEntity
import dev.maahdi.mavick.data.message.MessageRepository
import dev.maahdi.mavick.data.rules.ExclusionRepository
import dev.maahdi.mavick.data.rules.RuleEffect
import dev.maahdi.mavick.data.rules.RuleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MessageUiState(
    val message: MessageEntity? = null,
    /** How many saved messages its chat has, for "Never read this chat". */
    val chatMessageCount: Int = 0,
    val loading: Boolean = true,
    /** The message was deleted meanwhile (retention, or its chat was excluded). */
    val missing: Boolean = false,
)

class MessageViewModel(
    private val openMessages: suspend () -> MessageRepository,
    private val openExclusions: suspend () -> ExclusionRepository,
    messageId: String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MessageUiState())

    val state: StateFlow<MessageUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val messages = openMessages()
            val message = messages.find(messageId)
            mutableState.value = if (message == null) {
                MessageUiState(loading = false, missing = true)
            } else {
                MessageUiState(
                    message = message,
                    chatMessageCount = messages.countConversation(message.app, message.accountKey, message.conversationKey),
                    loading = false,
                )
            }
        }
    }

    /**
     * Adds a "Never read" rule for this message's chat (by its key, so a rename doesn't undo it),
     * deletes the chat's saved messages, then calls [onDone].
     */
    fun neverReadChat(chatName: String, onDone: () -> Unit) {
        val message = mutableState.value.message ?: return
        viewModelScope.launch {
            openExclusions().add(RuleType.CHAT, RuleEffect.EXCLUDE, message.conversationKey, chatName, message.app, message.accountKey)
            openMessages().deleteConversation(message.app, message.accountKey, message.conversationKey)
            onDone()
        }
    }

    companion object {
        fun factory(container: AppContainer, messageId: String) = viewModelFactory {
            initializer { MessageViewModel(container::openMessages, container::openExclusions, messageId) }
        }
    }
}

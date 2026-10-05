package dev.maahdi.mavick.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.maahdi.mavick.AppContainer
import dev.maahdi.mavick.data.message.MessageEntity
import dev.maahdi.mavick.data.message.MessageRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class InboxUiState(
    /** Newest first. */
    val messages: List<MessageEntity> = emptyList(),
    val loading: Boolean = true,
    /** Set when the encrypted database can't be opened; the screen explains instead of crashing. */
    val storageError: String? = null,
)

class InboxViewModel(private val openMessages: suspend () -> MessageRepository) : ViewModel() {
    /** Watches the database only while the screen is visible (WhileSubscribed), to save battery. */
    val state: StateFlow<InboxUiState> = flow {
        emitAll(openMessages().observeRecent().map { InboxUiState(messages = it, loading = false) })
    }
        .catch { error -> emit(InboxUiState(loading = false, storageError = error.javaClass.simpleName)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_WATCHING_AFTER_MS), InboxUiState())

    companion object {
        private const val STOP_WATCHING_AFTER_MS = 5_000L

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { InboxViewModel(container::openMessages) }
        }
    }
}

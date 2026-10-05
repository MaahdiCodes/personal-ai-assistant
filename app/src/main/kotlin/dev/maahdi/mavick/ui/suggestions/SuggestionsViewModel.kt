package dev.maahdi.mavick.ui.suggestions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.maahdi.mavick.AppContainer
import dev.maahdi.mavick.data.message.MessageRepository
import dev.maahdi.mavick.data.rules.ExclusionRepository
import dev.maahdi.mavick.data.rules.NeverReadChat
import dev.maahdi.mavick.data.suggestion.SuggestionEntity
import dev.maahdi.mavick.data.suggestion.SuggestionRepository
import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.data.task.TaskRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SuggestionsUiState(
    /** Waiting for you, newest message first. */
    val suggestions: List<SuggestionEntity> = emptyList(),
    val loading: Boolean = true,
    /** Set when the encrypted database can't be opened; the screen explains instead of crashing. */
    val storageError: String? = null,
)

/** A suggestion was added or ignored; the snackbar's Undo puts it back. */
sealed interface SuggestionUndo {
    val suggestionId: String

    data class Added(override val suggestionId: String, val taskId: String) : SuggestionUndo

    data class Ignored(override val suggestionId: String) : SuggestionUndo
}

/** "Never read this chat" waits for you to confirm, showing how many saved messages it deletes. */
data class NeverReadPrompt(val suggestion: SuggestionEntity, val messageCount: Int)

class SuggestionsViewModel(
    private val openSuggestions: suspend () -> SuggestionRepository,
    private val openTasks: suspend () -> TaskRepository,
    private val openMessages: suspend () -> MessageRepository,
    private val openExclusions: suspend () -> ExclusionRepository,
) : ViewModel() {
    private val undoEvents = MutableSharedFlow<SuggestionUndo>(extraBufferCapacity = 1)
    private val prompt = MutableStateFlow<NeverReadPrompt?>(null)

    /** Watches the database only while the screen is visible (WhileSubscribed), to save battery. */
    val state: StateFlow<SuggestionsUiState> = flow {
        emitAll(openSuggestions().observeNew().map { SuggestionsUiState(suggestions = it, loading = false) })
    }
        .catch { error -> emit(SuggestionsUiState(loading = false, storageError = error.javaClass.simpleName)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_WATCHING_AFTER_MS), SuggestionsUiState())

    val undo: SharedFlow<SuggestionUndo> = undoEvents.asSharedFlow()

    val neverReadPrompt: StateFlow<NeverReadPrompt?> = prompt.asStateFlow()

    /** Adds [draft] as a task and marks the suggestion added. */
    fun add(suggestion: SuggestionEntity, draft: TaskDraft): Job =
        viewModelScope.launch {
            val task = openTasks().create(draft)
            openSuggestions().accept(suggestion.id, task.id)
            undoEvents.emit(SuggestionUndo.Added(suggestion.id, task.id))
        }

    fun ignore(suggestion: SuggestionEntity): Job =
        viewModelScope.launch {
            openSuggestions().ignore(suggestion.id)
            undoEvents.emit(SuggestionUndo.Ignored(suggestion.id))
        }

    /** Undo: an added task is deleted again, and the suggestion waits once more. */
    fun undo(event: SuggestionUndo): Job =
        viewModelScope.launch {
            if (event is SuggestionUndo.Added) openTasks().delete(event.taskId)
            openSuggestions().reopen(event.suggestionId)
        }

    fun askNeverRead(suggestion: SuggestionEntity): Job =
        viewModelScope.launch {
            val count = openMessages().countConversation(suggestion.app, suggestion.accountKey, suggestion.conversationKey)
            prompt.value = NeverReadPrompt(suggestion, count)
        }

    fun dismissNeverRead() {
        prompt.value = null
    }

    /** Confirmed: the chat is never read again, and its messages and suggestions are deleted. */
    fun neverReadChat(chatName: String): Job? {
        val suggestion = prompt.value?.suggestion ?: return null
        prompt.value = null
        return viewModelScope.launch {
            NeverReadChat.apply(openExclusions(), openMessages(), suggestion.app, suggestion.accountKey, suggestion.conversationKey, chatName)
        }
    }

    companion object {
        private const val STOP_WATCHING_AFTER_MS = 5_000L

        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                SuggestionsViewModel(container::openSuggestions, container::openTasks, container::openMessages, container::openExclusions)
            }
        }
    }
}

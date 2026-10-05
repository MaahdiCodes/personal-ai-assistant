package dev.maahdi.mavick.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dev.maahdi.mavick.data.task.TaskDraft

sealed interface Destination {
    data object Tasks : Destination

    data object Settings : Destination

    /** [sessionId] gives each opened editor its own state, even for the same task. */
    data class Editor(val sessionId: Long, val taskId: String? = null, val draft: TaskDraft? = null) : Destination

    /** Messages read from notifications (Phase 2). */
    data object Inbox : Destination

    data class Message(val messageId: String) : Destination

    /** What Mavick reads: app switches, "Never read" and "Only read" rules, the pause. */
    data object Reading : Destination
}

/**
 * Which screen is showing. A plain back stack, no navigation library: a few screens don't need one.
 * Kept in a ViewModel so it survives screen rotation.
 */
class NavigationViewModel : ViewModel() {
    private val backStack = mutableStateListOf<Destination>(Destination.Tasks)
    private var nextSessionId = 0L

    /** Opened from another app's Share menu: leaving the editor returns to that app. */
    var finishAfterEditor by mutableStateOf(false)
        private set

    val current: Destination get() = backStack.last()

    val canGoBack: Boolean get() = backStack.size > 1

    fun openSettings() = push(Destination.Settings)

    fun openInbox() = push(Destination.Inbox)

    fun openMessage(messageId: String) = push(Destination.Message(messageId))

    fun openReading() = push(Destination.Reading)

    /** Opens an editor; an editor already open is replaced, so only one exists at a time. */
    fun openEditor(taskId: String? = null, draft: TaskDraft? = null, fromShare: Boolean = false) {
        if (current is Destination.Editor) backStack.removeAt(backStack.lastIndex)
        backStack.add(Destination.Editor(nextSessionId++, taskId, draft))
        finishAfterEditor = fromShare
    }

    /** Returns false when already on the first screen. */
    fun back(): Boolean {
        if (!canGoBack) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    /** Opens [destination]; the same screen already on top isn't opened twice. */
    private fun push(destination: Destination) {
        if (current != destination) backStack.add(destination)
    }
}

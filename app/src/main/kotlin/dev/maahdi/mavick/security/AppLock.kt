package dev.maahdi.mavick.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Decides when Mavick's screen must be unlocked (fingerprint or phone PIN) before it shows anything.
 *
 * Locks when the app is first opened in a process, and again after it has been out of sight for
 * [timeoutMillis]. Only the visible screen is locked: reminders keep working in the background.
 *
 * @param isEnabled the user's App lock setting.
 * @param elapsedRealtime a clock that keeps counting while the phone sleeps and ignores clock changes.
 */
class AppLock(
    private val isEnabled: () -> Boolean,
    private val elapsedRealtime: () -> Long,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    private val lockedState = MutableStateFlow(true)
    private var unlockedOnce = false
    private var hiddenSince: Long? = null

    val locked: StateFlow<Boolean> = lockedState.asStateFlow()

    /**
     * Mavick's screen became visible.
     *
     * @param canAuthenticate whether the phone has a screen lock. Without one, Mavick can't lock.
     */
    fun onScreenShown(canAuthenticate: Boolean) {
        val hiddenFor = hiddenSince?.let { elapsedRealtime() - it }
        val timedOut = !unlockedOnce || (hiddenFor != null && hiddenFor >= timeoutMillis)
        lockedState.value = isEnabled() && canAuthenticate && (lockedState.value || timedOut)
        hiddenSince = null
    }

    fun onScreenHidden() {
        hiddenSince = elapsedRealtime()
    }

    fun onUnlocked() {
        unlockedOnce = true
        lockedState.value = false
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 5 * 60 * 1000L
    }
}

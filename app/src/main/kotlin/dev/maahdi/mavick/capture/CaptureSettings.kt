package dev.maahdi.mavick.capture

import java.time.Instant

/** Whether Mavick reads an app, and which of its chats. */
data class AppCapture(val enabled: Boolean = true, val mode: CaptureMode = CaptureMode.ALL_EXCEPT)

/** Stored by name: never rename a constant. */
enum class CaptureMode {
    /** Every chat, except what the "Never read" rules exclude. */
    ALL_EXCEPT,

    /** Only the chats and people on the "Only read" list. */
    ONLY_LISTED,
}

/** A pause on all message reading. */
sealed interface CapturePause {
    fun isActive(now: Instant): Boolean

    data object Off : CapturePause {
        override fun isActive(now: Instant) = false
    }

    data class Until(val until: Instant) : CapturePause {
        override fun isActive(now: Instant) = now.isBefore(until)
    }

    data object UntilResumed : CapturePause {
        override fun isActive(now: Instant) = true
    }
}

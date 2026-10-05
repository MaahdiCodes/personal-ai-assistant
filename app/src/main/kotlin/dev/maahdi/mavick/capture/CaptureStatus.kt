package dev.maahdi.mavick.capture

import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.Duration
import java.time.Instant

/** What the Health section shows about message reading. Times and counts only, never content. */
data class CaptureStatus(
    /** When Android last connected Mavick's notification listener. */
    val connectedAt: Instant? = null,
    val disconnectedAt: Instant? = null,
    /** When a notification from a supported app last arrived. */
    val lastSeenAt: Instant? = null,
    /** When a message was last saved. */
    val lastSavedAt: Instant? = null,
    val saved: Long = 0,
    /** Dropped by a rule, the pause or an app switch. */
    val skipped: Long = 0,
    val duplicates: Long = 0,
    val failures: Long = 0,
)

/** Keeps [CaptureStatus] in a small private preferences file. */
class CaptureStatusStore(private val preferences: SharedPreferences) {
    @Synchronized
    fun markConnected(at: Instant) = preferences.edit { putLong(KEY_CONNECTED, at.toEpochMilli()) }

    @Synchronized
    fun markDisconnected(at: Instant) = preferences.edit { putLong(KEY_DISCONNECTED, at.toEpochMilli()) }

    /** One supported-app notification was handled. */
    @Synchronized
    fun record(at: Instant, result: CaptureResult) {
        val current = snapshot()
        preferences.edit {
            putLong(KEY_LAST_SEEN, at.toEpochMilli())
            if (result.saved > 0) putLong(KEY_LAST_SAVED, at.toEpochMilli())
            putLong(KEY_SAVED, current.saved + result.saved)
            putLong(KEY_SKIPPED, current.skipped + result.skipped)
            putLong(KEY_DUPLICATES, current.duplicates + result.duplicates)
        }
    }

    @Synchronized
    fun markFailure() = preferences.edit { putLong(KEY_FAILURES, snapshot().failures + 1) }

    fun snapshot(): CaptureStatus = CaptureStatus(
        connectedAt = instant(KEY_CONNECTED),
        disconnectedAt = instant(KEY_DISCONNECTED),
        lastSeenAt = instant(KEY_LAST_SEEN),
        lastSavedAt = instant(KEY_LAST_SAVED),
        saved = preferences.getLong(KEY_SAVED, 0),
        skipped = preferences.getLong(KEY_SKIPPED, 0),
        duplicates = preferences.getLong(KEY_DUPLICATES, 0),
        failures = preferences.getLong(KEY_FAILURES, 0),
    )

    private fun instant(key: String): Instant? =
        preferences.getLong(key, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)

    companion object {
        const val FILE_NAME = "capture_status"
        private const val KEY_CONNECTED = "connected_at"
        private const val KEY_DISCONNECTED = "disconnected_at"
        private const val KEY_LAST_SEEN = "last_seen_at"
        private const val KEY_LAST_SAVED = "last_saved_at"
        private const val KEY_SAVED = "saved"
        private const val KEY_SKIPPED = "skipped"
        private const val KEY_DUPLICATES = "duplicates"
        private const val KEY_FAILURES = "failures"
    }
}

/** Whether message reading works, as the Health section and the daily check see it. */
enum class ReadingState {
    /** Notification access isn't granted. */
    NO_ACCESS,

    /** Access is granted, but Android hasn't connected the listener (or disconnected it). */
    NOT_CONNECTED,

    /** Connected, but no notification from a supported app arrived for a while. */
    QUIET,
    OK,
}

object ReadingHealth {
    fun state(hasAccess: Boolean, status: CaptureStatus, now: Instant, quietAfter: Duration): ReadingState {
        if (!hasAccess) return ReadingState.NO_ACCESS
        val connectedAt = status.connectedAt ?: return ReadingState.NOT_CONNECTED
        val disconnectedAt = status.disconnectedAt
        if (disconnectedAt != null && disconnectedAt.isAfter(connectedAt)) return ReadingState.NOT_CONNECTED
        val lastSign = status.lastSeenAt?.takeIf { it.isAfter(connectedAt) } ?: connectedAt
        return if (Duration.between(lastSign, now) >= quietAfter) ReadingState.QUIET else ReadingState.OK
    }
}

package dev.maahdi.mavick.capture

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dev.maahdi.mavick.AppContainer
import dev.maahdi.mavick.MavickApp
import dev.maahdi.mavick.data.health.HealthEventEntity
import dev.maahdi.mavick.data.health.HealthEventType
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android calls this when a notification is posted, once the user grants Mavick "Notification
 * access". It only reads: it never changes, answers, opens or removes another app's notification
 * (enforced by the build's read-only check, docs/PLAN.md §5.1).
 *
 * Notifications from apps other than the supported ones are dropped on the first line. The rest
 * are handled one at a time, in arrival order, off the main thread.
 */
class MavickNotificationListener : NotificationListenerService() {
    private val container: AppContainer get() = (application as MavickApp).container
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val recorder: NotificationRecorder by lazy { createNotificationRecorder(this) }

    override fun onListenerConnected() {
        val now = now()
        container.captureStatus.markConnected(now)
        scope.launch {
            guarded { container.healthEvents.insert(HealthEventEntity(type = HealthEventType.LISTENER_CONNECTED, at = now)) }
            // Messages that arrived while Mavick wasn't connected may still be in the shade.
            val waiting = try {
                activeNotifications.orEmpty()
            } catch (e: SecurityException) {
                emptyArray() // disconnected again in the meantime
            }
            waiting.filter { SourceApp.fromPackage(it.packageName) != null }.forEach { handle(it) }
        }
    }

    override fun onListenerDisconnected() {
        val now = now()
        container.captureStatus.markDisconnected(now)
        scope.launch {
            guarded { container.healthEvents.insert(HealthEventEntity(type = HealthEventType.LISTENER_DISCONNECTED, at = now)) }
        }
        // Some phones disconnect listeners to save power; ask Android to connect Mavick again.
        requestRebind(ComponentName(this, MavickNotificationListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Every other app's notification stops here, unread.
        if (SourceApp.fromPackage(sbn.packageName) == null) return
        scope.launch { handle(sbn) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Separately guarded, so a recorder problem (debug builds) never costs a message. */
    private suspend fun handle(sbn: StatusBarNotification) {
        guarded { recorder.record(sbn) }
        guarded { container.capture.onNotification(NotificationReader.read(sbn)) }
    }

    /** Never crashes over one notification, and logs only the error's type, never content. */
    private suspend fun guarded(work: suspend () -> Unit) {
        try {
            work()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed(e)
        } catch (e: LinkageError) {
            failed(e) // the encryption library failed to load
        }
    }

    private fun failed(error: Throwable) {
        Log.w(TAG, "Message capture failed: ${error.javaClass.simpleName}")
        container.captureStatus.markFailure()
    }

    private fun now(): Instant = Instant.now(container.clock())

    private companion object {
        const val TAG = "MavickCapture"
    }
}

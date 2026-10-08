package dev.maahdi.mavick.ai

import android.os.Process
import android.util.Log
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs the suggestions queue ([process], normally SuggestionQueue.processPending) when something may
 * be waiting: a message was saved, Mavick opened, the daily alarm went off, a model was imported
 * (docs/PLAN.md §5.3, Runtime). No job, service, alarm or wake lock of its own: it rides on the
 * wake-ups Mavick already has.
 *
 * Work happens on one thread at background priority, so the phone stays responsive. Wake-ups during
 * a run merge into one more run. [unload] (closing the model) follows [UNLOAD_AFTER_MS] after the
 * last run, freeing its memory.
 */
class SuggestionWorker(
    /** Handles the waiting messages; returns how many suggestions it saved. */
    private val process: suspend () -> Int,
    private val unload: suspend () -> Unit,
    /** Called with the number of new suggestions after a run that saved some. */
    private val onSaved: suspend (Int) -> Unit,
    dispatcher: CoroutineDispatcher = lowPriorityThread(),
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val wakeUps = Channel<Unit>(Channel.CONFLATED)
    private var unloadLater: Job? = null

    init {
        scope.launch {
            for (wakeUp in wakeUps) {
                unloadLater?.cancel()
                runOnce()
                unloadLater = launch {
                    delay(UNLOAD_AFTER_MS)
                    unload()
                }
            }
        }
    }

    /** Asks for a run; returns at once. */
    fun wake() {
        wakeUps.trySend(Unit)
    }

    private suspend fun runOnce() {
        try {
            val saved = process()
            if (saved > 0) onSaved(saved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Never content: the error's type only. The next wake-up tries again.
            Log.w(TAG, "Suggestions failed: ${e.javaClass.simpleName}")
        } catch (e: LinkageError) {
            // The encryption library failed to load; Settings shows the storage problem.
            Log.w(TAG, "Suggestions failed: ${e.javaClass.simpleName}")
        }
    }

    companion object {
        /** The model stays loaded this long after a run, for messages that come in a burst. */
        const val UNLOAD_AFTER_MS = 60_000L
        private const val TAG = "MavickAI"

        /** One thread at background priority. The runtime's own threads are made from it and inherit that. */
        fun lowPriorityThread(): ExecutorCoroutineDispatcher = aiThread(Process.THREAD_PRIORITY_BACKGROUND)

        /** One thread at [priority] (an android.os.Process value). The app always uses [lowPriorityThread]. */
        fun aiThread(priority: Int): ExecutorCoroutineDispatcher = Executors.newSingleThreadExecutor { task ->
            Thread({
                Process.setThreadPriority(priority)
                task.run()
            }, "MavickAI")
        }.asCoroutineDispatcher()
    }
}

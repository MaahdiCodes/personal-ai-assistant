package dev.maahdi.mavick.ai

import java.io.File
import java.time.Clock
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Holds the AI model in memory only while there is work (docs/PLAN.md §5.3, Runtime): loaded on
 * first use, closed by [unload] once the work stops (SuggestionWorker waits a minute first).
 *
 * Guards message reading against a model that misbehaves, since both run in Mavick's one process:
 * - Loading and answering are bracketed by marks in [AiStatusStore]. A mark left behind means
 *   Mavick stopped during that work; after [AiStatus.MAX_INTERRUPTIONS] in a row the model is
 *   switched off until you turn it on again in Settings.
 * - A load or answer that fails leaves the model alone for an hour ([AiStatus.RETRY_AFTER]).
 * Meanwhile suggestions come from the rules, as without a model.
 */
class ModelHost(
    private val store: ModelStore,
    private val status: AiStatusStore,
    private val loader: (File) -> LanguageModel,
    private val clock: () -> Clock,
    /** Milliseconds from a clock that never jumps, for timing the load. */
    private val elapsedMillis: () -> Long,
) {
    private val mutex = Mutex()

    @Volatile
    private var loaded: LanguageModel? = null

    /** Whether a model is imported and may be used now (it may still need loading). */
    fun isUsable(): Boolean {
        val current = status.snapshot()
        return store.hasModel() && !current.modelSwitchedOff && !current.inProblemPause(now())
    }

    val isLoaded: Boolean get() = loaded != null

    /**
     * Runs [work] with the model, loading it first if needed, and times it (without the load).
     * Returns null without running [work] when no model can be used. If [work] throws, the model is
     * closed and left alone for an hour, and the error is passed on.
     */
    suspend fun <T> withModel(work: (LanguageModel) -> T): T? = mutex.withLock {
        val model = loaded ?: load() ?: return null
        status.beginModelWork(now())
        val started = elapsedMillis()
        try {
            work(model).also {
                status.endModelWork(completed = true)
                status.recordAnswer(elapsedMillis() - started)
            }
        } catch (e: CancellationException) {
            status.endModelWork(completed = false)
            throw e
        } catch (e: Exception) {
            status.endModelWork(completed = false)
            status.markProblem(now(), e)
            closeLoaded()
            throw e
        }
    }

    /** Frees the model's memory. The next [withModel] loads it again. */
    suspend fun unload() = mutex.withLock { closeLoaded() }

    /** Runs [change] (an import or a removal) with the model closed, so its file can be replaced. */
    suspend fun <T> whileClosed(change: suspend () -> T): T = mutex.withLock {
        closeLoaded()
        change()
    }

    private fun load(): LanguageModel? {
        if (!isUsable()) return null
        status.beginModelWork(now())
        val started = elapsedMillis()
        return try {
            loader(store.modelFile).also {
                status.endModelWork(completed = true)
                status.markLoaded(now(), elapsedMillis() - started)
                loaded = it
            }
        } catch (e: Exception) {
            loadFailed(e)
        } catch (e: LinkageError) {
            // The runtime's native library failed to load.
            loadFailed(e)
        }
    }

    private fun loadFailed(error: Throwable): LanguageModel? {
        status.endModelWork(completed = false)
        status.markProblem(now(), error)
        return null
    }

    private fun closeLoaded() {
        val model = loaded ?: return
        loaded = null
        try {
            model.close()
        } catch (e: Exception) {
            // Closing only frees memory; nothing else depends on it.
        }
    }

    private fun now(): Instant = Instant.now(clock())
}

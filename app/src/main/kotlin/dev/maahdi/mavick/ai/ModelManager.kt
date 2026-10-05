package dev.maahdi.mavick.ai

import dev.maahdi.mavick.capture.SourceApp
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a test question to the model showed. */
sealed interface ModelCheck {
    /** It answered in the right form; [millis] is how long the answer took (without loading). */
    data class Worked(val millis: Long, val itemsFound: Int) : ModelCheck

    /** No model could be used: none imported, switched off, or failing to load ([AiStatus.problem]). */
    data object NotUsable : ModelCheck

    /** It answered, but not in the form Mavick needs, twice: probably not a suitable model. */
    data object BadAnswer : ModelCheck

    /** The runtime failed while answering; [error] is the error's type. */
    data class Failed(val error: String) : ModelCheck
}

/**
 * What Settings does with the AI model. Each change waits for any AI work in progress, and a model
 * is checked with a sample message ([SAMPLE]) on the AI thread, like real work.
 */
class ModelManager(
    private val store: ModelStore,
    private val host: ModelHost,
    private val status: AiStatusStore,
    /** The runtime's prepared weights, which belong to one model file. */
    private val runtimeCacheDir: File,
    private val aiDispatcher: CoroutineDispatcher,
    /** Asks for a suggestions run, so waiting messages get the new model. */
    private val wakeQueue: () -> Unit,
    private val clock: () -> Clock,
    private val elapsedMillis: () -> Long,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun info(): ModelInfo? = withContext(ioDispatcher) { store.info() }

    suspend fun isUsable(): Boolean = withContext(ioDispatcher) { host.isUsable() }

    /** Imports a model from [open] (closed here), replacing the current one only if the new one checks out. */
    suspend fun import(name: String, sizeBytes: Long?, open: () -> InputStream, onProgress: (Long) -> Unit): ImportResult =
        host.whileClosed {
            val result = withContext(ioDispatcher) {
                try {
                    open().use { input -> store.import(input, name, sizeBytes, Instant.now(clock()), onProgress) }
                } catch (e: IOException) {
                    ImportResult.Rejected(ImportProblem.READ_FAILED)
                } catch (e: SecurityException) {
                    ImportResult.Rejected(ImportProblem.READ_FAILED)
                }
            }
            if (result is ImportResult.Imported) {
                withContext(ioDispatcher) { runtimeCacheDir.deleteRecursively() }
                status.resetModel()
                wakeQueue()
            }
            result
        }

    /** Deletes the model; suggestions then come from the rules. */
    suspend fun remove() = host.whileClosed {
        withContext(ioDispatcher) {
            store.remove()
            runtimeCacheDir.deleteRecursively()
        }
        status.resetModel()
    }

    /** After the model was switched off (Mavick stopped while it ran): give it another chance. */
    fun turnOnAgain() {
        status.resetModel()
        wakeQueue()
    }

    /** Loads the model if needed and asks it about [SAMPLE]. */
    suspend fun check(): ModelCheck = withContext(aiDispatcher) {
        try {
            var answerMillis = 0L
            val result = host.withModel { model ->
                val started = elapsedMillis()
                GemmaExtractor(model).extract(sample(LocalDateTime.now(clock()))).also { answerMillis = elapsedMillis() - started }
            }
            when (result) {
                null -> ModelCheck.NotUsable
                is ExtractionResult.Failed -> ModelCheck.BadAnswer
                is ExtractionResult.Found -> ModelCheck.Worked(answerMillis, result.items.size)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ModelCheck.Failed(e.javaClass.simpleName)
        }
    }

    companion object {
        /** A made-up message with one clear task, for checking a model. */
        const val SAMPLE = "Can you send me the signed form by Thursday 5pm? Thanks!"

        fun sample(sentAt: LocalDateTime) = ExtractionInput(
            app = SourceApp.WHATSAPP,
            chatTitle = "Sam",
            isGroup = false,
            earlier = emptyList(),
            message = ChatLine(sender = "Sam", isFromMe = false, text = SAMPLE),
            partial = false,
            sentAt = sentAt,
        )
    }
}

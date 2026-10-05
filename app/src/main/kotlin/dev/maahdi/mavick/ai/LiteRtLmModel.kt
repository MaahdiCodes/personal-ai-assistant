package dev.maahdi.mavick.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LiteRtLmJniException
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File

/**
 * The imported model running on the phone's CPU with LiteRT-LM (docs/PLAN.md §5.3, Runtime). The
 * only class that touches the AI runtime.
 *
 * Light on the phone: [THREADS] CPU threads, created from Mavick's low-priority AI thread so they
 * run at its priority, and a fresh, short conversation per message.
 */
class LiteRtLmModel private constructor(private val engine: Engine) : LanguageModel {
    /** Cleared if the runtime refuses the JSON schema; answers are then only checked afterwards. */
    private var schemaAccepted = true

    override fun generate(request: GenerationRequest): String {
        val schema = request.jsonSchema?.takeIf { schemaAccepted } ?: return ask(request, schema = null)
        return try {
            ask(request, schema)
        } catch (e: LiteRtLmJniException) {
            schemaAccepted = false
            ask(request, schema = null)
        } catch (e: IllegalArgumentException) {
            schemaAccepted = false
            ask(request, schema = null)
        }
    }

    private fun ask(request: GenerationRequest, schema: String?): String {
        val config = ConversationConfig(
            systemInstruction = Contents.of(request.systemInstruction),
            samplerConfig = if (request.varied) VARIED else STEADY,
            maxOutputToken = MAX_OUTPUT_TOKENS,
            enableResponseFormat = schema != null,
        )
        engine.createConversation(config).use { conversation ->
            val reply = conversation.sendMessage(request.prompt, responseFormat = schema?.let(ResponseFormat::json))
            return reply.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
        }
    }

    override fun close() = engine.close()

    companion object {
        /** At most two CPU threads (docs/PLAN.md §5.8), so the phone stays responsive. */
        const val THREADS = 2

        /** Enough for three items of JSON; a runaway answer stops here. */
        const val MAX_OUTPUT_TOKENS = 256

        /** Always the most likely word: the same message gives the same answer. */
        private val STEADY = SamplerConfig(topK = 1, topP = 1.0, temperature = 1.0, seed = 0)

        /** For the one retry after an unusable answer. */
        private val VARIED = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7, seed = 7)

        /**
         * Loads [modelFile]: seconds of work, so call it on the AI thread. [cacheDir] keeps the
         * runtime's prepared weights, so later loads are faster; Android may clear it.
         */
        fun load(modelFile: File, cacheDir: File): LiteRtLmModel {
            cacheDir.mkdirs()
            val engine = Engine(EngineConfig(modelPath = modelFile.path, backend = Backend.CPU(threadCount = THREADS), cacheDir = cacheDir.path))
            try {
                engine.initialize()
            } catch (e: Throwable) {
                engine.close()
                throw e
            }
            return LiteRtLmModel(engine)
        }
    }
}

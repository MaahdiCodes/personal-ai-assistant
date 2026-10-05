package dev.maahdi.mavick.ai

/** One question to the AI model. */
data class GenerationRequest(
    val systemInstruction: String,
    val prompt: String,
    /** A JSON schema the answer must follow, or null for free text. */
    val jsonSchema: String?,
    /**
     * False: always the model's most likely answer (repeatable). True: some variety, for a retry
     * after an unusable answer, which would otherwise just come back the same.
     */
    val varied: Boolean,
)

/**
 * A loaded AI model. Only [LiteRtLmModel] touches the AI runtime, so the rest of Mavick (and its
 * tests) never depend on it.
 */
interface LanguageModel : AutoCloseable {
    /** Blocking, for seconds: call on the AI thread. Throws if the runtime fails. */
    fun generate(request: GenerationRequest): String
}

package dev.maahdi.mavick.ai

/** How one answer's time was spent, as the AI runtime measured it. Only the accuracy check asks. */
data class ModelTimings(
    /** The instructions, context and message the model read before answering. */
    val promptTokens: Int,
    val promptTokensPerSecond: Double,
    /** The answer it wrote. */
    val answerTokens: Int,
    val answerTokensPerSecond: Double,
    val firstTokenSeconds: Double,
)

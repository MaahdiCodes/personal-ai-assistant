package dev.maahdi.mavick.ai

import dev.maahdi.mavick.data.suggestion.SuggestionSource

/**
 * Finds tasks with the imported AI model (Gemma 3 1B first, docs/PLAN.md §2). An unusable answer
 * is retried once with a little variety; if that fails too, the message is counted as failed.
 */
class GemmaExtractor(private val model: LanguageModel) : TaskExtractor {
    override val source = SuggestionSource.MODEL

    override fun extract(input: ExtractionInput): ExtractionResult {
        val prompt = Prompt.forMessage(input)
        var problem = "no_answer"
        for (attempt in 1..MAX_ATTEMPTS) {
            val reply = model.generate(GenerationRequest(Prompt.SYSTEM, prompt, ExtractionJson.SCHEMA, varied = attempt > 1))
            when (val parsed = ExtractionJson.parse(reply)) {
                is ExtractionJson.Parsed.Valid -> return ExtractionResult.Found(parsed.items)
                is ExtractionJson.Parsed.Invalid -> problem = parsed.reason
            }
        }
        return ExtractionResult.Failed(problem)
    }

    companion object {
        /** The first answer and one retry. */
        const val MAX_ATTEMPTS = 2
    }
}

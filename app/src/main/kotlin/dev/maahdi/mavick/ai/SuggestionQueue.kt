package dev.maahdi.mavick.ai

import dev.maahdi.mavick.capture.CaptureDecision
import dev.maahdi.mavick.capture.CapturePause
import dev.maahdi.mavick.capture.ExclusionEngine
import dev.maahdi.mavick.data.message.AiState
import dev.maahdi.mavick.data.message.MessageEntity
import dev.maahdi.mavick.data.message.MessageRepository
import dev.maahdi.mavick.data.message.asIncoming
import dev.maahdi.mavick.data.rules.ExclusionRepository
import dev.maahdi.mavick.data.rules.ExclusionRuleEntity
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.data.suggestion.SuggestionEntity
import dev.maahdi.mavick.data.suggestion.SuggestionRepository
import dev.maahdi.mavick.data.suggestion.SuggestionSource
import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.time.WhenParser
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * Looks for tasks in the messages waiting for the AI (docs/PLAN.md §5.3), newest first, one at a
 * time:
 * 1. Still allowed by your rules? A rule added after the message arrived counts too, so excluded
 *    text never reaches the AI. Earlier messages used as context are checked the same way.
 * 2. The prefilter: most chat stops here, at almost no cost.
 * 3. The AI model, or the rules when no model can be used.
 * 4. Dates read from the message's own time, and saved unless a near-duplicate exists.
 *
 * Messages older than [MAX_MESSAGE_AGE] are skipped. A run stops while the phone can't spare the
 * work ([DeviceConditions]); the next wake-up carries on where it stopped.
 */
class SuggestionQueue(
    private val messages: MessageRepository,
    private val suggestions: SuggestionRepository,
    private val rules: ExclusionRepository,
    private val settings: SettingsRepository,
    private val modelHost: ModelHost,
    private val ruleExtractor: TaskExtractor,
    private val conditions: DeviceConditions,
    private val status: AiStatusStore,
    private val whenParser: () -> WhenParser,
    private val clock: () -> Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** Handles every waiting message, unless the phone needs a pause. Returns how many suggestions were saved. */
    suspend fun processPending(): Int {
        if (!settings.current.suggestionsEnabled) return 0
        messages.skipPendingBefore(now().minus(MAX_MESSAGE_AGE))
        var saved = 0
        while (settings.current.suggestionsEnabled) {
            val pause = conditions.pauseReason()
            if (pause != null) {
                status.markPaused(pause, now())
                return saved
            }
            val message = messages.nextPending() ?: break
            saved += process(message)
        }
        status.markRun(now())
        return saved
    }

    /** Returns how many suggestions the message gave. */
    private suspend fun process(message: MessageEntity): Int {
        val activeRules = rules.all()
        if (!allowed(message, activeRules)) return skip(message)
        val parser = whenParser()
        val input = ExtractionInput(
            app = message.app,
            chatTitle = message.conversationTitle,
            isGroup = message.isGroup,
            earlier = messages.earlierInChat(message).filter { allowed(it, activeRules) }.map(::chatLine),
            message = chatLine(message),
            partial = message.cutShort,
            sentAt = LocalDateTime.ofInstant(message.postedAt, clock().zone),
        )
        if (!Prefilter.passes(input, parser)) return skip(message)
        status.recordChecked(skipped = false)

        // Marked failed before the model reads it: if the model brings Mavick down, this message is
        // never tried again. Success below marks it done.
        messages.setAiState(message.id, AiState.FAILED)
        val (result, source) = extract(input) ?: return failed()
        val found = when (result) {
            is ExtractionResult.Failed -> return failed()
            is ExtractionResult.Found -> result
        }
        // Deleted meanwhile (its chat was excluded): nothing to attach suggestions to.
        if (messages.find(message.id) == null) return 0

        var saved = 0
        for (item in found.items) {
            val resolved = WhenResolver.resolve(item.whenText, input.sentAt, parser)
            if (suggestions.addUnlessDuplicate(suggestionOf(message, item, resolved, source))) saved++
        }
        messages.setAiState(message.id, AiState.DONE)
        status.recordSuggested(saved)
        return saved
    }

    /** The model's answer, or the rules' when no model can be used; null if the model failed. */
    private suspend fun extract(input: ExtractionInput): Pair<ExtractionResult, SuggestionSource>? {
        val fromModel = try {
            modelHost.withModel { model -> GemmaExtractor(model).extract(input) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null // ModelHost recorded the problem and left the model alone for a while.
        }
        return if (fromModel != null) fromModel to SuggestionSource.MODEL else ruleExtractor.extract(input) to ruleExtractor.source
    }

    private suspend fun skip(message: MessageEntity): Int {
        messages.setAiState(message.id, AiState.SKIPPED)
        status.recordChecked(skipped = true)
        return 0
    }

    private fun failed(): Int {
        status.recordFailure()
        return 0
    }

    /** Whether the capture rules would still read [message] (a pause doesn't count: it was read already). */
    private fun allowed(message: MessageEntity, activeRules: List<ExclusionRuleEntity>): Boolean =
        ExclusionEngine.decide(message.asIncoming(), activeRules, settings.current::captureFor, CapturePause.Off, now()) is CaptureDecision.Read

    private fun chatLine(message: MessageEntity) = ChatLine(message.sender, message.isFromMe, message.text)

    private fun suggestionOf(message: MessageEntity, item: ExtractedItem, resolved: ResolvedWhen, source: SuggestionSource) = SuggestionEntity(
        id = newId(),
        messageId = message.id,
        app = message.app,
        accountKey = message.accountKey,
        conversationKey = message.conversationKey,
        chatTitle = message.conversationTitle,
        sender = message.sender,
        isFromMe = message.isFromMe,
        isGroup = message.isGroup,
        excerpt = message.text.trim().take(TaskDraft.MAX_EXCERPT_LENGTH),
        messagePostedAt = message.postedAt,
        kind = item.kind,
        title = item.title,
        // The rules pass the whole message as their "when" words; it is in the excerpt already.
        whenText = item.whenText.takeIf { source == SuggestionSource.MODEL },
        dueDate = resolved.dueDate,
        dueTime = resolved.dueTime,
        repeatRule = resolved.repeatRule,
        needsTime = resolved.needsTime,
        person = item.person,
        confidence = item.confidence,
        source = source,
        createdAt = now(),
    )

    private fun now(): Instant = Instant.now(clock())

    companion object {
        /** Older messages are skipped: a suggestion from last week rarely helps. */
        val MAX_MESSAGE_AGE: Duration = Duration.ofDays(3)
    }
}

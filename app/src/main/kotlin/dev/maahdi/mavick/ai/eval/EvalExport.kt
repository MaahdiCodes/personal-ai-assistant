package dev.maahdi.mavick.ai.eval

import dev.maahdi.mavick.capture.ExclusionEngine
import dev.maahdi.mavick.data.message.MessageEntity
import dev.maahdi.mavick.data.message.MessageRepository
import dev.maahdi.mavick.data.message.asIncoming
import dev.maahdi.mavick.data.rules.ExclusionRepository
import dev.maahdi.mavick.data.rules.ExclusionRuleEntity
import dev.maahdi.mavick.data.settings.SettingsRepository
import java.time.Clock
import java.time.Instant

/** An exported file, ready to save, and how many messages it holds. */
class EvalExportFile(val csv: String, val count: Int)

/**
 * Settings › Suggestions › Export messages for an accuracy check: your newest saved messages, as
 * an [EvalSet] file to label on the PC. Only on request, and only what your rules still allow: a
 * chat or word excluded since is left out, from the messages and their context alike.
 */
class EvalExport(
    private val messages: MessageRepository,
    private val rules: ExclusionRepository,
    private val settings: SettingsRepository,
    private val clock: () -> Clock,
) {
    suspend fun export(limit: Int = LIMIT): EvalExportFile {
        val now = Instant.now(clock())
        val activeRules = rules.all()
        val chosen = messages.recent(limit).filter { allowed(it, activeRules, now) }.map { message ->
            message to messages.earlierInChat(message).filter { allowed(it, activeRules, now) }
        }
        return EvalExportFile(EvalSet.write(chosen, clock().zone), chosen.size)
    }

    private fun allowed(message: MessageEntity, activeRules: List<ExclusionRuleEntity>, now: Instant): Boolean =
        ExclusionEngine.stillAllows(message.asIncoming(), activeRules, settings.current::captureFor, now)

    companion object {
        /** Enough for the 100 to 200 labelled messages the plan asks for (docs/PLAN.md §7). */
        const val LIMIT = 300
    }
}

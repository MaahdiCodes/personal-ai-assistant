package dev.maahdi.mavick.capture

import dev.maahdi.mavick.data.message.MessageRepository
import dev.maahdi.mavick.data.rules.ExclusionRepository
import dev.maahdi.mavick.data.settings.SettingsRepository
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/** What happened to one notification. Counts only, never content. */
data class CaptureResult(
    val saved: Int = 0,
    val skipped: Int = 0,
    val duplicates: Int = 0,
    /** Older than the retention period, so not worth saving. */
    val tooOld: Int = 0,
    val noise: Boolean = false,
    val ignored: Boolean = false,
)

/**
 * Handles one notification (docs/PLAN.md §5.1): supported app → noise → parse → rules (in memory)
 * → too old? → save once. A skipped message never reaches storage, and nothing is logged.
 */
class MessageCapture(
    private val messages: MessageRepository,
    private val rules: ExclusionRepository,
    private val settings: SettingsRepository,
    private val status: CaptureStatusStore,
    private val clock: () -> Clock,
    private val parser: MessageParser = MessageParser(),
) {
    suspend fun onNotification(raw: RawNotification): CaptureResult {
        val app = SourceApp.fromPackage(raw.packageName) ?: return CaptureResult(ignored = true)
        val now = Instant.now(clock())
        if (Noise.isNoise(raw)) return CaptureResult(noise = true).also { status.record(now, it) }

        val current = settings.current
        val activeRules = rules.all()
        val oldest = now.minus(current.messageRetentionDays.toLong(), ChronoUnit.DAYS)
        var saved = 0
        var skipped = 0
        var duplicates = 0
        var tooOld = 0
        val matchedRules = mutableSetOf<String>()
        for (message in parser.parse(app, raw)) {
            when (val decision = ExclusionEngine.decide(message, activeRules, current::captureFor, current.capturePause, now)) {
                is CaptureDecision.Skip -> {
                    skipped++
                    decision.ruleId?.let(matchedRules::add)
                }
                is CaptureDecision.Read -> {
                    decision.ruleId?.let(matchedRules::add)
                    when {
                        message.postedAt.isBefore(oldest) -> tooOld++
                        messages.save(message, receivedAt = now) -> saved++
                        else -> duplicates++
                    }
                }
            }
        }
        rules.markMatched(matchedRules, now)
        return CaptureResult(saved, skipped, duplicates, tooOld).also { status.record(now, it) }
    }
}

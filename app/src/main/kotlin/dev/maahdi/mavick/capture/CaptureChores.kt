package dev.maahdi.mavick.capture

import dev.maahdi.mavick.data.health.HealthEventDao
import dev.maahdi.mavick.data.message.MessageRepository
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.reminders.DailyChores
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Message reading's share of the daily alarm: deletes messages past the retention period and old
 * listener events, and warns if reading seems to have stopped.
 */
class CaptureChores(
    private val messages: MessageRepository,
    private val healthEvents: HealthEventDao,
    private val settings: SettingsRepository,
    private val status: CaptureStatusStore,
    private val hasAccess: () -> Boolean,
    private val warn: (ReadingState) -> Unit,
    private val clock: () -> Clock,
) : DailyChores {
    override suspend fun cleanUp() {
        val now = Instant.now(clock())
        messages.deleteOlderThan(now.minus(settings.current.messageRetentionDays.toLong(), ChronoUnit.DAYS))
        healthEvents.deleteOlderThan(now.minus(HEALTH_EVENT_DAYS, ChronoUnit.DAYS))
    }

    /** Warns once a day, at the briefing time, if reading stopped or nothing arrived for a while. */
    override suspend fun daily() {
        val days = settings.current.readingWarningDays
        if (days == 0) return
        val state = ReadingHealth.state(hasAccess(), status.snapshot(), Instant.now(clock()), Duration.ofDays(days.toLong()))
        if (state == ReadingState.NOT_CONNECTED || state == ReadingState.QUIET) warn(state)
    }

    companion object {
        const val HEALTH_EVENT_DAYS = 30L
    }
}

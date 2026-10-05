package dev.maahdi.mavick.ui.inbox

import dev.maahdi.mavick.capture.CapturePause
import dev.maahdi.mavick.data.settings.SettingsRepository
import java.time.Clock
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/** Pauses message reading as [choice] says, starting now. */
fun SettingsRepository.pauseReading(choice: PauseChoice, clock: Clock) =
    update { it.copy(capturePause = choice.pauseFrom(ZonedDateTime.now(clock))) }

fun SettingsRepository.resumeReading() = update { it.copy(capturePause = CapturePause.Off) }

/** The ways to pause message reading (docs/PLAN.md §5.2). */
enum class PauseChoice {
    ONE_HOUR,

    /** Until 06:00 tomorrow, so the night's messages are skipped too. */
    UNTIL_TOMORROW_MORNING,
    UNTIL_RESUMED,
    ;

    fun pauseFrom(now: ZonedDateTime): CapturePause = when (this) {
        ONE_HOUR -> CapturePause.Until(now.plus(Duration.ofHours(1)).toInstant())
        UNTIL_TOMORROW_MORNING -> CapturePause.Until(now.toLocalDate().plusDays(1).atTime(MORNING).atZone(now.zone).toInstant())
        UNTIL_RESUMED -> CapturePause.UntilResumed
    }

    companion object {
        val MORNING: LocalTime = LocalTime.of(6, 0)
    }
}

package dev.maahdi.mavick.ui.inbox

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.capture.CapturePause
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class PauseChoiceTest {
    private val dhaka = ZoneId.of("Asia/Dhaka")
    private val evening = ZonedDateTime.of(2026, 10, 5, 22, 15, 0, 0, dhaka)

    @Test
    fun `one hour pauses until an hour from now`() {
        assertThat(PauseChoice.ONE_HOUR.pauseFrom(evening)).isEqualTo(CapturePause.Until(evening.plusHours(1).toInstant()))
    }

    @Test
    fun `until tomorrow morning pauses until 6 in the morning tomorrow, local time`() {
        // 06:00 on 6 October in Dhaka (UTC+6) is midnight UTC.
        assertThat(PauseChoice.UNTIL_TOMORROW_MORNING.pauseFrom(evening))
            .isEqualTo(CapturePause.Until(Instant.parse("2026-10-06T00:00:00Z")))
    }

    @Test
    fun `just after midnight, tomorrow morning is still the next day's`() {
        val earlyHours = ZonedDateTime.of(2026, 10, 6, 0, 30, 0, 0, dhaka)

        assertThat(PauseChoice.UNTIL_TOMORROW_MORNING.pauseFrom(earlyHours))
            .isEqualTo(CapturePause.Until(ZonedDateTime.of(2026, 10, 7, 6, 0, 0, 0, dhaka).toInstant()))
    }

    @Test
    fun `until resumed has no end`() {
        assertThat(PauseChoice.UNTIL_RESUMED.pauseFrom(evening)).isEqualTo(CapturePause.UntilResumed)
    }
}

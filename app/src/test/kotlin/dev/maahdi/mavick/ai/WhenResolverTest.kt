package dev.maahdi.mavick.ai

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.time.RepeatRule
import dev.maahdi.mavick.time.WhenParser
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Test

class WhenResolverTest {
    private val parser = WhenParser()

    /** Monday 5 October 2026, 10:00. */
    private val monday10am = LocalDateTime.of(2026, 10, 5, 10, 0)

    @Test
    fun `no words about when means no date and nothing to ask`() {
        assertThat(WhenResolver.resolve(null, monday10am, parser)).isEqualTo(ResolvedWhen())
        assertThat(WhenResolver.resolve("  ", monday10am, parser)).isEqualTo(ResolvedWhen())
    }

    @Test
    fun `a day and time are read like quick-add`() {
        assertThat(WhenResolver.resolve("Thursday 5pm", monday10am, parser))
            .isEqualTo(ResolvedWhen(LocalDate.of(2026, 10, 8), LocalTime.of(17, 0)))
    }

    @Test
    fun `dates count from when the message was sent, not when Mavick read it`() {
        val sundayNight = LocalDateTime.of(2026, 10, 4, 22, 0)

        assertThat(WhenResolver.resolve("tomorrow", sundayNight, parser).dueDate).isEqualTo(LocalDate.of(2026, 10, 5))
    }

    @Test
    fun `a time already past when sent means the next day`() {
        val evening = LocalDateTime.of(2026, 10, 5, 18, 0)

        assertThat(WhenResolver.resolve("at 5", evening, parser))
            .isEqualTo(ResolvedWhen(LocalDate.of(2026, 10, 6), LocalTime.of(17, 0)))
    }

    @Test
    fun `a repeat is kept`() {
        val resolved = WhenResolver.resolve("every Monday at 9", monday10am, parser)

        assertThat(resolved.repeatRule).isEqualTo(RepeatRule.Weekly(setOf(DayOfWeek.MONDAY)))
        assertThat(resolved.dueTime).isEqualTo(LocalTime.of(9, 0))
        assertThat(resolved.dueDate).isEqualTo(LocalDate.of(2026, 10, 12))
    }

    @Test
    fun `words that can't be read ask you to pick a time`() {
        assertThat(WhenResolver.resolve("end of the month", monday10am, parser)).isEqualTo(ResolvedWhen(needsTime = true))
        assertThat(WhenResolver.resolve("asap", monday10am, parser)).isEqualTo(ResolvedWhen(needsTime = true))
    }
}

package dev.maahdi.mavick.time

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.time.RepeatRule.Daily
import dev.maahdi.mavick.time.RepeatRule.Monthly
import dev.maahdi.mavick.time.RepeatRule.Weekly
import dev.maahdi.mavick.time.RepeatRule.Yearly
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import org.junit.Test

class DueFormatterTest {
    private val today = LocalDate.of(2026, 10, 5) // a Monday

    @Test
    fun `a moment shows its day and time in the given time zone`() {
        val dhaka = ZoneId.of("Asia/Dhaka")
        val tenFive = Instant.parse("2026-10-05T04:05:00Z") // 10:05 in Dhaka

        assertThat(DueFormatter.moment(tenFive, dhaka, today, use24Hour = true)).isEqualTo("Today · 10:05")
        assertThat(DueFormatter.moment(tenFive.minus(Duration.ofDays(1)), dhaka, today, use24Hour = false)).isEqualTo("Yesterday · 10:05 AM")
        // 22:30 UTC on the 4th is already the 5th in Dhaka.
        assertThat(DueFormatter.moment(Instant.parse("2026-10-04T22:30:00Z"), dhaka, today, use24Hour = true)).isEqualTo("Today · 04:30")
    }

    @Test
    fun `how long ago reads naturally`() {
        val now = Instant.parse("2026-10-05T04:00:00Z")

        assertThat(DueFormatter.ago(now, now)).isEqualTo("just now")
        assertThat(DueFormatter.ago(now.minusSeconds(59), now)).isEqualTo("just now")
        assertThat(DueFormatter.ago(now.minus(Duration.ofMinutes(5)), now)).isEqualTo("5 min ago")
        assertThat(DueFormatter.ago(now.minus(Duration.ofMinutes(59)), now)).isEqualTo("59 min ago")
        assertThat(DueFormatter.ago(now.minus(Duration.ofHours(3)), now)).isEqualTo("3 h ago")
        assertThat(DueFormatter.ago(now.minus(Duration.ofHours(30)), now)).isEqualTo("1 day ago")
        assertThat(DueFormatter.ago(now.minus(Duration.ofDays(4)), now)).isEqualTo("4 days ago")
        // A clock set back (or a time zone change) never gives a negative age.
        assertThat(DueFormatter.ago(now.plusSeconds(600), now)).isEqualTo("just now")
    }

    @Test
    fun `days near today get friendly names`() {
        assertThat(DueFormatter.dayLabel(today, today)).isEqualTo("Today")
        assertThat(DueFormatter.dayLabel(today.plusDays(1), today)).isEqualTo("Tomorrow")
        assertThat(DueFormatter.dayLabel(today.minusDays(1), today)).isEqualTo("Yesterday")
        assertThat(DueFormatter.dayLabel(today.plusDays(2), today)).isEqualTo("Wednesday")
        assertThat(DueFormatter.dayLabel(today.plusDays(6), today)).isEqualTo("Sunday")
    }

    @Test
    fun `other days get a short date, with the year when it differs`() {
        assertThat(DueFormatter.dayLabel(today.plusDays(7), today)).isEqualTo("Mon 12 Oct")
        assertThat(DueFormatter.dayLabel(today.minusDays(2), today)).isEqualTo("Sat 3 Oct")
        assertThat(DueFormatter.dayLabel(LocalDate.of(2027, 1, 1), today)).isEqualTo("Fri 1 Jan 2027")
    }

    @Test
    fun `due label joins day and time`() {
        val tomorrow = today.plusDays(1)
        assertThat(DueFormatter.dueLabel(tomorrow, LocalTime.of(17, 0), today, use24Hour = true)).isEqualTo("Tomorrow · 17:00")
        assertThat(DueFormatter.dueLabel(tomorrow, LocalTime.of(17, 0), today, use24Hour = false)).isEqualTo("Tomorrow · 5:00 PM")
        assertThat(DueFormatter.dueLabel(tomorrow, null, today, use24Hour = true)).isEqualTo("Tomorrow")
        assertThat(DueFormatter.dueLabel(null, null, today, use24Hour = true)).isEmpty()
    }

    @Test
    fun `times follow the phone's 12 or 24 hour setting`() {
        assertThat(DueFormatter.time(LocalTime.of(9, 5), use24Hour = true)).isEqualTo("09:05")
        assertThat(DueFormatter.time(LocalTime.of(9, 5), use24Hour = false)).isEqualTo("9:05 AM")
        assertThat(DueFormatter.time(LocalTime.of(0, 30), use24Hour = false)).isEqualTo("12:30 AM")
    }

    @Test
    fun `repeat labels`() {
        val workDays = DEFAULT_WORK_DAYS
        assertThat(DueFormatter.repeatLabel(Daily(), workDays)).isEqualTo("Every day")
        assertThat(DueFormatter.repeatLabel(Daily(3), workDays)).isEqualTo("Every 3 days")
        assertThat(DueFormatter.repeatLabel(Weekly(DayOfWeek.entries.toSet()), workDays)).isEqualTo("Every day")
        assertThat(DueFormatter.repeatLabel(Weekly(workDays), workDays)).isEqualTo("Every work day")
        assertThat(DueFormatter.repeatLabel(Weekly(setOf(MONDAY)), workDays)).isEqualTo("Every Monday")
        assertThat(DueFormatter.repeatLabel(Weekly(setOf(THURSDAY, MONDAY)), workDays)).isEqualTo("Every Mon, Thu")
        assertThat(DueFormatter.repeatLabel(Weekly(setOf(FRIDAY), interval = 2), workDays)).isEqualTo("Every 2 weeks on Friday")
        assertThat(DueFormatter.repeatLabel(Monthly(1), workDays)).isEqualTo("Every month on the 1st")
        assertThat(DueFormatter.repeatLabel(Yearly(Month.OCTOBER, 12), workDays)).isEqualTo("Every year on 12 Oct")
    }

    @Test
    fun `ordinals`() {
        val expected = mapOf(
            1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th", 11 to "11th", 12 to "12th", 13 to "13th",
            21 to "21st", 22 to "22nd", 23 to "23rd", 31 to "31st", 101 to "101st", 111 to "111th",
        )
        expected.forEach { (number, text) -> assertThat(DueFormatter.ordinal(number)).isEqualTo(text) }
    }
}

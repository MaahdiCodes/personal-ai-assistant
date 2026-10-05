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
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import org.junit.Test

class DueFormatterTest {
    private val today = LocalDate.of(2026, 10, 5) // a Monday

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

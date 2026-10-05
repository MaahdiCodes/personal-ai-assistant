package dev.maahdi.mavick.time

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.time.RepeatRule.Daily
import dev.maahdi.mavick.time.RepeatRule.Monthly
import dev.maahdi.mavick.time.RepeatRule.Weekly
import dev.maahdi.mavick.time.RepeatRule.Yearly
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalDate
import java.time.Month
import org.junit.Assert.assertThrows
import org.junit.Test

class RepeatRuleTest {
    private fun date(text: String) = LocalDate.parse(text)

    // --- Stored form: must never change, existing database rows depend on it ---

    @Test
    fun `stored forms are stable`() {
        assertThat(Daily().toStorageString()).isEqualTo("DAILY")
        assertThat(Daily(3).toStorageString()).isEqualTo("DAILY/3")
        assertThat(Weekly(setOf(THURSDAY, MONDAY)).toStorageString()).isEqualTo("WEEKLY:MON,THU")
        assertThat(Weekly(DEFAULT_WORK_DAYS).toStorageString()).isEqualTo("WEEKLY:MON,TUE,WED,THU,SUN")
        assertThat(Weekly(setOf(FRIDAY), interval = 2).toStorageString()).isEqualTo("WEEKLY/2:FRI")
        assertThat(Monthly(31).toStorageString()).isEqualTo("MONTHLY:31")
        assertThat(Yearly(Month.FEBRUARY, 29).toStorageString()).isEqualTo("YEARLY:2-29")
    }

    @Test
    fun `every rule reads back from its stored form`() {
        listOf(
            Daily(),
            Daily(RepeatRule.MAX_INTERVAL),
            Weekly(setOf(MONDAY, THURSDAY)),
            Weekly(DEFAULT_WORK_DAYS),
            Weekly(setOf(FRIDAY), interval = 2),
            Monthly(1),
            Monthly(31),
            Yearly(Month.FEBRUARY, 29),
            Yearly(Month.DECEMBER, 31),
        ).forEach { rule ->
            assertThat(RepeatRule.fromStorageString(rule.toStorageString())).isEqualTo(rule)
        }
    }

    @Test
    fun `damaged stored forms are rejected`() {
        listOf(
            "", "daily", "HOURLY", "DAILY/0", "DAILY:5", "WEEKLY", "WEEKLY:", "WEEKLY:XYZ", "WEEKLY:MONDAY",
            "WEEKLY/2:MON,TUE", "MONTHLY", "MONTHLY:0", "MONTHLY:32", "MONTHLY/2:5", "YEARLY:13-1",
            "YEARLY:2-30", "YEARLY:2", "YEARLY/2:1-1",
        ).forEach { text ->
            assertThrows("'$text' should be rejected", IllegalArgumentException::class.java) {
                RepeatRule.fromStorageString(text)
            }
        }
    }

    @Test
    fun `impossible rules can't be created`() {
        assertThrows(IllegalArgumentException::class.java) { Daily(0) }
        assertThrows(IllegalArgumentException::class.java) { Weekly(emptySet()) }
        assertThrows(IllegalArgumentException::class.java) { Weekly(setOf(MONDAY, TUESDAY), interval = 2) }
        assertThrows(IllegalArgumentException::class.java) { Monthly(32) }
        assertThrows(IllegalArgumentException::class.java) { Yearly(Month.APRIL, 31) }
    }

    // --- Next occurrence ---

    @Test
    fun `daily rules step by their interval`() {
        assertThat(Daily().nextAfter(date("2026-10-05"))).isEqualTo(date("2026-10-06"))
        assertThat(Daily(3).nextAfter(date("2026-10-05"))).isEqualTo(date("2026-10-08"))
        assertThat(Daily().nextAfter(date("2026-12-31"))).isEqualTo(date("2027-01-01"))
    }

    @Test
    fun `weekly rules go to the next listed day`() {
        val mondayAndThursday = Weekly(setOf(MONDAY, THURSDAY))
        assertThat(mondayAndThursday.nextAfter(date("2026-10-05"))).isEqualTo(date("2026-10-08"))
        assertThat(mondayAndThursday.nextAfter(date("2026-10-08"))).isEqualTo(date("2026-10-12"))
        assertThat(mondayAndThursday.nextAfter(date("2026-10-10"))).isEqualTo(date("2026-10-12"))
    }

    @Test
    fun `work-day rule skips the weekend`() {
        // Thursday 8 October -> Sunday 11 October (Friday and Saturday are not work days).
        assertThat(Weekly(DEFAULT_WORK_DAYS).nextAfter(date("2026-10-08"))).isEqualTo(date("2026-10-11"))
    }

    @Test
    fun `every-other-week rule jumps two weeks from an occurrence`() {
        val everyOtherFriday = Weekly(setOf(FRIDAY), interval = 2)
        assertThat(everyOtherFriday.nextAfter(date("2026-10-09"))).isEqualTo(date("2026-10-23"))
        // From a day that isn't a Friday, the series starts at the next Friday.
        assertThat(everyOtherFriday.nextAfter(date("2026-10-05"))).isEqualTo(date("2026-10-09"))
    }

    @Test
    fun `monthly on the 31st uses the last day of shorter months and returns to the 31st`() {
        val rule = Monthly(31)
        assertThat(rule.nextAfter(date("2026-01-31"))).isEqualTo(date("2026-02-28"))
        assertThat(rule.nextAfter(date("2026-02-28"))).isEqualTo(date("2026-03-31"))
        assertThat(rule.nextAfter(date("2028-01-31"))).isEqualTo(date("2028-02-29"))
        assertThat(rule.nextAfter(date("2026-04-15"))).isEqualTo(date("2026-04-30"))
    }

    @Test
    fun `monthly mid-month rule`() {
        val rule = Monthly(15)
        assertThat(rule.nextAfter(date("2026-10-05"))).isEqualTo(date("2026-10-15"))
        assertThat(rule.nextAfter(date("2026-10-15"))).isEqualTo(date("2026-11-15"))
        assertThat(rule.nextAfter(date("2026-12-20"))).isEqualTo(date("2027-01-15"))
    }

    @Test
    fun `yearly on 29 February falls on 28 February outside leap years`() {
        val rule = Yearly(Month.FEBRUARY, 29)
        assertThat(rule.nextAfter(date("2026-03-01"))).isEqualTo(date("2027-02-28"))
        assertThat(rule.nextAfter(date("2027-02-28"))).isEqualTo(date("2028-02-29"))
        assertThat(rule.nextAfter(date("2028-02-29"))).isEqualTo(date("2029-02-28"))
    }

    @Test
    fun `yearly rule`() {
        val rule = Yearly(Month.OCTOBER, 12)
        assertThat(rule.nextAfter(date("2026-10-05"))).isEqualTo(date("2026-10-12"))
        assertThat(rule.nextAfter(date("2026-10-12"))).isEqualTo(date("2027-10-12"))
    }

    // --- First occurrence ---

    @Test
    fun `first occurrence counts the start day when it matches`() {
        assertThat(Weekly(setOf(MONDAY)).firstOnOrAfter(date("2026-10-05"))).isEqualTo(date("2026-10-05"))
        assertThat(Weekly(setOf(MONDAY)).firstOnOrAfter(date("2026-10-06"))).isEqualTo(date("2026-10-12"))
        assertThat(Daily(3).firstOnOrAfter(date("2026-10-06"))).isEqualTo(date("2026-10-06"))
    }

    @Test
    fun `the clamped last day counts as a monthly occurrence`() {
        assertThat(Monthly(31).isOccurrence(date("2026-02-28"))).isTrue()
        assertThat(Monthly(31).isOccurrence(date("2026-02-27"))).isFalse()
        assertThat(Yearly(Month.FEBRUARY, 29).isOccurrence(date("2027-02-28"))).isTrue()
    }
}

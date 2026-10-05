package dev.maahdi.mavick.time

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.time.RepeatRule.Daily
import dev.maahdi.mavick.time.RepeatRule.Monthly
import dev.maahdi.mavick.time.RepeatRule.Weekly
import dev.maahdi.mavick.time.RepeatRule.Yearly
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Quick-add examples. Unless a case says otherwise, "now" is Monday 5 October 2026, 10:00, dates
 * are day/month, and work days are Sunday to Thursday.
 */
@RunWith(Parameterized::class)
class WhenParserTest(private val case: Case) {

    data class Case(
        val input: String,
        val title: String,
        val date: String? = null,
        val time: String? = null,
        val repeat: RepeatRule? = null,
        val now: LocalDateTime = MONDAY_10AM,
        val order: DateOrder = DateOrder.DAY_MONTH,
    ) {
        override fun toString() = "\"$input\"" + (if (now != MONDAY_10AM) " at $now" else "") +
            (if (order != DateOrder.DAY_MONTH) " ($order)" else "")
    }

    @Test
    fun parses() {
        val parsed = WhenParser(case.order, WORK_DAYS).parse(case.input, case.now)

        assertThat(parsed).isEqualTo(
            ParsedTask(
                title = case.title,
                dueDate = case.date?.let(LocalDate::parse),
                dueTime = case.time?.let(LocalTime::parse),
                repeatRule = case.repeat,
            ),
        )
    }

    companion object {
        private val MONDAY_10AM = LocalDateTime.of(2026, 10, 5, 10, 0)
        private val WORK_DAYS = setOf(SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Case> = listOf(
            // Relative days and times
            Case("Call the bank tomorrow 5pm", "Call the bank", "2026-10-06", "17:00"),
            Case("Call the bank tomorrow at 5pm", "Call the bank", "2026-10-06", "17:00"),
            Case("Call TOMORROW AT 5PM", "Call", "2026-10-06", "17:00"),
            Case("call tmrw", "call", "2026-10-06"),
            Case("call tmr", "call", "2026-10-06"),
            Case("call day after tomorrow", "call", "2026-10-07"),
            Case("Buy milk today", "Buy milk", "2026-10-05"),
            Case("Pay 500 tk tomorrow", "Pay 500 tk", "2026-10-06"),
            Case("tomorrow", "", "2026-10-06"),
            Case("pay bills, tomorrow", "pay bills", "2026-10-06"),
            Case("tomorrow - pay bills", "pay bills", "2026-10-06"),
            Case("Renew passport in 3 days", "Renew passport", "2026-10-08"),
            Case("Renew passport in 2 weeks", "Renew passport", "2026-10-19"),
            Case("Renew passport in a month", "Renew passport", "2026-11-05"),
            Case("Renew passport next week", "Renew passport", "2026-10-12"),
            Case("Renew passport next month", "Renew passport", "2026-11-05"),
            Case("Water plants in 2 hours", "Water plants", "2026-10-05", "12:00"),
            Case("Water plants in 30 minutes", "Water plants", "2026-10-05", "10:30"),
            Case("Water plants in half an hour", "Water plants", "2026-10-05", "10:30"),
            Case("Water plants in an hour", "Water plants", "2026-10-05", "11:00"),
            Case("in 2 days at 3pm", "", "2026-10-07", "15:00"),

            // Weekdays: always the next one after today
            Case("Submit report by friday", "Submit report", "2026-10-09"),
            Case("Meeting monday 9am", "Meeting", "2026-10-12", "09:00"),
            Case("Gym next monday", "Gym", "2026-10-12"),
            Case("Dentist this friday at 4:30pm", "Dentist", "2026-10-09", "16:30"),
            Case("Dentist on fri", "Dentist", "2026-10-09"),
            Case("Exam next friday 9am", "Exam", "2026-10-09", "09:00"),
            Case("Call Sunday", "Call", "2026-10-11"),
            Case("Exam friday", "Exam", "2026-10-16", now = LocalDateTime.of(2026, 10, 9, 10, 0)),
            Case("Exam this friday", "Exam", "2026-10-16", now = LocalDateTime.of(2026, 10, 9, 10, 0)),
            Case("Sat exam prep", "Sat exam prep"),

            // Parts of the day
            Case("Buy milk tonight", "Buy milk", "2026-10-05", "20:00"),
            Case("Buy milk tonight", "Buy milk", "2026-10-05", now = LocalDateTime.of(2026, 10, 5, 21, 0)),
            Case("Call mom this evening", "Call mom", "2026-10-05", "18:00"),
            Case("Call mom tomorrow morning", "Call mom", "2026-10-06", "09:00"),
            Case("Call mom friday evening", "Call mom", "2026-10-09", "18:00"),
            Case("Call mom in the evening", "Call mom", "2026-10-05", "18:00"),
            Case("Call at 5 tomorrow morning", "Call", "2026-10-06", "05:00"),
            Case("Dinner tomorrow night at 8", "Dinner", "2026-10-06", "20:00"),
            Case("Meeting at 2 in the afternoon", "Meeting", "2026-10-05", "14:00"),
            Case("Breakfast at 8 in the morning", "Breakfast", "2026-10-06", "08:00"),

            // Times without a date: today, or tomorrow once passed
            Case("Dentist 4pm", "Dentist", "2026-10-05", "16:00"),
            Case("Dentist 9am", "Dentist", "2026-10-06", "09:00"),
            Case("Dentist 10am", "Dentist", "2026-10-06", "10:00"),
            Case("Call mom at 5", "Call mom", "2026-10-05", "17:00"),
            Case("Call mom at 9", "Call mom", "2026-10-06", "09:00"),
            Case("Call mom at 11", "Call mom", "2026-10-05", "11:00"),
            Case("Call mom at 12", "Call mom", "2026-10-05", "12:00"),
            Case("Call mom at 17:30", "Call mom", "2026-10-05", "17:30"),
            Case("Call mom at 9:15", "Call mom", "2026-10-06", "09:15"),
            Case("Call mom at 9:15pm", "Call mom", "2026-10-05", "21:15"),
            Case("Call mom 12am", "Call mom", "2026-10-06", "00:00"),
            Case("Call mom 12pm", "Call mom", "2026-10-05", "12:00"),
            Case("Call @5pm", "Call", "2026-10-05", "17:00"),
            Case("Call at 5 p.m.", "Call", "2026-10-05", "17:00"),
            // A dot between hours and minutes, as often written ("10.30")
            Case("call me today at 10.08 AM", "call me", "2026-10-05", "10:08"),
            Case("Call mom at 10.08am", "Call mom", "2026-10-05", "10:08"),
            Case("Call mom 9.15 p.m.", "Call mom", "2026-10-05", "21:15"),
            Case("Meeting at 10.30", "Meeting", "2026-10-05", "10:30"),
            Case("Meeting at 5.30", "Meeting", "2026-10-05", "17:30"),
            Case("Meeting @ 9.45", "Meeting", "2026-10-06", "09:45"),
            Case("Pay 10.50 at 5pm", "Pay 10.50", "2026-10-05", "17:00"),
            Case("Pick up kids at 3:30", "Pick up kids", "2026-10-05", "15:30"),
            Case("Wake up at 06:30", "Wake up", "2026-10-06", "06:30"),
            Case("Read chapter 5 at 7", "Read chapter 5", "2026-10-06", "07:00"),
            Case("Lunch with 2 people at 1", "Lunch with 2 people", "2026-10-05", "13:00"),
            Case("Lunch at noon", "Lunch", "2026-10-05", "12:00"),
            Case("Lunch at noon tomorrow", "Lunch", "2026-10-06", "12:00"),

            // Written dates
            Case("Flight on 12/10", "Flight", "2026-10-12"),
            Case("Flight on 12/10", "Flight", "2026-12-10", order = DateOrder.MONTH_DAY),
            Case("Flight 3/4", "Flight", "2027-04-03"),
            Case("Flight 3/4", "Flight", "2027-03-04", order = DateOrder.MONTH_DAY),
            Case("Flight 25/12/2026", "Flight", "2026-12-25"),
            Case("Flight 25/12/27", "Flight", "2027-12-25"),
            Case("Flight 31/2", "Flight 31/2"),
            Case("Flight 2026-11-20", "Flight", "2026-11-20"),
            Case("Report due 9/10", "Report", "2026-10-09"),
            Case("Birthday 12 oct", "Birthday", "2026-10-12"),
            Case("Birthday oct 12", "Birthday", "2026-10-12"),
            Case("Birthday 12th of October", "Birthday", "2026-10-12"),
            Case("Birthday October 12th, 2027", "Birthday", "2027-10-12"),
            Case("Birthday 5 jan", "Birthday", "2027-01-05"),
            Case("Birthday 5 oct", "Birthday", "2026-10-05"),
            Case("May day 1 may", "May day", "2027-05-01"),
            Case("Meeting at 10am on 12/10", "Meeting", "2026-10-12", "10:00"),
            Case("Meeting 12/10 at 10am", "Meeting", "2026-10-12", "10:00"),
            Case("Call 10/10/2026 at 9", "Call", "2026-10-10", "09:00"),

            // Day of the month: the next one after today
            Case("Pay rent on the 1st 10am", "Pay rent", "2026-11-01", "10:00"),
            Case("Pay rent on the 5th", "Pay rent", "2026-11-05"),
            Case("Meet on the 31st", "Meet", "2026-10-31"),
            Case("Meet on the 31st", "Meet", "2026-12-31", now = LocalDateTime.of(2026, 11, 5, 10, 0)),
            Case("Meet the 15th", "Meet", "2026-10-15"),

            // Repeats
            Case("Take medicine every day at 9pm", "Take medicine", "2026-10-05", "21:00", Daily()),
            Case("Take medicine every day at 9am", "Take medicine", "2026-10-06", "09:00", Daily()),
            Case("Take medicine daily", "Take medicine", "2026-10-05", repeat = Daily()),
            Case("every day", "", "2026-10-05", repeat = Daily()),
            Case("Stretch every other day", "Stretch", "2026-10-05", repeat = Daily(2)),
            Case("Stretch every 3 days", "Stretch", "2026-10-05", repeat = Daily(3)),
            Case("Standup every weekday at 10:30", "Standup", "2026-10-05", "10:30", Weekly(WORK_DAYS)),
            Case("Standup every weekday", "Standup", "2026-10-11", repeat = Weekly(WORK_DAYS), now = LocalDateTime.of(2026, 10, 9, 10, 0)),
            Case("Gym every monday and thursday", "Gym", "2026-10-05", repeat = Weekly(setOf(MONDAY, THURSDAY))),
            Case("Gym every mon, wed & fri at 7am", "Gym", "2026-10-07", "07:00", Weekly(setOf(MONDAY, WEDNESDAY, FRIDAY))),
            Case("Gym on tuesdays", "Gym", "2026-10-06", repeat = Weekly(setOf(TUESDAY))),
            Case("Take out trash every sunday at 9pm", "Take out trash", "2026-10-11", "21:00", Weekly(setOf(SUNDAY))),
            Case("Call at 5pm every friday", "Call", "2026-10-09", "17:00", Weekly(setOf(FRIDAY))),
            Case("Review every week", "Review", "2026-10-05", repeat = Weekly(setOf(MONDAY))),
            Case("Review weekly on friday", "Review", "2026-10-09", repeat = Weekly(setOf(FRIDAY))),
            Case("Clean every 2 weeks", "Clean", "2026-10-05", repeat = Weekly(setOf(MONDAY), interval = 2)),
            Case("Clean every other week on saturday", "Clean", "2026-10-10", repeat = Weekly(setOf(SATURDAY), interval = 2)),
            Case("Pay rent every month on the 1st", "Pay rent", "2026-11-01", repeat = Monthly(1)),
            Case("Pay rent monthly", "Pay rent", "2026-10-05", repeat = Monthly(5)),
            Case("Pay rent every month on the 31st", "Pay rent", "2026-10-31", repeat = Monthly(31)),
            Case("Birthday every year on 12 oct", "Birthday", "2026-10-12", repeat = Yearly(Month.OCTOBER, 12)),
            Case("Anniversary yearly 29 feb", "Anniversary", "2028-02-29", repeat = Yearly(Month.FEBRUARY, 29)),
            Case("every year", "", "2026-10-05", repeat = Yearly(Month.OCTOBER, 5)),

            // Polite openings are dropped from the title
            Case("remind me to call mom tomorrow", "call mom", "2026-10-06"),
            Case("Don't forget to pay rent on the 1st", "pay rent", "2026-11-01"),

            // Nothing to understand: the text stays as it is
            Case("", ""),
            Case("   ", ""),
            Case("Just a note", "Just a note"),
            Case("Read 1st chapter", "Read 1st chapter"),
            Case("Call 911", "Call 911"),
            Case("Buy 2 apples", "Buy 2 apples"),
            Case("Fix bug #12", "Fix bug #12"),
            Case("Pay 1.5k", "Pay 1.5k"),
            // A dotted number without "at" is a price, and dots never separate a date
            Case("Pay 10.50", "Pay 10.50"),
            Case("Meet at 10.08.2026", "Meet at 10.08.2026"),
            Case("Visit Mars", "Visit Mars"),
        )
    }
}

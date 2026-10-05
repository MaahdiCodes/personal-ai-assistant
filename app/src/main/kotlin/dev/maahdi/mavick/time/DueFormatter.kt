package dev.maahdi.mavick.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Short, friendly English text for due dates and repeats ("Tomorrow · 17:00", "Every work day"). */
object DueFormatter {
    private val DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val DATE_WITH_YEAR = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)
    private val TIME_24_HOUR = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val TIME_12_HOUR = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    fun dueLabel(date: LocalDate?, time: LocalTime?, today: LocalDate, use24Hour: Boolean): String {
        if (date == null) return time?.let { time(it, use24Hour) }.orEmpty()
        val day = dayLabel(date, today)
        return if (time == null) day else "$day · ${time(time, use24Hour)}"
    }

    fun dayLabel(date: LocalDate, today: LocalDate): String = when (val daysAway = ChronoUnit.DAYS.between(today, date)) {
        0L -> "Today"
        1L -> "Tomorrow"
        -1L -> "Yesterday"
        else -> when {
            daysAway in 2L..6L -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            date.year == today.year -> DATE.format(date)
            else -> DATE_WITH_YEAR.format(date)
        }
    }

    fun time(time: LocalTime, use24Hour: Boolean): String = (if (use24Hour) TIME_24_HOUR else TIME_12_HOUR).format(time)

    fun repeatLabel(rule: RepeatRule, workDays: Set<DayOfWeek>): String = when (rule) {
        is RepeatRule.Daily -> if (rule.interval == 1) "Every day" else "Every ${rule.interval} days"
        is RepeatRule.Weekly -> weeklyLabel(rule, workDays)
        is RepeatRule.Monthly -> "Every month on the ${ordinal(rule.dayOfMonth)}"
        is RepeatRule.Yearly -> "Every year on ${rule.dayOfMonth} ${rule.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
    }

    fun ordinal(number: Int): String {
        val suffix = if (number % 100 in 11..13) {
            "th"
        } else {
            when (number % 10) {
                1 -> "st"
                2 -> "nd"
                3 -> "rd"
                else -> "th"
            }
        }
        return "$number$suffix"
    }

    private fun weeklyLabel(rule: RepeatRule.Weekly, workDays: Set<DayOfWeek>): String {
        val days = rule.days
        return when {
            rule.interval > 1 -> "Every ${rule.interval} weeks on ${fullName(days.single())}"
            days.size == DayOfWeek.entries.size -> "Every day"
            days == workDays -> "Every work day"
            days.size == 1 -> "Every ${fullName(days.single())}"
            else -> "Every " + days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
        }
    }

    private fun fullName(day: DayOfWeek) = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
}

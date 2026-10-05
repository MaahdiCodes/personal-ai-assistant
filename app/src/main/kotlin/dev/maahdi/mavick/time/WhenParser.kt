package dev.maahdi.mavick.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class DateOrder { DAY_MONTH, MONTH_DAY }

/** What quick-add understood from a line of text. */
data class ParsedTask(
    val title: String,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val repeatRule: RepeatRule? = null,
)

/**
 * Finds English dates, times and repeats inside a line of text, such as
 * "Pay rent every month on the 1st at 10am" or "Call the bank tomorrow 5pm". Recognised phrases
 * are removed and the rest becomes the title.
 *
 * Plain rules, no AI: the result is predictable and fully tested, and quick-add always shows what
 * it understood before saving. Choices worth knowing:
 * - A weekday ("friday", "this friday", "next friday") means the next one after today.
 * - A time without a date means today, or tomorrow if that time has already passed.
 * - An hour without am/pm: 1-6 and 12 are afternoon, 7-11 morning ("at 5" = 17:00), unless a
 *   word like "morning" or "evening" says otherwise. "06:30" (leading zero) is taken as written.
 * - Minutes follow a colon ("10:30") or a dot. A dot counts only with am/pm ("10.30 am") or after
 *   a word like "at" ("at 10.30"), so "Pay 10.50" stays text.
 * - Numbers like 12/10 follow [dateOrder].
 */
class WhenParser(
    private val dateOrder: DateOrder = DateOrder.DAY_MONTH,
    private val workDays: Set<DayOfWeek> = DEFAULT_WORK_DAYS,
) {
    fun parse(input: String, now: LocalDateTime): ParsedTask {
        val text = UnusedText(input)
        val today = now.toLocalDate()
        val nowTime = now.toLocalTime()

        val repeat = findRepeat(text)
        val relative = findRelative(text, now)
        val partOfDay = findPartOfDay(text)
        var date = relative?.date ?: findDate(text, today)
        val explicitTime = relative?.time ?: findTime(text, partOfDay?.part)
        var time = explicitTime ?: partOfDay?.part?.defaultTime

        if (date == null && partOfDay?.meansToday == true) {
            date = today
            // "tonight" typed after 8 pm still means today, just without a time.
            if (explicitTime == null && time != null && !time.isAfter(nowTime)) time = null
        }

        var rule: RepeatRule? = null
        if (repeat != null) {
            val anchor = date ?: today
            rule = repeat.build(anchor)
            var start = rule.firstOnOrAfter(anchor)
            if (date == null && start == today && time != null && !time.isAfter(nowTime)) {
                start = rule.nextAfter(today)
            }
            date = start
        } else if (date == null && time != null) {
            date = if (time.isAfter(nowTime)) today else today.plusDays(1)
        }

        return ParsedTask(title = text.remainingTitle(), dueDate = date, dueTime = time, repeatRule = rule)
    }

    // --- Repeats ------------------------------------------------------------------------------

    /** A repeat whose details may depend on the first due date ("every week" = on that weekday). */
    private fun interface RepeatSpec {
        fun build(anchor: LocalDate): RepeatRule
    }

    private fun findRepeat(text: UnusedText): RepeatSpec? =
        text.take(EVERY_N_DAYS) { match ->
            intervalOf(match.groupValues[1])?.let { interval -> RepeatSpec { RepeatRule.Daily(interval) } }
        }
            ?: text.take(DAILY) { RepeatSpec { RepeatRule.Daily() } }
            ?: text.take(WORK_DAYS) { RepeatSpec { RepeatRule.Weekly(workDays) } }
            ?: text.take(EVERY_N_WEEKS) { match ->
                val interval = intervalOf(match.groupValues[1]) ?: return@take null
                val day = match.groups[2]?.value?.let(::dayOfWeekOf)
                RepeatSpec { anchor -> RepeatRule.Weekly(setOf(day ?: anchor.dayOfWeek), interval) }
            }
            ?: text.take(EVERY_LISTED_WEEKDAYS) { match ->
                dayListOf(match.groupValues[1])?.let { days -> RepeatSpec { RepeatRule.Weekly(days) } }
            }
            ?: text.take(PLURAL_WEEKDAY) { match ->
                dayOfWeekOf(match.groupValues[1])?.let { day -> RepeatSpec { RepeatRule.Weekly(setOf(day)) } }
            }
            ?: text.take(WEEKLY) { match ->
                val day = match.groups[1]?.value?.let(::dayOfWeekOf)
                RepeatSpec { anchor -> RepeatRule.Weekly(setOf(day ?: anchor.dayOfWeek)) }
            }
            ?: text.take(MONTHLY) { match ->
                val day = match.groups[1]?.value?.toInt()
                if (day != null && day !in 1..31) null else RepeatSpec { anchor -> RepeatRule.Monthly(day ?: anchor.dayOfMonth) }
            }
            ?: text.take(YEARLY) { RepeatSpec { anchor -> RepeatRule.Yearly(anchor.month, anchor.dayOfMonth) } }

    private fun intervalOf(text: String): Int? =
        if (text.equals("other", ignoreCase = true)) 2 else text.toIntOrNull()?.takeIf { it in 1..RepeatRule.MAX_INTERVAL }

    private fun dayListOf(text: String): Set<DayOfWeek>? {
        val days = WORD.findAll(text).map { it.value }.filterNot { it.equals("and", ignoreCase = true) }.map(::dayOfWeekOf).toList()
        return if (days.isEmpty() || days.any { it == null }) null else days.filterNotNull().toSet()
    }

    // --- "in 2 hours", "in 3 days" -------------------------------------------------------------

    private class DateAndTime(val date: LocalDate, val time: LocalTime?) {
        companion object {
            fun at(moment: LocalDateTime): DateAndTime =
                moment.truncatedTo(ChronoUnit.MINUTES).let { DateAndTime(it.toLocalDate(), it.toLocalTime()) }
        }
    }

    private fun findRelative(text: UnusedText, now: LocalDateTime): DateAndTime? = text.take(RELATIVE) { match ->
        val amountText = match.groupValues[1].lowercase()
        val unit = match.groupValues[2].lowercase()
        if (amountText.startsWith("half")) {
            return@take if (unit.startsWith("h")) DateAndTime.at(now.plusMinutes(30)) else null
        }
        val amount = (amountText.toIntOrNull() ?: NUMBER_WORDS[amountText])?.toLong() ?: return@take null
        val today = now.toLocalDate()
        when {
            unit.startsWith("min") -> DateAndTime.at(now.plusMinutes(amount))
            unit.startsWith("h") -> DateAndTime.at(now.plusHours(amount))
            unit.startsWith("d") -> DateAndTime(today.plusDays(amount), null)
            unit.startsWith("w") -> DateAndTime(today.plusWeeks(amount), null)
            else -> DateAndTime(today.plusMonths(amount), null)
        }
    }

    // --- "tonight", "tomorrow morning", "in the evening" --------------------------------------

    private enum class PartOfDay(val defaultTime: LocalTime, val isAfternoonOrLater: Boolean) {
        MORNING(LocalTime.of(9, 0), false),
        AFTERNOON(LocalTime.of(14, 0), true),
        EVENING(LocalTime.of(18, 0), true),
        NIGHT(LocalTime.of(20, 0), true),
    }

    private class PartOfDayMatch(val part: PartOfDay, val meansToday: Boolean)

    private fun findPartOfDay(text: UnusedText): PartOfDayMatch? =
        text.take(TONIGHT) { PartOfDayMatch(PartOfDay.NIGHT, meansToday = true) }
            ?: text.take(THIS_PART_OF_DAY) { match -> PartOfDayMatch(partOfDayOf(match.groupValues[1]), meansToday = true) }
            // "tomorrow morning": take only "morning"; "tomorrow" is left for the date.
            ?: text.take(DATE_WORD_PART_OF_DAY, group = 1) { match ->
                PartOfDayMatch(partOfDayOf(match.groupValues[1]), meansToday = false)
            }
            ?: text.take(IN_THE_PART_OF_DAY) { match ->
                PartOfDayMatch(partOfDayOf(match.groups[1]?.value ?: match.groupValues[2]), meansToday = false)
            }

    private fun partOfDayOf(word: String) = PartOfDay.valueOf(word.uppercase())

    // --- Dates --------------------------------------------------------------------------------

    private fun findDate(text: UnusedText, today: LocalDate): LocalDate? =
        text.take(ISO_DATE) { match -> dateOrNull(match.int(1), match.int(2), match.int(3)) }
            ?: text.take(DAY_MONTH_NAME) { match ->
                monthOf(match.groupValues[2])?.let { dateWithOptionalYear(match.groups[3]?.value, it.value, match.int(1), today) }
            }
            ?: text.take(MONTH_NAME_DAY) { match ->
                monthOf(match.groupValues[1])?.let { dateWithOptionalYear(match.groups[3]?.value, it.value, match.int(2), today) }
            }
            ?: text.take(NUMERIC_DATE) { match ->
                val first = match.int(1)
                val second = match.int(2)
                val (day, month) = if (dateOrder == DateOrder.DAY_MONTH) first to second else second to first
                dateWithOptionalYear(match.groups[3]?.value, month, day, today)
            }
            ?: text.take(DAY_OF_MONTH) { match -> nextDayOfMonth(match.int(1), today) }
            ?: text.take(DAY_AFTER_TOMORROW) { today.plusDays(2) }
            ?: text.take(TOMORROW) { today.plusDays(1) }
            ?: text.take(TODAY) { today }
            ?: text.take(NEXT_WEEK) { today.plusWeeks(1) }
            ?: text.take(NEXT_MONTH) { today.plusMonths(1) }
            ?: text.take(WEEKDAY) { match -> dayOfWeekOf(match.groupValues[1])?.let { today.with(TemporalAdjusters.next(it)) } }
            ?: text.take(WEEKDAY_ABBREVIATED) { match ->
                dayOfWeekOf(match.groupValues[1])?.let { today.with(TemporalAdjusters.next(it)) }
            }

    /** Without a year: the first such date from today on (29 Feb waits for a leap year). */
    private fun dateWithOptionalYear(yearText: String?, month: Int, day: Int, today: LocalDate): LocalDate? {
        if (yearText != null) {
            val year = yearText.toInt().let { if (yearText.length == 2) 2000 + it else it }
            return dateOrNull(year, month, day)
        }
        for (yearsAhead in 0..MAX_YEARS_AHEAD) {
            val candidate = dateOrNull(today.year + yearsAhead, month, day) ?: continue
            if (!candidate.isBefore(today)) return candidate
        }
        return null
    }

    /** "on the 31st": the next such day after today, skipping months that are too short. */
    private fun nextDayOfMonth(day: Int, today: LocalDate): LocalDate? {
        if (day !in 1..31) return null
        for (monthsAhead in 0L..12L) {
            val month = YearMonth.from(today).plusMonths(monthsAhead)
            if (day > month.lengthOfMonth()) continue
            val candidate = month.atDay(day)
            if (candidate.isAfter(today)) return candidate
        }
        return null
    }

    private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    // --- Times --------------------------------------------------------------------------------

    private fun findTime(text: UnusedText, part: PartOfDay?): LocalTime? {
        val hourAndMinutes = { match: MatchResult -> guessTime(match.groupValues[1], match.int(2), part) }
        return text.take(TIME_12_HOUR) { match ->
            twelveHourTime(match.int(1), match.groups[2]?.value?.toInt() ?: 0, isPm = match.groupValues[3].equals("p", ignoreCase = true))
        }
            ?: text.take(TIME_WITH_MINUTES, convert = hourAndMinutes)
            ?: text.take(TIME_WITH_DOT, convert = hourAndMinutes)
            ?: text.take(BARE_HOUR) { match -> guessTime(match.groupValues[1], 0, part) }
            ?: text.take(NOON) { LocalTime.NOON }
    }

    private fun guessTime(hourText: String, minute: Int, part: PartOfDay?): LocalTime? {
        val hour = hourText.toInt()
        if (hour > 23 || minute > 59) return null
        val asWritten = hour == 0 || hour > 12 || (hourText.length == 2 && hourText.startsWith('0'))
        if (asWritten) return LocalTime.of(hour, minute)
        val isPm = part?.isAfternoonOrLater ?: (hour <= 6 || hour == 12)
        return twelveHourTime(hour, minute, isPm)
    }

    private fun twelveHourTime(hour: Int, minute: Int, isPm: Boolean): LocalTime? {
        if (hour !in 1..12 || minute !in 0..59) return null
        val hourOfDay = when {
            hour == 12 -> if (isPm) 12 else 0
            isPm -> hour + 12
            else -> hour
        }
        return LocalTime.of(hourOfDay, minute)
    }

    private companion object {
        const val MAX_YEARS_AHEAD = 8

        const val WEEKDAY_FULL = "monday|tuesday|wednesday|thursday|friday|saturday|sunday"
        const val WEEKDAY_SHORT = "mon|tues|tue|wed|thurs|thur|thu|fri|sat|sun"
        const val WEEKDAY_ANY = "$WEEKDAY_FULL|$WEEKDAY_SHORT"
        const val MONTH_NAME =
            "january|february|march|april|may|june|july|august|september|october|november|december|" +
                "jan|feb|mar|apr|jun|jul|aug|sept|sep|oct|nov|dec"
        const val ORDINAL = "(?:st|nd|rd|th)"
        const val DATE_PREFIX = "(?:(?:on|by|due|until|before)\\s+)?"
        /** A word saying a time follows: "at", "by", "@" and so on. */
        const val TIME_WORD = "(?:\\b(?:at|by|before|around|from)\\s+|@\\s*)"
        const val TIME_PREFIX = "$TIME_WORD?"

        /** Not inside a number: in "10.08 am", "08 am" alone must not be read as 8 am. */
        const val NOT_INSIDE_NUMBER = "(?<![\\d.:])"
        const val TOMORROW_WORDS = "tomorrow|tmrw|tmr|tommorow|tomorow|tommorrow"

        fun pattern(regex: String) = Regex(regex, RegexOption.IGNORE_CASE)

        val EVERY_N_DAYS = pattern("\\bevery\\s+(\\d{1,3}|other)\\s+days?\\b")
        val DAILY = pattern("\\b(?:every\\s*day|daily)\\b")
        val WORK_DAYS = pattern("\\b(?:every\\s+(?:work\\s*day|weekday)s?|on\\s+weekdays|weekdays)\\b")
        val EVERY_N_WEEKS = pattern("\\bevery\\s+(\\d{1,2}|other)\\s+weeks?(?:\\s+on\\s+($WEEKDAY_ANY))?\\b")
        val EVERY_LISTED_WEEKDAYS =
            pattern("\\bevery\\s+((?:$WEEKDAY_ANY)(?:\\s*(?:,|&|\\+|\\band\\b)\\s*(?:$WEEKDAY_ANY))*)\\b")
        val PLURAL_WEEKDAY = pattern("\\b(?:on\\s+)?(mondays|tuesdays|wednesdays|thursdays|fridays|saturdays|sundays)\\b")
        val WEEKLY = pattern("\\b(?:every\\s+week|weekly)(?:\\s+on\\s+($WEEKDAY_ANY))?\\b")
        val MONTHLY = pattern("\\b(?:every\\s+month|monthly)(?:\\s+on\\s+(?:the\\s+)?(\\d{1,2})$ORDINAL?)?\\b")
        val YEARLY = pattern("\\b(?:every\\s+year|yearly|annually)\\b")

        val RELATIVE = pattern(
            "\\bin\\s+(\\d{1,3}|an?|one|two|three|four|five|six|seven|eight|nine|ten|half\\s+an)\\s+" +
                "(minutes?|mins?|hours?|hrs?|days?|weeks?|months?)\\b",
        )
        val NUMBER_WORDS = mapOf(
            "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
            "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        )

        val TONIGHT = pattern("\\btonight\\b")
        val THIS_PART_OF_DAY = pattern("\\bthis\\s+(morning|afternoon|evening)\\b")
        val DATE_WORD_PART_OF_DAY = pattern("\\b(?:today|$TOMORROW_WORDS|$WEEKDAY_FULL)\\s+(morning|afternoon|evening|night)\\b")
        val IN_THE_PART_OF_DAY = pattern("\\b(?:in\\s+the\\s+(morning|afternoon|evening)|at\\s+(night))\\b")

        val ISO_DATE = pattern("\\b$DATE_PREFIX(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b")
        val DAY_MONTH_NAME = pattern("\\b$DATE_PREFIX(\\d{1,2})$ORDINAL?\\s+(?:of\\s+)?($MONTH_NAME)\\.?(?:,?\\s+(\\d{4}))?\\b")
        val MONTH_NAME_DAY = pattern("\\b$DATE_PREFIX($MONTH_NAME)\\.?\\s+(\\d{1,2})$ORDINAL?(?:,?\\s+(\\d{4}))?\\b")
        val NUMERIC_DATE = pattern("\\b$DATE_PREFIX(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}|\\d{2}))?\\b")
        val DAY_OF_MONTH = pattern("\\b(?:(?:on|by|due|until|before)\\s+(?:the\\s+)?|the\\s+)(\\d{1,2})$ORDINAL\\b")
        val DAY_AFTER_TOMORROW = pattern("\\b$DATE_PREFIX(?:the\\s+)?day\\s+after\\s+tomorrow\\b")
        val TOMORROW = pattern("\\b$DATE_PREFIX(?:$TOMORROW_WORDS)\\b")
        val TODAY = pattern("\\b${DATE_PREFIX}today\\b")
        val NEXT_WEEK = pattern("\\bnext\\s+week\\b")
        val NEXT_MONTH = pattern("\\bnext\\s+month\\b")
        val WEEKDAY = pattern("\\b(?:(?:on|by|due|until|before|this|next)\\s+)?($WEEKDAY_FULL)\\b")
        val WEEKDAY_ABBREVIATED = pattern("\\b(?:on|by|due|until|before|this|next)\\s+($WEEKDAY_SHORT)\\b")

        val TIME_12_HOUR = pattern("$TIME_PREFIX$NOT_INSIDE_NUMBER\\b(\\d{1,2})(?:[:.]([0-5]\\d))?\\s*([ap])\\.?m\\b\\.?")
        val TIME_WITH_MINUTES = pattern("$TIME_PREFIX\\b(\\d{1,2}):([0-5]\\d)\\b")

        // "at 10.30". Only after a time word, so a price like "Pay 10.50" stays text, and never
        // followed by another dotted number, so a date like "10.08.2026" stays text too.
        val TIME_WITH_DOT = pattern("$TIME_WORD(\\d{1,2})\\.([0-5]\\d)\\b(?![.:]\\d)")
        val BARE_HOUR = pattern(
            "(?:\\b(?:at|around)\\s+|@\\s*)(\\d{1,2})\\b(?!\\s*(?:[/:%.,]\\d|$ORDINAL\\b|" +
                "(?:days?|weeks?|months?|years?|hours?|hrs?|mins?|minutes?|people|persons?|times)\\b))",
        )
        val NOON = pattern("\\b(?:at\\s+)?(?:noon|midday)\\b")

        val WORD = Regex("[a-z]+", RegexOption.IGNORE_CASE)

        fun dayOfWeekOf(word: String): DayOfWeek? =
            DayOfWeek.entries.firstOrNull { it.name.startsWith(word.take(3).uppercase()) }

        fun monthOf(word: String): Month? = Month.entries.firstOrNull { it.name.startsWith(word.take(3).uppercase()) }

        fun MatchResult.int(group: Int): Int = groupValues[group].toInt()
    }
}

/** The input, with each recognised phrase blanked out once used. What is left becomes the title. */
private class UnusedText(input: String) {
    private val chars = input.toCharArray()

    /**
     * Uses the first match of [regex] for which [convert] returns a value, blanking that match
     * (or only [group] of it, when given) so later rules can't reuse it.
     */
    fun <T : Any> take(regex: Regex, group: Int = 0, convert: (MatchResult) -> T?): T? {
        for (match in regex.findAll(String(chars))) {
            val value = convert(match) ?: continue
            val range = if (group == 0) match.range else match.groups[group]?.range ?: match.range
            for (index in range) chars[index] = ' '
            return value
        }
        return null
    }

    fun remainingTitle(): String {
        val collapsed = String(chars).replace(WHITESPACE, " ").trim().trim(*EDGE_PUNCTUATION).trim()
        return LEADING_FILLER.replaceFirst(collapsed, "").trim()
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val EDGE_PUNCTUATION = charArrayOf(',', ';', ':', '-', '–', '—')
        val LEADING_FILLER = Regex("^(?:remind me to|remember to|don'?t forget to)\\s+", RegexOption.IGNORE_CASE)
    }
}

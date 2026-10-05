package dev.maahdi.mavick.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth

/** Your work week, used by "every work day" repeats (docs/PLAN.md §10). Changeable in Settings. */
val DEFAULT_WORK_DAYS: Set<DayOfWeek> = setOf(
    DayOfWeek.SUNDAY,
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
)

/**
 * How a task repeats. Stored in the database as short text ([toStorageString]). Existing rows are
 * read back with [fromStorageString], so that text format may be extended but never changed.
 */
sealed interface RepeatRule {
    /** The next occurrence strictly after [date]. */
    fun nextAfter(date: LocalDate): LocalDate

    /** Whether [date] is an occurrence. */
    fun isOccurrence(date: LocalDate): Boolean

    fun toStorageString(): String

    /** The first occurrence on or after [date]. */
    fun firstOnOrAfter(date: LocalDate): LocalDate = if (isOccurrence(date)) date else nextAfter(date)

    /** Every [interval] days, counted from the current due date. */
    data class Daily(val interval: Int = 1) : RepeatRule {
        init {
            require(interval in 1..MAX_INTERVAL) { "Interval must be between 1 and $MAX_INTERVAL." }
        }

        override fun nextAfter(date: LocalDate): LocalDate = date.plusDays(interval.toLong())

        override fun isOccurrence(date: LocalDate) = true

        override fun toStorageString() = if (interval == 1) "DAILY" else "DAILY/$interval"
    }

    /** On [days] every week, or on a single day every [interval] weeks. */
    data class Weekly(val days: Set<DayOfWeek>, val interval: Int = 1) : RepeatRule {
        init {
            require(days.isNotEmpty()) { "At least one day is needed." }
            require(interval in 1..MAX_INTERVAL) { "Interval must be between 1 and $MAX_INTERVAL." }
            require(interval == 1 || days.size == 1) { "Repeating every few weeks works on one day only." }
        }

        override fun nextAfter(date: LocalDate): LocalDate {
            if (interval > 1 && date.dayOfWeek in days) return date.plusWeeks(interval.toLong())
            var candidate = date.plusDays(1)
            while (candidate.dayOfWeek !in days) candidate = candidate.plusDays(1)
            return candidate
        }

        override fun isOccurrence(date: LocalDate) = date.dayOfWeek in days

        override fun toStorageString(): String {
            val dayCodes = days.sorted().joinToString(",") { it.name.take(3) }
            return if (interval == 1) "WEEKLY:$dayCodes" else "WEEKLY/$interval:$dayCodes"
        }
    }

    /** On [dayOfMonth] every month; in shorter months, on the month's last day. */
    data class Monthly(val dayOfMonth: Int) : RepeatRule {
        init {
            require(dayOfMonth in 1..31) { "Day of month must be between 1 and 31." }
        }

        override fun nextAfter(date: LocalDate): LocalDate {
            val month = YearMonth.from(date)
            val inThisMonth = occurrenceIn(month)
            return if (inThisMonth.isAfter(date)) inThisMonth else occurrenceIn(month.plusMonths(1))
        }

        override fun isOccurrence(date: LocalDate) = date == occurrenceIn(YearMonth.from(date))

        override fun toStorageString() = "MONTHLY:$dayOfMonth"

        private fun occurrenceIn(month: YearMonth): LocalDate = month.atDay(minOf(dayOfMonth, month.lengthOfMonth()))
    }

    /** Every year on the given day; 29 February falls on 28 February in other years. */
    data class Yearly(val month: Month, val dayOfMonth: Int) : RepeatRule {
        init {
            require(dayOfMonth in 1..month.maxLength()) { "$month has no day $dayOfMonth." }
        }

        override fun nextAfter(date: LocalDate): LocalDate {
            val inThisYear = occurrenceIn(date.year)
            return if (inThisYear.isAfter(date)) inThisYear else occurrenceIn(date.year + 1)
        }

        override fun isOccurrence(date: LocalDate) = date == occurrenceIn(date.year)

        override fun toStorageString() = "YEARLY:${month.value}-$dayOfMonth"

        private fun occurrenceIn(year: Int): LocalDate {
            val yearMonth = YearMonth.of(year, month)
            return yearMonth.atDay(minOf(dayOfMonth, yearMonth.lengthOfMonth()))
        }
    }

    companion object {
        const val MAX_INTERVAL = 365

        private val STORED_FORM = Regex("^(DAILY|WEEKLY|MONTHLY|YEARLY)(?:/(\\d{1,3}))?(?::(.+))?$")
        private val MONTH_DAY = Regex("^(\\d{1,2})-(\\d{1,2})$")

        /** @throws IllegalArgumentException if [text] is not a stored repeat rule. */
        fun fromStorageString(text: String): RepeatRule {
            val match = STORED_FORM.matchEntire(text) ?: throw IllegalArgumentException("Not a repeat rule: '$text'.")
            val kind = match.groupValues[1]
            val interval = match.groups[2]?.value?.toInt() ?: 1
            val argument = match.groups[3]?.value
            return when (kind) {
                "DAILY" -> {
                    require(argument == null) { "Unexpected text in '$text'." }
                    Daily(interval)
                }
                "WEEKLY" -> Weekly(parseDays(argument ?: throw IllegalArgumentException("No days in '$text'.")), interval)
                "MONTHLY" -> {
                    require(interval == 1) { "Monthly rules have no interval: '$text'." }
                    Monthly(argument?.toIntOrNull() ?: throw IllegalArgumentException("No day in '$text'."))
                }
                else -> {
                    require(interval == 1) { "Yearly rules have no interval: '$text'." }
                    val monthDay = MONTH_DAY.matchEntire(argument.orEmpty())
                        ?: throw IllegalArgumentException("No date in '$text'.")
                    val month = Month.entries.getOrNull(monthDay.groupValues[1].toInt() - 1)
                        ?: throw IllegalArgumentException("Bad month in '$text'.")
                    Yearly(month, monthDay.groupValues[2].toInt())
                }
            }
        }

        private fun parseDays(text: String): Set<DayOfWeek> = text.split(',').map { code ->
            DayOfWeek.entries.firstOrNull { it.name.take(3) == code }
                ?: throw IllegalArgumentException("Bad day '$code'.")
        }.toSet()
    }
}

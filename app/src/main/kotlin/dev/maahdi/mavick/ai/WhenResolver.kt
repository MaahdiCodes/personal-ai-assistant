package dev.maahdi.mavick.ai

import dev.maahdi.mavick.time.RepeatRule
import dev.maahdi.mavick.time.WhenParser
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** A suggestion's date and time, read from its words. */
data class ResolvedWhen(
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val repeatRule: RepeatRule? = null,
    /** There were words about when, but they couldn't be read: you pick a time when adding. */
    val needsTime: Boolean = false,
)

/**
 * Turns the AI's "when" words into a date with [WhenParser], the same code as quick-add
 * (docs/PLAN.md §2: small models are bad at date arithmetic). Counted from when the message was
 * sent, not when Mavick read it: "tomorrow" in yesterday's message means today.
 */
object WhenResolver {
    fun resolve(whenText: String?, sentAt: LocalDateTime, parser: WhenParser): ResolvedWhen {
        val text = whenText?.trim()?.takeIf { it.isNotEmpty() } ?: return ResolvedWhen()
        val parsed = parser.parse(text, sentAt)
        if (parsed.dueDate == null && parsed.dueTime == null && parsed.repeatRule == null) return ResolvedWhen(needsTime = true)
        return ResolvedWhen(parsed.dueDate, parsed.dueTime, parsed.repeatRule)
    }
}

package dev.maahdi.mavick.calendar

import android.content.res.Resources
import dev.maahdi.mavick.R
import dev.maahdi.mavick.time.DueFormatter
import java.time.ZoneId

/** "Dentist", "Dentist and 2 more", or "an event" when the first has no title. [events] is not empty. */
fun clashEventsLabel(resources: Resources, events: List<CalendarOccurrence>): String {
    val first = events.first().title.ifBlank { resources.getString(R.string.clash_untitled) }
    val others = events.size - 1
    return if (others == 0) first else resources.getQuantityString(R.plurals.clash_and_more, others, first, others)
}

/** "Clashes with Dentist at 17:00": what a task row and the editor say. [events] is not empty. */
fun clashSentence(resources: Resources, events: List<CalendarOccurrence>, zone: ZoneId, use24Hour: Boolean): String {
    val at = DueFormatter.time(events.first().start.atZone(zone).toLocalTime(), use24Hour)
    return resources.getString(R.string.clash_with, clashEventsLabel(resources, events), at)
}

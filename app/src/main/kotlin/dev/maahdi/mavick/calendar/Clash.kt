package dev.maahdi.mavick.calendar

import dev.maahdi.mavick.data.task.TaskEntity
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * One event in the phone's calendar, on one day (a repeating event gives one for each time it
 * happens). [busy] is false for an event that doesn't block your time: shown as free, declined by
 * you, or cancelled.
 */
data class CalendarOccurrence(
    val eventId: Long,
    val calendarId: Long,
    /** Empty for an event without a title. */
    val title: String,
    val start: Instant,
    val end: Instant,
    val allDay: Boolean,
    val busy: Boolean,
)

/** A timed task and the calendar events it overlaps. */
data class Clash(val task: TaskEntity, val events: List<CalendarOccurrence>)

/** Which events to look at: [calendarIds] empty means every visible calendar. */
data class ClashScope(val calendarIds: Set<Long> = emptySet(), val ownEventIds: Set<Long> = emptySet())

/** Finds where a task's time overlaps something in the calendar (docs/PLAN.md §5.9). Pure. */
object ClashFinder {
    /** An event with no length (or a backwards one) still takes this long, so it can clash. */
    private val MINIMUM_LENGTH: Duration = Duration.ofMinutes(1)

    /** The events in [events] that overlap the [start], [end] slot, in time order. */
    fun overlapping(start: Instant, end: Instant, events: List<CalendarOccurrence>, scope: ClashScope): List<CalendarOccurrence> =
        events
            .filter { it.busy && !it.allDay }
            .filter { it.eventId !in scope.ownEventIds }
            .filter { scope.calendarIds.isEmpty() || it.calendarId in scope.calendarIds }
            .filter { it.start.isBefore(end) && start.isBefore(effectiveEnd(it)) }
            .sortedWith(compareBy({ it.start }, { it.eventId }))

    /** The clash of each task that has one, by task ID. A task without a date and a time has none. */
    fun forTasks(tasks: List<TaskEntity>, events: List<CalendarOccurrence>, zone: ZoneId, scope: ClashScope): Map<String, List<CalendarOccurrence>> {
        val clashes = mutableMapOf<String, List<CalendarOccurrence>>()
        for (task in tasks) {
            val slot = CalendarEventPolicy.eventFor(task, zone) ?: continue
            val overlapping = overlapping(slot.start, slot.end, events, scope)
            if (overlapping.isNotEmpty()) clashes[task.id] = overlapping
        }
        return clashes
    }

    private fun effectiveEnd(event: CalendarOccurrence): Instant =
        if (event.end.isAfter(event.start)) event.end else event.start.plus(MINIMUM_LENGTH)
}

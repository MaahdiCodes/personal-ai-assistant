package dev.maahdi.mavick.calendar

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.task.TaskStatus
import dev.maahdi.mavick.testing.TEST_ZONE
import dev.maahdi.mavick.testing.task
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Test

class ClashFinderTest {
    private val date = LocalDate.of(2026, 10, 8)
    private val scope = ClashScope()

    /** 17:00 to 17:30 on [date] in Dhaka (UTC+6) is 11:00 to 11:30 UTC. */
    private val slotStart: Instant = Instant.parse("2026-10-08T11:00:00Z")
    private val slotEnd: Instant = Instant.parse("2026-10-08T11:30:00Z")

    private fun event(
        id: Long = 1,
        calendarId: Long = 1,
        title: String = "Dentist",
        start: String,
        end: String,
        allDay: Boolean = false,
        busy: Boolean = true,
    ) = CalendarOccurrence(id, calendarId, title, Instant.parse(start), Instant.parse(end), allDay, busy)

    private fun overlapping(vararg events: CalendarOccurrence, scope: ClashScope = this.scope) =
        ClashFinder.overlapping(slotStart, slotEnd, events.toList(), scope)

    // --- Overlap ---

    @Test
    fun `an event inside the slot clashes`() {
        val inside = event(start = "2026-10-08T11:10:00Z", end = "2026-10-08T11:20:00Z")

        assertThat(overlapping(inside)).containsExactly(inside)
    }

    @Test
    fun `an event that starts before and ends inside clashes`() {
        val early = event(start = "2026-10-08T10:30:00Z", end = "2026-10-08T11:05:00Z")

        assertThat(overlapping(early)).containsExactly(early)
    }

    @Test
    fun `an event that starts inside and ends after clashes`() {
        val late = event(start = "2026-10-08T11:25:00Z", end = "2026-10-08T12:00:00Z")

        assertThat(overlapping(late)).containsExactly(late)
    }

    @Test
    fun `an event that covers the whole slot clashes`() {
        val long = event(start = "2026-10-08T09:00:00Z", end = "2026-10-08T15:00:00Z")

        assertThat(overlapping(long)).containsExactly(long)
    }

    @Test
    fun `an event that ends exactly when the slot starts does not clash`() {
        assertThat(overlapping(event(start = "2026-10-08T10:00:00Z", end = "2026-10-08T11:00:00Z"))).isEmpty()
    }

    @Test
    fun `an event that starts exactly when the slot ends does not clash`() {
        assertThat(overlapping(event(start = "2026-10-08T11:30:00Z", end = "2026-10-08T12:30:00Z"))).isEmpty()
    }

    @Test
    fun `an event on another day does not clash`() {
        assertThat(overlapping(event(start = "2026-10-09T11:00:00Z", end = "2026-10-09T11:30:00Z"))).isEmpty()
    }

    @Test
    fun `an event with no length clashes when it falls inside the slot`() {
        val moment = event(start = "2026-10-08T11:10:00Z", end = "2026-10-08T11:10:00Z")

        assertThat(overlapping(moment)).containsExactly(moment)
    }

    @Test
    fun `an event with no length at the moment the slot starts clashes, but not at the moment it ends`() {
        val atStart = event(id = 1, start = "2026-10-08T11:00:00Z", end = "2026-10-08T11:00:00Z")
        val atEnd = event(id = 2, start = "2026-10-08T11:30:00Z", end = "2026-10-08T11:30:00Z")

        assertThat(overlapping(atStart, atEnd)).containsExactly(atStart)
    }

    @Test
    fun `an event that ends before it starts is treated like one with no length`() {
        val backwards = event(start = "2026-10-08T11:10:00Z", end = "2026-10-08T11:00:00Z")

        assertThat(overlapping(backwards)).containsExactly(backwards)
    }

    // --- What does not count ---

    @Test
    fun `all-day events never clash`() {
        assertThat(overlapping(event(start = "2026-10-08T00:00:00Z", end = "2026-10-09T00:00:00Z", allDay = true))).isEmpty()
    }

    @Test
    fun `events that are free, declined or cancelled never clash`() {
        assertThat(overlapping(event(start = "2026-10-08T11:00:00Z", end = "2026-10-08T12:00:00Z", busy = false))).isEmpty()
    }

    @Test
    fun `Mavick's own events never clash with its tasks`() {
        val own = event(id = 77, start = "2026-10-08T11:00:00Z", end = "2026-10-08T11:30:00Z")
        val other = event(id = 5, start = "2026-10-08T11:00:00Z", end = "2026-10-08T11:30:00Z")

        assertThat(overlapping(own, other, scope = ClashScope(ownEventIds = setOf(77)))).containsExactly(other)
    }

    // --- Which calendars ---

    @Test
    fun `no calendar choice means every calendar is checked`() {
        val first = event(id = 1, calendarId = 1, start = "2026-10-08T11:00:00Z", end = "2026-10-08T11:30:00Z")
        val second = event(id = 2, calendarId = 2, start = "2026-10-08T11:00:00Z", end = "2026-10-08T11:30:00Z")

        assertThat(overlapping(first, second)).containsExactly(first, second)
    }

    @Test
    fun `a calendar choice leaves the other calendars out`() {
        val first = event(id = 1, calendarId = 1, start = "2026-10-08T11:00:00Z", end = "2026-10-08T11:30:00Z")
        val second = event(id = 2, calendarId = 2, start = "2026-10-08T11:00:00Z", end = "2026-10-08T11:30:00Z")

        assertThat(overlapping(first, second, scope = ClashScope(calendarIds = setOf(2)))).containsExactly(second)
    }

    // --- Order ---

    @Test
    fun `clashing events come in time order`() {
        val later = event(id = 1, title = "Later", start = "2026-10-08T11:20:00Z", end = "2026-10-08T11:40:00Z")
        val earlier = event(id = 2, title = "Earlier", start = "2026-10-08T10:50:00Z", end = "2026-10-08T11:10:00Z")

        assertThat(overlapping(later, earlier).map { it.title }).containsExactly("Earlier", "Later").inOrder()
    }

    // --- Tasks ---

    private fun timed(id: String, time: LocalTime, status: TaskStatus = TaskStatus.OPEN, day: LocalDate = date) =
        task(id = id, dueDate = day, dueTime = time, status = status)

    @Test
    fun `each timed task gets the events it overlaps, by its id`() {
        val clashing = timed("a", LocalTime.of(17, 0))
        val free = timed("b", LocalTime.of(20, 0))
        val dentist = event(start = "2026-10-08T11:10:00Z", end = "2026-10-08T12:00:00Z")

        val found = ClashFinder.forTasks(listOf(clashing, free), listOf(dentist), TEST_ZONE, scope)

        assertThat(found).containsExactly("a", listOf(dentist))
    }

    @Test
    fun `tasks without a time, finished tasks and deleted tasks have no clash`() {
        val dentist = event(start = "2026-10-08T00:00:00Z", end = "2026-10-08T23:00:00Z")
        val tasks = listOf(
            task(id = "date only", dueDate = date),
            task(id = "undated"),
            timed("done", LocalTime.of(17, 0), status = TaskStatus.DONE),
            task(id = "deleted", dueDate = date, dueTime = LocalTime.of(17, 0), deletedAt = Instant.EPOCH),
        )

        assertThat(ClashFinder.forTasks(tasks, listOf(dentist), TEST_ZONE, scope)).isEmpty()
    }

    @Test
    fun `the task's time is read in the phone's time zone`() {
        val dentist = event(start = "2026-10-08T11:10:00Z", end = "2026-10-08T12:00:00Z")
        val task = timed("a", LocalTime.of(17, 0))

        val inDhaka = ClashFinder.forTasks(listOf(task), listOf(dentist), TEST_ZONE, scope)
        val inLondon = ClashFinder.forTasks(listOf(task), listOf(dentist), java.time.ZoneId.of("Europe/London"), scope)

        assertThat(inDhaka).hasSize(1)
        assertThat(inLondon).isEmpty()
    }

    @Test
    fun `two tasks at the same time both clash with the same event`() {
        val dentist = event(start = "2026-10-08T11:00:00Z", end = "2026-10-08T12:00:00Z")
        val tasks = listOf(timed("a", LocalTime.of(17, 0)), timed("b", LocalTime.of(17, 10)))

        assertThat(ClashFinder.forTasks(tasks, listOf(dentist), TEST_ZONE, scope).keys).containsExactly("a", "b")
    }

    @Test
    fun `tasks never clash with each other, only with the calendar`() {
        val tasks = listOf(timed("a", LocalTime.of(17, 0)), timed("b", LocalTime.of(17, 10)))

        assertThat(ClashFinder.forTasks(tasks, emptyList(), TEST_ZONE, scope)).isEmpty()
    }
}

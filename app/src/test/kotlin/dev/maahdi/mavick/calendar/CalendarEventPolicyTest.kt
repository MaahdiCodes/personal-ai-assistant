package dev.maahdi.mavick.calendar

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.task.TaskStatus
import dev.maahdi.mavick.testing.TEST_NOW
import dev.maahdi.mavick.testing.TEST_ZONE
import dev.maahdi.mavick.testing.task
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test

class CalendarEventPolicyTest {
    private val date = LocalDate.of(2026, 10, 8)
    private val time = LocalTime.of(17, 0)

    private fun eventFor(
        dueDate: LocalDate? = date,
        dueTime: LocalTime? = time,
        status: TaskStatus = TaskStatus.OPEN,
        deletedAt: Instant? = null,
        title: String = "Call the bank",
        zone: ZoneId = TEST_ZONE,
    ): CalendarEvent? = CalendarEventPolicy.eventFor(
        task(title = title, dueDate = dueDate, dueTime = dueTime, status = status, deletedAt = deletedAt),
        zone,
    )

    // --- Which tasks get an event ---

    @Test
    fun `an open task with a date and a time gets an event`() {
        assertThat(eventFor()).isNotNull()
    }

    @Test
    fun `a task with only a date gets no event`() {
        assertThat(eventFor(dueTime = null)).isNull()
    }

    @Test
    fun `a task with no date gets no event`() {
        assertThat(eventFor(dueDate = null, dueTime = null)).isNull()
    }

    @Test
    fun `a time without a date gets no event`() {
        // Not a state the editor allows, but the rule must not depend on that.
        assertThat(eventFor(dueDate = null)).isNull()
    }

    @Test
    fun `a finished task gets no event`() {
        assertThat(eventFor(status = TaskStatus.DONE)).isNull()
        assertThat(eventFor(status = TaskStatus.ARCHIVED)).isNull()
    }

    @Test
    fun `a deleted task gets no event`() {
        assertThat(eventFor(deletedAt = TEST_NOW)).isNull()
    }

    // --- What the event holds ---

    @Test
    fun `the event starts at the task's local time in the phone's time zone and lasts 30 minutes`() {
        val event = eventFor()!!

        // 17:00 in Dhaka (UTC+6) is 11:00 UTC.
        assertThat(event.start).isEqualTo(Instant.parse("2026-10-08T11:00:00Z"))
        assertThat(event.end).isEqualTo(Instant.parse("2026-10-08T11:30:00Z"))
        assertThat(Duration.between(event.start, event.end)).isEqualTo(CalendarEventPolicy.EVENT_LENGTH)
        assertThat(event.zone).isEqualTo(TEST_ZONE)
    }

    @Test
    fun `the event carries the title and nothing else`() {
        assertThat(eventFor(title = "Pay rent")!!.title).isEqualTo("Pay rent")
    }

    @Test
    fun `the same local time is a different moment in another time zone`() {
        val dhaka = eventFor(zone = TEST_ZONE)!!
        val london = eventFor(zone = ZoneId.of("Europe/London"))!!

        // 17:00 in London in October is still on summer time (UTC+1).
        assertThat(london.start).isEqualTo(Instant.parse("2026-10-08T16:00:00Z"))
        assertThat(london.start).isNotEqualTo(dhaka.start)
    }

    @Test
    fun `a time skipped by the clocks going forward moves to the hour after`() {
        // 2:30 on 8 March 2026 never happened in New York: clocks went from 2:00 to 3:00.
        val event = eventFor(dueDate = LocalDate.of(2026, 3, 8), dueTime = LocalTime.of(2, 30), zone = ZoneId.of("America/New_York"))!!

        assertThat(event.start).isEqualTo(Instant.parse("2026-03-08T07:30:00Z")) // 3:30 EDT
    }

    @Test
    fun `a time that happened twice as the clocks went back uses the first one`() {
        // 1:30 on 1 November 2026 happened twice in New York.
        val event = eventFor(dueDate = LocalDate.of(2026, 11, 1), dueTime = LocalTime.of(1, 30), zone = ZoneId.of("America/New_York"))!!

        assertThat(event.start).isEqualTo(Instant.parse("2026-11-01T05:30:00Z")) // 1:30 EDT
    }

    // --- The fingerprint ---

    @Test
    fun `the same event has the same fingerprint`() {
        assertThat(eventFor()!!.fingerprint).isEqualTo(eventFor()!!.fingerprint)
    }

    @Test
    fun `a different title time or zone changes the fingerprint`() {
        val base = eventFor()!!.fingerprint

        assertThat(eventFor(title = "Call the bank!")!!.fingerprint).isNotEqualTo(base)
        assertThat(eventFor(dueTime = LocalTime.of(17, 1))!!.fingerprint).isNotEqualTo(base)
        assertThat(eventFor(dueDate = date.plusDays(1))!!.fingerprint).isNotEqualTo(base)
        assertThat(eventFor(zone = ZoneId.of("Asia/Kolkata"))!!.fingerprint).isNotEqualTo(base)
    }

    @Test
    fun `the fingerprint does not contain the title`() {
        assertThat(eventFor(title = "Secret plan")!!.fingerprint).doesNotContain("Secret")
    }

    @Test
    fun `values that run together do not share a fingerprint`() {
        // Without a separator both would read "a123" followed by the zone.
        val first = CalendarEvent("a", Instant.ofEpochMilli(12), Instant.ofEpochMilli(3), TEST_ZONE).fingerprint
        val second = CalendarEvent("a1", Instant.ofEpochMilli(2), Instant.ofEpochMilli(3), TEST_ZONE).fingerprint

        assertThat(first).isNotEqualTo(second)
    }
}

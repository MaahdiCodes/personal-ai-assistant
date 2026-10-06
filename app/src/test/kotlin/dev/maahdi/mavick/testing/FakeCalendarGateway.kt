package dev.maahdi.mavick.testing

import dev.maahdi.mavick.calendar.CalendarAccessException
import dev.maahdi.mavick.calendar.CalendarEvent
import dev.maahdi.mavick.calendar.CalendarGateway
import dev.maahdi.mavick.calendar.CalendarProblem
import dev.maahdi.mavick.calendar.DeviceCalendar

val PERSONAL_CALENDAR = DeviceCalendar(id = 1, name = "Personal", account = "me@example.com")
val WORK_CALENDAR = DeviceCalendar(id = 2, name = "Work", account = "me@work.example")

/** A phone's calendar kept in memory, which records what Mavick asked of it. */
class FakeCalendarGateway(var calendars: List<DeviceCalendar> = listOf(PERSONAL_CALENDAR, WORK_CALENDAR)) : CalendarGateway {
    data class Stored(val calendarId: Long, val event: CalendarEvent)

    /** The events in the calendar, by event ID. */
    val events = linkedMapOf<Long, Stored>()

    /** Every call that reached the calendar, in order: "insert", "update" or "delete". */
    val calls = mutableListOf<String>()

    var permission = true

    /** Makes the list of calendars fail with this problem, while writing events still works. */
    var listFailure: CalendarProblem? = null

    /** Makes the next [failuresLeft] calls fail with this problem (after being recorded in [calls]). */
    var failure: CalendarProblem? = null
    var failuresLeft = Int.MAX_VALUE

    private var nextEventId = 100L

    override fun hasPermission(): Boolean = permission

    override fun writableCalendars(): List<DeviceCalendar> {
        if (!permission) throw CalendarAccessException(CalendarProblem.NO_PERMISSION)
        listFailure?.let { throw CalendarAccessException(it) }
        return calendars
    }

    override fun insert(calendarId: Long, event: CalendarEvent): Long {
        record("insert")
        val id = nextEventId++
        events[id] = Stored(calendarId, event)
        return id
    }

    override fun update(eventId: Long, event: CalendarEvent): Boolean {
        record("update")
        val stored = events[eventId] ?: return false
        events[eventId] = stored.copy(event = event)
        return true
    }

    override fun delete(eventId: Long): Boolean {
        record("delete")
        return events.remove(eventId) != null
    }

    /** What is in [calendarId] now, as (title, start) pairs read easily in a test. */
    fun titlesIn(calendarId: Long): List<String> = events.values.filter { it.calendarId == calendarId }.map { it.event.title }

    /** Someone deletes an event in the Calendar app: it disappears without Mavick being involved. */
    fun deleteInCalendarApp(eventId: Long) {
        events.remove(eventId)
    }

    private fun record(call: String) {
        calls += call
        if (!permission) throw CalendarAccessException(CalendarProblem.NO_PERMISSION)
        val problem = failure
        if (problem != null && failuresLeft > 0) {
            failuresLeft--
            throw CalendarAccessException(problem)
        }
    }
}

package dev.maahdi.mavick.calendar

import java.time.Instant

/** A calendar on the phone. [writable]: Mavick may add events to it. */
data class DeviceCalendar(val id: Long, val name: String, val account: String, val writable: Boolean = true) {
    /** "Personal (me@gmail.com)": the name alone when the account would only repeat it. */
    val label: String get() = if (account.isBlank() || account == name) name else "$name ($account)"
}

/** Why the phone's calendar could not be used. */
enum class CalendarProblem {
    /** The calendar permission is off (never granted, or taken away in Android's settings). */
    NO_PERMISSION,

    /** The calendar storage refused or failed: no calendar app, a sync in progress, an account removed. */
    UNAVAILABLE,
}

/** The message is only the problem's name: nothing from a task ever goes into an exception. */
class CalendarAccessException(val problem: CalendarProblem, cause: Throwable? = null) : Exception(problem.name, cause)

/**
 * The phone's calendar storage, as Mavick needs it. Calls block, so run them off the main thread.
 * Every failure is a [CalendarAccessException], so callers need to know nothing about Android.
 */
interface CalendarGateway {
    fun hasPermission(): Boolean

    /** Every calendar shown in the Calendar app, writable or not, ordered by account, then name. */
    fun visibleCalendars(): List<DeviceCalendar>

    /** The visible calendars Mavick may add events to, ordered by account, then name. */
    fun writableCalendars(): List<DeviceCalendar>

    /**
     * The events that happen between [from] and [to] in every visible calendar, a repeating event
     * once for each time. Needs only the read permission.
     */
    fun occurrences(from: Instant, to: Instant): List<CalendarOccurrence>

    /** Adds [event] to the calendar and returns the new event's ID. */
    fun insert(calendarId: Long, event: CalendarEvent): Long

    /** Rewrites the event. Returns false if there is no such event any more (someone deleted it). */
    fun update(eventId: Long, event: CalendarEvent): Boolean

    /** Removes the event. Returns false if there was none (already gone). */
    fun delete(eventId: Long): Boolean
}

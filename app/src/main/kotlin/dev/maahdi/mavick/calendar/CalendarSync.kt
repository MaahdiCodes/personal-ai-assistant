package dev.maahdi.mavick.calendar

import android.util.Log
import dev.maahdi.mavick.data.calendar.CalendarLinkDao
import dev.maahdi.mavick.data.calendar.CalendarLinkEntity
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.data.task.TaskChangeListener
import dev.maahdi.mavick.data.task.TaskDao
import java.time.Clock
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps the phone's calendar in step with the tasks (docs/PLAN.md §5.9). One-way: the task wins. An
 * event is written when its task gets a date and a time, rewritten when the title, the time or the
 * phone's time zone changes, and removed when the task is done, deleted, loses its time, or the
 * feature is switched off. What someone does to the event in the Calendar app is left alone until
 * the task itself changes.
 *
 * Each call looks at the task's state at that moment and makes the calendar match it, so calls
 * that overlap or repeat can't leave a stale event behind.
 */
class CalendarSync(
    private val tasks: TaskDao,
    private val links: CalendarLinkDao,
    private val gateway: CalendarGateway,
    private val settings: SettingsRepository,
    private val clock: () -> Clock,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TaskChangeListener {
    private val lock = Mutex()

    /** Brings the task's calendar event in line with the task as it is now. A calendar problem is only logged. */
    override suspend fun taskChanged(taskId: String) {
        attempt { syncTask(taskId) }
    }

    /**
     * Brings every event in line: after the feature is switched on or off or another calendar is
     * chosen, after a time-zone change, and once a day in case something was missed (a permission
     * that was off for a while, an event that failed). A task whose event fails is left for next
     * time; with the permission off, nothing is tried at all.
     */
    suspend fun reconcileAll() {
        attempt {
            if (settings.current.calendarTarget == null && links.count() == 0) return@attempt
            val ids = (tasks.getOpenTimed().map { it.id } + links.all().map { it.taskId }).toSet()
            for (id in ids) {
                try {
                    syncTask(id)
                } catch (e: CalendarAccessException) {
                    // Without the permission every task would fail the same way.
                    if (e.problem == CalendarProblem.NO_PERMISSION) throw e
                    logProblem(e)
                }
            }
        }
    }

    /** How many tasks have an event in the calendar now. */
    suspend fun eventCount(): Int = links.count()

    private suspend fun syncTask(taskId: String) = withContext(dispatcher) {
        // Held for one task at a time, so a task saved during a long reconcile isn't kept waiting.
        lock.withLock {
            val calendarId = settings.current.calendarTarget
            val task = tasks.findById(taskId)
            val link = links.find(taskId)
            val wanted = task?.let { CalendarEventPolicy.eventFor(it, clock().zone) }
            matchCalendar(taskId, calendarId, wanted, link)
        }
    }

    /** Makes the calendar hold [wanted] (or nothing) for the task, given what [link] says was written. */
    private suspend fun matchCalendar(taskId: String, calendarId: Long?, wanted: CalendarEvent?, link: CalendarLinkEntity?) {
        if (calendarId == null || wanted == null) {
            if (link != null) remove(link)
            return
        }
        when {
            link == null -> if (!startsBeforeToday(wanted)) add(taskId, calendarId, wanted)
            link.calendarId != calendarId -> {
                remove(link)
                add(taskId, calendarId, wanted)
            }
            link.fingerprint != wanted.fingerprint -> change(link, wanted)
        }
    }

    private suspend fun add(taskId: String, calendarId: Long, event: CalendarEvent) {
        val eventId = gateway.insert(calendarId, event)
        // A crash between these two lines would leave one event nobody knows about: very unlikely,
        // and harmless (a duplicate that never changes).
        links.upsert(CalendarLinkEntity(taskId, calendarId, eventId, event.fingerprint))
    }

    private suspend fun change(link: CalendarLinkEntity, event: CalendarEvent) {
        if (gateway.update(link.eventId, event)) {
            links.upsert(link.copy(fingerprint = event.fingerprint))
        } else {
            // The event was deleted in the Calendar app, and the task has changed since: write it again.
            add(link.taskId, link.calendarId, event)
        }
    }

    private suspend fun remove(link: CalendarLinkEntity) {
        // False means the event is already gone, which is what we wanted.
        gateway.delete(link.eventId)
        links.delete(link.taskId)
    }

    /**
     * A task that was already past when it got its time isn't copied into the calendar: switching
     * the feature on shouldn't fill the past with every overdue task.
     */
    private fun startsBeforeToday(event: CalendarEvent): Boolean {
        val startOfToday = LocalDate.now(clock()).atStartOfDay(event.zone).toInstant()
        return event.start.isBefore(startOfToday)
    }

    private suspend fun attempt(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: CalendarAccessException) {
            logProblem(e)
        } catch (e: Exception) {
            Log.w(TAG, "Calendar sync failed: ${e.javaClass.simpleName}")
        }
    }

    /** Only the kind of problem is logged, never a task. */
    private fun logProblem(e: CalendarAccessException) {
        Log.w(TAG, "Calendar unavailable: ${e.problem}")
    }

    private companion object {
        const val TAG = "Mavick"
    }
}

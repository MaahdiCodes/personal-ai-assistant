package dev.maahdi.mavick.calendar

import android.util.Log
import dev.maahdi.mavick.data.calendar.CalendarLinkDao
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.data.task.TaskEntity
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Looks for clashes between tasks that have a time and events in the phone's calendar (docs/PLAN.md
 * §5.9). Read-only, and only while the user has switched "Warn about clashes" on and allowed the
 * calendar permission. Mavick's own events (the copies of its tasks) never count. Anything that goes
 * wrong means "no clashes found", never an error on screen.
 *
 * One calendar read serves all the tasks asked about, however many, and nothing is kept: the
 * answer is worked out again whenever it is asked for.
 */
class ClashService(
    private val gateway: CalendarGateway,
    private val links: CalendarLinkDao,
    private val settings: SettingsRepository,
    private val clock: () -> Clock,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * The calendar events each of [tasks] overlaps, by task ID; tasks with none are left out. Only
     * tasks from today on, and within [HORIZON], are looked at.
     */
    suspend fun clashesFor(tasks: List<TaskEntity>): Map<String, List<CalendarOccurrence>> {
        if (!isOn()) return emptyMap()
        val zone = clock().zone
        val startOfToday = LocalDate.now(clock()).atStartOfDay(zone).toInstant()
        val horizon = startOfToday.plus(HORIZON)
        val slots = tasks.mapNotNull { task ->
            CalendarEventPolicy.eventFor(task, zone)?.takeIf { it.end.isAfter(startOfToday) && it.start.isBefore(horizon) }?.let { task to it }
        }
        if (slots.isEmpty()) return emptyMap()
        val from = slots.minOf { it.second.start }
        val to = slots.maxOf { it.second.end }
        return quietly(emptyMap()) {
            val events = readEvents(from, to)
            ClashFinder.forTasks(slots.map { it.first }, events, zone, scope())
        }
    }

    /** The events that overlap a task at [date] and [time], for the editor to warn about. */
    suspend fun clashesAt(date: LocalDate, time: LocalTime): List<CalendarOccurrence> {
        if (!isOn()) return emptyList()
        val start = date.atTime(time).atZone(clock().zone).toInstant()
        val end = start.plus(CalendarEventPolicy.EVENT_LENGTH)
        return quietly(emptyList()) { ClashFinder.overlapping(start, end, readEvents(start, end), scope()) }
    }

    private fun isOn(): Boolean = settings.current.clashCheckEnabled && gateway.hasPermission()

    private suspend fun readEvents(from: Instant, to: Instant): List<CalendarOccurrence> = withContext(dispatcher) { gateway.occurrences(from, to) }

    private suspend fun scope(): ClashScope {
        val chosen = settings.current.clashCalendarIds
        // A chosen calendar that is gone is forgotten; with none left, every calendar is checked.
        val existing = if (chosen.isEmpty()) chosen else withContext(dispatcher) { gateway.visibleCalendars() }.map { it.id }.toSet()
        return ClashScope(
            calendarIds = chosen intersect existing,
            ownEventIds = links.all().map { it.eventId }.toSet(),
        )
    }

    private suspend fun <T> quietly(fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: CalendarAccessException) {
        Log.w(TAG, "Calendar unavailable: ${e.problem}")
        fallback
    } catch (e: Exception) {
        Log.w(TAG, "Clash check failed: ${e.javaClass.simpleName}")
        fallback
    } catch (e: LinkageError) {
        fallback // the encryption library failed to load; Health explains it
    }

    companion object {
        /** Tasks further away than this aren't checked: keeps the calendar read small. */
        val HORIZON: Duration = Duration.ofDays(60)
        private const val TAG = "Mavick"
    }
}

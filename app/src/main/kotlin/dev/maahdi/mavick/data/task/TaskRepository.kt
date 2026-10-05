package dev.maahdi.mavick.data.task

import dev.maahdi.mavick.reminders.ReminderScheduler
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Today's and overdue open tasks, and suggested tasks waiting for you, for the morning briefing. */
data class Briefing(val overdue: List<TaskEntity>, val today: List<TaskEntity>, val suggestionsWaiting: Int = 0) {
    val isEmpty: Boolean get() = overdue.isEmpty() && today.isEmpty() && suggestionsWaiting == 0
}

/**
 * Every change to tasks goes through here, so the phone's reminder alarms always match the data.
 *
 * Changes are serialized with a lock: a notification button and the app screen changing the same
 * task at the same moment can't overwrite each other.
 *
 * @param clock gives the current clock each time, so a time-zone change is picked up immediately.
 */
class TaskRepository(
    private val dao: TaskDao,
    private val scheduler: ReminderScheduler,
    private val clock: () -> Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val lock = Mutex()

    fun observeOpen(): Flow<List<TaskEntity>> = dao.observeOpen()

    fun observeDone(): Flow<List<TaskEntity>> = dao.observeDone(DONE_LIST_LIMIT)

    suspend fun find(id: String): TaskEntity? = dao.findById(id)?.takeIf { it.deletedAt == null }

    /** @throws IllegalArgumentException if the draft's title is blank. */
    suspend fun create(draft: TaskDraft): TaskEntity = lock.withLock {
        val now = now()
        val clean = draft.normalized(now.toLocalDate())
        val stamp = stamp()
        val task = TaskEntity(
            id = newId(),
            title = clean.title,
            notes = clean.notes,
            dueDate = clean.dueDate,
            dueTime = clean.dueTime,
            remindAt = pendingReminder(clean.dueDate, clean.reminderTime, now),
            priority = clean.priority,
            source = clean.source,
            sourceExcerpt = clean.sourceExcerpt,
            createdAt = stamp,
            updatedAt = stamp,
            reminderTime = clean.reminderTime,
            repeatRule = clean.repeatRule,
        )
        dao.insert(task)
        syncAlarm(task)
        task
    }

    /**
     * Replaces the editable fields with [draft]. Where the task came from, its status and its
     * history are kept. Returns null if the task no longer exists.
     */
    suspend fun update(id: String, draft: TaskDraft): TaskEntity? = mutate(id) { task, now, stamp ->
        val clean = draft.normalized(now.toLocalDate())
        task.copy(
            title = clean.title,
            notes = clean.notes,
            dueDate = clean.dueDate,
            dueTime = clean.dueTime,
            reminderTime = clean.reminderTime,
            remindAt = if (task.status == TaskStatus.OPEN) pendingReminder(clean.dueDate, clean.reminderTime, now) else null,
            repeatRule = clean.repeatRule,
            priority = clean.priority,
            updatedAt = stamp,
        )
    }

    /**
     * Marks the task done. A repeating task instead moves to its next occurrence after today (or
     * after its due date, if it was done early), with its reminder set again.
     */
    suspend fun complete(id: String): TaskEntity? = mutate(id) { task, now, stamp ->
        if (task.status != TaskStatus.OPEN) return@mutate null
        val rule = task.repeatRule
        val dueDate = task.dueDate
        if (rule != null && dueDate != null) {
            val today = now.toLocalDate()
            val nextDue = rule.nextAfter(if (dueDate.isAfter(today)) dueDate else today)
            task.copy(
                dueDate = nextDue,
                remindAt = pendingReminder(nextDue, task.reminderTime, now),
                completedAt = stamp,
                updatedAt = stamp,
            )
        } else {
            task.copy(status = TaskStatus.DONE, remindAt = null, completedAt = stamp, updatedAt = stamp)
        }
    }

    /** Puts a finished task back on the open list, with its reminder if that is still ahead. */
    suspend fun reopen(id: String): TaskEntity? = mutate(id) { task, now, stamp ->
        if (task.status == TaskStatus.OPEN) return@mutate null
        task.copy(
            status = TaskStatus.OPEN,
            completedAt = null,
            remindAt = pendingReminder(task.dueDate, task.reminderTime, now),
            updatedAt = stamp,
        )
    }

    /** Hides the task. It stays in the database for 30 days (for undo, backups and sync). */
    suspend fun delete(id: String): TaskEntity? = mutate(id) { task, _, stamp ->
        task.copy(deletedAt = stamp, remindAt = null, updatedAt = stamp)
    }

    /** Undo: puts back an earlier copy of a task, exactly as it was (except a reminder already past). */
    suspend fun restore(snapshot: TaskEntity) = lock.withLock {
        val restored = snapshot.copy(remindAt = snapshot.remindAt?.takeIf { it.isAfter(now()) }, updatedAt = stamp())
        dao.upsert(restored)
        syncAlarm(restored)
    }

    suspend fun snooze(id: String, minutes: Long): TaskEntity? = mutate(id) { task, now, stamp ->
        if (task.status != TaskStatus.OPEN) return@mutate null
        task.copy(remindAt = now.plusMinutes(minutes).truncatedTo(ChronoUnit.SECONDS), updatedAt = stamp)
    }

    /** Moves the task to tomorrow, with a reminder at its usual time (09:00 if it has none). */
    suspend fun moveToTomorrow(id: String): TaskEntity? = mutate(id) { task, now, stamp ->
        if (task.status != TaskStatus.OPEN) return@mutate null
        val tomorrow = now.toLocalDate().plusDays(1)
        val reminderTime = task.reminderTime ?: task.dueTime ?: DEFAULT_REMINDER_TIME
        task.copy(dueDate = tomorrow, reminderTime = reminderTime, remindAt = tomorrow.atTime(reminderTime), updatedAt = stamp)
    }

    /**
     * Called when a reminder alarm goes off. Returns the task if its reminder is due now, and
     * marks that reminder as used. Returns null if the task changed meanwhile (done, deleted,
     * or its reminder moved to later, in which case the alarm is set again).
     */
    suspend fun takeDueReminder(id: String): TaskEntity? = lock.withLock {
        val task = dao.findById(id) ?: return null
        if (task.deletedAt != null || task.status != TaskStatus.OPEN) return null
        val remindAt = task.remindAt ?: return null
        if (remindAt.isAfter(now().plusSeconds(EARLY_ALARM_TOLERANCE_SECONDS))) {
            scheduler.schedule(id, remindAt)
            return null
        }
        val used = task.copy(remindAt = null)
        dao.update(used)
        used
    }

    /**
     * Sets every pending reminder's alarm again (after a restart, a time change, or the app
     * being force-stopped). Reminders whose time passed while the alarms were gone are used up
     * and returned, so they can be shown as missed.
     */
    suspend fun rescheduleAll(): List<TaskEntity> = lock.withLock {
        val now = now()
        val missed = mutableListOf<TaskEntity>()
        for (task in dao.getPendingReminders()) {
            val remindAt = task.remindAt ?: continue
            if (remindAt.isAfter(now)) {
                scheduler.schedule(task.id, remindAt)
            } else {
                val used = task.copy(remindAt = null)
                dao.update(used)
                missed += used
            }
        }
        missed
    }

    suspend fun briefing(today: LocalDate): Briefing {
        val due = dao.getOpenDueOnOrBefore(today)
        return Briefing(
            overdue = due.filter { it.dueDate?.isBefore(today) == true },
            today = due.filter { it.dueDate == today },
        )
    }

    /** Permanently removes tasks deleted more than 30 days ago. */
    suspend fun purgeOldDeleted(): Int = dao.purgeDeletedBefore(stamp().minus(DELETED_RETENTION_DAYS, ChronoUnit.DAYS))

    /** Reads, changes and saves one task under the lock. [change] returns null for "no change". */
    private suspend fun mutate(
        id: String,
        change: (task: TaskEntity, now: LocalDateTime, stamp: Instant) -> TaskEntity?,
    ): TaskEntity? = lock.withLock {
        val task = dao.findById(id)?.takeIf { it.deletedAt == null } ?: return null
        val updated = change(task, now(), stamp()) ?: return task
        dao.update(updated)
        syncAlarm(updated)
        updated
    }

    private fun syncAlarm(task: TaskEntity) {
        val remindAt = task.remindAt
        if (remindAt != null && task.status == TaskStatus.OPEN && task.deletedAt == null) {
            scheduler.schedule(task.id, remindAt)
        } else {
            scheduler.cancel(task.id)
        }
    }

    private fun pendingReminder(date: LocalDate?, reminderTime: LocalTime?, now: LocalDateTime): LocalDateTime? {
        if (date == null || reminderTime == null) return null
        return date.atTime(reminderTime).takeIf { it.isAfter(now) }
    }

    private fun now(): LocalDateTime = LocalDateTime.now(clock())

    private fun stamp(): Instant = Instant.now(clock())

    companion object {
        val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)
        const val DONE_LIST_LIMIT = 200
        const val DELETED_RETENTION_DAYS = 30L

        /** An alarm may arrive slightly early; within this many seconds it counts as on time. */
        const val EARLY_ALARM_TOLERANCE_SECONDS = 60L
    }
}

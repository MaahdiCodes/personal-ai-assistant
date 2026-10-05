package dev.maahdi.mavick.data.task

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.maahdi.mavick.time.RepeatRule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A to-do. Dates and times are "floating" local values: a task due at 09:00 stays at 09:00 if
 * the phone changes time zone.
 */
@Entity(
    tableName = "task",
    indices = [Index("status"), Index("dueDate")],
)
data class TaskEntity(
    /** A UUID, so tasks from both phones and from backups can be merged without ID clashes. */
    @PrimaryKey val id: String,
    val title: String,
    val notes: String? = null,
    val dueDate: LocalDate? = null,
    /** Null means "any time that day". */
    val dueTime: LocalTime? = null,
    /**
     * The reminder still waiting to go off for the current occurrence. Null once it has gone off,
     * or if there is no reminder. Snoozing moves only this.
     */
    val remindAt: LocalDateTime? = null,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val status: TaskStatus = TaskStatus.OPEN,
    val source: TaskSource = TaskSource.MANUAL,
    /** A short quote of the message or note the task came from. Kept after old messages are deleted. */
    val sourceExcerpt: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Set instead of deleting the row, so a restore or sync knows the task was deleted on purpose. */
    val deletedAt: Instant? = null,
    /** Added in database version 2: the reminder's clock time on the due date, kept for repeats. */
    val reminderTime: LocalTime? = null,
    /** Added in database version 2. */
    val repeatRule: RepeatRule? = null,
    /** Added in database version 2: when the task (or, for repeats, its last occurrence) was done. */
    val completedAt: Instant? = null,
)

// These enums are stored by name: never rename a constant, or existing rows become unreadable.

enum class TaskStatus { OPEN, DONE, ARCHIVED }

enum class TaskPriority { LOW, NORMAL, HIGH }

enum class TaskSource { MANUAL, MESSAGE, KEEP, SHARE }

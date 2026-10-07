package dev.maahdi.mavick.data.task

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Query("SELECT * FROM task WHERE id = :id")
    suspend fun findById(id: String): TaskEntity?

    /** Open, not deleted tasks: dated ones first (earliest first), undated ones last. */
    @Query(
        """
        SELECT * FROM task
        WHERE status = 'OPEN' AND deletedAt IS NULL
        ORDER BY dueDate IS NULL, dueDate, dueTime IS NULL, dueTime, createdAt
        """,
    )
    fun observeOpen(): Flow<List<TaskEntity>>

    /** Finished tasks, most recently finished first. */
    @Query(
        """
        SELECT * FROM task
        WHERE status = 'DONE' AND deletedAt IS NULL
        ORDER BY completedAt DESC
        LIMIT :limit
        """,
    )
    fun observeDone(limit: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task WHERE status = 'OPEN' AND deletedAt IS NULL AND remindAt IS NOT NULL")
    suspend fun getPendingReminders(): List<TaskEntity>

    /** Open tasks due on or before [date], for the morning briefing. */
    @Query(
        """
        SELECT * FROM task
        WHERE status = 'OPEN' AND deletedAt IS NULL AND dueDate IS NOT NULL AND dueDate <= :date
        ORDER BY dueDate, dueTime IS NULL, dueTime, createdAt
        """,
    )
    suspend fun getOpenDueOnOrBefore(date: LocalDate): List<TaskEntity>

    /** Open tasks with both a date and a time: the ones that get a calendar event (Phase 4). */
    @Query("SELECT * FROM task WHERE status = 'OPEN' AND deletedAt IS NULL AND dueDate IS NOT NULL AND dueTime IS NOT NULL")
    suspend fun getOpenTimed(): List<TaskEntity>

    /** Every task, including deleted ones (they are kept as proof of the deletion): for backups and merging. */
    @Query("SELECT * FROM task")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM task WHERE deletedAt IS NULL")
    suspend fun countActive(): Int

    /** Permanently removes tasks that were deleted before [cutoff]. */
    @Query("DELETE FROM task WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Instant): Int
}

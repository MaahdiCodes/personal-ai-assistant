package dev.maahdi.mavick.data.task

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: TaskEntity)

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

    @Query("SELECT COUNT(*) FROM task WHERE deletedAt IS NULL")
    suspend fun countActive(): Int
}

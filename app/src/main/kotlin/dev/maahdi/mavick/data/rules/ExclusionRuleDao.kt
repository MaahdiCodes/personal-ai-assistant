package dev.maahdi.mavick.data.rules

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface ExclusionRuleDao {
    @Query("SELECT * FROM exclusion_rule ORDER BY effect, type, displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<ExclusionRuleEntity>>

    @Query("SELECT * FROM exclusion_rule")
    suspend fun getAll(): List<ExclusionRuleEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(rule: ExclusionRuleEntity)

    @Query("DELETE FROM exclusion_rule WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("UPDATE exclusion_rule SET lastMatchedAt = :at WHERE id IN (:ids)")
    suspend fun markMatched(ids: Collection<String>, at: Instant)
}

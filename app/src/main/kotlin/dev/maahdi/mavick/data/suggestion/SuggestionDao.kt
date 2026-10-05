package dev.maahdi.mavick.data.suggestion

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.maahdi.mavick.capture.SourceApp
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface SuggestionDao {
    @Insert
    suspend fun insert(suggestion: SuggestionEntity)

    /** Waiting for you, newest message first. */
    @Query("SELECT * FROM suggestion WHERE state = 'NEW' ORDER BY messagePostedAt DESC, createdAt DESC")
    fun observeNew(): Flow<List<SuggestionEntity>>

    @Query("SELECT COUNT(*) FROM suggestion WHERE state = 'NEW'")
    fun observeNewCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM suggestion WHERE state = 'NEW'")
    suspend fun countNew(): Int

    @Query("SELECT title FROM suggestion WHERE state = 'NEW' ORDER BY messagePostedAt DESC, createdAt DESC LIMIT :limit")
    suspend fun newTitles(limit: Int): List<String>

    @Query("SELECT * FROM suggestion WHERE id = :id")
    suspend fun findById(id: String): SuggestionEntity?

    /** Every suggestion from one chat, in any state, to spot near-duplicates. */
    @Query("SELECT * FROM suggestion WHERE app = :app AND accountKey = :accountKey AND conversationKey = :conversationKey")
    suspend fun inChat(app: SourceApp, accountKey: String, conversationKey: String): List<SuggestionEntity>

    @Query("UPDATE suggestion SET state = :state, decidedAt = :decidedAt, taskId = :taskId WHERE id = :id")
    suspend fun setState(id: String, state: SuggestionState, decidedAt: Instant?, taskId: String?): Int

    @Query("SELECT COUNT(*) FROM suggestion")
    suspend fun count(): Int
}

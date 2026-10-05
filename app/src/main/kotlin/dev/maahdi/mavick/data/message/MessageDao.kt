package dev.maahdi.mavick.data.message

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.maahdi.mavick.capture.SourceApp
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    /** Returns -1 when an identical message (same dedupHash) is already saved. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    /** Newest first. */
    @Query("SELECT * FROM message ORDER BY postedAt DESC, receivedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<MessageEntity>>

    @Query("SELECT * FROM message WHERE id = :id")
    suspend fun findById(id: String): MessageEntity?

    @Query(
        """
        SELECT COUNT(*) FROM message
        WHERE app = :app AND accountKey = :accountKey AND conversationKey = :conversationKey
        """,
    )
    suspend fun countConversation(app: SourceApp, accountKey: String, conversationKey: String): Int

    @Query(
        """
        DELETE FROM message
        WHERE app = :app AND accountKey = :accountKey AND conversationKey = :conversationKey
        """,
    )
    suspend fun deleteConversation(app: SourceApp, accountKey: String, conversationKey: String): Int

    @Query("DELETE FROM message WHERE postedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Instant): Int

    @Query("DELETE FROM message")
    suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM message")
    suspend fun count(): Int

    /** Chats with saved messages, most recent first. Each shows its latest name. */
    @Query(
        """
        SELECT app, accountKey, conversationKey, conversationTitle, MAX(postedAt) AS lastAt FROM message
        GROUP BY app, accountKey, conversationKey
        ORDER BY lastAt DESC
        LIMIT :limit
        """,
    )
    suspend fun recentConversations(limit: Int): List<ConversationRef>

    /** People who wrote saved messages (not you), most recent first. */
    @Query(
        """
        SELECT app, sender AS name, MAX(postedAt) AS lastAt FROM message
        WHERE sender IS NOT NULL AND isFromMe = 0
        GROUP BY app, sender
        ORDER BY lastAt DESC
        LIMIT :limit
        """,
    )
    suspend fun recentSenders(limit: Int): List<SenderRef>

    @Query("SELECT DISTINCT app, accountKey FROM message ORDER BY app, accountKey")
    suspend fun accounts(): List<AccountRef>

    /** The newest message waiting for the AI (Phase 3). */
    @Query("SELECT * FROM message WHERE aiState = 'PENDING' ORDER BY postedAt DESC, receivedAt DESC LIMIT 1")
    suspend fun nextPending(): MessageEntity?

    @Query("SELECT COUNT(*) FROM message WHERE aiState = 'PENDING'")
    suspend fun countPending(): Int

    @Query("UPDATE message SET aiState = :state WHERE id = :id")
    suspend fun setAiState(id: String, state: AiState): Int

    /** Messages still waiting that were sent before [cutoff] are too old to suggest anything. */
    @Query("UPDATE message SET aiState = 'SKIPPED' WHERE aiState = 'PENDING' AND postedAt < :cutoff")
    suspend fun skipPendingBefore(cutoff: Instant): Int

    /** Up to [limit] messages sent before [before] in one chat, newest first: context for the AI. */
    @Query(
        """
        SELECT * FROM message
        WHERE app = :app AND accountKey = :accountKey AND conversationKey = :conversationKey AND postedAt < :before
        ORDER BY postedAt DESC, receivedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun earlierInChat(app: SourceApp, accountKey: String, conversationKey: String, before: Instant, limit: Int): List<MessageEntity>

    /** The newest messages, for the accuracy-check export. */
    @Query("SELECT * FROM message ORDER BY postedAt DESC, receivedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<MessageEntity>
}

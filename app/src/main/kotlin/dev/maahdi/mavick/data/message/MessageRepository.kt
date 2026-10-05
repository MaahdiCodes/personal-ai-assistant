package dev.maahdi.mavick.data.message

import dev.maahdi.mavick.capture.IncomingMessage
import dev.maahdi.mavick.capture.SourceApp
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/** Saved messages: what the Inbox shows, and what the AI will read (Phase 3). */
class MessageRepository(
    private val dao: MessageDao,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    fun observeRecent(): Flow<List<MessageEntity>> = dao.observeRecent(INBOX_LIMIT)

    suspend fun find(id: String): MessageEntity? = dao.findById(id)

    /** Saves a message; false if it was saved before (apps post recent messages again). */
    suspend fun save(message: IncomingMessage, receivedAt: Instant): Boolean = dao.insert(
        MessageEntity(
            id = newId(),
            app = message.app,
            accountKey = message.accountKey,
            conversationKey = message.conversationKey,
            conversationTitle = message.conversationTitle,
            sender = message.sender,
            text = message.text,
            postedAt = message.postedAt,
            receivedAt = receivedAt,
            isFromMe = message.isFromMe,
            isGroup = message.isGroup,
            cutShort = message.cutShort,
            dedupHash = message.dedupHash(),
        ),
    ) != -1L

    suspend fun countConversation(app: SourceApp, accountKey: String, conversationKey: String): Int =
        dao.countConversation(app, accountKey, conversationKey)

    suspend fun deleteConversation(app: SourceApp, accountKey: String, conversationKey: String): Int =
        dao.deleteConversation(app, accountKey, conversationKey)

    /** Retention: deletes messages sent before [cutoff]. */
    suspend fun deleteOlderThan(cutoff: Instant): Int = dao.deleteOlderThan(cutoff)

    suspend fun deleteAll(): Int = dao.deleteAll()

    suspend fun recentConversations(): List<ConversationRef> = dao.recentConversations(PICKER_LIMIT)

    suspend fun recentSenders(): List<SenderRef> = dao.recentSenders(PICKER_LIMIT)

    suspend fun accounts(): List<AccountRef> = dao.accounts()

    companion object {
        const val INBOX_LIMIT = 500
        const val PICKER_LIMIT = 50
    }
}

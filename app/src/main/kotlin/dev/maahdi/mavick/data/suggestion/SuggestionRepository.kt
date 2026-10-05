package dev.maahdi.mavick.data.suggestion

import dev.maahdi.mavick.ai.TitleSimilarity
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Tasks Mavick found in messages, and what you decided about them. */
class SuggestionRepository(
    private val dao: SuggestionDao,
    private val clock: () -> Clock,
) {
    /** Checking for a near-duplicate and saving happen together, so two can't slip in at once. */
    private val lock = Mutex()

    fun observeNew(): Flow<List<SuggestionEntity>> = dao.observeNew()

    fun observeNewCount(): Flow<Int> = dao.observeNewCount()

    suspend fun countNew(): Int = dao.countNew()

    /** The newest waiting titles, for the notification. */
    suspend fun newTitles(limit: Int = NOTIFICATION_TITLES): List<String> = dao.newTitles(limit)

    suspend fun find(id: String): SuggestionEntity? = dao.findById(id)

    /**
     * Saves [suggestion] unless its chat already has a near-duplicate, in any state: one waiting
     * already, one you added, or one you ignored (so an ignored plan doesn't come back). Returns
     * whether it was saved.
     */
    suspend fun addUnlessDuplicate(suggestion: SuggestionEntity): Boolean = lock.withLock {
        val sameChat = dao.inChat(suggestion.app, suggestion.accountKey, suggestion.conversationKey)
        if (sameChat.any { isNearDuplicate(it, suggestion, clock().zone) }) return false
        dao.insert(suggestion)
        true
    }

    /** You added it; [taskId] is the task it became. */
    suspend fun accept(id: String, taskId: String) {
        dao.setState(id, SuggestionState.ACCEPTED, now(), taskId)
    }

    suspend fun ignore(id: String) {
        dao.setState(id, SuggestionState.IGNORED, now(), taskId = null)
    }

    /** Undo: back to waiting. */
    suspend fun reopen(id: String) {
        dao.setState(id, SuggestionState.NEW, decidedAt = null, taskId = null)
    }

    private fun now(): Instant = Instant.now(clock())

    companion object {
        const val NOTIFICATION_TITLES = 5

        /**
         * The same plan twice (docs/PLAN.md §5.3, step 6): similar titles, on the same day. That is
         * the same due date, or, for two without a date, messages sent on the same day.
         */
        fun isNearDuplicate(first: SuggestionEntity, second: SuggestionEntity, zone: ZoneId): Boolean {
            val sameDay = if (first.dueDate != null || second.dueDate != null) {
                first.dueDate == second.dueDate
            } else {
                first.messagePostedAt.atZone(zone).toLocalDate() == second.messagePostedAt.atZone(zone).toLocalDate()
            }
            return sameDay && TitleSimilarity.similar(first.title, second.title)
        }
    }
}

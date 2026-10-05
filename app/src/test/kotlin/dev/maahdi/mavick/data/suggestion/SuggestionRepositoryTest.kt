package dev.maahdi.mavick.data.suggestion

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.capture.IncomingMessage
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.MavickDatabase
import dev.maahdi.mavick.data.message.MessageEntity
import dev.maahdi.mavick.data.message.MessageRepository
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.MutableClock
import dev.maahdi.mavick.testing.suggestion
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SuggestionRepositoryTest {
    private val clock = MutableClock(MONDAY_10AM)
    private val now: Instant get() = clock.instant()
    private lateinit var database: MavickDatabase
    private lateinit var messages: MessageRepository
    private lateinit var suggestions: SuggestionRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MavickDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        messages = MessageRepository(database.messageDao())
        suggestions = SuggestionRepository(database.suggestionDao(), clock = { clock })
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** Saves a message from Sam in the "Family" group and returns it. */
    private suspend fun message(text: String, minutesAgo: Long = 0, chat: String = "s:family", account: String = "0"): MessageEntity {
        val sentAt = now.minus(Duration.ofMinutes(minutesAgo))
        messages.save(IncomingMessage(SourceApp.WHATSAPP, account, chat, "Family", "Sam", text, sentAt, false, true, false), receivedAt = now)
        return messages.recent(1_000).single { it.text == text && it.conversationKey == chat && it.accountKey == account }
    }

    private fun from(message: MessageEntity, title: String, dueDate: LocalDate? = null) = suggestion(
        messageId = message.id,
        title = title,
        dueDate = dueDate,
        accountKey = message.accountKey,
        conversationKey = message.conversationKey,
        messagePostedAt = message.postedAt,
        createdAt = now,
    )

    @Test
    fun `a new suggestion waits, newest message first`() = runTest {
        val older = message("Bring the cake", minutesAgo = 10)
        val newer = message("Pay the rent", minutesAgo = 1)

        assertThat(suggestions.addUnlessDuplicate(from(older, "Bring the cake"))).isTrue()
        assertThat(suggestions.addUnlessDuplicate(from(newer, "Pay the rent"))).isTrue()

        assertThat(suggestions.observeNew().first().map { it.title }).containsExactly("Pay the rent", "Bring the cake").inOrder()
        assertThat(suggestions.countNew()).isEqualTo(2)
        assertThat(suggestions.observeNewCount().first()).isEqualTo(2)
        assertThat(suggestions.newTitles(1)).containsExactly("Pay the rent")
    }

    @Test
    fun `a near-duplicate from the same chat and day is not saved, whatever happened to the first`() = runTest {
        val friday = LocalDate.of(2026, 10, 9)
        val first = message("Bring the cake on Friday", minutesAgo = 5)
        val again = message("ok so you bring cake friday", minutesAgo = 1)
        suggestions.addUnlessDuplicate(from(first, "Bring the cake", friday))

        assertThat(suggestions.addUnlessDuplicate(from(again, "Bring cake", friday))).isFalse()

        val waiting = suggestions.observeNew().first().single()
        suggestions.ignore(waiting.id)
        assertThat(suggestions.addUnlessDuplicate(from(again, "bring the cake!", friday))).isFalse()
        assertThat(database.suggestionDao().count()).isEqualTo(1)
    }

    @Test
    fun `similar titles on other days, or in other chats, are kept`() = runTest {
        val first = message("Bring the cake on Friday", minutesAgo = 5)
        val otherChat = message("Bring the cake on Friday", chat = "s:work")
        val otherAccount = message("Bring the cake on Friday", account = "999")
        suggestions.addUnlessDuplicate(from(first, "Bring the cake", LocalDate.of(2026, 10, 9)))

        assertThat(suggestions.addUnlessDuplicate(from(first, "Bring the cake", LocalDate.of(2026, 10, 16)))).isTrue()
        assertThat(suggestions.addUnlessDuplicate(from(otherChat, "Bring the cake", LocalDate.of(2026, 10, 9)))).isTrue()
        assertThat(suggestions.addUnlessDuplicate(from(otherAccount, "Bring the cake", LocalDate.of(2026, 10, 9)))).isTrue()
    }

    @Test
    fun `without dates, the messages' days decide`() = runTest {
        val today = message("Call the plumber", minutesAgo = 5)
        val yesterday = message("Call the plumber!", minutesAgo = Duration.ofDays(1).toMinutes())
        val alsoToday = message("call plumber pls", minutesAgo = 1)
        suggestions.addUnlessDuplicate(from(today, "Call the plumber"))

        assertThat(suggestions.addUnlessDuplicate(from(alsoToday, "Call plumber"))).isFalse()
        assertThat(suggestions.addUnlessDuplicate(from(yesterday, "Call the plumber"))).isTrue()
    }

    @Test
    fun `adding, ignoring and undoing change only the state`() = runTest {
        val first = from(message("Pay the rent"), "Pay the rent")
        val second = from(message("Bring the cake", minutesAgo = 1), "Bring the cake")
        suggestions.addUnlessDuplicate(first)
        suggestions.addUnlessDuplicate(second)

        suggestions.accept(first.id, taskId = "task-1")
        suggestions.ignore(second.id)

        assertThat(suggestions.observeNew().first()).isEmpty()
        with(suggestions.find(first.id)!!) {
            assertThat(state).isEqualTo(SuggestionState.ACCEPTED)
            assertThat(taskId).isEqualTo("task-1")
            assertThat(decidedAt).isEqualTo(now)
            assertThat(title).isEqualTo("Pay the rent")
        }
        assertThat(suggestions.find(second.id)!!.state).isEqualTo(SuggestionState.IGNORED)

        suggestions.reopen(first.id)

        assertThat(suggestions.find(first.id)).isEqualTo(first)
    }

    @Test
    fun `deleting a message deletes its suggestions, however it is deleted`() = runTest {
        val old = message("Old plan", minutesAgo = Duration.ofDays(20).toMinutes())
        val kept = message("New plan")
        val work = message("Work plan", chat = "s:work")
        suggestions.addUnlessDuplicate(from(old, "Old plan"))
        suggestions.addUnlessDuplicate(from(kept, "New plan"))
        suggestions.addUnlessDuplicate(from(work, "Work plan"))

        messages.deleteOlderThan(now.minus(Duration.ofDays(14)))
        assertThat(suggestions.observeNew().first().map { it.title }).containsExactly("New plan", "Work plan")

        messages.deleteConversation(SourceApp.WHATSAPP, "0", "s:work")
        assertThat(suggestions.observeNew().first().map { it.title }).containsExactly("New plan")

        messages.deleteAll()
        assertThat(database.suggestionDao().count()).isEqualTo(0)
    }
}

package dev.maahdi.mavick.data.message

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.capture.IncomingMessage
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.MavickDatabase
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Saving and finding messages, on an in-memory database (encryption is tested on the phone). */
@RunWith(AndroidJUnit4::class)
class MessageRepositoryTest {
    private val start = Instant.parse("2026-10-05T04:00:00Z")
    private lateinit var database: MavickDatabase
    private lateinit var repository: MessageRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MavickDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MessageRepository(database.messageDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun message(
        text: String = "Hi",
        minute: Long = 0,
        app: SourceApp = SourceApp.WHATSAPP,
        account: String = "0",
        chat: String = "s:family",
        title: String = "Family",
        sender: String? = "Sam",
        fromMe: Boolean = false,
    ) = IncomingMessage(app, account, chat, title, sender, text, start.plusSeconds(minute * 60), fromMe, isGroup = true, cutShort = false)

    private suspend fun all() = repository.observeRecent().first()

    @Test
    fun `a saved message comes back with every field, newest first`() = runTest {
        repository.save(message("first", minute = 0), receivedAt = start)
        repository.save(message("second", minute = 1).copy(cutShort = true), receivedAt = start.plusSeconds(90))

        val saved = all()
        assertThat(saved.map { it.text }).containsExactly("second", "first").inOrder()
        with(saved.first()) {
            assertThat(app).isEqualTo(SourceApp.WHATSAPP)
            assertThat(accountKey).isEqualTo("0")
            assertThat(conversationKey).isEqualTo("s:family")
            assertThat(conversationTitle).isEqualTo("Family")
            assertThat(sender).isEqualTo("Sam")
            assertThat(postedAt).isEqualTo(start.plusSeconds(60))
            assertThat(receivedAt).isEqualTo(start.plusSeconds(90))
            assertThat(cutShort).isTrue()
            assertThat(isGroup).isTrue()
            assertThat(aiState).isEqualTo(AiState.PENDING)
            assertThat(repository.find(id)).isEqualTo(this)
        }
    }

    @Test
    fun `saving the same message twice keeps one`() = runTest {
        assertThat(repository.save(message(), receivedAt = start)).isTrue()
        assertThat(repository.save(message(), receivedAt = start.plusSeconds(5))).isFalse()

        assertThat(all()).hasSize(1)
    }

    @Test
    fun `deleting a chat removes only that chat, in that app and account`() = runTest {
        repository.save(message("a"), start)
        repository.save(message("b", minute = 1), start)
        repository.save(message("other account", account = "999"), start)
        repository.save(message("other chat", chat = "s:work"), start)
        repository.save(message("other app", app = SourceApp.MESSENGER), start)

        assertThat(repository.countConversation(SourceApp.WHATSAPP, "0", "s:family")).isEqualTo(2)
        assertThat(repository.deleteConversation(SourceApp.WHATSAPP, "0", "s:family")).isEqualTo(2)

        assertThat(all().map { it.text }).containsExactly("other account", "other chat", "other app")
    }

    @Test
    fun `retention deletes by the time a message was sent`() = runTest {
        repository.save(message("old", minute = 0), start)
        repository.save(message("new", minute = 10), start)

        assertThat(repository.deleteOlderThan(start.plusSeconds(300))).isEqualTo(1)

        assertThat(all().map { it.text }).containsExactly("new")
    }

    @Test
    fun `everything can be deleted at once`() = runTest {
        repository.save(message("a"), start)
        repository.save(message("b", minute = 1), start)

        assertThat(repository.deleteAll()).isEqualTo(2)
        assertThat(all()).isEmpty()
    }

    @Test
    fun `recent chats show each chat once, with its latest name`() = runTest {
        repository.save(message("a", minute = 0, title = "Family"), start)
        repository.save(message("b", minute = 5, title = "Family 2026"), start)
        repository.save(message("c", minute = 2, chat = "s:work", title = "Work"), start)

        val chats = repository.recentConversations()

        assertThat(chats.map { it.conversationTitle }).containsExactly("Family 2026", "Work").inOrder()
        assertThat(chats.first().lastAt).isEqualTo(start.plusSeconds(300))
    }

    @Test
    fun `recent people leave out you`() = runTest {
        repository.save(message("a", sender = "Sam"), start)
        repository.save(message("b", minute = 1, sender = "Rina"), start)
        repository.save(message("c", minute = 2, sender = null, fromMe = true), start)

        assertThat(repository.recentSenders().map { it.name }).containsExactly("Rina", "Sam").inOrder()
    }

    @Test
    fun `the newest message waiting for the AI comes first, and handled ones are left alone`() = runTest {
        repository.save(message("old", minute = 0), start)
        repository.save(message("new", minute = 5), start)

        val first = repository.nextPending()!!
        assertThat(first.text).isEqualTo("new")
        repository.setAiState(first.id, AiState.DONE)

        assertThat(repository.nextPending()!!.text).isEqualTo("old")
        assertThat(repository.countPending()).isEqualTo(1)
        assertThat(repository.find(first.id)!!.aiState).isEqualTo(AiState.DONE)
    }

    @Test
    fun `waiting messages sent before a cutoff are skipped, others keep waiting`() = runTest {
        repository.save(message("old", minute = 0), start)
        repository.save(message("new", minute = 10), start)
        repository.setAiState(all().single { it.text == "new" }.id, AiState.DONE)
        repository.save(message("old but done", minute = 1), start)
        repository.setAiState(all().single { it.text == "old but done" }.id, AiState.DONE)

        assertThat(repository.skipPendingBefore(start.plusSeconds(300))).isEqualTo(1)

        assertThat(all().associate { it.text to it.aiState }).isEqualTo(
            mapOf("old" to AiState.SKIPPED, "new" to AiState.DONE, "old but done" to AiState.DONE),
        )
        assertThat(repository.nextPending()).isNull()
    }

    @Test
    fun `context is the three messages before, oldest first, from that chat and account only`() = runTest {
        listOf("a", "b", "c", "d", "e").forEachIndexed { minute, text -> repository.save(message(text, minute = minute.toLong()), start) }
        repository.save(message("other chat", minute = 2, chat = "s:work"), start)
        repository.save(message("other account", minute = 3, account = "999"), start)
        repository.save(message("other app", minute = 3, app = SourceApp.MESSENGER), start)

        val current = all().single { it.text == "e" }

        assertThat(repository.earlierInChat(current).map { it.text }).containsExactly("b", "c", "d").inOrder()
        assertThat(repository.earlierInChat(all().single { it.text == "a" })).isEmpty()
    }

    @Test
    fun `the newest messages come first for the export`() = runTest {
        repository.save(message("a", minute = 0), start)
        repository.save(message("b", minute = 1), start)
        repository.save(message("c", minute = 2), start)

        assertThat(repository.recent(2).map { it.text }).containsExactly("c", "b").inOrder()
    }

    @Test
    fun `accounts are listed per app`() = runTest {
        repository.save(message("a"), start)
        repository.save(message("b", minute = 1, account = "999"), start)
        repository.save(message("c", minute = 2, app = SourceApp.GMAIL, account = "0/you@gmail.com"), start)

        assertThat(repository.accounts()).containsExactly(
            AccountRef(SourceApp.WHATSAPP, "0"),
            AccountRef(SourceApp.WHATSAPP, "999"),
            AccountRef(SourceApp.GMAIL, "0/you@gmail.com"),
        )
    }
}

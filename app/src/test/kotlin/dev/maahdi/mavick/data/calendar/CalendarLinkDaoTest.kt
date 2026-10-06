package dev.maahdi.mavick.data.calendar

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.MavickDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarLinkDaoTest {
    private lateinit var database: MavickDatabase
    private lateinit var dao: CalendarLinkDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MavickDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.calendarLinkDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun link(taskId: String = "task-1", calendarId: Long = 1, eventId: Long = 100, fingerprint: String = "abc") =
        CalendarLinkEntity(taskId, calendarId, eventId, fingerprint)

    @Test
    fun `a saved link comes back as it was`() = runTest {
        dao.upsert(link())

        assertThat(dao.find("task-1")).isEqualTo(link())
    }

    @Test
    fun `an unknown task has no link`() = runTest {
        assertThat(dao.find("missing")).isNull()
    }

    @Test
    fun `saving a task's link again replaces it`() = runTest {
        dao.upsert(link(eventId = 100, fingerprint = "old"))

        dao.upsert(link(eventId = 101, fingerprint = "new"))

        assertThat(dao.all()).containsExactly(link(eventId = 101, fingerprint = "new"))
    }

    @Test
    fun `deleting a link leaves the others`() = runTest {
        dao.upsert(link("a", eventId = 1))
        dao.upsert(link("b", eventId = 2))

        dao.delete("a")

        assertThat(dao.all().map { it.taskId }).containsExactly("b")
        assertThat(dao.count()).isEqualTo(1)
    }

    @Test
    fun `deleting a link that is not there is harmless`() = runTest {
        dao.delete("missing")

        assertThat(dao.count()).isEqualTo(0)
    }

    @Test
    fun `a link does not depend on the task existing`() = runTest {
        // Removing a purged task's event may have to wait, so its link must survive without the task.
        dao.upsert(link("purged-task"))

        assertThat(dao.find("purged-task")).isNotNull()
    }
}

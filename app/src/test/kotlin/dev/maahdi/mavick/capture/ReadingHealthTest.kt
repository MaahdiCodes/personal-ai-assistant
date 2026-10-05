package dev.maahdi.mavick.capture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingHealthTest {
    private val now = Instant.parse("2026-10-05T04:00:00Z")
    private val day = Duration.ofDays(1)
    private val preferences = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("status-test", Context.MODE_PRIVATE)

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    private fun state(status: CaptureStatus, hasAccess: Boolean = true) = ReadingHealth.state(hasAccess, status, now, day)

    @Test
    fun `without notification access, reading is off whatever else is known`() {
        assertThat(state(CaptureStatus(connectedAt = now), hasAccess = false)).isEqualTo(ReadingState.NO_ACCESS)
    }

    @Test
    fun `access without a connection, or after a disconnection, is not connected`() {
        assertThat(state(CaptureStatus())).isEqualTo(ReadingState.NOT_CONNECTED)
        assertThat(state(CaptureStatus(connectedAt = now.minusSeconds(60), disconnectedAt = now.minusSeconds(30))))
            .isEqualTo(ReadingState.NOT_CONNECTED)
    }

    @Test
    fun `a reconnection after a disconnection is fine again`() {
        val status = CaptureStatus(connectedAt = now.minusSeconds(10), disconnectedAt = now.minusSeconds(30))

        assertThat(state(status)).isEqualTo(ReadingState.OK)
    }

    @Test
    fun `nothing arriving for the whole period is quiet, but one notification is enough`() {
        val connectedLongAgo = now.minus(Duration.ofDays(3))

        assertThat(state(CaptureStatus(connectedAt = connectedLongAgo))).isEqualTo(ReadingState.QUIET)
        assertThat(state(CaptureStatus(connectedAt = connectedLongAgo, lastSeenAt = now.minus(Duration.ofHours(25))))).isEqualTo(ReadingState.QUIET)
        assertThat(state(CaptureStatus(connectedAt = connectedLongAgo, lastSeenAt = now.minus(Duration.ofHours(23))))).isEqualTo(ReadingState.OK)
    }

    @Test
    fun `a fresh connection isn't quiet yet`() {
        assertThat(state(CaptureStatus(connectedAt = now.minusSeconds(5)))).isEqualTo(ReadingState.OK)
    }

    @Test
    fun `the store keeps connection times and adds up counts`() {
        val store = CaptureStatusStore(preferences)

        store.markConnected(now)
        store.record(now, CaptureResult(saved = 2, skipped = 1))
        store.record(now.plusSeconds(5), CaptureResult(duplicates = 3))
        store.markFailure()
        store.markDisconnected(now.plusSeconds(10))

        assertThat(CaptureStatusStore(preferences).snapshot()).isEqualTo(
            CaptureStatus(
                connectedAt = now,
                disconnectedAt = now.plusSeconds(10),
                lastSeenAt = now.plusSeconds(5),
                lastSavedAt = now,
                saved = 2,
                skipped = 1,
                duplicates = 3,
                failures = 1,
            ),
        )
    }

    @Test
    fun `an empty store knows nothing yet`() {
        assertThat(CaptureStatusStore(preferences).snapshot()).isEqualTo(CaptureStatus())
    }
}

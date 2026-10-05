package dev.maahdi.mavick.ui.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.capture.ReadingState
import dev.maahdi.mavick.data.settings.AppSettings
import dev.maahdi.mavick.health.StorageStatus
import java.time.Duration
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Messages and Health parts of Settings. */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-10-05T04:00:00Z")
    private var settings = AppSettings()
    private val calls = mutableListOf<String>()

    private fun show(health: HealthInfo) {
        compose.setContent {
            SettingsScreen(
                settings = settings,
                health = health,
                now = now,
                use24Hour = true,
                onChange = { change -> settings = change(settings) },
                onFixNotifications = { calls += "notifications" },
                onFixBattery = { calls += "battery" },
                onFixNotificationAccess = { calls += "access" },
                onRestartReading = { calls += "restart" },
                onOpenAutostart = { calls += "autostart" },
                onOpenReading = { calls += "reading" },
                onDeleteAllMessages = { calls += "delete" },
                onBack = {},
            )
        }
    }

    private val working = HealthInfo(
        storage = StorageStatus.Ready(0),
        notificationAccess = true,
        reading = ReadingState.OK,
        lastSeenAt = now.minus(Duration.ofMinutes(5)),
    )

    @Test
    fun `working message reading shows when the last message came`() {
        show(working)

        compose.onNodeWithText("Working · last message 5 min ago").performScrollTo().assertExists()
        compose.onNodeWithText("Alarms, and reading notifications as they arrive").performScrollTo().assertExists()
    }

    @Test
    fun `without access, Fix opens notification access and the restricted-setting hint shows`() {
        show(working.copy(notificationAccess = false, reading = ReadingState.NO_ACCESS))

        compose.onNodeWithText("Off: Mavick can't read messages").performScrollTo().assertExists()
        compose.onNodeWithText("If Android says \"Restricted setting\": App info › ⋮ › Allow restricted settings, then try again.").assertExists()
        compose.onAllNodesWithText("Fix")[0].performScrollTo().performClick()

        assertThat(calls).containsExactly("access")
    }

    @Test
    fun `reading stopped by Android offers a restart and shows how often it happened`() {
        show(working.copy(reading = ReadingState.NOT_CONNECTED, disconnectsThisWeek = 3))

        compose.onNodeWithText("Stopped by Android").performScrollTo().assertExists()
        compose.onNodeWithText("Android stopped it 3 times in the last 7 days").assertExists()
        compose.onNodeWithText("Restart").performScrollTo().performClick()

        assertThat(calls).containsExactly("restart")
    }

    @Test
    fun `quiet reading says since when`() {
        show(working.copy(reading = ReadingState.QUIET, lastSeenAt = now.minus(Duration.ofDays(2))))

        compose.onNodeWithText("Nothing received since 2 days ago").performScrollTo().assertExists()
    }

    @Test
    fun `Xiaomi phones get the Autostart check, other phones don't`() {
        show(working.copy(isXiaomi = true))

        compose.onNodeWithText("Autostart (Xiaomi)").performScrollTo().assertExists()
        compose.onNodeWithText("Open").performScrollTo().performClick()
        assertThat(calls).containsExactly("autostart")
    }

    @Test
    fun `phones other than Xiaomi don't show Autostart`() {
        show(working)

        compose.onNodeWithText("Autostart (Xiaomi)").assertDoesNotExist()
    }

    @Test
    fun `how long messages are kept can be changed`() {
        show(working)

        compose.onNodeWithText("14 days").performScrollTo().performClick()
        compose.onNodeWithText("30 days").performClick()

        assertThat(settings.messageRetentionDays).isEqualTo(30)
    }

    @Test
    fun `reading warnings can be turned off`() {
        show(working)

        compose.onNodeWithText("1 day").performScrollTo().performClick()
        compose.onNodeWithText("Never warn").performClick()

        assertThat(settings.readingWarningDays).isEqualTo(0)
    }

    @Test
    fun `deleting all messages asks first`() {
        show(working)

        compose.onNodeWithText("Delete all saved messages").performScrollTo().performClick()
        assertThat(calls).isEmpty()
        compose.onNodeWithText("Tasks you made from messages are kept.").assertExists()
        compose.onNodeWithText("Delete").performClick()

        assertThat(calls).containsExactly("delete")
    }

    @Test
    fun `what Mavick reads opens from Settings`() {
        show(working)

        compose.onNodeWithText("What Mavick reads").performScrollTo().performClick()

        assertThat(calls).containsExactly("reading")
    }
}

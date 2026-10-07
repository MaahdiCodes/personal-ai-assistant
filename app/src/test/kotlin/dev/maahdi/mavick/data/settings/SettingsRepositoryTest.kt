package dev.maahdi.mavick.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.capture.AppCapture
import dev.maahdi.mavick.capture.CaptureMode
import dev.maahdi.mavick.capture.CapturePause
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.time.DateOrder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("settings-test", Context.MODE_PRIVATE)

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun `defaults match the plan`() {
        val settings = SettingsRepository(preferences).current

        assertThat(settings.briefingEnabled).isTrue()
        assertThat(settings.briefingTime).isEqualTo(LocalTime.of(8, 0))
        assertThat(settings.appLockEnabled).isTrue()
        assertThat(settings.workDays).containsExactly(
            DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
        )
        assertThat(settings.dateOrder).isEqualTo(DateOrder.DAY_MONTH)
        assertThat(settings.notificationPermissionRequested).isFalse()
        SourceApp.entries.forEach { app -> assertThat(settings.captureFor(app)).isEqualTo(AppCapture(enabled = true, mode = CaptureMode.ALL_EXCEPT)) }
        assertThat(settings.capturePause).isEqualTo(CapturePause.Off)
        assertThat(settings.messageRetentionDays).isEqualTo(14)
        assertThat(settings.readingWarningDays).isEqualTo(1)
        assertThat(settings.xiaomiAutostartOn).isFalse()
        assertThat(settings.defaultRulesAdded).isFalse()
    }

    @Test
    fun `message reading settings are saved and survive a restart`() {
        val until = Instant.parse("2026-10-05T10:00:00Z")
        SettingsRepository(preferences).update {
            it.copy(
                appCapture = it.appCapture +
                    (SourceApp.GMAIL to AppCapture(enabled = false)) +
                    (SourceApp.WHATSAPP to AppCapture(mode = CaptureMode.ONLY_LISTED)),
                capturePause = CapturePause.Until(until),
                messageRetentionDays = 30,
                readingWarningDays = 3,
                xiaomiAutostartOn = true,
                defaultRulesAdded = true,
            )
        }

        val reloaded = SettingsRepository(preferences).current

        assertThat(reloaded.captureFor(SourceApp.GMAIL)).isEqualTo(AppCapture(enabled = false))
        assertThat(reloaded.captureFor(SourceApp.WHATSAPP)).isEqualTo(AppCapture(mode = CaptureMode.ONLY_LISTED))
        assertThat(reloaded.captureFor(SourceApp.MESSENGER)).isEqualTo(AppCapture())
        assertThat(reloaded.capturePause).isEqualTo(CapturePause.Until(until))
        assertThat(reloaded.messageRetentionDays).isEqualTo(30)
        assertThat(reloaded.readingWarningDays).isEqualTo(3)
        assertThat(reloaded.xiaomiAutostartOn).isTrue()
        assertThat(reloaded.defaultRulesAdded).isTrue()
    }

    @Test
    fun `suggestions are on at first, and turning them off survives a restart`() {
        assertThat(SettingsRepository(preferences).current.suggestionsEnabled).isTrue()

        SettingsRepository(preferences).update { it.copy(suggestionsEnabled = false) }

        assertThat(SettingsRepository(preferences).current.suggestionsEnabled).isFalse()
    }

    @Test
    fun `the calendar is off at first with no calendar chosen`() {
        val settings = SettingsRepository(preferences).current

        assertThat(settings.calendarEnabled).isFalse()
        assertThat(settings.calendarId).isNull()
        assertThat(settings.calendarName).isNull()
        assertThat(settings.calendarTarget).isNull()
    }

    @Test
    fun `the chosen calendar is saved and survives a restart`() {
        SettingsRepository(preferences).update { it.copy(calendarEnabled = true, calendarId = 42, calendarName = "Personal (me@gmail.com)") }

        val reloaded = SettingsRepository(preferences).current

        assertThat(reloaded.calendarEnabled).isTrue()
        assertThat(reloaded.calendarId).isEqualTo(42L)
        assertThat(reloaded.calendarName).isEqualTo("Personal (me@gmail.com)")
        assertThat(reloaded.calendarTarget).isEqualTo(42L)
    }

    @Test
    fun `switching the calendar off keeps the choice but writes nowhere`() {
        val repository = SettingsRepository(preferences)
        repository.update { it.copy(calendarEnabled = true, calendarId = 42, calendarName = "Personal") }

        repository.update { it.copy(calendarEnabled = false) }

        val reloaded = SettingsRepository(preferences).current
        assertThat(reloaded.calendarId).isEqualTo(42L)
        assertThat(reloaded.calendarName).isEqualTo("Personal")
        assertThat(reloaded.calendarTarget).isNull()
    }

    @Test
    fun `the calendar cannot be on without a calendar chosen`() {
        val repository = SettingsRepository(preferences)

        repository.update { it.copy(calendarEnabled = true) }

        assertThat(repository.current.calendarEnabled).isFalse()
        assertThat(SettingsRepository(preferences).current.calendarEnabled).isFalse()
    }

    @Test
    fun `forgetting the calendar clears what was saved`() {
        val repository = SettingsRepository(preferences)
        repository.update { it.copy(calendarEnabled = true, calendarId = 42, calendarName = "Personal") }

        repository.update { it.copy(calendarEnabled = false, calendarId = null, calendarName = null) }

        val reloaded = SettingsRepository(preferences).current
        assertThat(reloaded.calendarId).isNull()
        assertThat(reloaded.calendarName).isNull()
    }

    @Test
    fun `a damaged calendar choice reads as no calendar`() {
        preferences.edit().putBoolean("calendar_enabled", true).putString("calendar_id", "not a number").commit()
        assertThat(SettingsRepository(preferences).current.calendarEnabled).isFalse()
        assertThat(SettingsRepository(preferences).current.calendarId).isNull()

        preferences.edit().putString("calendar_id", "-3").commit()
        assertThat(SettingsRepository(preferences).current.calendarId).isNull()
        assertThat(SettingsRepository(preferences).current.calendarEnabled).isFalse()
    }

    @Test
    fun `clash warnings are off at first and check every calendar`() {
        val settings = SettingsRepository(preferences).current

        assertThat(settings.clashCheckEnabled).isFalse()
        assertThat(settings.clashCalendarIds).isEmpty()
    }

    @Test
    fun `the clash choices are saved and survive a restart`() {
        SettingsRepository(preferences).update { it.copy(clashCheckEnabled = true, clashCalendarIds = setOf(7, 3)) }

        val reloaded = SettingsRepository(preferences).current

        assertThat(reloaded.clashCheckEnabled).isTrue()
        assertThat(reloaded.clashCalendarIds).containsExactly(3L, 7L)
    }

    @Test
    fun `choosing every calendar again clears the saved choice`() {
        val repository = SettingsRepository(preferences)
        repository.update { it.copy(clashCalendarIds = setOf(3)) }

        repository.update { it.copy(clashCalendarIds = emptySet()) }

        assertThat(SettingsRepository(preferences).current.clashCalendarIds).isEmpty()
    }

    @Test
    fun `damaged clash calendar ids are left out`() {
        preferences.edit().putString("clash_calendar_ids", "3,abc,-4,0,,9").commit()

        assertThat(SettingsRepository(preferences).current.clashCalendarIds).containsExactly(3L, 9L)

        preferences.edit().putString("clash_calendar_ids", "garbage").commit()
        assertThat(SettingsRepository(preferences).current.clashCalendarIds).isEmpty()
    }

    @Test
    fun `a pause until resumed survives a restart, and resuming clears it`() {
        val repository = SettingsRepository(preferences)
        repository.update { it.copy(capturePause = CapturePause.UntilResumed) }
        assertThat(SettingsRepository(preferences).current.capturePause).isEqualTo(CapturePause.UntilResumed)

        repository.update { it.copy(capturePause = CapturePause.Off) }

        assertThat(SettingsRepository(preferences).current.capturePause).isEqualTo(CapturePause.Off)
    }

    @Test
    fun `a damaged pause stays paused, so reading never restarts by accident`() {
        preferences.edit().putString("capture_pause", "garbage").commit()

        assertThat(SettingsRepository(preferences).current.capturePause).isEqualTo(CapturePause.UntilResumed)
    }

    @Test
    fun `out-of-range numbers are kept in range`() {
        val repository = SettingsRepository(preferences)

        repository.update { it.copy(messageRetentionDays = 0, readingWarningDays = 99) }
        assertThat(repository.current.messageRetentionDays).isEqualTo(1)
        assertThat(repository.current.readingWarningDays).isEqualTo(7)

        repository.update { it.copy(messageRetentionDays = 500, readingWarningDays = -1) }
        assertThat(repository.current.messageRetentionDays).isEqualTo(90)
        assertThat(repository.current.readingWarningDays).isEqualTo(0)
    }

    @Test
    fun `damaged message reading values fall back to their defaults`() {
        preferences.edit()
            .putInt("message_retention_days", 0)
            .putInt("reading_warning_days", 42)
            .putString("capture_WHATSAPP_mode", "SOMETIMES")
            .commit()

        val settings = SettingsRepository(preferences).current

        assertThat(settings.messageRetentionDays).isEqualTo(14)
        assertThat(settings.readingWarningDays).isEqualTo(1)
        assertThat(settings.captureFor(SourceApp.WHATSAPP)).isEqualTo(AppCapture())
    }

    @Test
    fun `changes are saved and survive a restart`() {
        SettingsRepository(preferences).update {
            it.copy(
                briefingEnabled = false,
                briefingTime = LocalTime.of(6, 45),
                appLockEnabled = false,
                workDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                dateOrder = DateOrder.MONTH_DAY,
                notificationPermissionRequested = true,
            )
        }

        val reloaded = SettingsRepository(preferences).current

        assertThat(reloaded).isEqualTo(
            AppSettings(
                briefingEnabled = false,
                briefingTime = LocalTime.of(6, 45),
                appLockEnabled = false,
                workDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                dateOrder = DateOrder.MONTH_DAY,
                notificationPermissionRequested = true,
            ),
        )
    }

    @Test
    fun `screens see changes straight away`() {
        val repository = SettingsRepository(preferences)

        repository.update { it.copy(briefingTime = LocalTime.of(7, 0)) }

        assertThat(repository.settings.value.briefingTime).isEqualTo(LocalTime.of(7, 0))
    }

    @Test
    fun `at least one work day is always kept`() {
        val repository = SettingsRepository(preferences)

        repository.update { it.copy(workDays = emptySet()) }

        assertThat(repository.current.workDays).isNotEmpty()
    }

    @Test
    fun `damaged saved values fall back to defaults instead of crashing`() {
        preferences.edit()
            .putString("briefing_time", "25:99")
            .putString("work_days", "MONDAY,NOTADAY")
            .putString("date_order", "SIDEWAYS")
            .commit()

        val settings = SettingsRepository(preferences).current

        assertThat(settings).isEqualTo(AppSettings())
    }
}

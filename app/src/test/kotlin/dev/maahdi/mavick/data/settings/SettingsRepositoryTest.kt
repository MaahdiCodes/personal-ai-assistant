package dev.maahdi.mavick.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.time.DateOrder
import java.time.DayOfWeek
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

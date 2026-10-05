package dev.maahdi.mavick.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.maahdi.mavick.time.DEFAULT_WORK_DAYS
import dev.maahdi.mavick.time.DateOrder
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val briefingEnabled: Boolean = true,
    val briefingTime: LocalTime = LocalTime.of(8, 0),
    val appLockEnabled: Boolean = true,
    val workDays: Set<DayOfWeek> = DEFAULT_WORK_DAYS,
    val dateOrder: DateOrder = DateOrder.DAY_MONTH,
    /** Whether Mavick already asked for notification permission once (Android asks only so often). */
    val notificationPermissionRequested: Boolean = false,
)

/**
 * Settings, kept in a small private preferences file (never backed up, see
 * data_extraction_rules.xml). A damaged value falls back to its default instead of crashing.
 */
class SettingsRepository(private val preferences: SharedPreferences) {
    private val state = MutableStateFlow(read())

    val settings: StateFlow<AppSettings> = state.asStateFlow()

    val current: AppSettings get() = state.value

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(state.value)
        if (updated.workDays.isEmpty()) return // at least one work day is needed
        write(updated)
        state.value = updated
    }

    private fun read(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            briefingEnabled = preferences.getBoolean(KEY_BRIEFING_ENABLED, defaults.briefingEnabled),
            briefingTime = preferences.getString(KEY_BRIEFING_TIME, null)
                ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                ?: defaults.briefingTime,
            appLockEnabled = preferences.getBoolean(KEY_APP_LOCK_ENABLED, defaults.appLockEnabled),
            workDays = preferences.getString(KEY_WORK_DAYS, null)
                ?.let(::parseDays)
                ?: defaults.workDays,
            dateOrder = preferences.getString(KEY_DATE_ORDER, null)
                ?.let { name -> DateOrder.entries.firstOrNull { it.name == name } }
                ?: defaults.dateOrder,
            notificationPermissionRequested = preferences.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false),
        )
    }

    private fun write(settings: AppSettings) {
        preferences.edit {
            putBoolean(KEY_BRIEFING_ENABLED, settings.briefingEnabled)
            putString(KEY_BRIEFING_TIME, settings.briefingTime.toString())
            putBoolean(KEY_APP_LOCK_ENABLED, settings.appLockEnabled)
            putString(KEY_WORK_DAYS, settings.workDays.sorted().joinToString(",") { it.name })
            putString(KEY_DATE_ORDER, settings.dateOrder.name)
            putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, settings.notificationPermissionRequested)
        }
    }

    private fun parseDays(text: String): Set<DayOfWeek>? {
        val days = text.split(',').map { name -> DayOfWeek.entries.firstOrNull { it.name == name } }
        return if (days.isEmpty() || days.any { it == null }) null else days.filterNotNull().toSet()
    }

    companion object {
        const val FILE_NAME = "settings"
        private const val KEY_BRIEFING_ENABLED = "briefing_enabled"
        private const val KEY_BRIEFING_TIME = "briefing_time"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_WORK_DAYS = "work_days"
        private const val KEY_DATE_ORDER = "date_order"
        private const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
    }
}

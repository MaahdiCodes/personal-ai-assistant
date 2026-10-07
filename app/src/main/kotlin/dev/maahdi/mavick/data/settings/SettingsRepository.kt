package dev.maahdi.mavick.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.maahdi.mavick.capture.AppCapture
import dev.maahdi.mavick.capture.CaptureMode
import dev.maahdi.mavick.capture.CapturePause
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.time.DEFAULT_WORK_DAYS
import dev.maahdi.mavick.time.DateOrder
import java.time.DayOfWeek
import java.time.Instant
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
    /** Which apps are read, and which of their chats (Phase 2). */
    val appCapture: Map<SourceApp, AppCapture> = SourceApp.entries.associateWith { AppCapture() },
    val capturePause: CapturePause = CapturePause.Off,
    /** Saved messages are deleted after this many days. */
    val messageRetentionDays: Int = DEFAULT_RETENTION_DAYS,
    /** Warn when no message has been read for this many days; 0 means never. */
    val readingWarningDays: Int = DEFAULT_WARNING_DAYS,
    /** Xiaomi only: the user confirmed Autostart is on (Android can't check it). */
    val xiaomiAutostartOn: Boolean = false,
    /** The default "Never read" keywords were added once; deleting one keeps it deleted. */
    val defaultRulesAdded: Boolean = false,
    /** Look for tasks in new messages and suggest them (Phase 3). */
    val suggestionsEnabled: Boolean = true,
    /** Write tasks that have a time to a phone calendar (Phase 4). Off until a calendar is chosen. */
    val calendarEnabled: Boolean = false,
    /** The chosen calendar's ID in the phone's calendar storage, and a name to show for it. */
    val calendarId: Long? = null,
    val calendarName: String? = null,
    /** Warn when a task with a time overlaps a calendar event (Phase 4, part 2). Reads the calendar, so off at first. */
    val clashCheckEnabled: Boolean = false,
    /** The calendars to check for clashes; empty means every calendar shown in the Calendar app. */
    val clashCalendarIds: Set<Long> = emptySet(),
    /** Show task titles on the home-screen widget (Phase 4, part 3). Off shows only counts. */
    val widgetShowTitles: Boolean = true,
    /** When a backup file was last saved from this phone (Phase 5); never part of a backup. */
    val lastBackupAt: Instant? = null,
) {
    fun captureFor(app: SourceApp): AppCapture = appCapture[app] ?: AppCapture()

    /** The calendar tasks are written to now, or null while the feature is off. */
    val calendarTarget: Long? get() = calendarId.takeIf { calendarEnabled }

    companion object {
        const val DEFAULT_RETENTION_DAYS = 14
        val RETENTION_DAYS_RANGE = 1..90
        const val DEFAULT_WARNING_DAYS = 1
        val WARNING_DAYS_RANGE = 0..7
    }
}

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
        val transformed = transform(state.value)
        if (transformed.workDays.isEmpty()) return // at least one work day is needed
        val updated = transformed.copy(
            messageRetentionDays = transformed.messageRetentionDays.coerceIn(AppSettings.RETENTION_DAYS_RANGE),
            readingWarningDays = transformed.readingWarningDays.coerceIn(AppSettings.WARNING_DAYS_RANGE),
            // The calendar can't be on without a calendar to write to.
            calendarEnabled = transformed.calendarEnabled && transformed.calendarId != null,
        )
        write(updated)
        state.value = updated
    }

    private fun read(): AppSettings {
        val defaults = AppSettings()
        // Stored as text: a damaged or foreign value reads as "no calendar", never crashes.
        val calendarId = preferences.getString(KEY_CALENDAR_ID, null)?.toLongOrNull()?.takeIf { it > 0 }
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
            appCapture = SourceApp.entries.associateWith(::readAppCapture),
            capturePause = readPause(),
            messageRetentionDays = preferences.getInt(KEY_RETENTION_DAYS, AppSettings.DEFAULT_RETENTION_DAYS)
                .takeIf { it in AppSettings.RETENTION_DAYS_RANGE }
                ?: AppSettings.DEFAULT_RETENTION_DAYS,
            readingWarningDays = preferences.getInt(KEY_WARNING_DAYS, AppSettings.DEFAULT_WARNING_DAYS)
                .takeIf { it in AppSettings.WARNING_DAYS_RANGE }
                ?: AppSettings.DEFAULT_WARNING_DAYS,
            xiaomiAutostartOn = preferences.getBoolean(KEY_XIAOMI_AUTOSTART, false),
            defaultRulesAdded = preferences.getBoolean(KEY_DEFAULT_RULES_ADDED, false),
            suggestionsEnabled = preferences.getBoolean(KEY_SUGGESTIONS_ENABLED, defaults.suggestionsEnabled),
            calendarEnabled = calendarId != null && preferences.getBoolean(KEY_CALENDAR_ENABLED, defaults.calendarEnabled),
            calendarId = calendarId,
            calendarName = preferences.getString(KEY_CALENDAR_NAME, null),
            clashCheckEnabled = preferences.getBoolean(KEY_CLASH_ENABLED, defaults.clashCheckEnabled),
            clashCalendarIds = parseIds(preferences.getString(KEY_CLASH_CALENDARS, null)),
            widgetShowTitles = preferences.getBoolean(KEY_WIDGET_TITLES, defaults.widgetShowTitles),
            lastBackupAt = preferences.getString(KEY_LAST_BACKUP, null)?.toLongOrNull()?.let(Instant::ofEpochMilli),
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
            SourceApp.entries.forEach { app ->
                val capture = settings.captureFor(app)
                putBoolean(appEnabledKey(app), capture.enabled)
                putString(appModeKey(app), capture.mode.name)
            }
            when (val pause = settings.capturePause) {
                CapturePause.Off -> remove(KEY_PAUSE)
                CapturePause.UntilResumed -> putString(KEY_PAUSE, PAUSE_UNTIL_RESUMED)
                is CapturePause.Until -> putString(KEY_PAUSE, pause.until.toEpochMilli().toString())
            }
            putInt(KEY_RETENTION_DAYS, settings.messageRetentionDays)
            putInt(KEY_WARNING_DAYS, settings.readingWarningDays)
            putBoolean(KEY_XIAOMI_AUTOSTART, settings.xiaomiAutostartOn)
            putBoolean(KEY_DEFAULT_RULES_ADDED, settings.defaultRulesAdded)
            putBoolean(KEY_SUGGESTIONS_ENABLED, settings.suggestionsEnabled)
            putBoolean(KEY_CALENDAR_ENABLED, settings.calendarEnabled)
            if (settings.calendarId == null) remove(KEY_CALENDAR_ID) else putString(KEY_CALENDAR_ID, settings.calendarId.toString())
            if (settings.calendarName == null) remove(KEY_CALENDAR_NAME) else putString(KEY_CALENDAR_NAME, settings.calendarName)
            putBoolean(KEY_CLASH_ENABLED, settings.clashCheckEnabled)
            putBoolean(KEY_WIDGET_TITLES, settings.widgetShowTitles)
            if (settings.lastBackupAt == null) remove(KEY_LAST_BACKUP) else putString(KEY_LAST_BACKUP, settings.lastBackupAt.toEpochMilli().toString())
            if (settings.clashCalendarIds.isEmpty()) remove(KEY_CLASH_CALENDARS) else putString(KEY_CLASH_CALENDARS, settings.clashCalendarIds.sorted().joinToString(","))
        }
    }

    /** "3,7" as IDs. A damaged entry is left out; none left means "every calendar". */
    private fun parseIds(text: String?): Set<Long> =
        text?.split(',')?.mapNotNull { it.toLongOrNull()?.takeIf { id -> id > 0 } }?.toSet().orEmpty()

    private fun parseDays(text: String): Set<DayOfWeek>? {
        val days = text.split(',').map { name -> DayOfWeek.entries.firstOrNull { it.name == name } }
        return if (days.isEmpty() || days.any { it == null }) null else days.filterNotNull().toSet()
    }

    private fun readAppCapture(app: SourceApp): AppCapture {
        val defaults = AppCapture()
        return AppCapture(
            enabled = preferences.getBoolean(appEnabledKey(app), defaults.enabled),
            mode = preferences.getString(appModeKey(app), null)
                ?.let { name -> CaptureMode.entries.firstOrNull { it.name == name } }
                ?: defaults.mode,
        )
    }

    /** A damaged pause stays paused: for privacy, reading must not restart by accident. */
    private fun readPause(): CapturePause {
        val stored = preferences.getString(KEY_PAUSE, null) ?: return CapturePause.Off
        if (stored == PAUSE_UNTIL_RESUMED) return CapturePause.UntilResumed
        return stored.toLongOrNull()?.let { CapturePause.Until(Instant.ofEpochMilli(it)) } ?: CapturePause.UntilResumed
    }

    private fun appEnabledKey(app: SourceApp) = "capture_${app.name}_enabled"

    private fun appModeKey(app: SourceApp) = "capture_${app.name}_mode"

    companion object {
        const val FILE_NAME = "settings"
        private const val KEY_BRIEFING_ENABLED = "briefing_enabled"
        private const val KEY_BRIEFING_TIME = "briefing_time"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_WORK_DAYS = "work_days"
        private const val KEY_DATE_ORDER = "date_order"
        private const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
        private const val KEY_PAUSE = "capture_pause"
        private const val PAUSE_UNTIL_RESUMED = "until-resumed"
        private const val KEY_RETENTION_DAYS = "message_retention_days"
        private const val KEY_WARNING_DAYS = "reading_warning_days"
        private const val KEY_XIAOMI_AUTOSTART = "xiaomi_autostart_on"
        private const val KEY_DEFAULT_RULES_ADDED = "default_rules_added"
        private const val KEY_SUGGESTIONS_ENABLED = "suggestions_enabled"
        private const val KEY_CALENDAR_ENABLED = "calendar_enabled"
        private const val KEY_CALENDAR_ID = "calendar_id"
        private const val KEY_CALENDAR_NAME = "calendar_name"
        private const val KEY_CLASH_ENABLED = "clash_check_enabled"
        private const val KEY_CLASH_CALENDARS = "clash_calendar_ids"
        private const val KEY_WIDGET_TITLES = "widget_show_titles"
        private const val KEY_LAST_BACKUP = "last_backup_at"
    }
}

package dev.maahdi.mavick

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.os.storage.StorageManager
import dev.maahdi.mavick.ai.AiStatusStore
import dev.maahdi.mavick.ai.AndroidDeviceConditions
import dev.maahdi.mavick.ai.LiteRtLmModel
import dev.maahdi.mavick.ai.ModelHost
import dev.maahdi.mavick.ai.ModelManager
import dev.maahdi.mavick.ai.ModelStore
import dev.maahdi.mavick.ai.RuleExtractor
import dev.maahdi.mavick.ai.SuggestionQueue
import dev.maahdi.mavick.ai.SuggestionWorker
import dev.maahdi.mavick.ai.eval.EvalExport
import dev.maahdi.mavick.ai.eval.EvalExportFile
import dev.maahdi.mavick.backup.BackupService
import dev.maahdi.mavick.calendar.CalendarGateway
import dev.maahdi.mavick.calendar.CalendarSync
import dev.maahdi.mavick.calendar.ClashService
import dev.maahdi.mavick.calendar.ContentResolverCalendarGateway
import dev.maahdi.mavick.capture.CaptureChores
import dev.maahdi.mavick.capture.CaptureStatusStore
import dev.maahdi.mavick.capture.MavickNotificationListener
import dev.maahdi.mavick.capture.MessageCapture
import dev.maahdi.mavick.data.MavickDatabase
import dev.maahdi.mavick.data.health.HealthEventDao
import dev.maahdi.mavick.data.message.MessageRepository
import dev.maahdi.mavick.data.rules.ExclusionRepository
import dev.maahdi.mavick.data.security.AndroidKeystoreKeyWrapper
import dev.maahdi.mavick.data.security.DatabaseKeyRepository
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.data.suggestion.SuggestionRepository
import dev.maahdi.mavick.data.task.TaskRepository
import dev.maahdi.mavick.health.StorageHealthCheck
import dev.maahdi.mavick.reminders.AlarmReminderScheduler
import dev.maahdi.mavick.reminders.DailyChores
import dev.maahdi.mavick.reminders.ReminderEngine
import dev.maahdi.mavick.reminders.ReminderScheduler
import dev.maahdi.mavick.reminders.SystemNotifier
import dev.maahdi.mavick.security.AppLock
import dev.maahdi.mavick.time.WhenParser
import dev.maahdi.mavick.widget.WidgetUpdater
import java.io.File
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wires Mavick's objects together by hand. Everything is created lazily, on first use, so
 * starting the app (or waking it for one alarm or notification) does no work it doesn't need.
 *
 * Anything that opens the encrypted database must be first used off the main thread: screens use
 * the suspend open*() functions; receivers and the listener already run in the background.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** A fresh clock each time, so a time-zone change is picked up straight away. */
    val clock: () -> Clock = { Clock.systemDefaultZone() }

    private val databaseKeyRepository by lazy {
        DatabaseKeyRepository(
            // noBackupFilesDir is never included in any backup.
            keyFile = File(appContext.noBackupFilesDir, DATABASE_KEY_FILE_NAME),
            keyWrapper = AndroidKeystoreKeyWrapper(DATABASE_KEY_ALIAS),
        )
    }

    val database: MavickDatabase by lazy {
        MavickDatabase.open(appContext, databaseKeyRepository)
    }

    val settings: SettingsRepository by lazy {
        SettingsRepository(appContext.getSharedPreferences(SettingsRepository.FILE_NAME, Context.MODE_PRIVATE))
    }

    private val reminderScheduler: ReminderScheduler by lazy { AlarmReminderScheduler(appContext) }

    val notifier: SystemNotifier by lazy { SystemNotifier(appContext, clock) }

    /** The phone's calendar, as a place to write tasks that have a time (Phase 4). */
    val calendarGateway: CalendarGateway by lazy { ContentResolverCalendarGateway(appContext) }

    val calendarSync: CalendarSync by lazy {
        CalendarSync(database.taskDao(), database.calendarLinkDao(), calendarGateway, settings, clock)
    }

    /** Looks for tasks that overlap events in the calendar (Phase 4, part 2). */
    val clashes: ClashService by lazy {
        ClashService(calendarGateway, database.calendarLinkDao(), settings, clock)
    }

    /** The home-screen widget (Phase 4, part 3). Opens the database only once a widget is on the home screen. */
    val widgets: WidgetUpdater by lazy { WidgetUpdater(appContext, { date -> tasks.briefing(date) }, settings, clock) }

    val tasks: TaskRepository by lazy {
        TaskRepository(database.taskDao(), reminderScheduler, clock, listeners = listOf(calendarSync, widgets))
    }

    val messages: MessageRepository by lazy { MessageRepository(database.messageDao()) }

    val exclusions: ExclusionRepository by lazy { ExclusionRepository(database.exclusionRuleDao(), settings, clock) }

    val healthEvents: HealthEventDao by lazy { database.healthEventDao() }

    /** Counts and times about message reading; no content. */
    val captureStatus: CaptureStatusStore by lazy {
        CaptureStatusStore(appContext.getSharedPreferences(CaptureStatusStore.FILE_NAME, Context.MODE_PRIVATE))
    }

    val capture: MessageCapture by lazy { MessageCapture(messages, exclusions, settings, captureStatus, clock) }

    val suggestions: SuggestionRepository by lazy { SuggestionRepository(database.suggestionDao(), clock) }

    /** The task repository for screens, opened off the main thread (a Keystore operation). */
    suspend fun openTasks(): TaskRepository = offMain { tasks }

    suspend fun openMessages(): MessageRepository = offMain { messages }

    suspend fun openExclusions(): ExclusionRepository = offMain { exclusions }

    suspend fun openSuggestions(): SuggestionRepository = offMain { suggestions }

    /** The calendar sync for screens, opened off the main thread (it needs the database). */
    suspend fun openCalendarSync(): CalendarSync = offMain { calendarSync }

    /** Encrypted backups and restores (Phase 5). */
    val backups: BackupService by lazy {
        val versionName = appContext.packageManager.getPackageInfo(appContext.packageName, PackageManager.PackageInfoFlags.of(0)).versionName
        BackupService(tasks, exclusions, settings, clock, versionName.orEmpty())
    }

    suspend fun openBackups(): BackupService = offMain { backups }

    /** After a restore: the settings (the briefing time, the widget's titles) may have changed. */
    suspend fun afterRestore() {
        reminderEngine.scheduleDailyAlarm()
        widgets.update()
    }

    /** The clash check for screens, opened off the main thread (it needs the database). */
    suspend fun openClashes(): ClashService = offMain { clashes }

    /** Counts and times about suggestions; no content. */
    val aiStatus: AiStatusStore by lazy {
        AiStatusStore(appContext.getSharedPreferences(AiStatusStore.FILE_NAME, Context.MODE_PRIVATE))
    }

    /** The imported AI model, where no backup reaches. */
    val modelStore: ModelStore by lazy {
        val storage = appContext.getSystemService(StorageManager::class.java)
        ModelStore(
            directory = File(appContext.noBackupFilesDir, MODEL_DIRECTORY),
            freeBytes = { storage.getAllocatableBytes(storage.getUuidForPath(appContext.noBackupFilesDir)) },
        )
    }

    /** The runtime's prepared weights: in the cache, which Android may clear when space is short. */
    private val runtimeCacheDir: File get() = File(appContext.cacheDir, MODEL_CACHE_DIRECTORY)

    val modelHost: ModelHost by lazy {
        ModelHost(
            store = modelStore,
            status = aiStatus,
            loader = { file -> LiteRtLmModel.load(file, runtimeCacheDir) },
            clock = clock,
            elapsedMillis = SystemClock::elapsedRealtime,
        )
    }

    /** The one low-priority thread all AI work runs on. */
    private val aiDispatcher by lazy { SuggestionWorker.lowPriorityThread() }

    /** Import, check and remove the model, from Settings. */
    val modelManager: ModelManager by lazy {
        ModelManager(
            store = modelStore,
            host = modelHost,
            status = aiStatus,
            runtimeCacheDir = runtimeCacheDir,
            aiDispatcher = aiDispatcher,
            wakeQueue = { suggestionWorker.wake() },
            clock = clock,
            elapsedMillis = SystemClock::elapsedRealtime,
        )
    }

    private val suggestionQueue by lazy {
        SuggestionQueue(
            messages = messages,
            suggestions = suggestions,
            rules = exclusions,
            settings = settings,
            modelHost = modelHost,
            ruleExtractor = RuleExtractor(::whenParser),
            conditions = AndroidDeviceConditions(appContext),
            status = aiStatus,
            whenParser = ::whenParser,
            clock = clock,
        )
    }

    /** Looks for tasks in new messages, on a low-priority background thread (Phase 3). */
    val suggestionWorker: SuggestionWorker by lazy {
        SuggestionWorker(
            // Lambdas, not references: the queue (and the database it opens) is made on the AI
            // thread at its first run, never on the main thread that wakes it.
            process = { suggestionQueue.processPending() },
            unload = { modelHost.unload() },
            onSaved = { showSuggestionsNotification() },
            dispatcher = aiDispatcher,
        )
    }

    /** Your messages as a file for the accuracy check, on request only (Settings › Suggestions). */
    suspend fun exportForAccuracyCheck(): EvalExportFile = offMain { EvalExport(messages, exclusions, settings, clock) }.export()

    /** Updates the suggestions notification to what is waiting now. */
    suspend fun showSuggestionsNotification() {
        notifier.showSuggestions(suggestions.countNew(), suggestions.newTitles())
    }

    /**
     * After messages were deleted (and their suggestions with them): removes the suggestions
     * notification once nothing waits. A notification still true is left alone, not posted again.
     */
    suspend fun clearSuggestionsNotificationIfNone() {
        if (suggestions.countNew() == 0) notifier.cancelSuggestions()
    }

    /** Suggestions waiting, for the briefing; none while suggestions are switched off. */
    private suspend fun suggestionsForBriefing(): Int = if (settings.current.suggestionsEnabled) suggestions.countNew() else 0

    private val captureChores by lazy {
        CaptureChores(messages, healthEvents, settings, captureStatus, ::hasNotificationAccess, notifier::showReadingWarning, clock)
    }

    /**
     * The daily alarm's chores: message reading's, then the calendar and the widget, then another
     * chance for messages left waiting.
     */
    private val dailyChores = object : DailyChores {
        override suspend fun cleanUp() {
            captureChores.cleanUp()
            // Old messages went, and their suggestions with them.
            clearSuggestionsNotificationIfNone()
            // Runs after a restart or a time-zone change too, which is when events need rewriting.
            calendarSync.reconcileAll()
            // A new day, a new time zone or a new clock: the widget shows today's tasks again.
            widgets.update()
        }

        override suspend fun daily() {
            captureChores.daily()
            // Messages left waiting (paused for the battery, say) get another chance each morning.
            suggestionWorker.wake()
        }
    }

    val reminderEngine: ReminderEngine by lazy {
        ReminderEngine(
            tasks,
            notifier,
            reminderScheduler,
            settings,
            clock,
            dailyChores,
            countSuggestions = ::suggestionsForBriefing,
            findClashes = clashes::clashesFor,
        )
    }

    val appLock: AppLock by lazy {
        AppLock(isEnabled = { settings.current.appLockEnabled }, elapsedRealtime = SystemClock::elapsedRealtime)
    }

    val storageHealthCheck: StorageHealthCheck by lazy {
        StorageHealthCheck(countTasks = { database.taskDao().countActive() })
    }

    /** Mavick's notification listener, as Android's settings name it. */
    val listenerComponent: ComponentName = ComponentName(appContext, MavickNotificationListener::class.java)

    /** Whether the user granted "Notification access" (needed to read messages). */
    fun hasNotificationAccess(): Boolean =
        appContext.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(listenerComponent)

    /** A quick-add parser using the current work days and date format. */
    fun whenParser(): WhenParser = settings.current.let { WhenParser(it.dateOrder, it.workDays) }

    private suspend fun <T> offMain(get: () -> T): T = withContext(Dispatchers.IO) { get() }

    private companion object {
        const val DATABASE_KEY_FILE_NAME = "database.key"
        const val DATABASE_KEY_ALIAS = "mavick.database.key-wrapper"
        const val MODEL_DIRECTORY = "models"
        const val MODEL_CACHE_DIRECTORY = "litertlm"
    }
}

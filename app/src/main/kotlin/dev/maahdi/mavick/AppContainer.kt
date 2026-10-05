package dev.maahdi.mavick

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
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
import dev.maahdi.mavick.data.task.TaskRepository
import dev.maahdi.mavick.health.StorageHealthCheck
import dev.maahdi.mavick.reminders.AlarmReminderScheduler
import dev.maahdi.mavick.reminders.ReminderEngine
import dev.maahdi.mavick.reminders.ReminderScheduler
import dev.maahdi.mavick.reminders.SystemNotifier
import dev.maahdi.mavick.security.AppLock
import dev.maahdi.mavick.time.WhenParser
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

    val tasks: TaskRepository by lazy { TaskRepository(database.taskDao(), reminderScheduler, clock) }

    val messages: MessageRepository by lazy { MessageRepository(database.messageDao()) }

    val exclusions: ExclusionRepository by lazy { ExclusionRepository(database.exclusionRuleDao(), settings, clock) }

    val healthEvents: HealthEventDao by lazy { database.healthEventDao() }

    /** Counts and times about message reading; no content. */
    val captureStatus: CaptureStatusStore by lazy {
        CaptureStatusStore(appContext.getSharedPreferences(CaptureStatusStore.FILE_NAME, Context.MODE_PRIVATE))
    }

    val capture: MessageCapture by lazy { MessageCapture(messages, exclusions, settings, captureStatus, clock) }

    /** The task repository for screens, opened off the main thread (a Keystore operation). */
    suspend fun openTasks(): TaskRepository = offMain { tasks }

    suspend fun openMessages(): MessageRepository = offMain { messages }

    suspend fun openExclusions(): ExclusionRepository = offMain { exclusions }

    private val captureChores by lazy {
        CaptureChores(messages, healthEvents, settings, captureStatus, ::hasNotificationAccess, notifier::showReadingWarning, clock)
    }

    val reminderEngine: ReminderEngine by lazy {
        ReminderEngine(tasks, notifier, reminderScheduler, settings, clock, captureChores)
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
    }
}

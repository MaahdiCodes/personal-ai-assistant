package dev.maahdi.mavick

import android.content.Context
import android.os.SystemClock
import dev.maahdi.mavick.data.MavickDatabase
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
 * starting the app (or waking it for one alarm) does no work it doesn't need.
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

    /**
     * The task repository for screens. The first call opens the encrypted database (a Keystore
     * operation), so it is done off the main thread to keep the screen smooth.
     */
    suspend fun openTasks(): TaskRepository = withContext(Dispatchers.IO) { tasks }

    val reminderEngine: ReminderEngine by lazy {
        ReminderEngine(tasks, notifier, reminderScheduler, settings, clock)
    }

    val appLock: AppLock by lazy {
        AppLock(isEnabled = { settings.current.appLockEnabled }, elapsedRealtime = SystemClock::elapsedRealtime)
    }

    val storageHealthCheck: StorageHealthCheck by lazy {
        StorageHealthCheck(countTasks = { database.taskDao().countActive() })
    }

    /** A quick-add parser using the current work days and date format. */
    fun whenParser(): WhenParser = settings.current.let { WhenParser(it.dateOrder, it.workDays) }

    private companion object {
        const val DATABASE_KEY_FILE_NAME = "database.key"
        const val DATABASE_KEY_ALIAS = "mavick.database.key-wrapper"
    }
}

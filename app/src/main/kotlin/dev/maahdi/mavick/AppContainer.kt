package dev.maahdi.mavick

import android.content.Context
import dev.maahdi.mavick.data.MavickDatabase
import dev.maahdi.mavick.data.security.AndroidKeystoreKeyWrapper
import dev.maahdi.mavick.data.security.DatabaseKeyRepository
import dev.maahdi.mavick.health.StorageHealthCheck
import java.io.File

/**
 * Wires Mavick's objects together by hand. Everything is created lazily, on first use, so
 * starting the app does no work it doesn't need.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

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

    val storageHealthCheck: StorageHealthCheck by lazy {
        StorageHealthCheck(countTasks = { database.taskDao().countActive() })
    }

    private companion object {
        const val DATABASE_KEY_FILE_NAME = "database.key"
        const val DATABASE_KEY_ALIAS = "mavick.database.key-wrapper"
    }
}

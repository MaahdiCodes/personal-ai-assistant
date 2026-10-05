package dev.maahdi.mavick.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.maahdi.mavick.data.security.DatabaseKeyRepository
import dev.maahdi.mavick.data.task.TaskDao
import dev.maahdi.mavick.data.task.TaskEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Version history (schemas in app/schemas, migrations tested in MigrationTest):
 * 1. Phase 0: tasks.
 * 2. Phase 1: reminder time, repeat rule, completion time.
 */
@Database(
    entities = [TaskEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class MavickDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        const val FILE_NAME = "mavick.db"

        /**
         * Opens the encrypted database. Call off the main thread: the first call reads the key
         * from the Android Keystore. Keep one instance for the whole app (see AppContainer).
         */
        fun open(
            context: Context,
            keyRepository: DatabaseKeyRepository,
            fileName: String = FILE_NAME,
        ): MavickDatabase {
            System.loadLibrary("sqlcipher")
            val databaseExists = context.getDatabasePath(fileName).exists()
            val passphrase = keyRepository.getOrCreatePassphrase(databaseExists)
            return Room.databaseBuilder(context, MavickDatabase::class.java, fileName)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }
    }
}

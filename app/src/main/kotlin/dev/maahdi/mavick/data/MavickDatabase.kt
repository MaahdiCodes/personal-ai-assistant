package dev.maahdi.mavick.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.maahdi.mavick.data.calendar.CalendarLinkDao
import dev.maahdi.mavick.data.calendar.CalendarLinkEntity
import dev.maahdi.mavick.data.health.HealthEventDao
import dev.maahdi.mavick.data.health.HealthEventEntity
import dev.maahdi.mavick.data.message.MessageDao
import dev.maahdi.mavick.data.message.MessageEntity
import dev.maahdi.mavick.data.rules.ExclusionRuleDao
import dev.maahdi.mavick.data.rules.ExclusionRuleEntity
import dev.maahdi.mavick.data.security.DatabaseKeyRepository
import dev.maahdi.mavick.data.suggestion.SuggestionDao
import dev.maahdi.mavick.data.suggestion.SuggestionEntity
import dev.maahdi.mavick.data.task.TaskDao
import dev.maahdi.mavick.data.task.TaskEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Version history (schemas in app/schemas, migrations tested in MigrationTest):
 * 1. Phase 0: tasks.
 * 2. Phase 1: reminder time, repeat rule, completion time.
 * 3. Phase 2: messages read from notifications, the rules about what to read, listener health.
 * 4. Phase 3: suggestions found in messages (deleted together with their message).
 * 5. Phase 4: the calendar event written for each task (this phone only).
 */
@Database(
    entities = [
        TaskEntity::class,
        MessageEntity::class,
        ExclusionRuleEntity::class,
        HealthEventEntity::class,
        SuggestionEntity::class,
        CalendarLinkEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
    ],
)
@TypeConverters(Converters::class)
abstract class MavickDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    abstract fun messageDao(): MessageDao

    abstract fun exclusionRuleDao(): ExclusionRuleDao

    abstract fun healthEventDao(): HealthEventDao

    abstract fun suggestionDao(): SuggestionDao

    abstract fun calendarLinkDao(): CalendarLinkDao

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

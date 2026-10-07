package dev.maahdi.mavick.testing

import android.content.Context
import androidx.room.Room
import dev.maahdi.mavick.backup.BackupService
import dev.maahdi.mavick.data.MavickDatabase
import dev.maahdi.mavick.data.rules.ExclusionRepository
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.data.task.TaskRepository
import kotlinx.coroutines.Dispatchers

/** One phone's worth of Mavick: its own database, settings and clock. Two of them make "back up here, restore there". */
class TestPhone(name: String, context: Context, val clock: MutableClock) {
    val database: MavickDatabase = Room.inMemoryDatabaseBuilder(context, MavickDatabase::class.java).allowMainThreadQueries().build()
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    val settings = SettingsRepository(preferences)
    val scheduler = FakeReminderScheduler()
    val tasks = TaskRepository(database.taskDao(), scheduler, clock = { clock })
    val rules = ExclusionRepository(database.exclusionRuleDao(), settings, clock = { clock })

    /** Few PBKDF2 rounds and no thread hopping, so tests are quick and in order. */
    val service = BackupService(tasks, rules, settings, { clock }, "0.5.0", iterations = 1_000, dispatcher = Dispatchers.Unconfined)

    fun close() {
        database.close()
        preferences.edit().clear().commit()
    }
}

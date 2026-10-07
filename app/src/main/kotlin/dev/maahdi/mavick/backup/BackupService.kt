package dev.maahdi.mavick.backup

import dev.maahdi.mavick.data.rules.ExclusionRepository
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.data.task.TaskRepository
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A backup file made now: [bytes] to save, and what is in it. */
class BackupFile(val bytes: ByteArray, val tasks: Int, val rules: Int)

/** What restoring a backup would do, worked out without changing anything. */
data class RestorePreview(
    val createdAt: Instant,
    /** Tasks in the backup that are not deleted ones. */
    val tasksInBackup: Int,
    val tasksAdded: Int,
    val tasksUpdated: Int,
    /** Tasks this phone changed after the backup was made: they stay as they are. */
    val tasksKeptNewer: Int,
    val rulesAdded: Int,
    /** Tasks and rules in the file that couldn't be read. */
    val skipped: Int,
) {
    /** Whether restoring changes any task or rule (settings aside). */
    val changesAnything: Boolean get() = tasksAdded > 0 || tasksUpdated > 0 || rulesAdded > 0
}

data class RestoreReport(
    val tasksAdded: Int,
    val tasksUpdated: Int,
    val tasksKeptNewer: Int,
    val rulesAdded: Int,
    val settingsRestored: Boolean,
    val skipped: Int,
)

/**
 * Makes and restores encrypted backups (docs/PLAN.md §5.7 A). The file holds tasks (deleted ones too),
 * the rules about what Mavick reads, and the travelling settings: never messages, suggestions, the
 * AI model or anything that belongs to one phone (calendar choices, the pause of reading).
 *
 * Restoring **merges** by task ID, the version edited last wins, so restoring on a phone that
 * already has tasks, or restoring twice, never loses a newer edit. It only adds rules, never removes
 * any. Every function that takes a password wipes it after use.
 *
 * The slow work (the password's key, the encryption) runs on [dispatcher], never the main thread.
 */
class BackupService(
    private val tasks: TaskRepository,
    private val rules: ExclusionRepository,
    private val settings: SettingsRepository,
    private val clock: () -> Clock,
    private val appVersion: String,
    private val iterations: Int = BackupCrypto.DEFAULT_ITERATIONS,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /** @throws IllegalArgumentException for an empty password. */
    suspend fun create(password: CharArray): BackupFile {
        try {
            val document = BackupDocument(
                createdAt = Instant.now(clock()),
                appVersion = appVersion,
                tasks = tasks.everything(),
                rules = rules.snapshot(),
                settings = BackupSettings(settings.current),
            )
            val bytes = withContext(dispatcher) { BackupCrypto.encrypt(BackupJson.encode(document), password, iterations) }
            return BackupFile(bytes, tasks = document.tasks.count { it.deletedAt == null }, rules = document.rules.size)
        } finally {
            password.fill(WIPE)
        }
    }

    /** Remember that a backup file was saved (call once it really is). */
    fun recordBackupSaved() {
        settings.update { it.copy(lastBackupAt = Instant.now(clock())) }
    }

    /** Opens a backup file without changing anything. @throws BackupException when it can't be opened. */
    suspend fun open(file: ByteArray, password: CharArray): BackupDocument {
        try {
            return withContext(dispatcher) { BackupJson.decode(BackupCrypto.decrypt(file, password)) }
        } finally {
            password.fill(WIPE)
        }
    }

    suspend fun preview(document: BackupDocument): RestorePreview {
        val plan = tasks.previewMerge(document.tasks)
        return RestorePreview(
            createdAt = document.createdAt,
            tasksInBackup = document.tasks.count { it.deletedAt == null },
            tasksAdded = plan.added,
            tasksUpdated = plan.updated,
            tasksKeptNewer = plan.keptNewer,
            rulesAdded = rules.countMissing(document.rules),
            skipped = document.skipped,
        )
    }

    /** Merges the backup into this phone. Settings only if [restoreSettings]. */
    suspend fun restore(document: BackupDocument, restoreSettings: Boolean): RestoreReport {
        val rulesAdded = rules.importMissing(document.rules)
        // Default keywords the user had deleted stay deleted: the backup remembers they were added once.
        if (document.settings.portable.defaultRulesAdded) settings.update { it.copy(defaultRulesAdded = true) }
        val plan = tasks.merge(document.tasks)
        if (restoreSettings) settings.update { document.settings.applyTo(it) }
        return RestoreReport(plan.added, plan.updated, plan.keptNewer, rulesAdded, restoreSettings, document.skipped)
    }

    private companion object {
        const val WIPE = '\u0000'
    }
}

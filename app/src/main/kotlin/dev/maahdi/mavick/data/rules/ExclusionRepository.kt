package dev.maahdi.mavick.data.rules

import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.settings.SettingsRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The rules about what Mavick reads. The default "Never read" keywords are added once, the first
 * time rules are needed; deleting one keeps it deleted.
 */
class ExclusionRepository(
    private val dao: ExclusionRuleDao,
    private val settings: SettingsRepository,
    private val clock: () -> Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val lock = Mutex()

    fun observe(): Flow<List<ExclusionRuleEntity>> = dao.observeAll()

    /** Every rule, after adding the defaults if this is the first time. */
    suspend fun all(): List<ExclusionRuleEntity> = lock.withLock {
        addDefaultsOnce()
        dao.getAll()
    }

    /**
     * Adds a rule. Returns null, adding nothing, if [value] is blank or the same rule exists.
     */
    suspend fun add(
        type: RuleType,
        effect: RuleEffect,
        value: String,
        displayName: String,
        app: SourceApp? = null,
        accountKey: String? = null,
    ): ExclusionRuleEntity? = lock.withLock {
        addDefaultsOnce()
        insertUnlessPresent(type, effect, value, displayName, app, accountKey)
    }

    /** Every rule as it is, without adding the default ones: for a backup. */
    suspend fun snapshot(): List<ExclusionRuleEntity> = dao.getAll()

    /**
     * Adds the rules of a backup that this phone doesn't have: by ID, and not a rule that already means
     * the same (the default keywords, on a phone that has added them). Rules are only ever added here,
     * never removed or changed. Returns how many were added.
     */
    suspend fun importMissing(incoming: Collection<ExclusionRuleEntity>): Int = lock.withLock {
        val missing = missingFrom(incoming)
        missing.forEach { dao.insert(it) }
        missing.size
    }

    /** How many rules [importMissing] would add. */
    suspend fun countMissing(incoming: Collection<ExclusionRuleEntity>): Int = lock.withLock { missingFrom(incoming).size }

    private suspend fun missingFrom(incoming: Collection<ExclusionRuleEntity>): List<ExclusionRuleEntity> {
        val have = dao.getAll().toMutableList()
        val missing = mutableListOf<ExclusionRuleEntity>()
        for (rule in incoming) {
            val known = have.any { it.id == rule.id } || have.any { mine ->
                mine.type == rule.type && mine.effect == rule.effect && mine.app == rule.app &&
                    mine.accountKey == rule.accountKey && mine.value.equals(rule.value, ignoreCase = true)
            }
            if (!known) {
                missing += rule
                have += rule
            }
        }
        return missing
    }

    suspend fun remove(id: String) {
        dao.delete(id)
    }

    suspend fun markMatched(ids: Collection<String>, at: Instant) {
        if (ids.isNotEmpty()) dao.markMatched(ids, at)
    }

    private suspend fun addDefaultsOnce() {
        if (settings.current.defaultRulesAdded) return
        DEFAULT_KEYWORDS.forEach { keyword ->
            insertUnlessPresent(RuleType.KEYWORD, RuleEffect.EXCLUDE, keyword, keyword, app = null, accountKey = null)
        }
        settings.update { it.copy(defaultRulesAdded = true) }
    }

    private suspend fun insertUnlessPresent(
        type: RuleType,
        effect: RuleEffect,
        value: String,
        displayName: String,
        app: SourceApp?,
        accountKey: String?,
    ): ExclusionRuleEntity? {
        val cleanValue = value.trim()
        if (cleanValue.isEmpty()) return null
        val exists = dao.getAll().any {
            it.type == type && it.effect == effect && it.app == app && it.accountKey == accountKey &&
                it.value.equals(cleanValue, ignoreCase = true)
        }
        if (exists) return null
        val rule = ExclusionRuleEntity(
            id = newId(),
            type = type,
            effect = effect,
            value = cleanValue,
            app = app,
            accountKey = accountKey,
            displayName = displayName.trim().ifEmpty { cleanValue },
            createdAt = Instant.now(clock()),
        )
        dao.insert(rule)
        return rule
    }

    companion object {
        /** Never read messages with these words (docs/PLAN.md §5.2). Android 15+ also hides OTPs. */
        val DEFAULT_KEYWORDS = listOf("OTP", "password", "PIN", "verification code")
    }
}

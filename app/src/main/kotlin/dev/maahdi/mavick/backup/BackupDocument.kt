package dev.maahdi.mavick.backup

import dev.maahdi.mavick.capture.AppCapture
import dev.maahdi.mavick.capture.CaptureMode
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.rules.ExclusionRuleEntity
import dev.maahdi.mavick.data.rules.RuleEffect
import dev.maahdi.mavick.data.rules.RuleType
import dev.maahdi.mavick.data.settings.AppSettings
import dev.maahdi.mavick.data.task.TaskEntity
import dev.maahdi.mavick.data.task.TaskPriority
import dev.maahdi.mavick.data.task.TaskSource
import dev.maahdi.mavick.data.task.TaskStatus
import dev.maahdi.mavick.time.DateOrder
import dev.maahdi.mavick.time.RepeatRule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * The settings that travel with a backup. What belongs to one phone does not: the chosen calendar
 * and the calendars checked for clashes (their numbers mean nothing elsewhere), a pause of message
 * reading (a restore must never switch reading off or on by surprise), Android's permission prompts and
 * the Xiaomi Autostart tick. [portable] holds the travelling values; every other field stays at its default.
 */
data class BackupSettings(val portable: AppSettings = AppSettings()) {
    /** [current] with the travelling settings replaced by this backup's, and everything of this phone kept. */
    fun applyTo(current: AppSettings): AppSettings = current.copy(
        briefingEnabled = portable.briefingEnabled,
        briefingTime = portable.briefingTime,
        appLockEnabled = portable.appLockEnabled,
        workDays = portable.workDays,
        dateOrder = portable.dateOrder,
        appCapture = portable.appCapture,
        messageRetentionDays = portable.messageRetentionDays,
        readingWarningDays = portable.readingWarningDays,
        suggestionsEnabled = portable.suggestionsEnabled,
        clashCheckEnabled = portable.clashCheckEnabled,
        widgetShowTitles = portable.widgetShowTitles,
    )
}

/**
 * What a backup holds: tasks (including deleted ones, so a deletion isn't undone by a restore), the
 * rules about what Mavick reads, and the travelling settings. **Never messages**: they are short-lived
 * and the most sensitive data (docs/PLAN.md §5.7 A). [skipped] counts tasks and rules in a file that
 * couldn't be read and were left out.
 */
data class BackupDocument(
    val createdAt: Instant,
    val appVersion: String,
    val tasks: List<TaskEntity>,
    val rules: List<ExclusionRuleEntity>,
    val settings: BackupSettings,
    val skipped: Int = 0,
)

/**
 * The backup's contents as JSON text. The field names and value forms are a contract (like the
 * database's, docs/PLAN.md §0.7): old backups must always open, so fields may be added, never changed.
 * Written by hand over the JSON tree, so a damaged task is left out and counted instead of making the
 * whole backup unreadable.
 */
object BackupJson {
    const val FORMAT_VERSION = 1

    fun encode(document: BackupDocument): ByteArray {
        val json = buildJsonObject {
            put("formatVersion", FORMAT_VERSION)
            put("createdAt", document.createdAt.toEpochMilli())
            put("appVersion", document.appVersion)
            put("tasks", buildJsonArray { document.tasks.forEach { add(taskToJson(it)) } })
            put("exclusionRules", buildJsonArray { document.rules.forEach { add(ruleToJson(it)) } })
            put("settings", settingsToJson(document.settings.portable))
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    /** @throws BackupException [BackupProblem.NOT_A_BACKUP] or [BackupProblem.NEWER_FORMAT]. */
    fun decode(bytes: ByteArray): BackupDocument {
        val root = try {
            Json.parseToJsonElement(String(bytes, Charsets.UTF_8)) as? JsonObject
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } ?: throw BackupException(BackupProblem.NOT_A_BACKUP)
        val version = (root["formatVersion"] as? JsonPrimitive)?.intOrNull ?: throw BackupException(BackupProblem.NOT_A_BACKUP)
        if (version > FORMAT_VERSION) throw BackupException(BackupProblem.NEWER_FORMAT)
        if (version < 1) throw BackupException(BackupProblem.NOT_A_BACKUP)
        val taskItems = (root["tasks"] as? JsonArray) ?: throw BackupException(BackupProblem.NOT_A_BACKUP)
        val ruleItems = (root["exclusionRules"] as? JsonArray) ?: JsonArray(emptyList())
        val tasks = taskItems.map { (it as? JsonObject)?.let(::taskFromJson) }
        val rules = ruleItems.map { (it as? JsonObject)?.let(::ruleFromJson) }
        return BackupDocument(
            createdAt = (root["createdAt"] as? JsonPrimitive)?.longOrNull?.let(Instant::ofEpochMilli) ?: Instant.EPOCH,
            appVersion = (root["appVersion"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            tasks = tasks.filterNotNull(),
            rules = rules.filterNotNull(),
            settings = BackupSettings(settingsFromJson(root["settings"] as? JsonObject)),
            skipped = tasks.count { it == null } + rules.count { it == null },
        )
    }

    // --- Tasks ---

    private fun taskToJson(task: TaskEntity) = buildJsonObject {
        put("id", task.id)
        put("title", task.title)
        put("notes", task.notes)
        put("dueDate", task.dueDate?.toString())
        put("dueTime", task.dueTime?.toString())
        put("remindAt", task.remindAt?.toString())
        put("priority", task.priority.name)
        put("status", task.status.name)
        put("source", task.source.name)
        put("sourceExcerpt", task.sourceExcerpt)
        put("createdAt", task.createdAt.toEpochMilli())
        put("updatedAt", task.updatedAt.toEpochMilli())
        put("deletedAt", task.deletedAt?.toEpochMilli())
        put("reminderTime", task.reminderTime?.toString())
        put("repeatRule", task.repeatRule?.toStorageString())
        put("completedAt", task.completedAt?.toEpochMilli())
    }

    /** Null when the task can't be read: a missing or wrong field, an unknown name, a damaged date. */
    private fun taskFromJson(o: JsonObject): TaskEntity? = try {
        TaskEntity(
            id = o.required("id").also { require(it.isNotBlank()) },
            title = o.required("title").also { require(it.isNotBlank()) },
            notes = o.optional("notes"),
            dueDate = o.optional("dueDate")?.let(LocalDate::parse),
            dueTime = o.optional("dueTime")?.let(LocalTime::parse),
            remindAt = o.optional("remindAt")?.let(LocalDateTime::parse),
            priority = enumValueOf<TaskPriority>(o.required("priority")),
            status = enumValueOf<TaskStatus>(o.required("status")),
            source = enumValueOf<TaskSource>(o.required("source")),
            sourceExcerpt = o.optional("sourceExcerpt"),
            createdAt = Instant.ofEpochMilli(o.requiredLong("createdAt")),
            updatedAt = Instant.ofEpochMilli(o.requiredLong("updatedAt")),
            deletedAt = o.optionalLong("deletedAt")?.let(Instant::ofEpochMilli),
            reminderTime = o.optional("reminderTime")?.let(LocalTime::parse),
            repeatRule = o.optional("repeatRule")?.let(RepeatRule::fromStorageString),
            completedAt = o.optionalLong("completedAt")?.let(Instant::ofEpochMilli),
        )
    } catch (e: RuntimeException) {
        null
    }

    // --- Rules ---

    private fun ruleToJson(rule: ExclusionRuleEntity) = buildJsonObject {
        put("id", rule.id)
        put("type", rule.type.name)
        put("effect", rule.effect.name)
        put("value", rule.value)
        put("app", rule.app?.name)
        put("accountKey", rule.accountKey)
        put("displayName", rule.displayName)
        put("createdAt", rule.createdAt.toEpochMilli())
        put("lastMatchedAt", rule.lastMatchedAt?.toEpochMilli())
    }

    private fun ruleFromJson(o: JsonObject): ExclusionRuleEntity? = try {
        ExclusionRuleEntity(
            id = o.required("id").also { require(it.isNotBlank()) },
            type = enumValueOf<RuleType>(o.required("type")),
            effect = enumValueOf<RuleEffect>(o.required("effect")),
            value = o.required("value").also { require(it.isNotBlank()) },
            app = o.optional("app")?.let { enumValueOf<SourceApp>(it) },
            accountKey = o.optional("accountKey"),
            displayName = o.optional("displayName").orEmpty(),
            createdAt = Instant.ofEpochMilli(o.requiredLong("createdAt")),
            lastMatchedAt = o.optionalLong("lastMatchedAt")?.let(Instant::ofEpochMilli),
        )
    } catch (e: RuntimeException) {
        null
    }

    // --- Settings ---

    private fun settingsToJson(s: AppSettings) = buildJsonObject {
        put("briefingEnabled", s.briefingEnabled)
        put("briefingTime", s.briefingTime.toString())
        put("appLockEnabled", s.appLockEnabled)
        put("workDays", buildJsonArray { s.workDays.sorted().forEach { add(JsonPrimitive(it.name)) } })
        put("dateOrder", s.dateOrder.name)
        put(
            "appCapture",
            buildJsonObject {
                SourceApp.entries.forEach { app ->
                    val capture = s.captureFor(app)
                    put(
                        app.name,
                        buildJsonObject {
                            put("enabled", capture.enabled)
                            put("mode", capture.mode.name)
                        },
                    )
                }
            },
        )
        put("messageRetentionDays", s.messageRetentionDays)
        put("readingWarningDays", s.readingWarningDays)
        put("suggestionsEnabled", s.suggestionsEnabled)
        put("clashCheckEnabled", s.clashCheckEnabled)
        put("widgetShowTitles", s.widgetShowTitles)
        put("defaultRulesAdded", s.defaultRulesAdded)
    }

    /** Every setting that is missing or damaged keeps its default; the rest is read. */
    private fun settingsFromJson(o: JsonObject?): AppSettings {
        val defaults = AppSettings()
        if (o == null) return defaults
        fun bool(key: String, fallback: Boolean) = (o[key] as? JsonPrimitive)?.booleanOrNull ?: fallback
        fun int(key: String, fallback: Int) = (o[key] as? JsonPrimitive)?.intOrNull ?: fallback
        fun text(key: String) = (o[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val workDays = (o["workDays"] as? JsonArray)
            ?.map { element -> (element as? JsonPrimitive)?.contentOrNull?.let { name -> DayOfWeek.entries.firstOrNull { it.name == name } } }
            ?.takeIf { days -> days.isNotEmpty() && days.all { it != null } }
            ?.filterNotNull()?.toSet()
        val capture = (o["appCapture"] as? JsonObject)
        return defaults.copy(
            briefingEnabled = bool("briefingEnabled", defaults.briefingEnabled),
            briefingTime = text("briefingTime")?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: defaults.briefingTime,
            appLockEnabled = bool("appLockEnabled", defaults.appLockEnabled),
            workDays = workDays ?: defaults.workDays,
            dateOrder = text("dateOrder")?.let { name -> DateOrder.entries.firstOrNull { it.name == name } } ?: defaults.dateOrder,
            appCapture = SourceApp.entries.associateWith { app ->
                val one = capture?.get(app.name) as? JsonObject
                AppCapture(
                    enabled = (one?.get("enabled") as? JsonPrimitive)?.booleanOrNull ?: AppCapture().enabled,
                    mode = (one?.get("mode") as? JsonPrimitive)?.contentOrNull
                        ?.let { name -> CaptureMode.entries.firstOrNull { it.name == name } } ?: AppCapture().mode,
                )
            },
            messageRetentionDays = int("messageRetentionDays", defaults.messageRetentionDays)
                .takeIf { it in AppSettings.RETENTION_DAYS_RANGE } ?: defaults.messageRetentionDays,
            readingWarningDays = int("readingWarningDays", defaults.readingWarningDays)
                .takeIf { it in AppSettings.WARNING_DAYS_RANGE } ?: defaults.readingWarningDays,
            suggestionsEnabled = bool("suggestionsEnabled", defaults.suggestionsEnabled),
            clashCheckEnabled = bool("clashCheckEnabled", defaults.clashCheckEnabled),
            widgetShowTitles = bool("widgetShowTitles", defaults.widgetShowTitles),
            defaultRulesAdded = bool("defaultRulesAdded", defaults.defaultRulesAdded),
        )
    }

    // --- Reading fields strictly: a field that is there but the wrong kind is damage, not "empty" ---

    private fun JsonObject.required(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw IllegalArgumentException("Missing $key")

    private fun JsonObject.optional(key: String): String? = when (val value: JsonElement? = this[key]) {
        null, JsonNull -> null
        is JsonPrimitive -> value.takeIf { it.isString }?.content ?: throw IllegalArgumentException("Wrong kind: $key")
        else -> throw IllegalArgumentException("Wrong kind: $key")
    }

    private fun JsonObject.requiredLong(key: String): Long =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull ?: throw IllegalArgumentException("Missing $key")

    private fun JsonObject.optionalLong(key: String): Long? = when (val value: JsonElement? = this[key]) {
        null, JsonNull -> null
        is JsonPrimitive -> value.takeIf { !it.isString }?.longOrNull ?: throw IllegalArgumentException("Wrong kind: $key")
        else -> throw IllegalArgumentException("Wrong kind: $key")
    }
}

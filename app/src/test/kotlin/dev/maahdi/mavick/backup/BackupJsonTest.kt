package dev.maahdi.mavick.backup

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.capture.AppCapture
import dev.maahdi.mavick.capture.CaptureMode
import dev.maahdi.mavick.capture.CapturePause
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.rules.ExclusionRuleEntity
import dev.maahdi.mavick.data.rules.RuleEffect
import dev.maahdi.mavick.data.rules.RuleType
import dev.maahdi.mavick.data.settings.AppSettings
import dev.maahdi.mavick.data.task.TaskEntity
import dev.maahdi.mavick.data.task.TaskPriority
import dev.maahdi.mavick.data.task.TaskSource
import dev.maahdi.mavick.data.task.TaskStatus
import dev.maahdi.mavick.testing.TEST_NOW
import dev.maahdi.mavick.time.DateOrder
import dev.maahdi.mavick.time.RepeatRule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import org.junit.Test

class BackupJsonTest {
    private val created = Instant.parse("2026-10-07T06:30:00Z")

    private fun task(id: String = "t1", title: String = "Call the bank", configure: (TaskEntity) -> TaskEntity = { it }) = configure(
        TaskEntity(id = id, title = title, createdAt = TEST_NOW, updatedAt = TEST_NOW),
    )

    private fun rule(id: String = "r1", value: String = "PIN") = ExclusionRuleEntity(
        id = id, type = RuleType.KEYWORD, effect = RuleEffect.EXCLUDE, value = value, app = null, accountKey = null,
        displayName = value, createdAt = TEST_NOW,
    )

    private fun document(tasks: List<TaskEntity> = emptyList(), rules: List<ExclusionRuleEntity> = emptyList(), settings: AppSettings = AppSettings()) =
        BackupDocument(created, "0.5.0", tasks, rules, BackupSettings(settings))

    private fun roundTrip(document: BackupDocument) = BackupJson.decode(BackupJson.encode(document))

    private fun json(text: String) = text.toByteArray(Charsets.UTF_8)

    private fun problemOf(bytes: ByteArray): BackupProblem = (runCatching { BackupJson.decode(bytes) }.exceptionOrNull() as BackupException).problem

    private val validTask = """{"id":"a","title":"Call","priority":"NORMAL","status":"OPEN","source":"MANUAL","createdAt":1,"updatedAt":2}"""

    private fun withTasks(vararg items: String) = json("""{"formatVersion":1,"tasks":[${items.joinToString(",")}]}""")

    // --- Round trip ---

    @Test
    fun `every field of a task comes back as it was`() {
        val full = task("full", "Pay the rent") {
            it.copy(
                notes = "Use the blue folder",
                dueDate = LocalDate.of(2026, 10, 8),
                dueTime = LocalTime.of(17, 5),
                remindAt = LocalDateTime.of(2026, 10, 8, 16, 30),
                priority = TaskPriority.HIGH,
                status = TaskStatus.DONE,
                source = TaskSource.MESSAGE,
                sourceExcerpt = "can you pay the rent by Thu 5pm?",
                createdAt = Instant.ofEpochMilli(1_700_000_000_123),
                updatedAt = Instant.ofEpochMilli(1_700_000_999_456),
                deletedAt = Instant.ofEpochMilli(1_700_001_000_000),
                reminderTime = LocalTime.of(16, 30),
                repeatRule = RepeatRule.Monthly(31),
                completedAt = Instant.ofEpochMilli(1_700_000_500_000),
            )
        }

        val back = roundTrip(document(listOf(full)))

        assertThat(back.tasks).containsExactly(full)
        assertThat(back.skipped).isEqualTo(0)
    }

    @Test
    fun `a task with nothing optional comes back with nothing`() {
        val bare = task()

        assertThat(roundTrip(document(listOf(bare))).tasks).containsExactly(bare)
    }

    @Test
    fun `every kind of repeat survives`() {
        val rules = listOf(
            RepeatRule.Daily(), RepeatRule.Daily(3), RepeatRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
            RepeatRule.Weekly(setOf(DayOfWeek.FRIDAY), 2), RepeatRule.Monthly(31), RepeatRule.Yearly(Month.FEBRUARY, 29),
        )
        val tasks = rules.mapIndexed { index, rule -> task("t$index") { it.copy(repeatRule = rule) } }

        assertThat(roundTrip(document(tasks)).tasks.map { it.repeatRule }).containsExactlyElementsIn(rules).inOrder()
    }

    @Test
    fun `awkward text survives exactly`() {
        val awkward = listOf(
            "Quote \" and backslash \\ and tab\tand newline\nand \u0000 null",
            "emoji \uD83D\uDE00 bangla \u0985\u09AE\u09BF arabic \u0645\u0631\u062D\u0628\u0627 \u202E reversed",
            "x".repeat(5_000),
            "{\"formatVersion\": 99}",
        )
        val tasks = awkward.mapIndexed { index, text -> task("t$index", title = "Title $index") { it.copy(notes = text, sourceExcerpt = text) } }

        assertThat(roundTrip(document(tasks)).tasks).containsExactlyElementsIn(tasks).inOrder()
    }

    @Test
    fun `deleted tasks are in the backup, so a restore can't undo a deletion`() {
        val deleted = task("gone") { it.copy(deletedAt = TEST_NOW.plusSeconds(5), updatedAt = TEST_NOW.plusSeconds(5)) }

        assertThat(roundTrip(document(listOf(deleted))).tasks.single().deletedAt).isEqualTo(TEST_NOW.plusSeconds(5))
    }

    @Test
    fun `rules come back as they were`() {
        val rules = listOf(
            rule(),
            ExclusionRuleEntity("r2", RuleType.CHAT, RuleEffect.ALLOW, "s:family", SourceApp.WHATSAPP, "0", "Family", TEST_NOW, TEST_NOW.plusSeconds(60)),
        )

        assertThat(roundTrip(document(rules = rules)).rules).containsExactlyElementsIn(rules).inOrder()
    }

    @Test
    fun `when it was made and by which version come back`() {
        val back = roundTrip(document())

        assertThat(back.createdAt).isEqualTo(created)
        assertThat(back.appVersion).isEqualTo("0.5.0")
    }

    @Test
    fun `encoding is the same every time`() {
        val doc = document(listOf(task("a"), task("b")), listOf(rule()))

        assertThat(BackupJson.encode(doc)).isEqualTo(BackupJson.encode(doc))
    }

    // --- Settings ---

    private val changed = AppSettings(
        briefingEnabled = false,
        briefingTime = LocalTime.of(7, 15),
        appLockEnabled = false,
        workDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
        dateOrder = DateOrder.MONTH_DAY,
        appCapture = SourceApp.entries.associateWith { AppCapture(enabled = it != SourceApp.GMAIL, mode = CaptureMode.ONLY_LISTED) },
        messageRetentionDays = 30,
        readingWarningDays = 3,
        suggestionsEnabled = false,
        clashCheckEnabled = true,
        widgetShowTitles = false,
        defaultRulesAdded = true,
    )

    @Test
    fun `the travelling settings come back as they were`() {
        assertThat(roundTrip(document(settings = changed)).settings.portable).isEqualTo(changed)
    }

    @Test
    fun `settings that belong to one phone are not in the file at all`() {
        val deviceOnly = changed.copy(
            calendarEnabled = true,
            calendarId = 42,
            calendarName = "Personal (me@gmail.com)",
            clashCalendarIds = setOf(7),
            capturePause = CapturePause.UntilResumed,
            notificationPermissionRequested = true,
            xiaomiAutostartOn = true,
            lastBackupAt = TEST_NOW,
        )

        val text = String(BackupJson.encode(document(settings = deviceOnly)), Charsets.UTF_8)

        listOf("calendarId", "calendarName", "calendarEnabled", "clashCalendarIds", "capturePause", "notificationPermission", "xiaomi", "lastBackup", "me@gmail.com")
            .forEach { assertThat(text).doesNotContain(it) }
        assertThat(roundTrip(document(settings = deviceOnly)).settings.portable.calendarId).isNull()
    }

    @Test
    fun `applying a backup's settings keeps everything that belongs to this phone`() {
        val mine = AppSettings(
            calendarEnabled = true,
            calendarId = 42,
            calendarName = "Personal",
            clashCalendarIds = setOf(7),
            capturePause = CapturePause.UntilResumed,
            notificationPermissionRequested = true,
            xiaomiAutostartOn = true,
            lastBackupAt = TEST_NOW,
            defaultRulesAdded = false,
        )

        val applied = BackupSettings(changed).applyTo(mine)

        assertThat(applied.briefingTime).isEqualTo(LocalTime.of(7, 15))
        assertThat(applied.workDays).containsExactly(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
        assertThat(applied.captureFor(SourceApp.GMAIL).enabled).isFalse()
        assertThat(applied.widgetShowTitles).isFalse()
        assertThat(applied.calendarId).isEqualTo(42L)
        assertThat(applied.calendarEnabled).isTrue()
        assertThat(applied.clashCalendarIds).containsExactly(7L)
        assertThat(applied.capturePause).isEqualTo(CapturePause.UntilResumed)
        assertThat(applied.xiaomiAutostartOn).isTrue()
        assertThat(applied.lastBackupAt).isEqualTo(TEST_NOW)
        assertThat(applied.defaultRulesAdded).isFalse() // handled by the restore itself
    }

    @Test
    fun `a damaged setting keeps its default and the rest are read`() {
        val text = """{"formatVersion":1,"tasks":[],"settings":{"briefingTime":"25:99","workDays":["MONDAY","NOTADAY"],"dateOrder":"SIDEWAYS",
            "messageRetentionDays":0,"readingWarningDays":99,"briefingEnabled":false,"appCapture":{"GMAIL":{"enabled":false,"mode":"NOPE"}}}}"""

        val settings = BackupJson.decode(json(text)).settings.portable

        val defaults = AppSettings()
        assertThat(settings.briefingTime).isEqualTo(defaults.briefingTime)
        assertThat(settings.workDays).isEqualTo(defaults.workDays)
        assertThat(settings.dateOrder).isEqualTo(defaults.dateOrder)
        assertThat(settings.messageRetentionDays).isEqualTo(defaults.messageRetentionDays)
        assertThat(settings.readingWarningDays).isEqualTo(defaults.readingWarningDays)
        assertThat(settings.briefingEnabled).isFalse()
        assertThat(settings.captureFor(SourceApp.GMAIL)).isEqualTo(AppCapture(enabled = false, mode = AppCapture().mode))
    }

    @Test
    fun `a backup without settings gets the defaults`() {
        assertThat(BackupJson.decode(json("""{"formatVersion":1,"tasks":[]}""")).settings.portable).isEqualTo(AppSettings())
    }

    // --- Damage: one bad item never spoils the rest ---

    @Test
    fun `a task that cannot be read is left out and counted, the others are kept`() {
        val bad = listOf(
            """{"id":"b1","priority":"NORMAL","status":"OPEN","source":"MANUAL","createdAt":1,"updatedAt":2}""", // no title
            """{"id":"b2","title":"x","priority":"URGENT","status":"OPEN","source":"MANUAL","createdAt":1,"updatedAt":2}""", // unknown name
            """{"id":"b3","title":"x","priority":"NORMAL","status":"OPEN","source":"MANUAL","createdAt":1,"updatedAt":2,"dueDate":"not a date"}""",
            """{"id":"b4","title":"x","priority":"NORMAL","status":"OPEN","source":"MANUAL","createdAt":1,"updatedAt":2,"dueDate":20261008}""", // wrong kind
            """{"id":"b5","title":"x","priority":"NORMAL","status":"OPEN","source":"MANUAL","createdAt":"yesterday","updatedAt":2}""",
            """{"id":"b6","title":"x","priority":"NORMAL","status":"OPEN","source":"MANUAL","createdAt":1,"updatedAt":2,"repeatRule":"EVERY SO OFTEN"}""",
            """{"id":" ","title":"x","priority":"NORMAL","status":"OPEN","source":"MANUAL","createdAt":1,"updatedAt":2}""",
            """{"id":"b8","title":"  ","priority":"NORMAL","status":"OPEN","source":"MANUAL","createdAt":1,"updatedAt":2}""",
            "\"just a string\"",
            "42",
        )

        val back = BackupJson.decode(withTasks(validTask, *bad.toTypedArray()))

        assertThat(back.tasks.map { it.id }).containsExactly("a")
        assertThat(back.skipped).isEqualTo(bad.size)
    }

    @Test
    fun `a rule that cannot be read is left out and counted`() {
        val text = """{"formatVersion":1,"tasks":[],"exclusionRules":[
            {"id":"r1","type":"KEYWORD","effect":"EXCLUDE","value":"PIN","displayName":"PIN","createdAt":1},
            {"id":"r2","type":"NOPE","effect":"EXCLUDE","value":"x","createdAt":1},
            {"id":"r3","type":"KEYWORD","effect":"EXCLUDE","value":"","createdAt":1},
            {"id":"r4","type":"KEYWORD","effect":"EXCLUDE","value":"x","app":"MYSPACE","createdAt":1}]}"""

        val back = BackupJson.decode(json(text))

        assertThat(back.rules.map { it.id }).containsExactly("r1")
        assertThat(back.skipped).isEqualTo(3)
    }

    @Test
    fun `fields added by a later version of the same format are ignored`() {
        val text = """{"formatVersion":1,"somethingNew":[1,2],"tasks":[{"id":"a","title":"Call","priority":"NORMAL","status":"OPEN","source":"MANUAL",
            "createdAt":1,"updatedAt":2,"colour":"red"}]}"""

        assertThat(BackupJson.decode(json(text)).tasks.map { it.id }).containsExactly("a")
    }

    @Test
    fun `a backup with no rules still opens`() {
        assertThat(BackupJson.decode(withTasks(validTask)).rules).isEmpty()
    }

    // --- Not a backup ---

    @Test
    fun `things that are not a backup are said to be so`() {
        assertThat(problemOf(ByteArray(0))).isEqualTo(BackupProblem.NOT_A_BACKUP)
        assertThat(problemOf(json("hello"))).isEqualTo(BackupProblem.NOT_A_BACKUP)
        assertThat(problemOf(json("[1,2,3]"))).isEqualTo(BackupProblem.NOT_A_BACKUP)
        assertThat(problemOf(json("{\"tasks\":[]}"))).isEqualTo(BackupProblem.NOT_A_BACKUP) // no version
        assertThat(problemOf(json("{\"formatVersion\":1}"))).isEqualTo(BackupProblem.NOT_A_BACKUP) // no tasks
        assertThat(problemOf(json("{\"formatVersion\":\"one\",\"tasks\":[]}"))).isEqualTo(BackupProblem.NOT_A_BACKUP)
        assertThat(problemOf(json("{\"formatVersion\":0,\"tasks\":[]}"))).isEqualTo(BackupProblem.NOT_A_BACKUP)
        assertThat(problemOf(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00))).isEqualTo(BackupProblem.NOT_A_BACKUP)
    }

    @Test
    fun `a newer format is said to need a newer Mavick`() {
        assertThat(problemOf(json("{\"formatVersion\":2,\"tasks\":[]}"))).isEqualTo(BackupProblem.NEWER_FORMAT)
    }
}

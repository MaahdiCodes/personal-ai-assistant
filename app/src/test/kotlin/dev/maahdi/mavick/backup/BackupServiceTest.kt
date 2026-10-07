package dev.maahdi.mavick.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.capture.IncomingMessage
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.MavickDatabase
import dev.maahdi.mavick.data.message.MessageRepository
import dev.maahdi.mavick.data.rules.ExclusionRepository
import dev.maahdi.mavick.data.rules.RuleEffect
import dev.maahdi.mavick.data.rules.RuleType
import dev.maahdi.mavick.data.settings.AppSettings
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.data.task.TaskRepository
import dev.maahdi.mavick.testing.FakeReminderScheduler
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.MutableClock
import dev.maahdi.mavick.testing.TestPhone
import java.time.Duration
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupServiceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = MutableClock(MONDAY_10AM)
    private lateinit var old: TestPhone
    private lateinit var new: TestPhone
    private val password = "a long enough password"
    private val today = MONDAY_10AM.toLocalDate()

    @Before
    fun setUp() {
        old = TestPhone("backup-old", context, clock)
        new = TestPhone("backup-new", context, MutableClock(MONDAY_10AM.plusDays(1)))
    }

    @After
    fun tearDown() {
        old.close()
        new.close()
    }

    private suspend fun backUp(from: TestPhone = old, pw: String = password) = from.service.create(pw.toCharArray())

    private suspend fun restoreOnto(to: TestPhone, file: BackupFile, pw: String = password, withSettings: Boolean = true): RestoreReport =
        to.service.restore(to.service.open(file.bytes, pw.toCharArray()), withSettings)

    // --- Back up, restore on another phone ---

    @Test
    fun `a backup restored on a new phone brings back every task, done and deleted ones too`() = runTest {
        val open = old.tasks.create(TaskDraft(title = "Call the bank", dueDate = today, dueTime = LocalTime.of(17, 0), notes = "Ask about the loan"))
        val done = old.tasks.create(TaskDraft(title = "Pay rent"))
        old.tasks.complete(done.id)
        val gone = old.tasks.create(TaskDraft(title = "Old idea"))
        old.tasks.delete(gone.id)

        val report = restoreOnto(new, backUp())

        assertThat(report.tasksAdded).isEqualTo(2)
        assertThat(new.database.taskDao().getAll().associateBy { it.id }).isEqualTo(old.database.taskDao().getAll().associateBy { it.id })
        assertThat(new.tasks.observeOpen().first().map { it.id }).containsExactly(open.id)
        assertThat(new.database.taskDao().findById(gone.id)!!.deletedAt).isNotNull()
    }

    @Test
    fun `a task still waiting on a reminder keeps it on the new phone`() = runTest {
        val task = old.tasks.create(TaskDraft(title = "Call", dueDate = today, dueTime = LocalTime.of(17, 0), reminderTime = LocalTime.of(17, 0)))

        restoreOnto(new, backUp())

        // The new phone's clock is a day later, so the reminder is in the past there: not replayed.
        assertThat(new.database.taskDao().findById(task.id)!!.remindAt).isNull()
        assertThat(new.scheduler.alarms).isEmpty()
        assertThat(new.database.taskDao().findById(task.id)!!.reminderTime).isEqualTo(LocalTime.of(17, 0))
    }

    @Test
    fun `the rules about what Mavick reads come across, and the default ones are not doubled`() = runTest {
        old.rules.add(RuleType.CHAT, RuleEffect.EXCLUDE, "s:family", "Family", SourceApp.WHATSAPP, "0")
        old.rules.all() // adds the four default keywords
        val file = backUp()

        val report = restoreOnto(new, file)
        new.rules.all() // a new phone adds its defaults the first time it needs rules

        assertThat(report.rulesAdded).isEqualTo(5)
        val values = new.rules.snapshot().map { it.value }
        assertThat(values).containsNoDuplicates()
        assertThat(values).containsExactly("OTP", "password", "PIN", "verification code", "s:family")
    }

    @Test
    fun `a default keyword deleted on the old phone does not come back on the new one`() = runTest {
        old.rules.all()
        old.rules.remove(old.rules.snapshot().single { it.value == "PIN" }.id)

        restoreOnto(new, backUp(), withSettings = false)
        new.rules.all()

        assertThat(new.rules.snapshot().map { it.value }).doesNotContain("PIN")
    }

    // --- Settings ---

    @Test
    fun `the travelling settings are restored when asked, and the phone's own are kept`() = runTest {
        old.settings.update { it.copy(briefingTime = LocalTime.of(6, 45), widgetShowTitles = false, calendarEnabled = true, calendarId = 9, calendarName = "Old phone calendar") }
        new.settings.update { it.copy(calendarEnabled = true, calendarId = 3, calendarName = "New phone calendar", xiaomiAutostartOn = true) }

        val report = restoreOnto(new, backUp(), withSettings = true)

        assertThat(report.settingsRestored).isTrue()
        assertThat(new.settings.current.briefingTime).isEqualTo(LocalTime.of(6, 45))
        assertThat(new.settings.current.widgetShowTitles).isFalse()
        assertThat(new.settings.current.calendarId).isEqualTo(3L)
        assertThat(new.settings.current.calendarName).isEqualTo("New phone calendar")
        assertThat(new.settings.current.xiaomiAutostartOn).isTrue()
    }

    @Test
    fun `settings are left alone when not asked for`() = runTest {
        old.settings.update { it.copy(briefingTime = LocalTime.of(6, 45)) }

        val report = restoreOnto(new, backUp(), withSettings = false)

        assertThat(report.settingsRestored).isFalse()
        assertThat(new.settings.current.briefingTime).isEqualTo(AppSettings().briefingTime)
    }

    // --- What is never in a backup ---

    @Test
    fun `messages are never in a backup`() = runTest {
        val messages = MessageRepository(old.database.messageDao())
        val now = MONDAY_10AM.atZone(clock.zone).toInstant()
        messages.save(IncomingMessage(SourceApp.WHATSAPP, "0", "s:family", "Family", "Sam", "My secret message about the loan zq7", now, false, true, false), now)
        old.tasks.create(TaskDraft(title = "Call the bank"))
        val file = backUp()

        val plain = BackupCrypto.decrypt(file.bytes, password.toCharArray())

        assertThat(String(plain, Charsets.UTF_8)).doesNotContain("zq7")
        assertThat(String(plain, Charsets.UTF_8)).doesNotContain("Family")
        assertThat(new.database.messageDao().count()).isEqualTo(0)
        restoreOnto(new, file)
        assertThat(new.database.messageDao().count()).isEqualTo(0)
    }

    // --- Merging into a phone that already has tasks ---

    @Test
    fun `restoring twice changes nothing the second time`() = runTest {
        old.tasks.create(TaskDraft(title = "One"))
        old.tasks.create(TaskDraft(title = "Two"))
        val file = backUp()
        restoreOnto(new, file)

        val again = restoreOnto(new, file)

        assertThat(again.tasksAdded).isEqualTo(0)
        assertThat(again.tasksUpdated).isEqualTo(0)
        assertThat(again.rulesAdded).isEqualTo(0)
    }

    @Test
    fun `a task edited here after the backup is kept, and one edited there is replaced`() = runTest {
        val kept = old.tasks.create(TaskDraft(title = "Kept"))
        val replaced = old.tasks.create(TaskDraft(title = "Replaced"))
        val file = backUp()
        restoreOnto(new, file)
        // The backup was restored; now each side edits a different task, the new phone later.
        old.clock.advance(Duration.ofHours(1))
        old.tasks.update(replaced.id, TaskDraft(title = "Replaced, edited on the old phone"))
        val later = backUp()
        new.clock.advance(Duration.ofHours(5))
        new.tasks.update(kept.id, TaskDraft(title = "Kept, edited on the new phone"))

        val report = restoreOnto(new, later)

        assertThat(new.database.taskDao().findById(kept.id)!!.title).isEqualTo("Kept, edited on the new phone")
        assertThat(new.database.taskDao().findById(replaced.id)!!.title).isEqualTo("Replaced, edited on the old phone")
        assertThat(report.tasksKeptNewer).isEqualTo(1)
        assertThat(report.tasksUpdated).isEqualTo(1)
    }

    @Test
    fun `a task deleted after the backup was made stays deleted`() = runTest {
        val task = old.tasks.create(TaskDraft(title = "Call"))
        val file = backUp()
        restoreOnto(new, file)
        new.clock.advance(Duration.ofHours(1))
        new.tasks.delete(task.id)

        restoreOnto(new, file) // the older backup must not bring it back

        assertThat(new.tasks.observeOpen().first()).isEmpty()
    }

    @Test
    fun `a task deleted before the backup is deleted here too`() = runTest {
        val task = old.tasks.create(TaskDraft(title = "Call"))
        restoreOnto(new, backUp())
        old.clock.advance(Duration.ofHours(1))
        old.tasks.delete(task.id)

        restoreOnto(new, backUp())

        assertThat(new.tasks.observeOpen().first()).isEmpty()
    }

    // --- Preview ---

    @Test
    fun `the preview says what a restore would do, and does it to nothing`() = runTest {
        old.tasks.create(TaskDraft(title = "New to the other phone"))
        old.rules.add(RuleType.KEYWORD, RuleEffect.EXCLUDE, "salary", "salary")
        val opened = new.service.open(backUp().bytes, password.toCharArray())

        val preview = new.service.preview(opened)

        assertThat(preview.tasksInBackup).isEqualTo(1)
        assertThat(preview.tasksAdded).isEqualTo(1)
        // "salary" and the four default keywords that adding a first rule brings.
        assertThat(preview.rulesAdded).isEqualTo(5)
        assertThat(preview.changesAnything).isTrue()
        assertThat(preview.createdAt).isEqualTo(clock.instant())
        assertThat(new.database.taskDao().getAll()).isEmpty()
        assertThat(new.rules.snapshot()).isEmpty()
    }

    @Test
    fun `a backup that adds nothing says so`() = runTest {
        old.tasks.create(TaskDraft(title = "One"))
        val file = backUp()
        restoreOnto(new, file)

        val preview = new.service.preview(new.service.open(file.bytes, password.toCharArray()))

        assertThat(preview.changesAnything).isFalse()
    }

    // --- Passwords and mistakes ---

    @Test
    fun `the wrong password changes nothing`() = runTest {
        old.tasks.create(TaskDraft(title = "One"))
        val file = backUp()

        val problem = (runCatching { new.service.open(file.bytes, "not the password".toCharArray()) }.exceptionOrNull() as BackupException).problem

        assertThat(problem).isEqualTo(BackupProblem.WRONG_PASSWORD_OR_DAMAGED)
        assertThat(new.database.taskDao().getAll()).isEmpty()
    }

    @Test
    fun `a file that is not a backup is said to be one`() = runTest {
        val problem = (runCatching { new.service.open("hello".toByteArray(), password.toCharArray()) }.exceptionOrNull() as BackupException).problem

        assertThat(problem).isEqualTo(BackupProblem.NOT_A_BACKUP)
    }

    @Test
    fun `the password is wiped after use, whatever happens`() = runTest {
        val forCreate = password.toCharArray()
        val file = old.service.create(forCreate)
        assertThat(forCreate.all { it == '\u0000' }).isTrue()

        val forOpen = password.toCharArray()
        new.service.open(file.bytes, forOpen)
        assertThat(forOpen.all { it == '\u0000' }).isTrue()

        val forWrong = "wrong password!".toCharArray()
        runCatching { new.service.open(file.bytes, forWrong) }
        assertThat(forWrong.all { it == '\u0000' }).isTrue()
    }

    @Test
    fun `an empty password makes no backup, and the file is useless without the right one`() = runTest {
        val refused = runCatching { old.service.create(CharArray(0)) }.exceptionOrNull()

        assertThat(refused).isInstanceOf(IllegalArgumentException::class.java)
    }

    // --- What making a backup touches ---

    @Test
    fun `making a backup changes nothing on the phone, and remembering it is separate`() = runTest {
        old.tasks.create(TaskDraft(title = "One"))
        val settingsBefore = old.settings.current

        val file = backUp()

        assertThat(old.settings.current).isEqualTo(settingsBefore)
        assertThat(old.rules.snapshot()).isEmpty() // the default keywords were not added by reading them
        assertThat(file.tasks).isEqualTo(1)

        old.service.recordBackupSaved()
        assertThat(old.settings.current.lastBackupAt).isEqualTo(clock.instant())
    }

    @Test
    fun `when the last backup was made is not part of the next one nor restored`() = runTest {
        old.service.recordBackupSaved()
        restoreOnto(new, backUp())

        assertThat(new.settings.current.lastBackupAt).isNull()
    }

    @Test
    fun `an empty phone makes a backup that restores to an empty phone`() = runTest {
        val file = backUp()

        val report = restoreOnto(new, file)

        assertThat(file.tasks).isEqualTo(0)
        assertThat(report.tasksAdded).isEqualTo(0)
        assertThat(new.database.taskDao().getAll()).isEmpty()
    }

    @Test
    fun `the number of tasks counts the ones you can see, not the deleted ones`() = runTest {
        old.tasks.create(TaskDraft(title = "One"))
        val gone = old.tasks.create(TaskDraft(title = "Two"))
        old.tasks.delete(gone.id)

        assertThat(backUp().tasks).isEqualTo(1)
    }
}

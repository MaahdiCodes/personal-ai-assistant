package dev.maahdi.mavick.ui.settings

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.ai.AiPause
import dev.maahdi.mavick.backup.BackupProblem
import dev.maahdi.mavick.backup.RestorePreview
import dev.maahdi.mavick.backup.RestoreReport
import dev.maahdi.mavick.ai.AiStatus
import dev.maahdi.mavick.ai.ImportProblem
import dev.maahdi.mavick.ai.ModelCheck
import dev.maahdi.mavick.ai.ModelInfo
import dev.maahdi.mavick.capture.ReadingState
import dev.maahdi.mavick.data.settings.AppSettings
import dev.maahdi.mavick.health.StorageStatus
import dev.maahdi.mavick.testing.PERSONAL_CALENDAR
import dev.maahdi.mavick.testing.TEST_ZONE
import dev.maahdi.mavick.testing.WORK_CALENDAR
import java.time.Duration
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Messages, Suggestions and Health parts of Settings. */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-10-05T04:00:00Z")
    private var settings = AppSettings()
    private val calls = mutableListOf<String>()

    private fun show(
        health: HealthInfo,
        ai: AiSettingsState = AiSettingsState(),
        exportOutcome: ExportOutcome? = null,
        calendar: CalendarSettingsState = CalendarSettingsState(permissionGranted = true, loaded = true),
        backup: BackupUiState = BackupUiState(),
    ) {
        compose.setContent {
            SettingsScreen(
                settings = settings,
                health = health,
                ai = ai,
                calendar = calendar,
                backup = backup,
                backupActions = BackupActions(
                    onStartBackup = { calls += "backup start" },
                    onNewPassword = { password, confirm -> calls += "backup password $password/$confirm" },
                    onStartRestore = { calls += "restore start" },
                    onRestorePassword = { password -> calls += "restore password $password" },
                    onRestoreSettings = { on -> calls += "restore settings $on" },
                    onConfirmRestore = { calls += "restore confirm" },
                    onCancel = { calls += "backup cancel" },
                ),
                exportOutcome = exportOutcome,
                now = now,
                zone = TEST_ZONE,
                use24Hour = true,
                onChange = { change -> settings = change(settings) },
                onCalendarSwitch = { on -> calls += "calendar switch $on" },
                onChangeCalendar = { calls += "calendar change" },
                onPickCalendar = { calendar -> calls += "calendar pick ${calendar.id}" },
                onClosePicker = { calls += "calendar close" },
                onFixCalendarPermission = { calls += "calendar permission" },
                onClashSwitch = { on -> calls += "clash switch $on" },
                onChooseCheckedCalendars = { calls += "clash calendars" },
                onCheckedCalendars = { ids -> calls += "clash checked ${ids.sorted()}" },
                onCloseCheckedPicker = { calls += "clash close" },
                onFixNotifications = { calls += "notifications" },
                onFixBattery = { calls += "battery" },
                onFixNotificationAccess = { calls += "access" },
                onRestartReading = { calls += "restart" },
                onOpenAutostart = { calls += "autostart" },
                onOpenReading = { calls += "reading" },
                onDeleteAllMessages = { calls += "delete" },
                onImportModel = { calls += "import" },
                onCheckModel = { calls += "check" },
                onRemoveModel = { calls += "remove" },
                onTurnModelOnAgain = { calls += "turn on" },
                onExportMessages = { calls += "export" },
                onImportKeep = { calls += "keep" },
                onBack = {},
            )
        }
    }

    private val gemma = ModelInfo("gemma3-1b-it-int4.litertlm", 584_417_280, "ab".repeat(32), now)

    private val working = HealthInfo(
        storage = StorageStatus.Ready(0),
        notificationAccess = true,
        reading = ReadingState.OK,
        lastSeenAt = now.minus(Duration.ofMinutes(5)),
    )

    @Test
    fun `working message reading shows when the last message came`() {
        show(working)

        compose.onNodeWithText("Working · last message 5 min ago").performScrollTo().assertExists()
        compose.onNodeWithText("Alarms, and reading notifications as they arrive").performScrollTo().assertExists()
    }

    @Test
    fun `without access, Fix opens notification access and the restricted-setting hint shows`() {
        show(working.copy(notificationAccess = false, reading = ReadingState.NO_ACCESS))

        compose.onNodeWithText("Off: Mavick can't read messages").performScrollTo().assertExists()
        compose.onNodeWithText("If Android says \"Restricted setting\": App info › ⋮ › Allow restricted settings, then try again.").assertExists()
        compose.onAllNodesWithText("Fix")[0].performScrollTo().performClick()

        assertThat(calls).containsExactly("access")
    }

    @Test
    fun `reading stopped by Android offers a restart and shows how often it happened`() {
        show(working.copy(reading = ReadingState.NOT_CONNECTED, disconnectsThisWeek = 3))

        compose.onNodeWithText("Stopped by Android").performScrollTo().assertExists()
        compose.onNodeWithText("Android stopped it 3 times in the last 7 days").assertExists()
        compose.onNodeWithText("Restart").performScrollTo().performClick()

        assertThat(calls).containsExactly("restart")
    }

    @Test
    fun `quiet reading says since when`() {
        show(working.copy(reading = ReadingState.QUIET, lastSeenAt = now.minus(Duration.ofDays(2))))

        compose.onNodeWithText("Nothing received since 2 days ago").performScrollTo().assertExists()
    }

    @Test
    fun `Xiaomi phones get the Autostart check, other phones don't`() {
        show(working.copy(isXiaomi = true))

        compose.onNodeWithText("Autostart (Xiaomi)").performScrollTo().assertExists()
        compose.onNodeWithText("Open").performScrollTo().performClick()
        assertThat(calls).containsExactly("autostart")
    }

    @Test
    fun `phones other than Xiaomi don't show Autostart`() {
        show(working)

        compose.onNodeWithText("Autostart (Xiaomi)").assertDoesNotExist()
    }

    @Test
    fun `how long messages are kept can be changed`() {
        show(working)

        compose.onNodeWithText("14 days").performScrollTo().performClick()
        compose.onNodeWithText("30 days").performClick()

        assertThat(settings.messageRetentionDays).isEqualTo(30)
    }

    @Test
    fun `reading warnings can be turned off`() {
        show(working)

        compose.onNodeWithText("1 day").performScrollTo().performClick()
        compose.onNodeWithText("Never warn").performClick()

        assertThat(settings.readingWarningDays).isEqualTo(0)
    }

    @Test
    fun `deleting all messages asks first`() {
        show(working)

        compose.onNodeWithText("Delete all saved messages").performScrollTo().performClick()
        assertThat(calls).isEmpty()
        compose.onNodeWithText("Tasks you made from messages are kept.").assertExists()
        compose.onNodeWithText("Delete").performClick()

        assertThat(calls).containsExactly("delete")
    }

    @Test
    fun `what Mavick reads opens from Settings`() {
        show(working)

        compose.onNodeWithText("What Mavick reads").performScrollTo().performClick()

        assertThat(calls).containsExactly("reading")
    }

    @Test
    fun `exporting messages for an accuracy check warns first`() {
        show(working)

        compose.onNodeWithText("Export messages for an accuracy check…").performScrollTo().performClick()
        assertThat(calls).isEmpty()
        compose.onNodeWithText("The file holds up to 300 of your newest messages in plain text", substring = true).assertExists()
        compose.onNodeWithText("Choose where to save").performClick()

        assertThat(calls).containsExactly("export")
    }

    @Test
    fun `the export says how it went`() {
        show(working, exportOutcome = ExportOutcome.Saved(187))
        compose.onNodeWithText("187 messages exported.").performScrollTo().assertExists()
    }

    @Test
    fun `a failed export says so`() {
        show(working, exportOutcome = ExportOutcome.Failed)
        compose.onNodeWithText("The file couldn't be saved.").performScrollTo().assertExists()
    }

    @Test
    fun `Keep notes can be imported from a Takeout export`() {
        show(working)

        compose.onNodeWithText("Import notes from Google Takeout…").performScrollTo().performClick()

        assertThat(calls).containsExactly("keep")
    }

    @Test
    fun `suggestions can be switched off`() {
        show(working)

        compose.onNodeWithText("Suggest tasks from messages", substring = true).performScrollTo().performClick()

        assertThat(settings.suggestionsEnabled).isFalse()
    }

    @Test
    fun `without a model, simple rules are named and a model can be imported`() {
        show(working)

        compose.onNodeWithText("None. Simple rules find tasks instead: a date or time plus a to-do word.").performScrollTo().assertExists()
        compose.onNodeWithText("Check").assertDoesNotExist()
        compose.onNodeWithText("Import model").performScrollTo().performClick()

        assertThat(calls).containsExactly("import")
    }

    @Test
    fun `an imported model shows its name and size, its speed, and can be checked`() {
        show(working, AiSettingsState(model = gemma, usable = true, status = AiStatus(modelAnswers = 2, modelMillis = 12_200)))

        compose.onNodeWithText("gemma3-1b-it-int4.litertlm · 584 MB").performScrollTo().assertExists()
        compose.onNodeWithText("Answers in about 6.1 s per message").assertExists()
        compose.onNodeWithText("Check").performScrollTo().performClick()

        assertThat(calls).containsExactly("check")
    }

    @Test
    fun `removing the model asks first`() {
        show(working, AiSettingsState(model = gemma, usable = true))

        compose.onNodeWithText("Remove").performScrollTo().performClick()
        assertThat(calls).isEmpty()
        compose.onNodeWithText("This frees 584 MB. Suggestions then come from simple rules.").assertExists()
        compose.onNode(hasText("Remove") and hasAnyAncestor(isDialog())).performClick()

        assertThat(calls).containsExactly("remove")
    }

    @Test
    fun `a model switched off for stopping Mavick can be turned on again`() {
        show(working, AiSettingsState(model = gemma, usable = false, status = AiStatus(interruptions = AiStatus.MAX_INTERRUPTIONS)))

        compose.onNodeWithText("Switched off: Mavick stopped twice while it ran. Simple rules until you turn it on again.").performScrollTo().assertExists()
        compose.onNodeWithText("Check").assertDoesNotExist()
        compose.onNodeWithText("Turn on again").performScrollTo().performClick()

        assertThat(calls).containsExactly("turn on")
    }

    @Test
    fun `a model that failed says when it is tried again`() {
        val failedAt = now.minus(Duration.ofMinutes(10))
        show(working, AiSettingsState(model = gemma, usable = false, status = AiStatus(problemAt = failedAt, problem = "LiteRtLmJniException")))

        // Dhaka time: failed at 09:50, tried again from 10:50.
        compose.onNodeWithText("Couldn't run (LiteRtLmJniException). Trying again after Today · 10:50; simple rules until then.")
            .performScrollTo()
            .assertExists()
    }

    @Test
    fun `importing shows how far the copy got, and other buttons wait`() {
        show(working, AiSettingsState(model = gemma, importing = ImportProgress(copied = 146_104_320, total = 584_417_280)))

        compose.onNodeWithText("Copying the model… 25%").performScrollTo().assertExists()
        compose.onNodeWithText("Import model").assertIsNotEnabled()
        compose.onNodeWithText("Check").assertIsNotEnabled()
    }

    @Test
    fun `what happened last is said under the model`() {
        show(working, AiSettingsState(model = gemma, usable = true, outcome = ModelOutcome.Imported(gemma, ModelCheck.Worked(5_200, 1))))
        compose.onNodeWithText("Imported. It works: a test message took 5.2 s.").performScrollTo().assertExists()
    }

    @Test
    fun `a refused import says why`() {
        show(working, AiSettingsState(outcome = ModelOutcome.Rejected(ImportProblem.NOT_A_MODEL)))
        compose.onNodeWithText("That isn't a LiteRT-LM model (.litertlm).").performScrollTo().assertExists()
    }

    // --- Calendar ---

    private val calendars = listOf(PERSONAL_CALENDAR, WORK_CALENDAR)

    @Test
    fun `the calendar switch starts off and asks to be turned on`() {
        show(working)

        compose.onNodeWithText("Add tasks with a time to my calendar").performScrollTo().performClick()

        assertThat(calls).containsExactly("calendar switch true")
        compose.onNodeWithText("Choose a calendar").assertDoesNotExist()
    }

    @Test
    fun `the calendar section says what goes to Google and what switching off does`() {
        show(working)

        compose.onNodeWithText("If the calendar syncs with Google, Google receives it through the Calendar app", substring = true)
            .performScrollTo()
            .assertExists()
        compose.onNodeWithText("Switching this off removes the events Mavick added.", substring = true).assertExists()
    }

    @Test
    fun `with the feature on the chosen calendar and the number of events show`() {
        settings = settings.copy(calendarEnabled = true, calendarId = 1, calendarName = "Personal (me@example.com)")
        show(working, calendar = CalendarSettingsState(permissionGranted = true, calendars = calendars, loaded = true, eventCount = 3))

        compose.onNodeWithText("Personal (me@example.com)").performScrollTo().assertExists()
        compose.onNodeWithText("3 tasks are in the calendar").performScrollTo().assertExists()
        compose.onNodeWithText("The calendar permission is off", substring = true).assertDoesNotExist()
        compose.onNodeWithText("The calendar you chose is not on this phone any more. Choose another.").assertDoesNotExist()
    }

    @Test
    fun `one event is counted in the singular`() {
        show(working, calendar = CalendarSettingsState(permissionGranted = true, loaded = true, eventCount = 1))

        compose.onNodeWithText("1 task is in the calendar").performScrollTo().assertExists()
    }

    @Test
    fun `the switch turns the feature off and Change opens the picker`() {
        settings = settings.copy(calendarEnabled = true, calendarId = 1, calendarName = "Personal")
        show(working, calendar = CalendarSettingsState(permissionGranted = true, calendars = calendars, loaded = true))

        compose.onNodeWithText("Change").performScrollTo().performClick()
        compose.onNodeWithText("Add tasks with a time to my calendar").performScrollTo().performClick()

        assertThat(calls).containsExactly("calendar change", "calendar switch false").inOrder()
    }

    @Test
    fun `a lost permission is said and Fix opens Android's settings`() {
        settings = settings.copy(calendarEnabled = true, calendarId = 1, calendarName = "Personal")
        show(working, calendar = CalendarSettingsState(permissionGranted = false, loaded = true, eventCount = 2))

        compose.onNodeWithText("The calendar permission is off", substring = true).performScrollTo().assertExists()
        compose.onAllNodesWithText("Fix")[0].performScrollTo().performClick()

        assertThat(calls).containsExactly("calendar permission")
    }

    @Test
    fun `a refused permission is said even though the feature is still off`() {
        show(working, calendar = CalendarSettingsState(permissionGranted = false, permissionDenied = true))

        compose.onNodeWithText("The calendar permission is off", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun `events left behind without the permission are said even with the feature off`() {
        show(working, calendar = CalendarSettingsState(permissionGranted = false, loaded = true, eventCount = 2))

        compose.onNodeWithText("The calendar permission is off", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun `with no permission asked for and no events nothing is said about it`() {
        show(working, calendar = CalendarSettingsState(permissionGranted = false, loaded = true))

        compose.onNodeWithText("The calendar permission is off", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a chosen calendar that is gone is said, and Change lets you pick another`() {
        settings = settings.copy(calendarEnabled = true, calendarId = 9, calendarName = "Old calendar")
        show(working, calendar = CalendarSettingsState(permissionGranted = true, calendars = calendars, loaded = true))

        compose.onNodeWithText("The calendar you chose is not on this phone any more. Choose another.").performScrollTo().assertExists()
        compose.onAllNodesWithText("Change")[1].performScrollTo().performClick()

        assertThat(calls).containsExactly("calendar change")
    }

    @Test
    fun `the calendar is not called gone before the list has loaded`() {
        settings = settings.copy(calendarEnabled = true, calendarId = 9, calendarName = "Old calendar")
        show(working, calendar = CalendarSettingsState(permissionGranted = true, loaded = false))

        compose.onNodeWithText("The calendar you chose is not on this phone any more. Choose another.").assertDoesNotExist()
    }

    @Test
    fun `the picker lists the calendars with their accounts and picking one says which`() {
        settings = settings.copy(calendarEnabled = true, calendarId = 1, calendarName = "Personal")
        show(working, calendar = CalendarSettingsState(permissionGranted = true, calendars = calendars, loaded = true, picking = true))

        compose.onNodeWithText("Choose a calendar").assertExists()
        compose.onNodeWithText("me@example.com").assertExists()
        compose.onNodeWithText("Work").performClick()

        assertThat(calls).containsExactly("calendar pick 2")
    }

    @Test
    fun `the picker says when no calendar can take events`() {
        show(working, calendar = CalendarSettingsState(permissionGranted = true, loaded = true, picking = true))

        compose.onNodeWithText("No calendar can take new events.", substring = true).assertExists()
    }

    @Test
    fun `the picker says it is looking while the list loads`() {
        show(working, calendar = CalendarSettingsState(permissionGranted = true, loaded = false, picking = true))

        compose.onNodeWithText("Looking for calendars…").assertExists()
    }

    @Test
    fun `cancelling the picker closes it`() {
        show(working, calendar = CalendarSettingsState(permissionGranted = true, calendars = calendars, loaded = true, picking = true))

        compose.onNodeWithText("Cancel").performClick()

        assertThat(calls).containsExactly("calendar close")
    }

    @Test
    fun `while events are written the screen says so`() {
        show(working, calendar = CalendarSettingsState(permissionGranted = true, loaded = true, working = true, eventCount = 4))

        compose.onNodeWithText("Updating the calendar…").performScrollTo().assertExists()
        compose.onNodeWithText("4 tasks are in the calendar").assertDoesNotExist()
    }

    // --- Backup ---

    private fun typeInto(field: Int, text: String) {
        compose.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[field].performTextInput(text)
    }

    private val preview = RestorePreview(
        createdAt = now.minus(Duration.ofDays(2)),
        tasksInBackup = 12,
        tasksAdded = 3,
        tasksUpdated = 2,
        tasksKeptNewer = 1,
        rulesAdded = 4,
        skipped = 0,
    )

    @Test
    fun `with no backup made the section says so and offers both buttons`() {
        show(working)

        compose.onNodeWithText("No backup made yet.").performScrollTo().assertExists()
        compose.onNodeWithText("Never your messages.", substring = true).assertExists()
        compose.onNodeWithText("Back up now…").performScrollTo().performClick()
        compose.onNodeWithText("Restore from a backup…").performScrollTo().performClick()

        assertThat(calls).containsExactly("backup start", "restore start").inOrder()
    }

    @Test
    fun `the section says when the last backup was made`() {
        settings = settings.copy(lastBackupAt = now.minus(Duration.ofHours(5)))
        show(working)

        compose.onNodeWithText("Last backup: 5 h ago").performScrollTo().assertExists()
    }

    @Test
    fun `the buttons wait while a backup or restore is under way`() {
        show(working, backup = BackupUiState(BackupStep.Working))

        compose.onNodeWithText("Back up now…").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Restore from a backup…").assertIsNotEnabled()
        compose.onNodeWithText("One moment…").assertExists()
    }

    @Test
    fun `a new password is asked twice and passed on as typed`() {
        show(working, backup = BackupUiState(BackupStep.NewPassword))

        compose.onNodeWithText("Choose a password").assertExists()
        compose.onNodeWithText("Write it down somewhere safe", substring = true).assertExists()
        typeInto(0, "correct horse")
        typeInto(1, "correct horse")
        compose.onNodeWithText("Make backup").performClick()

        assertThat(calls).containsExactly("backup password correct horse/correct horse")
    }

    @Test
    fun `the password's mistakes are said under the fields`() {
        show(working, backup = BackupUiState(BackupStep.NewPassword, PasswordProblem.TOO_SHORT))
        compose.onNodeWithText("Use at least 8 characters.").assertExists()
    }

    @Test
    fun `two passwords that differ are said`() {
        show(working, backup = BackupUiState(BackupStep.NewPassword, PasswordProblem.MISMATCH))
        compose.onNodeWithText("The two passwords differ.").assertExists()
    }

    @Test
    fun `typed passwords are hidden`() {
        show(working, backup = BackupUiState(BackupStep.NewPassword))

        typeInto(0, "secret-word")

        // What is shown is dots; the typed text itself is not displayed.
        val shown = compose.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0]
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
        assertThat(shown).isNotEmpty()
        assertThat(shown).doesNotContain("secret")
    }

    @Test
    fun `cancelling the password closes the dialog without making anything`() {
        show(working, backup = BackupUiState(BackupStep.NewPassword))

        compose.onNodeWithText("Cancel").performClick()

        assertThat(calls).containsExactly("backup cancel")
    }

    @Test
    fun `the password of a backup being opened is asked once, and a wrong one is said`() {
        show(working, backup = BackupUiState(BackupStep.RestorePassword, PasswordProblem.WRONG))

        compose.onNodeWithText("Password of this backup").assertExists()
        compose.onNodeWithText("That password doesn't open this backup, or the file is damaged.").assertExists()
        typeInto(0, "my password")
        compose.onNodeWithText("Open").performClick()

        assertThat(calls).containsExactly("restore password my password")
    }

    @Test
    fun `the preview says what a restore would do, before anything changes`() {
        show(working, backup = BackupUiState(BackupStep.Previewing(preview)))

        compose.onNodeWithText("Restore this backup?").assertExists()
        compose.onNodeWithText("It holds 12 tasks.").assertExists()
        compose.onNodeWithText("3 new tasks will be added.", substring = true).assertExists()
        compose.onNodeWithText("2 tasks will be replaced by their newer versions.", substring = true).assertExists()
        compose.onNodeWithText("1 task you changed since stays as it is.", substring = true).assertExists()
        compose.onNodeWithText("4 reading rules will be added.", substring = true).assertExists()
        assertThat(calls).isEmpty()
    }

    @Test
    fun `a backup that adds nothing says so`() {
        val nothing = preview.copy(tasksAdded = 0, tasksUpdated = 0, tasksKeptNewer = 0, rulesAdded = 0)
        show(working, backup = BackupUiState(BackupStep.Previewing(nothing)))

        compose.onNodeWithText("This phone already has everything in it.").assertExists()
    }

    @Test
    fun `items that could not be read are counted in the preview`() {
        show(working, backup = BackupUiState(BackupStep.Previewing(preview.copy(skipped = 2))))

        compose.onNodeWithText("2 items in the file couldn't be read and are left out.").assertExists()
    }

    @Test
    fun `restoring the settings is a box, ticked at first`() {
        show(working, backup = BackupUiState(BackupStep.Previewing(preview, restoreSettings = true)))

        compose.onNodeWithText("Also restore my settings").performClick()
        compose.onNodeWithText("Restore").performClick()

        assertThat(calls).containsExactly("restore settings false", "restore confirm").inOrder()
    }

    @Test
    fun `cancelling the preview restores nothing`() {
        show(working, backup = BackupUiState(BackupStep.Previewing(preview)))

        compose.onNodeWithText("Cancel").performClick()

        assertThat(calls).containsExactly("backup cancel")
    }

    private fun finished(result: BackupResult) = BackupUiState(BackupStep.Finished(result))

    @Test
    fun `a saved backup says how many tasks it holds`() {
        show(working, backup = finished(BackupResult.Saved(3)))
        compose.onNodeWithText("Backup saved: 3 tasks.").assertExists()
        compose.onNodeWithText("OK").performClick()
        assertThat(calls).containsExactly("backup cancel")
    }

    private fun endsWith(result: BackupResult, text: String) {
        show(working, backup = finished(result))
        compose.onNodeWithText(text).assertExists()
    }

    @Test
    fun `a save that failed is said plainly`() =
        endsWith(BackupResult.SaveFailed, "The backup couldn't be saved. Nothing was changed. Try another place.")

    @Test
    fun `a file that is not a backup is said`() = endsWith(BackupResult.Problem(BackupProblem.NOT_A_BACKUP), "That isn't a Mavick backup.")

    @Test
    fun `a backup from a newer Mavick says to update`() =
        endsWith(BackupResult.Problem(BackupProblem.NEWER_FORMAT), "This backup was made by a newer Mavick. Update Mavick, then try again.")

    @Test
    fun `a file that could not be read is said`() = endsWith(BackupResult.ReadFailed, "That file couldn't be read. Nothing was changed.")

    @Test
    fun `a file that is far too big is said`() = endsWith(BackupResult.TooBig, "That file is too big to be a Mavick backup.")

    @Test
    fun `a restore that changed nothing says the phone had everything`() =
        endsWith(BackupResult.Restored(RestoreReport(0, 0, 0, 0, settingsRestored = false, skipped = 0)), "Done. This phone already had everything.")

    @Test
    fun `a restore says what it did`() = endsWith(
        BackupResult.Restored(RestoreReport(3, 1, 0, 2, settingsRestored = true, skipped = 0)),
        "Restored: 4 tasks added or updated.\n2 reading rules added.\nSettings restored.",
    )

    // --- The widget ---

    @Test
    fun `the widget's titles are shown at first, with a warning about the app lock, and can be hidden`() {
        show(working)

        compose.onNodeWithText("Show task titles on the widget").performScrollTo().assertExists()
        compose.onNodeWithText("outside the app lock", substring = true).assertExists()
        compose.onNodeWithText("Show task titles on the widget").performClick()

        assertThat(settings.widgetShowTitles).isFalse()
    }

    // --- Clash warnings ---

    @Test
    fun `the clash switch starts off and asks to be turned on`() {
        show(working)

        compose.onNodeWithText("Warn me about clashes").performScrollTo().performClick()

        assertThat(calls).containsExactly("clash switch true")
        compose.onNodeWithText("Calendars to check").assertDoesNotExist()
    }

    @Test
    fun `the clash section says what is read and where titles show`() {
        show(working)

        compose.onNodeWithText("Mavick reads your calendar on this phone", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("Event titles are shown only there.", substring = true).assertExists()
    }

    @Test
    fun `with clash warnings on every calendar is checked unless some are chosen`() {
        settings = settings.copy(clashCheckEnabled = true)
        show(working, calendar = CalendarSettingsState(permissionGranted = true, allCalendars = calendars, loaded = true))

        compose.onNodeWithText("Every calendar").performScrollTo().assertExists()
        compose.onNodeWithText("Calendars to check").performClick()

        assertThat(calls).containsExactly("clash calendars")
    }

    @Test
    fun `the number of calendars checked counts only calendars still there`() {
        settings = settings.copy(clashCheckEnabled = true, clashCalendarIds = setOf(2, 99))
        show(working, calendar = CalendarSettingsState(permissionGranted = true, allCalendars = calendars, loaded = true))

        compose.onNodeWithText("1 calendar").performScrollTo().assertExists()
    }

    @Test
    fun `when every chosen calendar is gone the section says every calendar is checked`() {
        settings = settings.copy(clashCheckEnabled = true, clashCalendarIds = setOf(99))
        show(working, calendar = CalendarSettingsState(permissionGranted = true, allCalendars = calendars, loaded = true))

        compose.onNodeWithText("Every calendar").performScrollTo().assertExists()
    }

    @Test
    fun `the picker of calendars to check lists every calendar and saves the ticked ones`() {
        show(
            working,
            calendar = CalendarSettingsState(permissionGranted = true, allCalendars = calendars, loaded = true, pickingChecked = true),
        )

        compose.onNodeWithText("Check these calendars").assertExists()
        compose.onNodeWithText("Work").performClick()
        compose.onNodeWithText("Done").performClick()

        assertThat(calls).containsExactly("clash checked [2]")
    }

    @Test
    fun `ticking nothing in the picker means every calendar`() {
        settings = settings.copy(clashCheckEnabled = true, clashCalendarIds = setOf(2))
        show(
            working,
            calendar = CalendarSettingsState(permissionGranted = true, allCalendars = calendars, loaded = true, pickingChecked = true),
        )

        compose.onNodeWithText("Work").performClick() // untick the only one
        compose.onNodeWithText("Done").performClick()

        assertThat(calls).containsExactly("clash checked []")
    }

    @Test
    fun `choosing every calendar in the picker clears the ticks`() {
        settings = settings.copy(clashCheckEnabled = true, clashCalendarIds = setOf(1, 2))
        show(
            working,
            calendar = CalendarSettingsState(permissionGranted = true, allCalendars = calendars, loaded = true, pickingChecked = true),
        )

        compose.onNodeWithText("Every calendar shown in the Calendar app").performClick()
        compose.onNodeWithText("Done").performClick()

        assertThat(calls).containsExactly("clash checked []")
    }

    @Test
    fun `cancelling the picker of calendars to check saves nothing`() {
        show(working, calendar = CalendarSettingsState(permissionGranted = true, allCalendars = calendars, loaded = true, pickingChecked = true))

        compose.onNodeWithText("Cancel").performClick()

        assertThat(calls).containsExactly("clash close")
    }

    @Test
    fun `with clash warnings on and no permission the section says so`() {
        settings = settings.copy(clashCheckEnabled = true)
        show(working, calendar = CalendarSettingsState(permissionGranted = false, loaded = true))

        compose.onNodeWithText("The calendar permission is off", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun `what the AI did is counted, with any pause`() {
        show(
            working,
            AiSettingsState(
                status = AiStatus(checked = 120, suggested = 4, lastRunAt = now.minus(Duration.ofMinutes(5)), pause = AiPause.BATTERY_LOW),
            ),
        )

        compose.onNodeWithText("120 messages checked · 4 suggestions · last looked 5 min ago · waiting: battery below 20%")
            .performScrollTo()
            .assertExists()
    }
}

# Mavick — Personal AI Assistant — Plan

> **Status:** Phases 0–3 are coded. **Phase 4 (calendar) is coded** (tasks to the calendar, clash warnings, widget and tile), and **the core of Phase 5 (encrypted backup and restore with merge)** is coded. 1,081 PC tests pass. Phases 0–3 run on the Pixel 7 Pro to the extent checked so far; no phase has had its full phone check yet, and Phases 4 and 5 have not run on a phone.
> **Last updated:** 2026-10-07, after building the Phase 5 backup core.
> **Next step:** keyword search ("ask my assistant"), then the Phase 6 merge core. After that everything left needs a phone. The user installs 0.5.0, runs the phone checks (PHONE_CHECKLIST.md, sections 9 and 10), imports the Gemma 3 1B model if not done, and fixes follow → accuracy check on real labelled messages (eval/README.md) → tags.

This document is the single source of truth for the project. **§0 is the hand-over for anyone, person or AI session, picking up the work.** Keep it current: update §0 and the status tables after every piece of work.

---

## 0. Start here (session hand-over)

### 0.1 Where things stand

✅ done · 🧪 code done, phone check pending · 🔨 in progress · ⬜ not started

| Phase | What it delivers | Status |
|---|---|---|
| Plan | Decisions, design, roadmap (this document) | ✅ |
| 0. Foundation | Project, encrypted storage, safety checks, scripts | 🧪 Code and tests done. Runs on the Pixel; full phone check pending. |
| 1. Tasks + reminders | Task lists, quick-add, reminders, morning briefing, app lock, Keep sharing | 🧪 Code and tests done. Runs on the Pixel; full phone check pending. |
| 2. Message capture | Reading WhatsApp / Messenger / Gmail / Keep notifications, rules about what to read, Inbox | 🧪 Code and tests done (464 PC tests in total). Not yet on a phone. WhatsApp's own account switcher is not told apart yet (§5.1). |
| 3. AI suggestions | On-device AI turning messages into suggested tasks | 🧪 Code and tests done (704 PC tests in total). Not yet on a phone: the AI runtime has never run on a device, and the accuracy targets need the user's labelled messages. |
| 4. Calendar + planning | Calendar sync, clashes, widget | 🧪 Parts 1 (tasks with a time → a calendar you choose), 2 (clash warnings) and 3 (home-screen widget, Quick Settings tile): code and tests done (947 PC tests in total), not yet on a phone. Auto-add with Undo waits for the Phase 3 accuracy numbers. |
| 5. Backups + hardening | Encrypted Google Drive backups, reliability | 🔨 Backup core 🧪 code and tests done (1,081 PC tests in total): encrypted backup file, restore with merge, Settings › Backup. Not yet on a phone. ⬜ Search, whether Drive accepts automatic weekly overwrites, battery and HyperOS hardening (need a phone). |
| 6. Combined task list | One list across both phones | ⬜ |

- **Git:** remote `https://github.com/MaahdiCodes/personal-ai-assistant` (private). `main` tracks `develop` (the user asked to merge before the phone checks): both hold Phases 0–2. New work goes on **`develop`**, then is merged into `main` and pushed; a tag `phase-N` marks each phase that passes its phone check.
- **On the phones so far (2026-10-05):** the user created the signing key and installed the release build on the **Pixel 7 Pro** (version 0.2.x). Quick-add read "10.08 AM" as 8 AM; fixed (dotted times, §5.4). The Phase 2 build (0.3.0) is not installed yet, the on-phone tests (`app/src/androidTest`) have not run, and the Poco has not been connected.
- **Phase 2 as built** (§5.1, §5.2):
  - `capture/MavickNotificationListener` reads only the five supported apps (WhatsApp, WhatsApp Business, Messenger, Gmail, Keep); every other app's notification is dropped on its first line. It only reads: a build check fails on any API that could answer, open, dismiss or snooze a notification.
  - Pipeline (`capture/MessageCapture`): noise filter → parser → rules in memory (`ExclusionEngine`) → too old? → dedup → encrypted `message` table (database version 3).
  - Screens: Inbox (with "Add as task" and "Never read this chat"), What Mavick reads (app switches, "All chats" / "Only listed chats", "Never read" and "Only read" rules, pause), Settings › Messages, and Health rows (notification access, reading status with Restart, Xiaomi Autostart).
  - "Add to Mavick" in Android's text-selection menu (approved by the user).
  - The daily alarm now runs every day even with the briefing off: it deletes messages past the retention period (14 days by default) and warns if reading stopped.
  - The debug build has a notification recorder for checking the parsers (`scripts/record-notifications.ps1`); the release build contains none of it.
- **Long messages, measured:** in Android 16's own code, a 3,000-character message keeps all 3,000 characters in the notification's data when the app builds it with AndroidX, and is cut to 1,024 when it uses Android's builder; the shade shows less either way. Mavick reads the data directly, so it gets whole messages from AndroidX apps. Cut text is marked "Cut short" (§5.1).
- **Safety audit (2026-10-05, before the first install):** Phases 0–1 can't affect any account (no internet permission, no account access, no access to other apps' data) or harm the phone (no background service, no setting changes, read-only `adb` use, uninstall removes everything). Fixed then: Fix buttons fall back to App info instead of crashing; shared styled text is accepted. Phase 2 keeps those properties: still no internet, and the only new service is the listener, which only Android can bind.
- **Phase 3 as built** (§5.3):
  - `ai/`: `Prefilter` → three earlier messages as context → `GemmaExtractor` (LiteRT-LM 0.16.1, JSON constrained to a schema, checked by `ExtractionJson`, one retry) or `RuleExtractor` when no model can be used → `WhenResolver` (WhenParser, counted from the message's time) → saved unless a near-duplicate (`TitleSimilarity`). Exclusion rules are re-checked first (`ExclusionEngine.stillAllows`), for the context too.
  - Runtime: `SuggestionWorker` (one background-priority thread, 2 CPU threads; woken by saved messages, app start and the daily alarm; no new service, job, alarm, wake lock or permission). `ModelHost` loads the model on use and closes it 1 minute after the work; a failure rests the model for 1 hour; a message is marked FAILED before the model reads it; 2 interrupted model runs in a row switch the model off. Pauses on low battery, Battery Saver or a warm phone.
  - Screens: Suggested tasks (Add, Edit, Ignore, Never read this chat, Undo), a banner on the task list, a quiet Suggestions notification, the briefing count, Settings › Suggestions (switch, Import model / Check / Remove / Turn on again, counts, export for the accuracy check), Settings › Google Keep (Takeout import with a choose-the-notes screen).
  - Database version 4: `suggestion` table, deleted with its message (foreign-key cascade).
  - Accuracy check: `ai/eval` (CSV export, labelled-file reader, runner, scorer), `ExtractionEvalRun` (on the phone), `scripts/eval.ps1`, `scripts/push-model.ps1`, `eval/README.md`, `eval/sample.csv` (made-up messages).
  - Release APK 25.6 MB: the budget was raised from 8 to 30 MB on purpose, for the AI runtime's 21.5 MB of native code (§10). R8 keep rules cover its JNI classes; kotlin-reflect is excluded. Whether R8 left the runtime working can only be seen on a phone: import the model in the release app and press Check.
  - The Part 1 commit message says 645 tests; it was 642 at that point.
- **Phase 4 part 1 as built** (§5.9):
  - Settings › Calendar: the switch asks for `READ_CALENDAR` and `WRITE_CALENDAR` (the only new permissions), then you pick one of the phone's calendars. Tasks with a date **and** a time become 30-minute events (private, free, no alarm, title and time only), kept in step with the task (`calendar/CalendarSync`): rewritten on edits and time-zone changes, removed when the task is done, deleted or loses its time, or the switch goes off. Off by default.
  - `TaskRepository` tells `TaskCalendar` after each change (outside its lock; a calendar problem never fails a change). `CalendarSync.reconcileAll()` runs inside `DailyChores.cleanUp()`, so app start, restart, time-zone change and the daily alarm tidy up. No new service, job, alarm or permission beyond the two.
  - Database version 5: `calendar_link` (task → event on this phone), no foreign key.
  - **If the chosen calendar syncs with Google, the Calendar app uploads the events** (§5.6). Settings says so.
  - On-phone test `CalendarGatewayDeviceTest` (a calendar of its own in an "On device" account) is written and compiles; it has **not been run** (no phone was connected).
- **Phase 4 part 2 as built** (§5.9): **Settings › Calendar › Warn me about clashes** (off by default; asks for the same calendar permission; **Calendars to check** narrows it, none ticked means every calendar shown in the Calendar app). With it on, Mavick reads the calendar's events (`CalendarContract.Instances`, read-only) and a timed task that overlaps a busy, timed event gets:
  - a line in the **morning briefing** ("Clash: 17:00 Call the bank, with Dentist"), first in the list, counted in its summary;
  - a red warning line on its **row** in the task lists ("Clashes with Dentist at 17:00");
  - a warning in the **editor**, live as the date or time changes.

  Free, declined, cancelled and all-day events never count, nor do Mavick's own events or other tasks. One calendar read serves all tasks (60-day window, from today); any calendar trouble means "no clashes", never an error. The task list shows at once and the warnings follow. No new permission, service or alarm. `CalendarGatewayDeviceTest` has three more on-phone tests, also not yet run.
- **Phase 4 part 3 as built** (§5.9):
  - **Home-screen widget** ("Mavick: today", add it from the launcher's widget list): the date, "2 tasks due today · 1 overdue", up to five lines (overdue first: "Overdue: Pay rent", "17:00  Call the bank"), "+N more", and a **+** that opens a new task. A line opens that task and the heading opens Mavick, both behind the app lock. It shows **titles by default; Settings › Home screen › Show task titles on the widget** hides them (only the counts remain), because a widget sits outside the app lock.
  - It is plain `RemoteViews` with fixed lines (no list service) and `updatePeriodMillis = 0`: it is redrawn **only** when a task changes, when the titles switch changes, and on the existing wake-ups (app start, restart, time or time-zone change, the daily alarm). Between midnight and the next of those it can show yesterday: its date line makes that visible. With no widget on the home screen nothing is read and no database is opened. If the tasks can't be read (the phone has not been unlocked since a restart), it says "Open Mavick to see your tasks".
  - **Quick Settings tile "New task"** (`NewTaskTileService`): Mavick's **second service**, allowed on purpose in `allowedServices` (protected by `BIND_QUICK_SETTINGS_TILE`). Android binds it only while the quick-settings panel shows. **You add it yourself:** pull down the quick settings, pencil/edit, drag "New task" in. Tapping opens a new task (on a locked phone Android asks to unlock first).
  - `TaskRepository` now takes a list of `TaskChangeListener`s (the calendar and the widget), told after each saved change, each guarded so one failing never stops another or undoes the change.
- **Phase 5 backup core as built** (§5.7 A):
  - **Settings › Backup › Back up now…**: you choose a password (at least 8 characters, typed twice), Mavick makes an encrypted file (`mavick-backup-<date>.mavickbackup`) and Android's *Save to…* screen lets you put it in Google Drive (or anywhere). Mavick never touches the network; Google only ever holds an encrypted file. **Restore from a backup…**: pick the file, type its password, and see **what a restore would do before anything changes** (new tasks, tasks replaced by their newer version, tasks you changed since that stay, reading rules to add), with a box to also restore settings.
  - **Contents:** tasks (finished and **deleted ones too**, so a restore can't undo a deletion), the rules about what Mavick reads, and the settings that travel (briefing, app lock, work days, date order, per-app reading, retention, suggestions, clash warnings, widget titles). **Never messages**, suggestions, the AI model, or anything that belongs to one phone (the chosen calendar and calendars checked, the reading pause, permission prompts, the Autostart tick, when the last backup was made).
  - **Encryption:** AES-256-GCM, key from the password by PBKDF2-HMAC-SHA256 with 600,000 rounds (stored in the file); salt and nonce random per backup; the whole header is authenticated. A wrong password and a damaged file look the same, by design. Mavick never keeps the password, and wipes the characters it was given after use.
  - **Merge (`TaskMerge`):** by task ID, the version edited last wins; at the same moment a deletion beats an edit; then the content decides, so the answer is the same whichever phone is "local". A winning version is stored with its own edit time, so restoring twice changes nothing, and a reminder already in the past is not replayed. Rules are only ever added (by id, and not a rule that means the same). A default keyword you deleted stays deleted. This is the merge code Phase 6 will reuse.
  - Alarms, the calendar and the widget follow through the same listeners as any task change. Restoring settings reschedules the daily alarm and redraws the widget.
  - Not built (needs a phone): whether Drive accepts automatic weekly overwrites, so there is no automatic backup and no weekly reminder yet; "Last backup: N days ago" shows in Settings.
- **Versions:** app `versionCode 5`, `versionName 0.5.0`; database version 5.
- **First phone test (Pixel 7 Pro, 2026-10-06):** the Gemma 3 1B model copied with `push-model.ps1` (SHA-256 matched) and imported in Settings. Two test messages each made a suggestion. Found: a suggestion's title copied the message's wording and ran on ("…is mentioned"), and the time in the title didn't match the sent text, so check the due time on the card. Not yet diagnosed; the accuracy check (eval/README.md) is the place to measure it. Do not change the prompt until the user has checked the due times.

### 0.2 Waiting on the user

1. **Install the current build (0.5.0)** on the Pixel: `.\scripts\install.ps1 -Phone pixel`. Then Settings › Health › Notification access › **Fix**. If Android says "Restricted setting": App info › ⋮ › *Allow restricted settings*, then try again.
2. **Recorder session** (the planned first step of Phase 2, now possible): install the debug app with `.\scripts\install.ps1 -DebugBuild -Phone pixel` and give **Mavick Debug** notification access too. Run `.\scripts\record-notifications.ps1 -Phone pixel -Start`. Send **fake** test messages from the other phone to every WhatsApp account, Messenger and Gmail, including long ones (about 300, 1,500 and 5,000 characters), then run `-Stop` and give Claude the `recordings` folder. This confirms the parsers and shows where WhatsApp names the account.
3. **Open question 1 (§10):** how the several WhatsApp accounts are set up on each phone (WhatsApp's own "Add account", WhatsApp Business, or a clone such as Xiaomi "Dual apps").
4. **Phone checks** on **both** phones: Phases 0–4 ([PHONE_CHECKLIST.md](PHONE_CHECKLIST.md); section 9 is Phase 3, section 10 is Phase 4 part 1, the calendar); share the filled-in results tables. For the calendar, use a throwaway test calendar first, and run `.\scripts\test.ps1 -OnPhone -Phone pixel` (it includes `CalendarGatewayDeviceTest`, which has never run).
5. **AI model:** download `gemma3-1b-it-int4.litertlm` from https://huggingface.co/litert-community/Gemma3-1B-IT (accept the Gemma terms), then `.\scripts\push-model.ps1` (checklist §9).
6. **Accuracy check:** export, label 100–200 messages, run `.\scripts\eval.ps1` ([eval/README.md](../eval/README.md)); share only `report.txt`.
7. **Signing key backup:** the key exists (`keystore.properties`, git-ignored); make sure the `.p12` file is in Google Drive and on a USB drive, and the password is stored elsewhere (§5.7 B).

### 0.3 Next actions, in order

1. **Recordings in:** check the parsers against them (Robolectric tests that load each recording). Add WhatsApp account detection from the field the recordings show, adjust the noise texts, and confirm where long messages are cut. Turn chosen recordings into committed fixtures with fake content only and phone numbers replaced (shortcut IDs contain them).
2. **Phone-check results:** record them in §7 and the `usage.ps1` numbers in §5.8. Fix anything that failed. The Poco (HyperOS) is the most likely to stop the listener or delay alarms (§6).
3. **Tags:** `phase-1`, then `phase-2`, on `main`, and push them.
4. **Phase 3 on the phone:** fix what the checks find. First suspects: R8 in the release build, test apps reading `/data/local/tmp`, constrained JSON with Gemma 3 1B. Tune the prompt and prefilter with the accuracy report until precision ≥ 85% and recall ≥ 70%, then tag `phase-3`. (Phase 4 was started before this, at the user's request, §10; auto-add stays out until these numbers are good.)
5. ~~Phase 4 part 3: widget and Quick Settings tile~~ (done 2026-10-07; §5.9). Phone-check results for part 1 may change the design of these: look at them first.
**Work that needs no phone** (the user wants these finished before the phone checks, 2026-10-07), in this order:
   1. ✅ Phase 4 part 2: clashes (§5.9), done 2026-10-07.
   2. ✅ Phase 4 part 3 (done 2026-10-07): widget and Quick Settings tile (§5.9).
   3. ✅ (done 2026-10-07) Phase 5, the PC-testable core: the encrypted backup file (AES-256-GCM, password-derived key) and restore with merge by task UUID, newest `updatedAt` wins (§5.7 A). This is also the Phase 6 merge code. Whether Drive accepts background overwrites needs a phone, so it stays out.
   4. Phase 5: "ask my assistant", keyword search over tasks and saved messages first.
   5. Phase 6, the PC-testable core: merging two phones' task lists, including both changing the same task offline.
   Needs a phone, so not in this list: recordings and parser fixes, the accuracy check and prompt tuning, battery and `usage.ps1` numbers, HyperOS reliability, the on-phone tests, tags.
7. After each step, update this document (§0 and the §7 tables), the assistant's notes (`docs/agent-memory`), and push `develop` and `main`.

### 0.4 How to resume in a new session

1. `git fetch`, `git switch develop`, `git pull`. The local folder `E:\Personal\personal-ai-assistant` is already on `develop`.
2. Read §0, then the sections for the phase being worked on.
3. Check that the baseline passes:
   - **Windows:** `.\scripts\test.ps1` (1,081 tests, Lint, permission and read-only checks).
   - **Linux or macOS:** `./gradlew :app:testDebugUnitTest :app:lintDebug :app:checkDebugPermissions :app:checkReleasePermissions :app:checkReadOnlyNotifications`. This needs JDK 17+ and an Android SDK with platform 36 and build-tools 36.1. The PowerShell scripts are Windows-only.
4. Ask the user for anything in §0.2 that is still missing before starting work that depends on it.

### 0.5 Repository map

```
app/build.gradle.kts            Android config; permission and service allow-lists, read-only check, APK size checks (end of file)
app/proguard-rules.pro          Keeps SQLCipher's JNI classes from R8
app/schemas/                    Room schema history (1.json to 5.json): commit every new version
app/src/main/AndroidManifest.xml  Permissions, receivers, the listener, share and "Add to Mavick" targets, startup trimming
app/src/main/kotlin/dev/maahdi/mavick/
  MavickApp.kt, AppContainer.kt   App start; manual dependency wiring, everything lazy
  MainActivity.kt                 The only activity: share and selected text, notification taps, app-lock prompt
  capture/                        Phase 2: listener, NotificationReader, MessageParser, Noise, ExclusionEngine,
                                  MessageCapture, CaptureStatus (counts and times), CaptureChores, ListenerRestart
  calendar/                       Phase 4: CalendarEvent and its rules, CalendarGateway (ContentResolver), CalendarSync, clashes (ClashFinder, ClashService)
  widget/                         Phase 4: the home-screen widget (plan, renderer, updater, receiver) and the Quick Settings tile
  backup/                         Phase 5: BackupCrypto (the file's protection), BackupJson (its contents), BackupService
  data/MavickDatabase.kt          Room database (version 5), opened with SQLCipher
  data/Converters.kt              java.time and RepeatRule to and from stored text and numbers
  data/security/                  Database key: Keystore wrapping, raw-key passphrase
  data/settings/                  SettingsRepository (SharedPreferences file "settings")
  data/task/                      TaskEntity, TaskDao, TaskDraft, TaskRepository (all task rules)
  data/message/, data/rules/, data/health/   Phase 2 tables: messages, reading rules, listener events
  data/suggestion/, data/calendar/           Phase 3 suggestions; Phase 4 the calendar event written for each task
  time/                           RepeatRule, WhenParser (quick-add English), DueFormatter
  reminders/                      Alarm scheduler, notifier, ReminderEngine, DailyChores, receivers, intents
  security/                       AppLock, DeviceAuthentication (fingerprint or phone PIN)
  share/                          SharedText (shared or selected text to task draft), MessageToTask
  health/                         StorageHealthCheck
  ui/                             MavickRoot, Navigation, PhoneSettings (Fix buttons), tasks/, editor/, inbox/,
                                  reading/ (What Mavick reads), settings/, lock/, components/, theme/
app/src/debug/                  "Mavick Debug": its name, and the notification recorder (never in release builds)
app/src/release/                The release build's empty recorder
app/src/test/                   PC tests (JVM and Robolectric); testing/TestDoubles.kt has MutableClock and fakes
app/src/androidTest/            On-phone tests: encryption, Keystore, real alarm to notification, capture into the encrypted database
app/src/sharedTest/             Helpers for both test sets: task() fixture, containsSequence
docs/PLAN.md                    This document
docs/PHONE_CHECKLIST.md         The manual check on each phone, with results tables
scripts/                        Windows PowerShell 5.1 scripts (README.md lists them)
gradle/libs.versions.toml       Every version; several are pinned on purpose (§0.8)
CLAUDE.md                       Short pointer to this section for AI coding sessions
```

### 0.6 Build, test, install

| Task | Windows (PowerShell, from the project folder) | Notes |
|---|---|---|
| PC tests, Lint, permission and read-only checks | `.\scripts\test.ps1` | What "green" means for every change |
| On-phone tests | `.\scripts\test.ps1 -OnPhone -Phone pixel` | Installs a temporary "Mavick Debug" app and its test app, and removes both afterwards |
| Build and install the release app | `.\scripts\install.ps1 -Phone pixel` | Needs the user's signing key (`keystore.properties`) |
| Build without installing | `.\scripts\install.ps1 -BuildOnly` | Add `-DebugBuild` for the debug app |
| Record raw notifications | `.\scripts\record-notifications.ps1 -Phone pixel -Start`, then `-Stop` | Debug app only; fake test messages only; files go to the git-ignored `recordings` folder |
| Phone resource report | `.\scripts\usage.ps1 -Phone poco` | Storage, memory, CPU, background services, jobs, alarms |
| Connected phones | `.\scripts\devices.ps1` | |
| Live log | `.\scripts\logs.ps1` | Mavick never logs task or message content |
| Plain Gradle | `.\gradlew.bat <task>` | Needs `JAVA_HOME` = Android Studio's `jbr` and `ANDROID_HOME` = the SDK. The scripts set both. |

**AI sessions:** PowerShell's `*>` redirection cuts off Kotlin compiler errors. To see the full `e:` lines, run Gradle through Bash:
`JAVA_HOME="S:/Programming/Android Studio/jbr" ANDROID_HOME="$LOCALAPPDATA/Android/Sdk" ./gradlew.bat :app:compileDebugKotlin > build.log 2>&1`

### 0.7 Rules for every change

**Definition of done**
1. Tests added or updated. The user's rule: well-tested code is non-negotiable, so err towards more tests and more edge cases.
2. `.\scripts\test.ps1` passes: all tests, Lint "No issues found", both permission checks and the read-only check.
3. The release APK is within its budget (§5.8).
4. This document is updated: §0 (state, waiting on, next actions), the §7 progress tables, and §10 for any decision.
5. Committed on `develop` with a clear message ending in the same `Co-Authored-By` line as earlier commits, then pushed, and `main` fast-forwarded and pushed.

**The user's engineering preferences**
- Flag repetition (DRY). Prefer explicit code to clever code. "Engineered enough": not hacky, not over-abstracted. Handle more edge cases, not fewer.
- Give one clear recommendation with reasons. Ask before decisions that are the user's to make.

**Privacy and phone-light rules**
- **Permissions:** never add one silently. Add it to the manifest, to `allowedPermissions` in `app/build.gradle.kts`, and to this plan (§5.6, §5.8), with the reason. The build fails otherwise. No `INTERNET` permission, ever.
- **Services:** the notification listener (Phase 2) and the Quick Settings tile (Phase 4) are the only two (`allowedServices`, which also requires each one's protecting permission). Any other service fails the build.
- **Message reading is read-only:** never answer, open, dismiss or snooze another app's notification, change Do Not Disturb, or read the screen. That would send read receipts, show you online, or lose notifications. `checkReadOnlyNotifications` fails the build on the APIs that could (§5.1). Don't name those APIs in code comments either: the check reads comments too.
- **WorkManager:** its manifest adds the `WAKE_LOCK` and `FOREGROUND_SERVICE` permissions and a service. Prefer the existing alarms: message clean-up already rides on the daily alarm (`DailyChores`). If WorkManager is really needed (perhaps the Phase 3 AI queue), update both allow-lists and §5.8 on purpose.
- **Background work:** no polling, no foreground service, no wake locks. Screens watch the database only while visible (`collectAsStateWithLifecycle` with `WhileSubscribed`).
- **Main thread:** never open the database on it. Screens use `container.openTasks()`, `openMessages()` and `openExclusions()`; receivers use `runInBackground` (in `Receivers.kt`); the listener works on its own background queue.
- **Logs:** never log message or task content. Log exception class names only. Capture status keeps counts and times only.
- **Git:** real messages never go into git. Test fixtures use fake messages sent between the two phones; raw recordings go to `/recordings/` and private AI evaluation data to `/eval/private/`, both git-ignored.
- **Builds:** the release build is the everyday app. The debug build is a separate app (`.debug`) with separate data, for development, tests and the recorder.

**Data rules**
- **Stored formats are contracts:** enum names (including `SourceApp`, `RuleType`, `RuleEffect`, `CaptureMode`, `AiState`), `RepeatRule` storage strings, ISO date and time text, epoch-millisecond instants, account keys (`"0"`, `"0/you@gmail.com"`) and conversation keys (`"s:<shortcut ID>"`, `"t:<name>"`). Never rename or change them; only add new ones.
- **Schema changes:** bump the `MavickDatabase` version, add an `AutoMigration` (or a hand-written `Migration`) and a test in `MigrationTest`, and commit the new `app/schemas/.../N.json`.
- **Task IDs and deletes:** IDs are UUIDs. Deletes are soft (`deletedAt`), and deleted tasks are purged after 30 days.
- **Dates and times:** "floating" local values (`LocalDate`, `LocalTime`, `LocalDateTime`), converted to an instant only when setting an alarm. Messages store instants. Always use the injected `clock: () -> Clock`, which gives a fresh clock on each call, so time-zone changes apply at once.

**Code style**
- **Structure:** Kotlin and Compose. Manual dependency wiring in `AppContainer`. No Hilt, no navigation library.
- **Text:** user-visible screen text goes in `res/values/strings.xml`. The English-only helper `DueFormatter` may build text in code.
- **Screens:** composables are stateless (state in, callbacks out) so they can be UI-tested. Each screen has a ViewModel with a factory that takes `AppContainer`.
- **PowerShell scripts:** ASCII only, because Windows PowerShell 5.1 misreads other characters. Run programs through `Invoke-Program` and `Invoke-Gradle` in `scripts/_common.ps1`.

### 0.8 Gotchas learned the hard way

| Problem | What to do |
|---|---|
| The installed Android Studio (2025.3.1) can open projects up to AGP 9.0 only | Keep AGP 9.0.x. Compose is pinned to BOM 2026.06.01 (Compose 1.11), because Compose 1.12+ needs compileSdk 37 and AGP 9.1. Lint's "newer version available" checks are off for the same reason. Lift the pins together after updating Android Studio. |
| AGP 9 has Kotlin built in | Don't apply `org.jetbrains.kotlin.android`. The Kotlin Gradle plugin version is pinned in the root `build.gradle.kts` buildscript, to match the Compose compiler plugin (2.3.20). |
| Robolectric 4.17 with Android 16 (API 36) on JDK 21 fails with `jdk.internal.access` errors | `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` in `testOptions` (already set). |
| SQLCipher's native library can't load on a PC | PC tests use in-memory Room without SQLCipher; encryption is tested on the phone (`EncryptedDatabaseTest`, `MessageCaptureDeviceTest`). App code also catches `LinkageError`, so a missing library shows an error instead of crashing. |
| Room's `MigrationTestHelper` (the SupportSQLite constructor) fails on Windows paths | Use the driver-based constructor with `AndroidSQLiteDriver`, as `MigrationTest` does. |
| Robolectric's `ScheduledAlarm` has no getter for the alarm's PendingIntent | Use the deprecated `operation` field with `@Suppress("DEPRECATION")`. |
| Compose's `createComposeRule` (v1) is deprecated | Use `androidx.compose.ui.test.junit4.v2.createComposeRule`. |
| Lint says `setExactAndAllowWhileIdle` needs `SCHEDULE_EXACT_ALARM` | A false positive: the app uses `USE_EXACT_ALARM` and checks `canScheduleExactAlarms()`. Suppressed with a comment. |
| Removing the emoji initializer from the startup provider made Lint report `MissingClass` | `androidx.startup:startup-runtime` is declared as a direct dependency. |
| Windows PowerShell 5.1 fails when a native program writes to stderr while `$ErrorActionPreference = 'Stop'` | Use the helpers in `_common.ps1`. For functions that return lists, callers wrap the result in `@()`; don't use `return ,$list`. |
| `gradlew` must be executable on Linux and macOS | The executable bit is stored in git (`git update-index --chmod=+x gradlew`). |
| No `gh` CLI on this PC | Pull requests, if wanted, are opened on github.com. |
| Android's `MessagingStyle.Message` (and `getMessagesFromBundleArray`) cuts message text to 1,024 characters | Read each message bundle's `text` key directly, as `NotificationReader` does: AndroidX apps keep the whole text there. |
| `LocalDate.ofInstant` needs API 34, but minSdk is 33 (Lint `NewApi`) | Use `instant.atZone(zone).toLocalDate()`. |
| Test names with `:` or `;` don't compile (they become JVM method names) | Use commas or words ("6 in the morning", not "06:00"). |
| Robolectric enforces `FLAG_ACTIVITY_NEW_TASK` for `startActivity` from the application context | Test screen-opening code from an Activity, as the app does. |
| Compose UI tests don't click nodes scrolled off screen | `performScrollTo()` before `performClick()` in long, scrolling screens. |
| Working-copy files have Windows line endings, so `sed`/`perl` patterns with `\n` don't match | Edit with the editor tool, or allow `\r` in patterns. |

### 0.9 About the user

- **Account:** GitHub `MaahdiCodes`. Asks "what do you suggest?": answer with one clear recommendation and the reasons. Asked to review the plan before any code was written. Wants work committed and pushed. Asked for an audit of account and phone safety before the first install (§0.1).
- **Priorities, in order:** free; private (messages never leave the phone); light on the phone (storage, CPU and battery, "the phone must never hang").
- **Phones:** Pixel 7 Pro (12 GB RAM) and Poco X7 Pro (12 GB RAM, HyperOS). Both are daily phones with different accounts, and each has several WhatsApp accounts. Time zone Asia/Dhaka. Messages are mostly English. The work week is Sunday to Thursday, but the assistant must work every day. Writes times with a dot ("10.08 AM").
- **PC:** Windows 11, PowerShell 5.1 (execution policy RemoteSigned), Git Bash available. Android Studio is at `S:\Programming\Android Studio`.

### 0.10 Working on another PC or laptop

- Pull `develop`, run `.\scripts\sync-memory.ps1` (copies the assistant's notes from `docs/agent-memory` into its local memory folder), then follow [docs/SETUP_NEW_PC.md](SETUP_NEW_PC.md).
- **Not in git, by design:** the signing key, `keystore.properties`, the AI model (`*.litertlm`, re-download), real messages (`eval/private`, `recordings`), Hugging Face tokens.
- Keep the notes current: at each phase end, update the notes and copy them to `docs/agent-memory` as well as this plan (see `feedback_mavick_update_memory_every_phase.md`).

---

## 1. Goal

**Mavick** is a small, private Android app that:

1. Reads incoming **WhatsApp**, **Messenger** and **Gmail** notifications, skipping the chats and keywords you exclude, plus **Google Keep** notes you send to it.
2. Finds tasks, appointments and promises in them and **suggests** them as to-dos.
3. Lets you **add tasks manually**.
4. **Reminds** you at the right time and gives you a **morning briefing**.

**Hard requirements:** costs **$0**, **messages never leave the phone**, and it runs on **both** of your phones.

---

## 2. Decisions (locked)

| Decision | Choice | Why |
|---|---|---|
| Name | **Mavick**. Package ID `dev.maahdi.mavick`; the debug build is `dev.maahdi.mavick.debug` | The package ID can't change after the first install without moving data across through a backup (§5.7) |
| Platform | Android only, native **Kotlin + Jetpack Compose** | iOS doesn't let apps read other apps' notifications. The difficult parts (notification listener, exact alarms, background work) are native Android APIs. |
| Phones | Same app on **Pixel 7 Pro (12 GB)** and **Poco X7 Pro (12 GB)**. Each phone has its own task list for now. A combined list comes later (Phase 6). | Both are daily phones, with different accounts |
| Reading messages | Android **Notification Listener** | The only safe, legitimate way. No unofficial WhatsApp/Messenger clients: they risk an account ban and need a server. |
| AI | On-device AI running in **LiteRT-LM**, **smallest model first**. Start with **Gemma 3 1B** (about 0.5 GB). Move to **Gemma 3n E2B** (about 3 GB) only if 1B misses the §7 targets, and only with your OK. Mavick also works with no model at all (rules only). | Free and offline. You asked to keep storage and CPU low (§5.8), so model size is a budgeted decision, not a default. Your messages are mostly English, which these models handle best. |
| Cloud AI | **None** | Free cloud tiers such as the Gemini API may use your data to improve their products, and human reviewers may read it |
| Network | App has **no `INTERNET` permission**. A build check enforces this. | Android itself then makes uploading anything impossible |
| Storage | Room + **SQLCipher**, with the database key protected by **Android Keystore** | Data is encrypted on the phone |
| Install | ADB over USB, through `scripts\install.ps1`. Your everyday app is the **optimized release build** (`dev.maahdi.mavick`), signed with your own key. The key is never in git and is backed up to Google Drive (§5.7). The **debug build is a separate app** ("Mavick Debug", `dev.maahdi.mavick.debug`) with its own data, used only for development and tests. | No Play Store, no fees, no Play rules about notification access. Release builds run much faster than debug builds. Keeping debug separate means tests can never touch your real tasks. |
| Backups | Encrypted backup file saved through Android's file picker to **Google Drive**. The Drive app does the upload. | Mavick still needs no internet permission, and Google only ever holds an encrypted file |
| Task IDs | **UUIDs**, and deletes are **soft** (`deletedAt`) | Tasks from two phones or a backup can merge without ID clashes. This makes Phase 6 (combined list) an addition rather than a rewrite. |
| Dependency injection | Manual (`AppContainer`), no Hilt | The app is small, so explicit wiring is easier to read |
| Dates | The AI extracts the **phrase** ("next Thu 5pm"). **Deterministic Kotlin code** (`WhenParser`) turns it into a date. | Small models are bad at date arithmetic. Code can be tested. The same parser serves quick-add (DRY). |
| Android versions | minSdk **33** (Android 13); target/compile SDK **36** | Both phones run Android 15+. API 33 provides `USE_EXACT_ALARM`. |

---

## 3. Sources — what can be read, and how

| Source | How | What we get | Limits |
|---|---|---|---|
| **WhatsApp** (`com.whatsapp`) and **WhatsApp Business** (`com.whatsapp.w4b`) | Notification listener, `MessagingStyle` (✅ Phase 2) | Sender, chat/group name, text, time, and **which account** received it (§5.1). Includes replies you send from the notification itself. | No messages you send in the app, no muted chats, no history before install, nothing from the chat that's open on screen. Very long messages may be cut at 1,024 characters, depending on how the app builds its notifications (§5.1, Long messages). |
| **Messenger** (`com.facebook.orca`) | Notification listener, `MessagingStyle` (✅ Phase 2) | Same as WhatsApp | Same as WhatsApp |
| **Gmail** (`com.google.android.gm`) | Notification listener, `BigText` style (✅ Phase 2) | Sender, subject, preview, the receiving address (the account) | Only emails Gmail notifies you about (usually Primary). Only the preview, not the full body: select the text in Gmail › **Add to Mavick** for the rest. |
| **Google Keep** (`com.google.android.keep`) | **No API for personal accounts** (the Keep API is for Google Workspace only). Three routes instead:<br>**(A)** Keep → ⋮ → *Send* → *Mavick* (share sheet): ✅ Phase 1 (`share/SharedText.kt`)<br>**(B)** one-time import of a **Google Takeout** export, in Phase 3<br>**(C)** Keep's own reminder notifications, captured when they fire: ✅ Phase 2 | Note title + text or checklist | The Takeout export may not include reminder times. The parser will be built against **your real export**. |
| **Manual** | Quick-add box, share sheet, and **Add to Mavick** on selected text in any app (✅ Phases 1–2). Later: home-screen widget and Quick Settings tile (Phase 4). | Anything | — |

> Tip: for *new* dated to-dos, quick-add in Mavick is faster than Keep. Keep Keep for free-form notes.

---

## 4. Architecture

Everything runs on the phone. There is no server.

```
WhatsApp / Messenger / Gmail / Keep reminders
        │  incoming notifications                       (Phase 2, built)
        ▼
NotificationListener   → only the source apps above; everything else ignored on the first line
        ▼
Per-app parser         → sender, chat, text, time  (noise like summaries/calls dropped)
        ▼
Exclusion filter       → excluded = dropped in memory: never stored, never seen by AI
        ▼
Dedup + encrypted DB   → raw messages auto-deleted after 14 days (configurable)
        ▼
Rule prefilter         → skip "ok 👍"; keep dates, times, "tomorrow", "pay", "meet", questions
        ▼                                               (Phase 3)
Gemma (on-device)      → { title, when_text, person, confidence } as JSON
        ▼
WhenParser (code)      → "Thu 5pm" → 2026-10-08 17:00
        ▼
Suggestions inbox      → [Add] [Edit] [Ignore] [Never from this chat]
        ▼                                        ▲
Tasks ◄── quick-add / share / Add to Mavick / Inbox "Add as task" ──┘   (Phases 1–2, built)
  │
  ├─► exact-alarm reminders  [Done] [Snooze] [Tomorrow]  (Phase 1, built)
  ├─► morning briefing                                   (Phase 1, built)
  └─► phone calendar                                     (Phase 4)
```

### Packages (single `app` module — split only if it grows)

**Built (Phases 0–2)**

| Package | Responsibility |
|---|---|
| `capture` | `MavickNotificationListener`, `NotificationReader` (Android objects to `RawNotification`), `MessageParser`, `Noise`, `ExclusionEngine` (pure Kotlin), `MessageCapture` (the pipeline), `CaptureStatusStore` and `ReadingHealth`, `CaptureChores` (retention, reading warning), `ListenerRestart`, `NotificationRecorder` (debug builds only) |
| `data` | Room database + SQLCipher (`MavickDatabase`), converters, `security/` (database key), `settings/` (`SettingsRepository`), `task/`, `message/` (`MessageRepository`), `rules/` (`ExclusionRepository`), `health/` (listener events) |
| `time` | `WhenParser` (English dates, times, repeats), `RepeatRule`, `DueFormatter` |
| `reminders` | `AlarmReminderScheduler`, `SystemNotifier` (also reading warnings), `ReminderEngine`, `DailyChores`, receivers, intent constants |
| `security` | `AppLock` (when to lock), `DeviceAuthentication` (fingerprint or phone PIN available?) |
| `share` | `SharedText` (shared or selected text to a task draft), `MessageToTask` ("Add as task") |
| `health` | `StorageHealthCheck` |
| `ui` | Compose: `MavickRoot`, `Navigation`, `PhoneSettings`, `tasks/`, `editor/`, `inbox/` (Inbox, one message, pause choices), `reading/` (What Mavick reads, add-rule dialog), `settings/` (with Messages and Health), `lock/`, `components/`, `theme/` |

**Built (Phase 4, part 1)**

| Package | Responsibility |
|---|---|
| `calendar` | `CalendarEvent` and `CalendarEventPolicy` (which tasks, how they look: pure), `CalendarGateway` (the phone's calendar storage as Mavick needs it) with `ContentResolverCalendarGateway`, `CalendarSync` (keeps events in step with tasks) and `TaskCalendar` (what `TaskRepository` tells it) |
| `data/calendar` | `CalendarLinkEntity` and `CalendarLinkDao`: the event written for each task, on this phone only |
| `widget` | `WidgetPlanner`, `TasksWidgetRenderer`, `WidgetUpdater` (a `TaskChangeListener`), `TasksWidgetProvider`, `NewTaskTileService`, `WidgetIntents` |
| `backup` | `BackupCrypto` (AES-GCM, PBKDF2), `BackupJson` and `BackupSettings` (what is in the file), `BackupService` (make, open, preview, restore). `data/task/TaskMerge` is the merge. |

**Planned**

| Package | Responsibility | Phase |
|---|---|---|
| `ai` | `TaskExtractor` interface, `RuleExtractor`, `GemmaExtractor`, output validation, model import | 3 (✅ built) |
| `importers` | Keep Takeout import (the share sheet already lives in `share`) | 3 (✅ built) |

| `ui` additions | Suggestions screen | 3 |

---

## 5. Key designs

### 5.1 Message capture (Phase 2, built)

`MavickNotificationListener.onNotificationPosted` drops every notification whose package isn't one of the five supported apps, on its first line. The rest go, one at a time and in arrival order, to a background queue, where `MessageCapture` does the following:

1. **Read:** `NotificationReader` copies what is needed into a plain `RawNotification`.
2. **Noise filter (`Noise`):** drops group summaries, ongoing and foreground notifications, and the categories call, missed call, progress, service, system, transport and status. Per message it also drops deleted-message notices, call notices, "Waiting for this message" and "N new messages" texts.
3. **Parse (`MessageParser`):** `IncomingMessage(app, accountKey, conversationKey, conversationTitle, sender, text, postedAt, isFromMe, isGroup, cutShort)`. Chat apps give one entry per message in `android.messages`; Gmail and Keep give one text (`bigText`, then text lines, then text). Chat and person names lose invisible direction marks and a trailing "(3 messages)", so they stay the same between notifications.
4. **Rules, in memory (`ExclusionEngine`, §5.2):** a skipped message is dropped. Only counters change.
5. **Too old:** a message sent before the retention period is not saved (an old unread notification could otherwise come back after clean-up).
6. **Dedup and save:** the fingerprint is `sha256(app, accountKey, conversationKey, sender, from-me, text, message time)`, unique in the database. Chat apps post a chat's recent messages again with every new one, so each is saved once. Saved messages wait for the AI with `aiState = PENDING` (Phase 3).

Further rules:
- **Accounts (`capture/Accounts.kt`).** Every message carries an account key, so accounts never mix in dedup, rules or the Inbox:
  - `"0"`, the Android user: 0 is the phone's main user, and app clones such as Xiaomi "Dual apps" run as another user (often 999).
  - `"0/you@gmail.com"`: Gmail names the receiving address in its sub-text.
  - WhatsApp Business is a separate app, so it is always told apart.
  - **Not yet:** WhatsApp's own account switcher (several accounts in one app, same Android user). Which notification field names the account is unknown until the recorder session (§0.2); until then those accounts share one key.
  - Still to confirm on the Poco: that HyperOS delivers a clone's notifications to Mavick's listener.
- **Chats:** a chat is identified by its app, account and `conversationKey` together. The key is `"s:<shortcut ID>"` when the notification has one (it survives renames), else `"t:<chat name>"`.
- **Listener health:** connect and disconnect times are kept (counts and times only), with an event row each (`health_event`, kept 30 days). After a disconnect the listener calls `requestRebind`, and opening Mavick asks again. On connecting, notifications still in the shade are read, so nothing that arrived while disconnected is missed. Settings › Health shows the state; a **Restart** switches the listener off and on, which makes Android connect it afresh.
- **Reading warning:** once a day, at the briefing time (so never at night), Mavick warns if reading stopped or nothing arrived for the chosen number of days (1 by default, off possible).
- **Logging:** message text is **never logged**. Errors log only their type.
- **Permission:** the listener is protected by `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` (declared on the service, not requested; only Android holds it). The user grants "Notification access" in Android settings; on an app installed over USB, Android may first need App info → ⋮ → *Allow restricted settings*. Default filter types are "conversations" and "alerting", so silent notifications don't wake Mavick.
- **Read-only, so accounts are never affected:** the listener only reads.
  - It never taps a notification's buttons (Reply, Mark as read, Mute) and never opens its tap action.
  - It never dismisses or snoozes a notification, and never changes Do Not Disturb or media playback.
  - It never uses Accessibility and never reads other apps' files.

  WhatsApp, Messenger and Gmail therefore can't tell Mavick exists. No read receipts ("blue ticks", "Seen") are sent, you never appear online, and no notification disappears. Smartwatches and car systems read notifications through the same Android feature.

  **Enforced:** `checkReadOnlyNotifications` (in `app/build.gradle.kts`) fails the build if app code uses any API that could do these things, and the manifest check fails on any accessibility or device-admin component.
- **Long messages:** the notification shade shows only a line or two, but Mavick reads the notification's data, which holds more:
  - Android cuts notification text to **1,024 characters** (`MAX_CHARSEQUENCE_LENGTH` in `Notification.java`: lowered from 5,120 in 2020, still 1,024 in Android 15 and in the current source). That applies to each message built with Android's own `MessagingStyle.Message`.
  - **Apps built with AndroidX keep the whole message** in `android.messages`: measured in Android 16's own code, a 3,000-character message arrived whole (`NotificationReaderTest`). `NotificationReader` reads that text directly, because Android's helper (`getMessagesFromBundleArray`) would cut it again.
  - Gmail sends only a preview of each email.

  Mavick therefore:
  1. **Measures** in the recorder session (§0.2) where each real app cuts, with long test messages.
  2. **Marks** a text of exactly 1,024 characters (Android's cut) as *cut short*. The Inbox labels it, and the message screen says "Android cut this message short. Open WhatsApp for the rest". Gmail messages always say they are a preview. Phase 3's AI is told the text is partial.
  3. **Offers manual routes** for a whole long text: select it in any app (Gmail, a browser, notes) › **Add to Mavick** (`ACTION_PROCESS_TEXT`, no permission needed), or Share › Mavick, or copy it and paste it into a task's notes (up to 5,000 characters). The message screen's text is selectable too.

### 5.2 Exclusions: what Mavick reads (Phase 2, built)

- **App switches:** each supported app can be turned off (a setting, not a rule).
- **"Never read" rules (`RuleEffect.EXCLUDE`):** account, chat, person and keyword. Checked in that order, after the pause and the app switch.
  - **Account:** for example "nothing from my clone" or one Gmail address. Picked from accounts seen so far, because account keys aren't names.
  - **Chat:** picked from saved messages, it matches the chat's key (so a rename doesn't break it) in that app and account. Typed, it matches the chat's name, in any case, in every app and account.
  - **Person:** matches who wrote a message, in any chat, never your own messages. Picked, it applies to that app; typed, to every app.
  - **Keyword:** whole words or phrases, any case and any alphabet: "PIN" matches "my pin is" but not "spinning", and "টাকা" doesn't match "টাকার". Special characters match literally.
- **Per-app mode:** *All chats* (default) or *Only listed chats*. An app in the second mode reads only chats and people on the **"Only read"** list (`RuleEffect.ALLOW`). "Never read" always wins over "Only read". The screen warns when an app is set to "Only listed chats" with nothing listed.
- **Pause:** 1 hour, until 6:00 tomorrow, or until resumed, from the Inbox menu or What Mavick reads. A damaged stored pause stays paused, so reading never restarts by accident.
- **Default keyword rules:** `OTP`, `password`, `PIN`, `verification code`, added once the first time rules are needed; a deleted default stays deleted. Android 15+ also hides OTPs from listener apps.
- **Guarantee:** excluded content is never stored, never shown to the AI and never logged. `MessageCaptureTest` proves it on the PC; `MessageCaptureDeviceTest` proves it on the phone against the encrypted database.
- **Shortcut:** "Never read this chat" on a message adds a chat rule by key and, after asking, deletes that chat's saved messages.
- **Dead rules:** each rule shows when it last matched ("last matched 3 h ago" or "not matched yet"), so a rule that stopped working is easy to spot.

### 5.3 AI extraction (Phase 3)

1. **Rule prefilter.** A message passes if it contains any of:
   - date/time words: today, tonight, tomorrow, weekdays, months, `5pm`, `17:30`, dates
   - task verbs: pay, send, call, submit, bring, buy, meet, book, remind, deadline, due, "don't forget"
   - a question to you

   Expected to skip most messages. Only counts are recorded.
2. **Context:** add the previous 3 messages from the same conversation, because "ok see you then" refers to an earlier message.
3. **Gemma returns JSON only:**
   ```json
   {
     "actionable": true,
     "items": [
       {
         "kind": "task",
         "title": "Send the signed form to Sam",
         "when_text": "Thursday at 5pm",
         "person": "Sam",
         "confidence": 0.86
       }
     ]
   }
   ```
   `kind` is one of `task | event | reminder`. `when_text` is `null` when no time is mentioned.
4. **Validate:** decode with `kotlinx.serialization`. Title must be 3–120 characters, confidence 0–1, and at most 3 items. On invalid output, retry once. If it fails again, drop it and count the failure.
5. **Resolve the date.** `WhenParser.parse(whenText, messageTime)` — the same code as quick-add — resolves `when_text` relative to the **message's** timestamp, not the processing time.
   - If it can't parse the phrase, the suggestion asks you to pick a time.
   - Default times (already built into `WhenParser`): morning 09:00, afternoon 14:00, evening 18:00, tonight 20:00.
6. **Suggestion card:** [Add] [Edit] [Ignore] [Never from this chat]. Near-duplicates (same chat, similar title, same day) are merged.
7. **Auto-add (later, Phase 4):** only if the eval numbers are good. It adds suggestions above a confidence threshold and shows an **[Undo]** notification.

**Runtime** (kept light, per §5.8)
- A background queue processes one message at a time, on at most 2 CPU threads. Whether that queue uses WorkManager or an alarm-driven loop is decided in Phase 3 (WorkManager needs allow-list changes, §0.7).
- The model is loaded only while there is work, and unloaded 1 minute after the queue empties to free its memory.
- Processing pauses while the battery is below 20% and not charging, while Battery Saver is on, or while the phone is warm (Android thermal status "moderate" or higher). It resumes automatically.
- Expect a few seconds per message. The rule prefilter keeps the number of messages small.

**Model install** (no network needed on the phone)
1. On the PC, download the free Gemma 3 1B `.litertlm` model from Hugging Face (Gemma 3n E2B only if approved, §2). You'll need to accept the Gemma terms.
2. `scripts/push-model.ps1` (to be written in Phase 3) copies it to the phone's Download folder.
3. The app's **Import model** button checks the SHA-256 and copies the file into private storage.

**Swappable engine:** a `TaskExtractor` interface with `RuleExtractor` and `GemmaExtractor` implementations, so the AI engine can change without touching the rest of the app.

### 5.4 Reminders (built in Phase 1)

- **Scheduling:** `AlarmManager.setExactAndAllowWhileIdle` with `USE_EXACT_ALARM`. That permission is granted automatically on API 33+, and Play Store policy doesn't apply to an app you install yourself. If exact alarms are ever unavailable, the app falls back to inexact alarms, and Settings → Health shows it.
- **One alarm per pending reminder**, plus **one daily alarm** at the briefing time. Each task's alarm is addressed by `mavick://task/<id>` in the intent data, so one task's alarm can never replace another's.
- **The daily alarm** runs every day, even with the briefing off (Phase 2): it sets tomorrow's alarm first, then shows the briefing if it's on and anything is due, then does the `DailyChores` (deleting old messages, the reading warning). A minute's margin keeps an alarm that fires a moment early from setting itself again for today. Its intent keeps the old `...action.BRIEFING` text, so after an update the new alarm replaces the old one.
- **Rescheduling:** every alarm is set again on `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED`, and once each time the app's screen starts in a new process (Android wipes alarms when an app is force-stopped).
- **After a reboot:** the encrypted database can't be read until the first unlock. Reminders due in that window appear right after unlocking, labelled **missed**.
- **Which tasks remind:** a task with a time reminds at that time (or at a reminder time you set in the editor). A task with only a date has no separate reminder; it appears in the morning briefing. You can still add a reminder to it in the editor.
- **Notification buttons:** Done · Snooze 10 min · Tomorrow. Android shows at most three, so "Snooze 1 h" was dropped. "Tomorrow" moves the task to tomorrow, reminding at its usual time (09:00 if it has none). The buttons need the phone unlocked before they act.
- **Lock-screen privacy:** reminders show only "Mavick reminder" until the phone is unlocked.
- **Repeats:** every day / every N days / every work day / weekly (one or more days, or every N weeks) / monthly / yearly. "Monthly on the 31st" falls on the last day of shorter months, and 29 February on 28 February outside leap years. Marking a repeating task done moves it to its next occurrence after today (or after its due date, if done early).
- **Time zones:** a reminder keeps its local clock time (e.g. 09:00) when the phone changes time zone.
- **Notification channels:** Reminders (high importance), Morning briefing (default) and Health warnings (default, Phase 2: reading stopped or quiet; tapping opens Settings). Suggestions arrive in Phase 3.
- **Morning briefing:** every day, weekends included, at a time you choose (default 08:00). Lists overdue and today's tasks, and only appears if there are any. Pending AI suggestions join it in Phase 3.

**Quick-add language** (`time/WhenParser.kt`, 128 tested phrases). Recognised phrases are removed and the rest becomes the title. Rules worth knowing:
- A weekday ("friday", "this friday", "next friday") means the next one after today.
- A time without a date means today, or tomorrow if that time has passed.
- An hour without am/pm: 1–6 and 12 are afternoon, 7–11 morning ("at 5" = 17:00), unless "morning", "evening" and so on say otherwise. "06:30" (leading zero) is taken as written.
- Minutes follow a colon or a dot. A dot counts only with am/pm ("10.08 AM") or after "at", "by", "@" and so on ("at 10.30"), so a price like "Pay 10.50" stays text.
- Numbers like 12/10 follow the date-order setting (day/month by default). Only "/" is a date separator, so "1.5k" and "10.08.2026" stay text.
- "tonight" typed after 20:00 still means today, without a time. "remind me to …" and "don't forget to …" are dropped from the title.

### 5.5 Data model

**Built: database version 5** (schemas in `app/schemas/`). Version 2 added the task's repeat columns; version 3 (Phase 2) added the `message`, `exclusion_rule` and `health_event` tables; version 4 (Phase 3) the `suggestion` table; version 5 (Phase 4) the `calendar_link` table. All are automatic migrations that leave tasks untouched (`MigrationTest`).

Table `task`:

| Column | Stored as | Notes |
|---|---|---|
| `id` | TEXT, a UUID | Primary key |
| `title` | TEXT | Up to 500 characters |
| `notes` | TEXT, nullable | Up to 5,000 characters |
| `dueDate` | TEXT `yyyy-MM-dd`, nullable | Floating local date |
| `dueTime` | TEXT `HH:mm[:ss]`, nullable | Null means "any time that day" |
| `remindAt` | TEXT ISO local date-time, nullable | The reminder still waiting to go off for the current occurrence. Null once it has gone off, or if there is no reminder. Snoozing moves only this. |
| `priority` | TEXT | `LOW`, `NORMAL`, `HIGH` |
| `status` | TEXT | `OPEN`, `DONE`, `ARCHIVED` (repeating tasks stay `OPEN`) |
| `source` | TEXT | `MANUAL`, `MESSAGE`, `KEEP`, `SHARE` |
| `sourceExcerpt` | TEXT, nullable | Up to 300 characters, kept after old messages are deleted |
| `createdAt`, `updatedAt` | INTEGER, epoch ms | |
| `deletedAt` | INTEGER, nullable | Soft delete; purged after 30 days |
| `reminderTime` | TEXT, nullable (v2) | The reminder's clock time on the due date, kept so repeats can set it again |
| `repeatRule` | TEXT, nullable (v2) | `DAILY`, `DAILY/3`, `WEEKLY:MON,THU`, `WEEKLY/2:FRI`, `MONTHLY:31`, `YEARLY:2-29` |
| `completedAt` | INTEGER, nullable (v2) | When the task, or the last occurrence of a repeating one, was done |

Indices: `status`, `dueDate`.

Table `message` (Phase 2):

| Column | Stored as | Notes |
|---|---|---|
| `id` | TEXT, a UUID | Primary key |
| `app` | TEXT | `WHATSAPP`, `WHATSAPP_BUSINESS`, `MESSENGER`, `GMAIL`, `KEEP` |
| `accountKey` | TEXT | `"0"` (Android user), `"0/you@gmail.com"` (with the app's account name) |
| `conversationKey` | TEXT | `"s:<shortcut ID>"` or `"t:<chat name>"`; a chat is (app, accountKey, conversationKey) |
| `conversationTitle` | TEXT | The chat or group name, or an email's sender; empty for Keep |
| `sender` | TEXT, nullable | Null for your own messages and Keep reminders |
| `text` | TEXT | Up to 10,000 characters |
| `postedAt`, `receivedAt` | INTEGER, epoch ms | When it was sent; when Mavick saved it. Retention uses `postedAt`. |
| `isFromMe`, `isGroup`, `cutShort` | INTEGER (0/1) | `cutShort`: Android cut the text at 1,024 characters |
| `dedupHash` | TEXT, unique | SHA-256 fingerprint (§5.1) |
| `aiState` | TEXT | `PENDING`, `SKIPPED`, `DONE`, `FAILED` (Phase 3) |

Indices: `dedupHash` (unique), `postedAt`, (`app`, `accountKey`, `conversationKey`).

Table `exclusion_rule` (Phase 2): `id` (UUID), `type` (`ACCOUNT`, `CHAT`, `SENDER`, `KEYWORD`), `effect` (`EXCLUDE` = never read, `ALLOW` = only read), `value`, `app` (null = every app), `accountKey` (null = every account), `displayName`, `createdAt`, `lastMatchedAt`.

Table `health_event` (Phase 2): `id`, `type` (`LISTENER_CONNECTED`, `LISTENER_DISCONNECTED`), `at`. **No message content**; kept 30 days.

**Settings** are not in the database: SharedPreferences file `settings` (briefing on and its time, default 08:00; app lock, default on; work days, default Sunday–Thursday; date order, default day/month; whether notification permission was already requested; per app: reading on and its mode; the pause; message retention, default 14 days; reading warning, default 1 day; Xiaomi Autostart confirmed; default rules added; suggestions on; the calendar: on or off (default off), the chosen calendar's ID and name; the switch can't be on without a calendar; clash warnings on or off (default off) and the calendars to check, none meaning all). **Capture status** (counts and times only) is the SharedPreferences file `capture_status`.

Table `calendar_link` (Phase 4, database version 5): `taskId` (primary key), `calendarId`, `eventId`, `fingerprint` (a SHA-256 of the title, start, end and time zone last written). **This phone only:** event IDs mean nothing on another phone, so it is never part of a backup or sync. No foreign key, on purpose (§5.9).

**Planned tables**

| Table | Key fields | Phase |
|---|---|---|
| `suggestion` | id, messageId, kind, title, whenText, resolvedAt, person, confidence, state (new/accepted/ignored) | 3 (✅ built, database version 4) |

Each task keeps a short `sourceExcerpt`, so deleting old messages never breaks a task. Deleted tasks are hidden, then purged after 30 days. The tombstones let a restore or a later sync know the task was deleted rather than missing.

### 5.6 Security checklist

**Data leaving the phone**
- [x] No `INTERNET` or network-state permission. The manifest strips them, and a Gradle permission allow-list fails the build if any library adds any permission (Phase 0).
- [x] No analytics, crash-reporting or ad SDKs.
- [x] `allowBackup="false"` plus data-extraction rules that exclude everything, so nothing goes to Google cloud backup or phone-to-phone transfer (Phase 0).
- [x] Allowed permissions (Phase 1): `POST_NOTIFICATIONS`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `USE_BIOMETRIC`, plus the AndroidX-internal broadcast permission. Nothing else. Phase 2 added none: Notification access is granted by the user in Android settings, not requested. **Phase 4 added `READ_CALENDAR` and `WRITE_CALENDAR`** (§5.9), asked for only when the user switches "Add tasks with a time to my calendar" on, never at install.
- [x] **Reading the calendar (Phase 4 part 2) is on-device and opt-in:** event titles are used in memory to warn about clashes and are never stored, logged or sent. No new permission.
- [x] **Calendar events can leave the phone, through Google and not through Mavick** (Phase 4): if the chosen calendar syncs with Google, the Calendar app uploads each event's title and time. Off by default; the switch says so; events are private, with no notes. Message text never goes to the calendar (only a task's own title, which the user has accepted).
- [x] Allowed services: the notification listener (Phase 2) and the Quick Settings tile (Phase 4), each protected so that only Android can bind it. The build fails on any other service, or any accessibility or device-admin component.
- [ ] The home-screen widget shows task titles outside the app lock, unless the user hides them in Settings › Home screen (Phase 4). The switch's text says so.

**Data on the phone**
- [x] SQLCipher database. Its 256-bit random key is encrypted with a non-exportable Keystore AES-GCM key and passed to SQLCipher as a raw key, so there is no slow key stretching (Phase 0; the on-phone tests confirm it).
- [x] Fingerprint or phone-PIN app lock when Mavick opens and after 5 minutes away (Phase 1). A phone without a screen lock can't use it; Settings says so.
- [x] `FLAG_SECURE` on the whole app, so no screenshots and a blank preview in Recents (Phase 0).
- [x] Reminder and briefing notifications show no content on the lock screen, and their buttons need the phone unlocked (Phase 1).
- [x] Mavick's broadcast receivers are not exported: other apps can't trigger them (Phase 1). The debug build's recorder switch is exported but needs `android.permission.DUMP`, which only `adb` has.
- [x] Raw messages auto-deleted after **14 days** (configurable 1–90 days), and never saved if already older. Tasks are kept (Phase 2). "Delete all saved messages" in Settings.
- [x] Notifications from apps other than the five supported ones are dropped on the listener's first line, unread (Phase 2).
- [x] Excluded messages never reach storage, logs or the AI; tests prove it on the PC and on the phone (Phase 2).
- [x] Message text is shown only inside Mavick, behind the app lock and `FLAG_SECURE` (Phase 2).

**Accounts**
- [x] Phases 0–1 never touch an account: no internet, no account access, no message reading, no access to other apps or their data (audited 2026-10-05, §0.1).
- [x] Phase 2's listener is read-only, enforced by a build check (§5.1): no read receipts, no "online", no dismissed notifications.

**Code and keys**
- [x] Real messages are never committed to git. Private eval data and raw recordings live in git-ignored folders (`/eval/private/`, `/recordings/`). Test fixtures use **fake messages sent between your two phones**.
- [x] The notification recorder exists only in debug builds; the release APK contains none of its code (checked in the built APK, 2026-10-05).
- [x] Release builds signed with your own key: the user created it with `new-signing-key.ps1` (2026-10-05). Its backup (the `.p12` file to Drive and a USB drive, the password elsewhere) is the user's to confirm (§0.2). Losing them means the app can't be updated without uninstalling it, which wipes its data.
- [x] Encrypted backups (§5.7 A, Phase 5): AES-256-GCM with a password-derived key; the user saves the file to Google Drive through Android's Save screen, so Mavick never touches the network. Raw messages, suggestions and the AI model are never in a backup. The password is never kept. (Automatic weekly backups are not built.)

### 5.7 Backups (Google Drive)

**A. App data (tasks, exclusion rules, settings)** — Phase 5 (the file, restore and merge are built; automatic weekly backup is not, see below)
- **Back up now** encrypts a backup file inside Mavick, then opens Android's standard *Save to…* screen. You pick **Google Drive**, and the Drive app uploads the file. Mavick never touches the network.
- **Encryption:** AES-256-GCM with a key derived from a **backup password you choose** (PBKDF2-HMAC-SHA256, high iteration count). Google only ever sees an encrypted file. If you forget this password, the backup can't be opened, by design.
- **Contents:** tasks, including soft-deleted ones (tombstones), exclusion rules and settings, in a versioned format (`formatVersion` field) so old backups still import after app updates. **Raw messages are not included**: they are short-lived (14 days) and the most sensitive data.
- **Restore** (new phone, reinstall, or a package-ID change): *Import* → pick the file from Drive → enter the password. The backup is **merged by task UUID, newest `updatedAt` wins**. This same merge code will power Phase 6.
- **Automatic weekly backup:** Mavick keeps permission to the Drive file you picked and overwrites it weekly in the background. Phase 5 must first **verify on both phones that the Drive app accepts background overwrites**. If it doesn't, Mavick shows a weekly *"Back up now"* notification that needs one tap.
- **Not used: Android's built-in Google backup.** The database key lives in the phone's secure hardware and can't move to another phone, so a copied database would be unreadable. That backup would also copy raw messages to the cloud.

**B. Signing key** — Phase 0 (created by the user on 2026-10-05)
- `scripts/new-signing-key.ps1` creates `%USERPROFILE%\.mavick\mavick-signing.p12` with a long random password. JDK 21 protects the key with AES-256. The script also writes a git-ignored `keystore.properties` in the project folder. **The user runs this script**, so the password never appears in a Claude session. It refuses to replace an existing key.
- **Upload the `.p12` file to Google Drive** (drive.google.com → New → File upload). It's safe there because it's useless without the password.
- **Keep the password somewhere other than Drive**: a password manager (e.g. free Bitwarden) or a paper copy. That way one hacked Google account doesn't expose both.
- Keep a second copy of the `.p12` file off the PC as well (e.g. a USB drive).
- Make sure 2-Step Verification is on for that Google account.
- On a new PC: copy the `.p12` back and recreate `keystore.properties` as shown in README.md.

### 5.8 Phone resource budget (your requirement: never slow the phone down)

| Resource | Budget | How it is enforced or checked |
|---|---|---|
| App size | Release APK **≤ 30 MB** (Phase 0: 3.3 MB, Phase 1: 4.5 MB, Phase 2: 4.7 MB, Phase 3: 25.6 MB, of which 21.5 MB is the AI runtime; raised from 8 MB on purpose, §10) | The build fails above budget (`checkReleaseApkSize`). Raising a budget needs a deliberate change here. |
| Code shipped | 64-bit ARM native code only, English resources only, unused code stripped (R8) | `abiFilters`, `localeFilters` and `isMinifyEnabled` in `app/build.gradle.kts` |
| App data | Typically a few MB | Raw messages deleted after 14 days (§5.6). `usage.ps1` shows data + cache. |
| AI model (Phase 3) | **Gemma 3 1B, about 0.5 GB**. E2B (about 3 GB) only with your OK. | Optional import. Mavick works without a model. |
| Memory | The AI model is in memory only while it is processing | Unloaded 1 minute after the queue empties. `usage.ps1` shows memory (PSS). |
| CPU at startup | One small database open, off the main thread, with no slow key stretching and no emoji-font loading | `DatabaseKeyRepository` (raw key) and the startup trimming in `AndroidManifest.xml` |
| Background, Phases 0–1 | **Nothing runs** except reminder alarms at their exact times and one morning-briefing alarm a day (Phase 1) | The release manifest declares 0 services. `usage.ps1` counts services, jobs and alarms. |
| Background, Phase 2+ | With Notification access on, Android keeps the listener connected: **1 service**, woken only when a notification arrives (alerting and conversation ones by default). Other apps' notifications return at once, unread. No polling, no wake locks. Retention clean-up rides on the daily alarm (no extra job). | The build allows only that service. `usage.ps1` counts services, jobs and alarms, plus Android's battery stats. |
| Background, Phase 4 | Nothing new: calendar events are written when a task changes and tidied on the existing wake-ups (app start, restart, time change, the daily alarm). The widget (a receiver, no timer) and the tile (a service Android binds only while the quick-settings panel is open) add no background work: the widget is redrawn on task changes and the existing wake-ups. | The service allow-list; `usage.ps1` |
| CPU for AI, Phase 3 | ≤ 2 threads, one message at a time. Pauses on low battery, Battery Saver, or a warm phone. | §5.3 Runtime. Measured on both phones. |

**Rule:** every phase ends by running `.\scripts\usage.ps1` on both phones, and the numbers are recorded in this plan.

### 5.9 Calendar (Phase 4)

Phase 4 has three parts: **1. tasks to the calendar** (built), **2. clashes** (planned), **3. widget and Quick Settings tile** (planned). Auto-add with Undo (§5.3 step 7) waits for good accuracy numbers (§7, Phase 3) and is not part of any of them.

**Part 1: tasks to the calendar (`calendar/`, `data/calendar/`)**
- **Which tasks:** open tasks with a date **and** a time. A date-only task is left out (the morning briefing covers it), so the calendar stays a schedule. The user chose this on 2026-10-07 (§10).
- **What an event holds:** the title and the time, nothing else. 30 minutes long (a task has no length). Written as *private* (a calendar shared with other people shows "busy"), *free* (a to-do isn't a meeting), with **no alarm** (Mavick's own reminders already go off), never all-day, never notes, place or guests. The task's local time becomes an instant in the phone's current time zone (`CalendarEventPolicy`, tested across daylight-saving gaps and overlaps).
- **Switching on:** Settings › Calendar. The switch asks for `READ_CALENDAR` and `WRITE_CALENDAR` (one Android prompt), then shows the visible calendars that take new events, by account, and the user picks one. The switch is on only once a calendar is chosen. Mavick never creates a calendar of its own: it can't add one to a Google account.
- **One-way: the task wins.** `CalendarSync` looks at a task's state and makes the calendar match it, so repeated or overlapping calls can't leave a stale event:
  - written when the task gets a date and a time; rewritten when the title, time or the phone's time zone changes (a *fingerprint* of those, kept in the link, decides); removed when the task is done, deleted, loses its time, or the feature is switched off;
  - an event someone **deletes in the Calendar app** stays deleted until the task itself changes, then it is written again; edits made to an event in the Calendar app are overwritten only when the task changes;
  - choosing **another calendar** moves the events (removed from the old one, added to the new);
  - **overdue tasks aren't copied** when the feature is switched on (nothing before today), but an event already written stays when its time passes.
- **When it runs:** after every task change (`TaskRepository` calls `TaskCalendar.taskChanged` once its lock is released, so a slow calendar never holds up a notification button; a calendar problem never undoes or fails the change), and `reconcileAll()` on the existing wake-ups: app start, restart, time or time-zone change, app update, and the daily alarm (`DailyChores`). Right after switching on, off or choosing another calendar, the Settings screen runs it too. No new alarm, job, service or wake lock. Calendar calls run on the IO dispatcher.
- **`calendar_link` table (database version 5):** task ID, calendar ID, event ID, fingerprint. Event IDs mean something on this phone only, so they are **not** in the task and must never go into a backup or a sync (Phases 5–6). No foreign key to the task: a link outlives a purged task until its event is removed, which may have to wait for the permission.
- **Problems:** without the permission (never given, or taken away) nothing is tried and links stay, so a later reconcile removes stale events; Settings says so, with **Fix** (App info). A chosen calendar that is gone says so, with **Change**. One task's failure doesn't stop the others; the permission being off stops the run at once. Only the kind of problem is logged (`NO_PERMISSION`, `UNAVAILABLE`), never a title.
- **Privacy:** Mavick has no internet permission and still can't send anything. But if the chosen calendar syncs with Google, **the Calendar app uploads the event** (title and time). That is why the feature is off by default, and Settings says so beside the switch. The permissions are asked for only when the user switches it on.

**Part 2: clashes (built).** Read-only, with the `READ_CALENDAR` permission part 1 already asks for. **Off by default** (it reads calendar content), switched on in Settings › Calendar › Warn me about clashes.
- **What counts as a clash (`ClashFinder`, pure):** a task with a date and a time occupies 30 minutes from its time (like its event); it clashes with a calendar event that overlaps that span. Events that merely touch (one ends as the other starts) don't clash; an event with no length counts as one minute. **Left out:** all-day events (holidays, birthdays), events shown as *free*, declined by you or cancelled, Mavick's own events (by `calendar_link`; they are also written as free), and other tasks (two tasks at once are not a clash; only the calendar is).
- **Which calendars:** every calendar shown in the Calendar app, or the ones ticked in Settings (`clashCalendarIds`; none ticked means all). A ticked calendar that no longer exists is forgotten; with none left, all are checked.
- **Reading (`ClashService`):** one `Instances` query covers all the tasks asked about, from the earliest start to the latest end, for tasks from today on and within 60 days. Nothing is kept: it is worked out again when asked. A permission that is off, or any failure, means no clashes and no error on screen (only the kind of problem is logged).
- **Where it shows:** the **morning briefing** (`ReminderEngine` checks only today's tasks; clash lines come first and are counted; the lock-screen version says nothing about them), the **task list** (`TasksViewModel` shows the list at once and adds warnings when the calendar has been read; read again when the screen comes back and when the switch or the calendar choice changes), and the **editor** (`EditorViewModel` checks the chosen date and time, replacing an older check still running).
- **Privacy:** event titles are read into memory only to be shown in the briefing, the list and the editor; they are not stored or logged.

**Part 3: widget and Quick Settings tile (built).**
- **Widget:** a plain Android `AppWidgetProvider` with `RemoteViews`, **not Jetpack Glance**, which is built on WorkManager (§0.7: its permissions and service fail the build). A list widget would need a `RemoteViewsService` (another service), so it has five fixed lines instead.
  - `WidgetPlanner` (pure) picks the lines from the briefing's tasks: overdue first, then today's, at most five, the rest counted; with titles hidden, no lines and only the counts. `TasksWidgetRenderer` turns that into views, reusing the briefing's wording (`BriefingText`). `WidgetUpdater` redraws every widget of Mavick, one update at a time, each reading the newest tasks.
  - **No timer:** `updatePeriodMillis = 0`. It is redrawn when a task changes (as a `TaskChangeListener`), when the titles switch changes, and on `DailyChores.cleanUp` (app start, restart, time or time-zone change, the daily alarm). Without a widget on the home screen it reads nothing. The date line shows when it was last drawn.
  - Taps open Mavick through the same activity and app lock as a notification: a line opens its task (`ACTION_OPEN_TASK`), the heading opens the app, the "+" opens a new task (`ACTION_NEW_TASK`).
  - **Privacy:** it shows **titles by default**, with a Settings switch to hide them (the user chose this, §10), because the widget is on the home screen, outside the app lock. The lock-screen is not a widget host (`widgetCategory = home_screen`).
- **Tile:** `NewTaskTileService` opens a new task. It is Mavick's **second service**, so `allowedServices` has it on purpose, protected by `android.permission.BIND_QUICK_SETTINGS_TILE`. Android binds it only while the quick-settings panel is open; it holds no data and does no background work. Android doesn't add tiles by itself: the user drags it in.

---

## 6. Phone setup

**Both phones**
1. Settings → About phone → tap **Build number** (Pixel) or **OS version** (Poco) 7 times. This unlocks Developer options.
2. Developer options → **USB debugging** ON.
3. After installing the app, set these:
   - Allow **Notifications** (Mavick asks once on first start).
   - Set **Battery** to *Unrestricted* (Settings → Health → Fix).
   - From Phase 2: grant **Notification access**. If Android blocks it with "Restricted setting", go to App info → ⋮ → *Allow restricted settings*.
4. When you're finished with the PC, turn **USB debugging** off, or Developer options off entirely (checklist §7). Mavick keeps working without it. Some banking and payment apps refuse to open while Developer options is on.

**Poco X7 Pro (HyperOS) — extra steps, because Xiaomi closes background apps aggressively**
- Developer options → **Install via USB** ON. Every install needs it, and turning it on may require signing in to a Xiaomi account.
- Developer options → **USB debugging (Security settings)** ON **only while running the on-phone tests**. `GrantPermissionRule` needs it, because it lets the connected PC grant permissions and simulate taps. Turn it off afterwards.
- App info → **Autostart** ON.
- App info → Battery saver → **No restrictions**.
- Recents → long-press the app card → **Lock**.
- Re-check these after HyperOS updates, which sometimes reset them.

**Settings → Health (built in Phase 1)** shows, with a Fix button where Android allows one:
- encrypted storage ready
- internet access (always none)
- notifications allowed
- on-time reminders (exact alarms allowed)
- battery use unrestricted
- background activity (only alarms)

**Built in Phase 2:** notification access (with Fix, and the restricted-setting hint), message reading (working with the last message's time, quiet, or stopped by Android with **Restart**, and how often Android stopped it in 7 days), and on Xiaomi phones a manual Autostart checkbox (Android can't detect it) with a button that opens that settings page. Background activity reads "Alarms, and reading notifications as they arrive" once access is on.

**Two-phone testing:** each phone sends test WhatsApp, Messenger and Gmail messages to the other. This creates real notifications with fake content.

---

## 7. Roadmap

Times assume part-time work, with Claude writing most of the code.

| Phase | Time | Scope | Done when |
|---|---|---|---|
| **0. Foundation** | 1–2 days | Kotlin/Compose project, Room + SQLCipher, version catalog, manual DI, permission allow-list and APK size checks, test setup (JUnit, Robolectric, coroutines-test). PowerShell scripts: `devices.ps1`, `install.ps1` (build + install on one or both phones), `test.ps1`, `logs.ps1`, `usage.ps1`, `new-signing-key.ps1` (you run this one yourself, §5.7). | `scripts/test.ps1` passes. App installs and opens on both phones; Settings → Health shows "Encrypted storage: Ready". On-phone tests pass on both. `usage.ps1` shows 0 background services and jobs. Signing key backed up to Drive, with its password stored elsewhere. |
| **1. Tasks + reminders** | 1–2 wks | Today / Upcoming / Done screens, add/edit, quick-add with English date parsing ("pay rent on the 1st 10am"), **share target (Keep → Send → Mavick)**, exact reminders + actions, repeats, rescheduling, missed reminders, morning briefing, app lock, `FLAG_SECURE`. | On both phones, reminders fire within 1 min after 1 h+ with the screen off, with battery saver on, after a reboot and after a time-zone change. Date-parser and repeat tests pass. |
| **2. Message capture** | 1–2 wks | Notification recorder (debug) first, then listener and parsers (WhatsApp with **multiple accounts per phone**, Messenger, Gmail, Keep reminders), account detection, noise filter, dedup, exclusion engine + UI (including per-account rules), pause, Inbox screen, retention cleanup, Health additions + HyperOS checklist. | 50 test messages sent between the phones, **to every WhatsApp account on each phone**, are captured with the right account and no duplicates. A test proves an excluded chat's text never reaches the database. The listener survives 48 h on the Poco. |
| **3. AI suggestions** | 2–3 wks | Model import, prefilter, `GemmaExtractor`, validation, `WhenParser` reuse, Suggestions inbox, "never from this chat", conversation context, **Keep Takeout import**, private eval set (100–200 of your real messages, labelled) + on-device eval runner, **model choice: Gemma 3 1B first, E2B only if needed and approved**. | Precision ≥ 85% and recall ≥ 70% on your eval set. ≤ 10 s per message on both phones. No noticeable battery drain over a normal day. The phone stays cool, and `usage.ps1` is within §5.8. |
| **4. Calendar + planning** | 1–2 wks | Write tasks/events to a calendar you choose (`CalendarContract`), read the calendar for clashes and the briefing, home-screen widget, Quick Settings tile, optional auto-add with Undo. | Accepted events appear in Google Calendar. The briefing shows clashes. |
| **5. Backups + hardening** | 1–2 wks, then ongoing | **Encrypted backup to Google Drive + restore with merge** (§5.7), test whether Drive accepts automatic weekly overwrites, "ask my assistant" (keyword search first, on-device Q&A later), battery profiling, long-run reliability on HyperOS. | A backup made on the Pixel restores on the Poco with the correct merge. Weekly backups run automatically, or the fallback reminder is in place. |
| **6. Combined task list** (later) | 1–2 wks | One task list shared across both phones. **Preferred design:** a shared, encrypted sync file in Google Drive, reusing the Phase 5 backup format and merge code. Each phone reads, merges and writes it. No internet permission is needed. Alternatives if Drive background access proves unreliable: a shared Google Calendar (dated items only), or Bluetooth sync when the phones are near each other. | A task added, edited, completed or deleted on one phone shows up correctly on the other, including when both phones changed the same task offline. |

**Total:** about 8–11 weeks part-time to finish Phase 4, plus about 2–4 weeks for Phases 5–6. **Phase 1 is useful on its own** as your reminder app.

**Phase 0 progress (2026-10-05)**

| Item | Status |
|---|---|
| Project, encrypted database, scripts | ✅ Done (the Phase 0 home screen was replaced by the Phase 1 task lists; its checks moved to Settings → Health) |
| PC tests + Android Lint | ✅ Key handling, file format, converters, task queries, storage check, app safety. Lint: no issues. |
| Permission allow-list | ✅ Enforced on every build |
| Release APK | ✅ 3.3 MB, signature verified (tested with a throwaway key, since deleted). R8 keeps the classes SQLCipher's native code needs. |
| Release manifest | ✅ 0 services. Emoji-font loader and Room's cross-process service removed. |
| Your signing key | ⏳ Run `.\scripts\new-signing-key.ps1`, then back it up (§5.7 B) |
| Install on both phones | ⏳ `.\scripts\install.ps1` |
| On-phone tests (real encryption hardware) | ⏳ `.\scripts\test.ps1 -OnPhone` |
| Usage measured on both phones | ⏳ `.\scripts\usage.ps1`. Record the numbers here. |

**Phase 1 progress (2026-10-05)**

| Item | Status |
|---|---|
| Today / Upcoming / Done lists, editor, quick-add with preview | ✅ Done |
| Quick-add language (dates, times, repeats, "in 2 hours", "every work day") | ✅ Done. 128 example phrases tested. |
| Repeats: every day / N days / work days / weekly / monthly / yearly | ✅ Done, including month-end and 29 February |
| Reminders: exact alarms, Done / Snooze / Tomorrow, missed after restart, reset on time-zone change | ✅ Done. Notifications are private on the lock screen. |
| Morning briefing, every day | ✅ Done. Only appears when something is due. |
| App lock (fingerprint / phone PIN, after 5 min away) | ✅ Done |
| Share from Google Keep (or any app) into a new task | ✅ Done |
| Database upgrade 1 → 2 | ✅ Done. Migration test shows Phase 0 tasks are kept. |
| PC tests + Lint | ✅ 295 tests passing, Lint: no issues |
| Release APK | ✅ 4.5 MB (budget 8 MB). Permissions: notifications, exact alarms, restart, fingerprint, nothing else. 0 services. |
| First use on the Pixel: "10.08 AM" read as 8 AM | ✅ Fixed 2026-10-05: dotted times ("10.08 AM", "at 10.30") understood; "Pay 10.50" and "10.08.2026" stay text. 9 new phrase tests. |
| Pre-install safety audit (accounts, phone, scripts, merged manifest) | ✅ 2026-10-05 (§0.1). Fixed: Fix buttons fall back to App info instead of crashing; styled shared text accepted. Checklist: Poco test switch, time-zone wording, turning developer settings off. |
| On-phone end-to-end reminder test | ⏳ Written (`ReminderDeliveryTest`). Runs with `.\scripts\test.ps1 -OnPhone`. |
| Phone check on both phones | ⏳ [PHONE_CHECKLIST.md](PHONE_CHECKLIST.md) |

**Phase 2 progress (2026-10-05)**

| Item | Status |
|---|---|
| Notification recorder (debug builds only) and `record-notifications.ps1` | ✅ Built. Records only the five apps, only when switched on, and switches itself off. ⏳ Recording session with fake messages: §0.2. |
| Listener: supported apps only, read-only, catch-up on connect, rebind | ✅ Built. The read-only build check fails on planted violations (verified). |
| Parsers: WhatsApp, WhatsApp Business, Messenger (conversation style), Gmail, Keep reminders, fallbacks | ✅ Built against Android's standard formats, tested with real notifications built in Android 16's own code. ⏳ Check against recordings. |
| Accounts: Android user (clones), Gmail address, WhatsApp Business | ✅ Built. ⏳ WhatsApp's own account switcher: waiting on the recordings. |
| Noise filter, dedup, too-old messages | ✅ Built |
| Rules: account / chat / person / keyword, "Never read" and "Only read", app switches, pause, defaults | ✅ Built. 16 engine tests over every type, scope, mode and pause. |
| "Excluded text never stored" guarantee | ✅ Proved on the PC (`MessageCaptureTest`). ⏳ On the phone: `MessageCaptureDeviceTest` (written, runs with `test.ps1 -OnPhone`). |
| Encrypted `message`, `exclusion_rule`, `health_event` tables (database version 3) | ✅ Built. Migration test shows Phase 1 tasks are kept. |
| Inbox, one message ("Add as task", "Never read this chat"), What Mavick reads | ✅ Built, with Compose UI tests |
| Long messages: whole text from AndroidX apps, "Cut short" marker, Gmail preview note, "Add to Mavick" on selected text | ✅ Built. ⏳ Where real apps cut: recordings. |
| Retention clean-up (14 days, configurable), "Delete all saved messages" | ✅ Built, on the daily alarm (which now runs even with the briefing off) |
| Health: notification access, reading state with Restart, disconnects this week, Xiaomi Autostart; reading warning | ✅ Built |
| PC tests + Lint | ✅ 464 tests passing, Lint: no issues |
| Release APK | ✅ 4.7 MB (budget 8 MB). Same 4 permissions. 1 service (the listener). No recorder code. |
| Phone check (50 test messages to every WhatsApp account, no duplicates, listener survives 48 h on the Poco) | ⏳ [PHONE_CHECKLIST.md](PHONE_CHECKLIST.md) §8 |


**Phase 3 progress (2026-10-05)**

| Item | Status |
|---|---|
| Prefilter, context, AI and rules extractors, JSON checks, dates, near-duplicates | ✅ Built and tested on the PC |
| On-device runtime (LiteRT-LM 0.16.1), model import / check / remove, crash and battery guards | ✅ Built. ⏳ Never run on a phone |
| Suggested tasks screen, banner, notification, briefing, Settings › Suggestions | ✅ Built, with Compose UI tests |
| Database version 4 (suggestion table), migration test | ✅ |
| Keep Takeout import | ✅ Built against Takeout's layout with made-up exports. ⏳ Check with a real export |
| Accuracy check: export, eval.ps1, push-model.ps1, on-phone runner | ✅ Built. ⏳ Labelled set and report |
| PC tests + Lint | ✅ 704 tests, Lint: no issues |
| Release APK | ✅ 25.6 MB (budget raised to 30 MB). Same 4 permissions, 1 service |
| Targets: precision ≥ 85%, recall ≥ 70%, ≤ 10 s per message, battery, warmth | ⏳ On the phones |

**Phase 5 progress (2026-10-07)**

| Item | Status |
|---|---|
| Encrypted backup file (AES-256-GCM, PBKDF2 600,000 rounds, authenticated header) | ✅ Built. Any changed byte, any cut, the wrong password and bad rounds are refused (tested) |
| Backup contents: tasks incl. deleted, rules, travelling settings; never messages | ✅ Built. Damaged items are left out and counted, not fatal |
| Restore with merge by task ID, newest edit wins, same answer from either side; rules only added | ✅ Built and tested, including random pairs and two phones merging into each other |
| Settings › Backup: password, save through Android, pick and open, preview before restoring, settings box, results | ✅ Built, with view-model and screen tests |
| Backup made on one phone, restored on the other (checklist §11) | ⏳ |
| Whether Drive accepts automatic weekly overwrites; weekly reminder | ⬜ Needs a phone |
| "Ask my assistant" (keyword search), battery profiling, HyperOS reliability | ⬜ |
| PC tests + Lint | ✅ 1,081 tests, Lint: no issues |
| Release APK | ✅ 25.7 MB (budget 30 MB). No new permission or service |

**Phase 4 progress (2026-10-07)**

| Item | Status |
|---|---|
| Part 1: tasks with a time → events in a calendar you choose, kept in step (edits, done, delete and undo, repeats, time zone, switching off, another calendar) | ✅ Built and tested on the PC against a fake calendar and a fake calendar provider |
| Settings › Calendar: permission prompt, calendar picker, "Fix", calendar gone, event count | ✅ Built, with Compose UI and view-model tests |
| Database version 5 (`calendar_link`), migration test | ✅ |
| Calendar tidy-up on the existing wake-ups (restart, time-zone change, daily alarm) | ✅ No new alarm, job or service |
| On-phone test against the real calendar storage (`CalendarGatewayDeviceTest`) | ⏳ Written, compiles, never run |
| Part 1 on the phones (events appear in Google Calendar; checklist §10) | ⏳ |
| Part 2: clashes in the briefing, on the task list and in the editor; Settings switch and calendar choice | ✅ Built and tested on the PC (overlap rules, the calendar read against a fake provider, the briefing, the notification, the list, the editor, Settings) |
| Part 2 on the phones (checklist §10) | ⏳ |
| Part 3: home-screen widget (titles shown, can be hidden) and Quick Settings tile | ✅ Built and tested on the PC (planner, the drawn views and their taps, updates with a fake widget host, no read without a widget, the manifest: not exported, no timer, tile protected, only two services) |
| Part 3 on the phones (checklist §10) | ⏳ |
| Auto-add with Undo | ⬜ Waits for the Phase 3 accuracy numbers |
| PC tests + Lint | ✅ 947 tests (at the end of Phase 4), Lint: no issues |
| Release APK | ✅ 25.7 MB (budget 30 MB). Permissions: the 4 earlier plus `READ_CALENDAR` and `WRITE_CALENDAR`. 2 services (the listener and the tile) |

---

## 8. Testing strategy

**As built (Phases 0–5 backup core): 1,081 PC tests, and the on-phone test classes listed below.** Shared conventions: a fixed "now" of Monday 2026-10-05 10:00 in Asia/Dhaka (`MutableClock` in `testing/TestDoubles.kt`), fakes for the alarm scheduler, notifier and daily chores (same file), and the `task()` fixture (`sharedTest`).

| Test class | Runs on | Covers |
|---|---|---|
| `WhenParserTest` | PC (JVM) | 128 quick-add phrases: relative days, weekdays, parts of the day, times, written dates, day of the month, repeats, and text that must stay unparsed |
| `RepeatRuleTest` | PC (JVM) | Stored forms never change, damaged forms are rejected, next occurrence, month-end, 29 February |
| `DueFormatterTest`, `TaskDraftTest`, `EditorStateTest`, `TasksUiStateTest`, `SharedTextTest`, `AppLockTest` | PC (JVM) | Labels, draft clean-up, editor mapping (custom repeats kept), list sections, Keep sharing, lock timing |
| `DatabaseKeyRepositoryTest`, `WrappedSecretCodecTest`, `ConvertersTest`, `StorageHealthCheckTest` | PC (JVM) | Database key creation and failure cases, stored formats, health check |
| `TaskRepositoryTest` | PC (Robolectric) | Every task rule: create, complete, repeats, reopen, delete, undo, purge, snooze, tomorrow, alarm handover, restart, edit, briefing |
| `ReminderEngineTest` | PC (Robolectric) | Alarm → notification, notification buttons, resync, briefing timing (weekends too) |
| `AlarmReminderSchedulerTest`, `SystemNotifierTest` | PC (Robolectric) | Real AlarmManager / NotificationManager calls: times, time zones (including a daylight-saving gap), replacing, privacy on the lock screen, buttons |
| `TaskDaoTest`, `MigrationTest`, `SettingsRepositoryTest` | PC (Robolectric) | Queries and ordering, database upgrade 1 → 2, settings persistence and damaged values |
| `ScreensTest`, `AppSafetyTest` | PC (Robolectric + Compose) | Quick-add preview and add, task row, tabs, lock screen; no `INTERNET`, no backup, `FLAG_SECURE` |
| `PhoneSettingsTest`, `ShareIntoMavickTest` | PC (Robolectric) | Fix buttons fall back through screens to App info when a phone lacks or hides one; sharing plain or styled text, and "Add to Mavick" on selected text, open the editor through the real activity; the reading warning opens Settings |
| `NotificationReaderTest` | PC (Robolectric) | Real notifications built as the apps build them: chat messages with senders and times, your own replies, groups, emails, list style, summaries, ongoing, clone users; a 3,000-character message arrives whole from AndroidX and cut to 1,024 from Android's builder |
| `MessageParserTest`, `NoiseAndKeysTest` | PC (JVM) | Each app's shape, chat names that change between notifications, placeholders, cut-short detection, size cap, accounts; noise; the supported-app list; fingerprints that never mix accounts or chats |
| `ExclusionEngineTest` | PC (JVM) | Every rule type, scope (all or one app or account), "Only listed chats", pause and app switch, order and "Never read wins"; whole-word keywords in any alphabet |
| `MessageCaptureTest` | PC (Robolectric) | The whole pipeline: other apps ignored and uncounted, dedup, two accounts, **an excluded chat's text found nowhere in storage**, default keywords from the first message, pause, app switch, too-old, noise, counts only |
| `MessageRepositoryTest`, `ExclusionRepositoryTest`, `CaptureChoresTest`, `ReadingHealthTest`, `JsonNotificationRecorderTest`, `ListenerRestartTest` | PC (Robolectric) | Saving and queries; defaults added once (also when asked twice at once), a deleted default stays deleted; retention and warnings; reading states and the status store; the debug recorder (off by default, other apps never, switches itself off); the listener restart and its protection |
| `InboxScreensTest`, `ReadingScreenTest`, `SettingsScreenTest`, `PauseChoiceTest`, `MessageToTaskTest`, `NavigationViewModelTest` | PC (Robolectric + Compose, JVM) | Inbox grouping and labels, banners, pause; one message, its notes and the confirm dialog; rules, app modes and the add-rule dialog; Messages and Health in Settings; pause times; "Add as task" dates counted from the message; back stack |
| `CalendarEventPolicyTest` | PC (JVM) | Which tasks get an event (open, dated and timed only), start and end in the phone's zone, daylight-saving gap and overlap, the fingerprint changing exactly when the event must be rewritten and never holding the title |
| `CalendarSyncTest` | PC (Robolectric) | Through the real task repository, database and a fake calendar: write, rewrite, move, done and reopen, delete and undo, repeats, taking the time off, switching on (overdue skipped) and off (other events left alone), another calendar (even when the old one is gone), time-zone change, an event deleted in the Calendar app, a calendar problem never stopping a save, permission off (links kept, later tidy-up, purged tasks), one failure not stopping the rest |
| `ContentResolverCalendarGatewayTest` | PC (Robolectric) | Against a fake calendar provider: which calendars are listed and in what order, the exact columns written (private, free, no alarm, nothing else), update and delete of a missing event, permission and provider failures, errors carrying only the problem's name |
| `ClashFinderTest` | PC (JVM) | Overlap edges (touching, partial, covering, no length, backwards), all-day, free, own events, calendar choice, order, tasks without a time or finished, time zones, tasks never clash with each other |
| `ClashServiceTest` | PC (Robolectric) | One read for many tasks and its range, off or no permission reads nothing, past and far tasks skipped, own events, chosen calendars (a gone calendar forgotten), failures mean no clashes, the editor's single slot |
| `TasksViewModelTest` | PC (Robolectric) | Clashes reach the list state, the list shows before the calendar answers, reading again on return and on a settings change (and not on others), a calendar problem leaves the list working |
| `TaskEditorScreenTest`, more in `EditorViewModelTest`, `ReminderEngineTest`, `SystemNotifierTest`, `ScreensTest`, `SettingsScreenTest` | PC (Robolectric + Compose) | The warning under the time, checks on open and on changing the date or time (a slow older answer never wins), clash lines first in the briefing and nothing on the lock screen, the row's warning, the clash switch and the calendar picker |
| `BackupCryptoTest` | PC (JVM) | Round trip (empty, 5 MB, any alphabet), wrong password, empty password, the file showing nothing of its text, two backups differing, the layout as a contract, **every single changed byte and every cut length refused**, a newer format, rounds out of range |
| `BackupJsonTest` | PC (JVM) | Every task field and every kind of repeat, awkward text, deleted tasks, rules, settings that travel and those that never do, a damaged task or rule left out and counted, later-version fields ignored, not-a-backup and newer-format files |
| `TaskMergeTest` | PC (JVM) | Which version wins (and at the same moment), the plan's counts, merging twice, **the same winner whichever side is local for 500 random pairs, and two phones merging into each other ending alike for 200 random lists** |
| `TaskRepositoryMergeTest`, more in `ExclusionRepositoryTest` | PC (Robolectric) | Stored as they were with their own edit times, alarms only for reminders still ahead, deletions, who is told, preview changing nothing; rules added by id or meaning, once, never removed |
| `BackupServiceTest` | PC (Robolectric) | Back up on one phone and restore on another, including deleted tasks, reminders, default keywords not doubled or revived, settings kept per phone, **messages never in the file**, restoring twice, newer edits kept on either side, the preview, wrong password, passwords wiped, nothing touched by making a backup |
| `BackupViewModelTest` and the backup tests in `SettingsScreenTest` | PC (Robolectric + Compose) | The whole dialog flow: password rules, the save screen, a refused place, a picked file (too big, unreadable, not a backup, wrong password then right), the preview, the settings box, cancelling forgetting the file, results; typed passwords hidden |
| `WidgetPlannerTest` | PC (JVM) | Overdue first, five lines, the rest counted, titles hidden leaves only counts, empty day |
| `TasksWidgetRendererTest` | PC (Robolectric) | The drawn views: date, summary, lines and their colours, "+N more", hidden titles appearing nowhere, empty and unreadable states, redrawing over an old draw, and what each tap opens |
| `WidgetUpdaterTest` | PC (Robolectric, fake widget host) | Nothing read without a widget, every widget updated, following each kind of task change, titles hidden at once, a new day, an unreadable database and recovery, a failure never failing a task change |
| `WidgetEntryTest` | PC (Robolectric) | The intents, the activity opening a new task or a task from them, the widget receiver not exported with no timer and home screen only, the tile protected and the only other service |
| `TaskRepositoryListenersTest` | PC (Robolectric) | Every listener told once per kind of change and not on no-ops, a failing listener not stopping the others or the change, told after the save |
| `CalendarSettingsViewModelTest`, `CalendarLinkDaoTest` | PC (Robolectric) | Permission and picker flow, choose and switch off, a slow refresh not overwriting a newer one, an unopenable database; the link table |
| `EncryptedDatabaseTest`, `AndroidKeystoreKeyWrapperTest` | Phone | The database file is really encrypted; Keystore wrapping; wrong or lost keys |
| `ReminderDeliveryTest` | Phone | A real exact alarm wakes Mavick and shows the notification |
| `MessageCaptureDeviceTest` | Phone | Capture into the real encrypted database: a saved message is readable through Mavick but not in the file; an excluded one is nowhere |
| `CalendarGatewayDeviceTest` | Phone | The real calendar storage, in a calendar of its own (an "On device" account that never syncs): listing, writing an event with its privacy settings, rewriting, removing, missing events |

**Build checks** (run with every build and by `test.ps1`): the permission allow-list, the service allow-list (only the listener and the tile, each with its protecting permission), no accessibility or device-admin components, the read-only check over all app code, `allowBackup=false`, the APK size budget, and Android Lint with no issues.

**Manual:** [PHONE_CHECKLIST.md](PHONE_CHECKLIST.md) for each phase on both phones: restart, battery saver, time-zone change, force-stop, lock screen, Keep sharing, and `usage.ps1` numbers.

**Planned additions**

| Layer | What | Phase |
|---|---|---|
| PC (Robolectric) | Parsers against recorded fixtures (fake messages from the two phones, phone numbers replaced) | 2, after the recordings |
| PC (JVM) | AI JSON validation | 3 |
| AI eval | Precision and recall on the private labelled set, after every prompt or model change (on-device runner + report) | 3 |

Test fixtures are fake messages sent between your two phones, so no real conversation ever goes into git.

---

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| HyperOS kills the listener or delays alarms on the Poco | High | Setup checklist; Health shows the reading state and how often Android stopped it, with **Restart**; rebind on disconnect and on opening Mavick; catch-up of notifications still in the shade; the daily "no messages for N days" warning |
| WhatsApp / Messenger / Gmail change their notification layout | Medium | Fixture tests per app. Fallbacks (big text, lines, text). The recorder makes a new layout quick to capture. |
| Can't tell which WhatsApp account a notification belongs to (WhatsApp's own switcher), or HyperOS hides a cloned app's notifications | Medium | Clones and WhatsApp Business are told apart already. The recordings show the switcher's field. Until then those accounts share one key, and chat rules still work per chat. |
| Long messages arrive cut short | Certain for very long messages from apps that use Android's builder; Gmail always previews | AndroidX apps keep the whole text, which Mavick reads (measured); "Cut short" marker; "Add to Mavick" on selected text; the recordings measure each real app (§5.1) |
| Notification access left with Mavick Debug after the recorder session | Low | The recorder records only while switched on and switches itself off (at most a day); the checklist says to remove Mavick Debug's access afterwards |
| Developer options left on after setup: some banking apps refuse to open, and a trusted PC keeps USB access | Medium | Checklist §7 turns them off; Android also forgets a PC after 7 days unused |
| The Drive app won't accept background overwrites (weekly backups, Phase 6 sync) | Medium | Weekly one-tap "Back up now" reminder. For Phase 6, use one of the sync alternatives. |
| AI suggests wrong tasks or misses some | Medium | You confirm every suggestion first. Eval set. Auto-add only after the numbers are good. |
| AI model too big, slow or hot | Medium | Smallest model first (Gemma 3 1B, about 0.5 GB). Throttling (§5.3). `usage.ps1` measurements. E2B only with your OK. |
| Newer libraries need a newer Android Studio (Compose 1.12+ needs compileSdk 37 and AGP 9.1) | Certain over time | Versions pinned in `gradle/libs.versions.toml` (§0.8). Update Android Studio, then lift the pins together. |
| A library adds a permission (WorkManager, for example) | Medium | The build check fails the build; decide on purpose (§0.7) |
| Signing key lost | Low | Back up the keystore and password (password manager + offline copy) |
| Pixel 7 Pro security updates end **Oct 2027** | Certain | Fine until then. The Poco gets security updates until about early 2029. |
| Google's sideloading verification goes worldwide (2027) | Medium | Installing your own build over ADB stays allowed. Free hobbyist developer accounts exist. |

---

## 10. Decisions log and open questions

**Answered on 2026-10-05**

| Question | Answer | Effect on the plan |
|---|---|---|
| Free or paid? | **All free** | On-device AI only, no cloud services, sideloaded over USB |
| Combined task list across both phones? | Yes, later | Phase 6 added. UUID task IDs and soft deletes from Phase 1 onward. |
| Poco RAM? | 12 GB | Both phones could run larger models, but storage and CPU limits (§5.8) come first: Gemma 3 1B is tried first |
| WhatsApp setup? | Regular WhatsApp, **multiple accounts on both phones** | `accountKey` on every message, per-account exclusion rules, verified in Phase 2 |
| App name? | **Mavick** | Package ID `dev.maahdi.mavick` |
| Signing-key backup location? | **Google Drive** | §5.7 B. App-data backups also go to Drive (§5.7 A). |
| Package ID? | `dev.maahdi.mavick` (debug: `dev.maahdi.mavick.debug`) | Used from Phase 0 |
| Phone resource limits? | Storage and CPU must stay low, and the phone must never hang | §5.8 budgets, enforced by build checks. Smallest AI model first. Release build for daily use. |
| Testing on the phones? | Check each phase on both phones before starting the next | [PHONE_CHECKLIST.md](PHONE_CHECKLIST.md). The user later asked to build Phase 2 before the Phase 0–1 check; its parsers are confirmed against real notifications recorded on the phones (§0.2). |
| Git workflow? | Push to GitHub; work on `develop`, merge into `main` and push both (merged early, before the phone check, at the user's request) | §0.1 |
| Work days? | Usually Sunday to Thursday, but messages come every day | "Every work day" repeats use Sun–Thu (changeable in Settings). Everything else, including the morning briefing and message reading, runs all 7 days. |
| What does 12/10 mean? | 12 October (day/month) | Quick-add reads numeric dates as day/month (changeable in Settings) |
| "Mavick" in the text-selection menu? | Yes ("but later"), then "build all" of Phase 2 | Built in Phase 2 as "Add to Mavick" (§5.1, Long messages) |

**Decided during Phase 1 (2026-10-05)**

| Decision | Why |
|---|---|
| Three notification buttons (Done, Snooze 10 min, Tomorrow); "Snooze 1 h" dropped | Android shows at most three |
| Date-only tasks have no reminder of their own; the morning briefing covers them | Fewer interruptions; a reminder can still be added in the editor |
| The briefing only appears when something is due | No empty notifications |
| Deleting a task asks for confirmation (no undo) | Undo exists for "done"; delete is rarer and confirmed instead |
| Platform `BiometricPrompt` instead of the AndroidX biometric library | No extra library or fragment dependency (minSdk 33 has everything needed) |
| Text-only English formatting in code (`DueFormatter`) | Testable on the PC; the app is English-only |
| "Fix" and "Turn on" buttons fall back to Mavick's App info page (`PhoneSettings`) | Android warns that some phones lack some settings screens; Mavick must never crash during setup |
| Phase 2's listener is read-only: it never replies, marks read, opens, dismisses or snoozes (§5.1) | Keeps WhatsApp, Messenger and Gmail accounts unaffected: no read receipts, no "online", no lost notifications |

**Decided during Phase 2 (2026-10-05)**

| Decision | Why |
|---|---|
| Built the whole phase before the recordings, with the recorder included, at the user's request ("build all") | Android's notification formats are standard; the recordings then confirm or adjust the parsers, which are small and tested |
| "Add to Mavick" in the text-selection menu: built (the user said yes, "but later", then asked for the whole phase) | Gets whole long messages and emails into a task, with no permission |
| WhatsApp Business supported as its own app | A common way to have two WhatsApp accounts on one phone; told apart for free |
| A chat is (app, account, `conversationKey`), the key without the account in it | Simpler keys; a typed chat rule can apply to every account |
| The app switch is a setting, not a rule type; rules are account, chat, person, keyword | Simpler screen; the switch is what an "app rule" meant |
| "Only listed chats" uses "Only read" rules; "Never read" always wins | Privacy first when both match |
| The daily alarm always runs (briefing optional) | Otherwise, with the briefing off, old messages would never be deleted |
| Messages older than the retention period are never saved | An old unread notification would otherwise come back after each clean-up |
| A damaged stored pause counts as paused until resumed | For privacy, reading must never restart by accident |
| "Never read this chat" also deletes the chat's saved messages (after asking) | What the user means by it; nothing from that chat stays |
| Reading warnings only at the briefing time, once a day | Never at night; no extra alarm |
| Health "Restart" switches the listener component off and on | The documented `requestRebind` does nothing for a listener Android still thinks is bound; the switch makes Android connect it afresh |
| WhatsApp's own account switcher not told apart until the recordings | Guessing a field (like the sub-text, which may hold "3 new messages") could split one account into many and break dedup |

**Decided during Phase 3 (2026-10-05)**

| Decision | Why |
|---|---|
| Built Phase 3 before the phone checks of Phases 0–2, as with Phase 2 | The user asked to go on once earlier phases were done; all were coded and green, and the rest needs the phones |
| AI work rides on existing wake-ups in Mavick's process (no WorkManager, JobScheduler or new service) | Settles open question 3: no new permission or service; a background-priority thread with crash and battery guards keeps the listener safe |
| Release APK budget raised from 8 to 30 MB | The approved on-device AI needs LiteRT-LM's 21.5 MB native library; stored uncompressed, so Android unpacks no second copy |
| LiteRT-LM 0.16.1, not 0.17 | 0.17 needs Kotlin 2.4 libraries; lift with the other pins (§0.8) |
| "Never from this chat" reuses "Never read this chat" | One rule type, already tested; deletes the chat's messages and their suggestions |
| Suggestions live and die with their message | Nothing from a raw message outlives the retention period, except tasks you add |
| Accuracy check: label on the PC, run on the phone | The user labels in a spreadsheet; reports are numbers only; real messages stay in eval/private |

**Decided during Phase 4 (2026-10-07)**

| Decision | Why |
|---|---|
| Started Phase 4 before Phase 3 passed its phone check and accuracy targets, at the user's request | As with Phases 2 and 3. Phase 4 doesn't depend on the AI, except auto-add, which stays out until the numbers are good (§5.3 step 7) |
| **Only tasks with a time go to the calendar** (asked; the user took the recommendation) | The calendar stays a schedule, and those are the tasks that can clash. Date-only tasks are in the briefing; "every dated task" would clutter, and a per-task switch would rarely be used for suggestions |
| **The home-screen widget shows task titles, with a setting to hide them** (asked; recommended option) | A widget is only useful if readable at a glance. Titles show on an unlocked home screen without the app lock, so a switch hides them. Built in part 3 |
| The calendar feature is **off by default**, and its permissions are asked for only on switching it on | If the calendar syncs with Google, the Calendar app uploads each event (§5.6). The user decides, once, with the explanation next to the switch |
| One-way sync, the task wins; an event deleted in the Calendar app isn't written back until the task changes | No two-way merge to get wrong. Mavick doesn't fight a deliberate deletion, but never leaves an edited task out of date |
| Events: 30 minutes, private, free, no alarm, title and time only | A task has no length; privacy on shared calendars; Mavick's own reminders already go off; nothing but the title and time reaches Google |
| Event IDs live in a `calendar_link` table with no foreign key, not in the task | They mean something on this phone only (backup and sync, Phases 5–6, must not carry them); a link must outlive a purged task until its event could be removed |
| Calendar work rides on the existing wake-ups (task changes, app start, restart, time change, daily alarm); no WorkManager, job, service or alarm | §0.7 and §5.8. The daily reconcile catches anything missed |
| The widget will be a plain `AppWidgetProvider` with `RemoteViews`, not Jetpack Glance | Glance is built on WorkManager, whose permissions and service fail the build (§0.7) |
| The Quick Settings tile will be a second service, allowed on purpose in `allowedServices` | Android binds it only while the quick-settings panel is open. Decided now, done in part 3 |
| Version 0.5.0, database version 5 | New permissions and a new table: a distinct build to install |
| Clash warnings are **off by default**, in their own switch, and reuse the calendar permission | They read calendar content; the user decides. Asked for the permission only when switching on |
| A clash means a task's 30 minutes overlap a busy, timed event; free, declined, cancelled, all-day events, Mavick's own events and other tasks never count | All-day holidays and birthdays would clash with every task; tasks at 17:00 and 17:20 are not a meeting clash; Mavick's own events are copies of tasks |
| Calendars checked: all visible by default, a picker narrows (as promised on 2026-10-07) | Clash detection with the work calendar is the point; the picker covers noisy calendars |
| The widget is plain `RemoteViews` with five fixed lines and no timer; the tile is the second service (as decided on 2026-10-07) | A list widget needs another service; a timer wakes the phone. The date line shows staleness |
| The calendar and the widget both follow tasks through one `TaskChangeListener` list in `TaskRepository` (replacing the calendar-only hook) | One mechanism, not two near copies; each listener guarded so none can break a change or another listener |
| Warnings read the calendar when asked and keep nothing | No stale or stored calendar content; one read per request is cheap (60-day window) |

**Decided during Phase 5 (2026-10-07)**

| Decision | Why |
|---|---|
| The backup is a password-encrypted file the user saves through Android's Save screen (Drive included) | As planned (§5.7 A): Mavick still needs no internet permission, and Google only holds an encrypted file |
| JSON built by hand over the JSON tree, not generated serializers | A damaged task or rule is left out and counted instead of making the whole backup unreadable; no compiler plugin; the field names are a contract |
| 600,000 PBKDF2 rounds, stored in the file; refused outside 1,000–5,000,000 | Slows guessing; a crafted file can't hang the phone |
| Tasks are backed up **with deleted ones** | Otherwise a restore would bring back what you deleted |
| Merge: newest `updatedAt` wins; a deletion wins a tie; then content decides | The same answer from either side, which Phase 6 needs; tested on random pairs |
| A winning version keeps its own edit time; past reminders are not replayed | Restoring twice changes nothing; no burst of old reminders |
| Rules are only added (by id, or by meaning), never removed; a deleted default keyword stays deleted | A restore can't weaken what Mavick is told not to read; the user's deletion of a default is remembered |
| Travelling settings only; the calendar choice, the reading pause, permissions, Autostart and the last-backup time stay with the phone | Their values mean nothing on another phone, or must never change by surprise (a pause) |
| The restore shows a preview and has a "restore settings" box, ticked | Nothing changes until the user has seen what will |
| No automatic weekly backup yet | Needs a phone to learn whether Drive accepts background overwrites (§5.7 A) |

**Still open**

1. **How are the multiple WhatsApp accounts set up on each phone?** WhatsApp's own account switcher, WhatsApp Business, or a clone such as Xiaomi "Dual apps"? Clones and Business already work; the recordings settle the switcher.
2. **Tags `phase-1` and `phase-2`:** after the phone checks pass.
3. ~~Background queue for the AI~~: decided in Phase 3 (no new job or service; see above).

---

## 11. Dev environment (checked 2026-10-05)

- **Android Studio 2025.3.1:** `S:\Programming\Android Studio`. Its bundled JDK 21 is in `jbr\`.
- **Android SDK:** `%LOCALAPPDATA%\Android\Sdk`.
  - Platforms 30, 33–36.1.
  - Build-tools 33.0.1, 35.0.0, 36.1.0.
  - adb 36.0.2. It is **not on PATH**, so the scripts use the full path.
- `JAVA_HOME` and `ANDROID_HOME` are not set. The scripts set them for each run.
- Neither phone has been connected over USB yet.
- Google Drive for desktop and 7-Zip are not installed. Neither is needed: upload the signing key at drive.google.com.
- GitHub CLI (`gh`) is not installed. The `origin` remote is HTTPS with credentials already stored, so `git push` works.
- **Toolchain** (pinned in `gradle/libs.versions.toml`): Gradle 9.2.1 (wrapper with SHA-256 check), AGP 9.0.1, Kotlin 2.3.20, KSP 2.3.12, Compose BOM 2026.06.01 (Compose 1.11), Room 2.8.5, SQLCipher 4.19.1, lifecycle 2.9.4, coroutines 1.11.0, Robolectric 4.17. AGP 9.0 is the newest line this Android Studio can open. Compose 1.12+ would need Android Studio with AGP 9.1+ and SDK 37.

---

## 12. Sources

- Android sensitive-notification (OTP) restrictions: https://www.androidauthority.com/android-15-sensitive-notifications-3416414
- Notification text cap lowered to 1,024 characters and applied to each chat message (2020): https://android.googlesource.com/platform/frameworks/base/+/aaf6b40%5E%21/
- Android 15 restricted settings (notification access) for sideloaded apps: https://androidauthority.com/android-15-restricted-settings-sideloading-3481098
- Keep API is Google Workspace-only: https://developers.google.com/keep/api/guides · https://workspaceupdates.googleblog.com/2021/05/keep-audit-logs-and-api.html
- LiteRT-LM: https://github.com/google-ai-edge/LiteRT-LM · https://ai.google.dev/edge/litert-lm/overview
- Gemini API free-tier data use: https://simonwillison.net/2024/Oct/17/gemini-terms-of-service
- Android developer verification timeline: https://www.helpnetsecurity.com/?p=364358
- Pixel update schedule: https://androidcentral.com/phones/when-will-your-pixel-phone-stop-receiving-updates
- Poco X7 Pro specs: https://www.mi.com/global/poco-x7-pro/specs
- Xiaomi background-app killing and workarounds: https://dontkillmyapp.com/xiaomi

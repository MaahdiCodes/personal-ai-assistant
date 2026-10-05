# Mavick — Personal AI Assistant — Plan

> **Status:** Phases 0 and 1 are coded and pass 274 PC tests. Nothing has run on a phone yet: waiting on the user's phone check ([PHONE_CHECKLIST.md](PHONE_CHECKLIST.md)).
> **Last updated:** 2026-10-05, at the end of the session that built Phases 0–1.
> **Next step:** the user runs the phone check on both phones → record the results → merge `develop` into `main` → start Phase 2.

This document is the single source of truth for the project. **§0 is the hand-over for anyone, person or AI session, picking up the work.** Keep it current: update §0 and the status tables after every piece of work.

---

## 0. Start here (session hand-over)

### 0.1 Where things stand

✅ done · 🧪 code done, phone check pending · 🔨 in progress · ⬜ not started

| Phase | What it delivers | Status |
|---|---|---|
| Plan | Decisions, design, roadmap (this document) | ✅ |
| 0. Foundation | Project, encrypted storage, safety checks, scripts | 🧪 Code and tests done. Phone check pending. |
| 1. Tasks + reminders | Task lists, quick-add, reminders, morning briefing, app lock, Keep sharing | 🧪 Code and tests done (274 PC tests in total). Phone check pending, together with Phase 0. |
| 2. Message capture | Reading WhatsApp / Messenger / Gmail notifications, exclusions | ⬜ |
| 3. AI suggestions | On-device AI turning messages into suggested tasks | ⬜ |
| 4. Calendar + planning | Calendar sync, clashes, widget | ⬜ |
| 5. Backups + hardening | Encrypted Google Drive backups, reliability | ⬜ |
| 6. Combined task list | One list across both phones | ⬜ |

- **Git:** remote `https://github.com/MaahdiCodes/personal-ai-assistant` (private). All Phase 0–1 work is on **`develop`**, pushed. **`main` still holds only the first two plan commits.** `develop` is merged into `main` once the phone check passes, so `main` always means "verified on the phones". **Work on `develop`.**
- **Nothing has run on a phone yet:** no phone has been connected to the PC, the release build has never been installed, and the on-phone tests (`app/src/androidTest`) compile but have never run.
- **Versions:** app `versionCode 2`, `versionName 0.2.0`; database version 2.

### 0.2 Waiting on the user

1. **Signing key:** run `.\scripts\new-signing-key.ps1` and back it up (§5.7 B). The user runs this; an AI session must never create it, because the password would end up in the transcript.
2. **Phone check** of Phases 0 + 1 on **both** phones ([PHONE_CHECKLIST.md](PHONE_CHECKLIST.md)), and share the filled-in results table.
3. **Merge approval:** once the check passes, the OK to merge `develop` into `main`.
4. **For Phase 2:** how the several WhatsApp accounts are set up on each phone (WhatsApp's own "Add account", or a cloned app such as Xiaomi "Dual apps"), and both phones at hand to send test messages to each other.

### 0.3 Next actions, in order

1. **Phone-check results:** record them in §7 (Phase 0 and Phase 1 progress tables) and the `usage.ps1` numbers in §5.8. Fix anything that failed. Reminder reliability on the Poco (HyperOS) is the most likely problem (§6).
2. **Release `main`:** merge `develop` into `main` (fast-forward), tag `phase-1`, push both.
3. **Phase 2** (§7), in this order:
   1. A **debug-only notification recorder** comes first. Using test messages with fake content sent between the two phones, it saves the raw notification extras as JSON fixtures. This shows what WhatsApp (with several accounts), Messenger, Gmail and Keep reminders really post on HyperOS and on the Pixel, and especially where the **WhatsApp account** appears (§5.1).
   2. Per-app parsers, built and tested against those fixtures (`src/test`, Robolectric).
   3. The exclusion engine (pure Kotlin, exhaustive tests), then the listener service, then the encrypted `message` table (database version 3, with a migration test), then the Inbox and Exclusions screens, then the Health additions (notification access, listener last seen, HyperOS checklist).
   4. Retention cleanup runs when the daily briefing alarm fires, so no new background job is needed (see the WorkManager rule in §0.7).
4. After each step, update this document (§0 and the §7 tables) and push `develop`.

### 0.4 How to resume in a new session

1. `git fetch`, `git switch develop`, `git pull`. The local folder `E:\Personal\personal-ai-assistant` is already on `develop`.
2. Read §0, then the sections for the phase being worked on.
3. Check that the baseline passes:
   - **Windows:** `.\scripts\test.ps1` (274 tests, Lint, permission checks).
   - **Linux or macOS:** `./gradlew :app:testDebugUnitTest :app:lintDebug :app:checkDebugPermissions :app:checkReleasePermissions`. This needs JDK 17+ and an Android SDK with platform 36 and build-tools 36.1. The PowerShell scripts are Windows-only.
4. Ask the user for anything in §0.2 that is still missing before starting work that depends on it.

### 0.5 Repository map

```
app/build.gradle.kts            Android config; permission allow-list and APK size checks (end of file)
app/proguard-rules.pro          Keeps SQLCipher's JNI classes from R8
app/schemas/                    Room schema history (1.json, 2.json): commit every new version
app/src/main/AndroidManifest.xml  Permissions, receivers, share target, startup trimming
app/src/main/kotlin/dev/maahdi/mavick/
  MavickApp.kt, AppContainer.kt   App start; manual dependency wiring, everything lazy
  MainActivity.kt                 The only activity: share intents, notification taps, app-lock prompt
  data/MavickDatabase.kt          Room database (version 2), opened with SQLCipher
  data/Converters.kt              java.time and RepeatRule to and from stored text and numbers
  data/security/                  Database key: Keystore wrapping, raw-key passphrase
  data/settings/                  SettingsRepository (SharedPreferences file "settings")
  data/task/                      TaskEntity, TaskDao, TaskDraft, TaskRepository (all task rules)
  time/                           RepeatRule, WhenParser (quick-add English), DueFormatter
  reminders/                      Alarm scheduler, notifier, ReminderEngine, receivers, intents
  security/                       AppLock, DeviceAuthentication (fingerprint or phone PIN)
  share/                          SharedText (Keep or share sheet to task draft)
  health/                         StorageHealthCheck
  ui/                             MavickRoot, Navigation, tasks/, editor/, settings/, lock/, components/, theme/
app/src/test/                   PC tests (JVM and Robolectric); testing/TestDoubles.kt has MutableClock and fakes
app/src/androidTest/            On-phone tests: encryption, Keystore, real alarm to notification
app/src/sharedTest/             Helpers for both test sets: task() fixture, containsSequence
app/src/debug/res/              The "Mavick Debug" app name
docs/PLAN.md                    This document
docs/PHONE_CHECKLIST.md         The manual check on each phone, with a results table
scripts/                        Windows PowerShell 5.1 scripts (README.md lists them)
gradle/libs.versions.toml       Every version; several are pinned on purpose (§0.8)
CLAUDE.md                       Short pointer to this section for AI coding sessions
```

### 0.6 Build, test, install

| Task | Windows (PowerShell, from the project folder) | Notes |
|---|---|---|
| PC tests, Lint, permission checks | `.\scripts\test.ps1` | What "green" means for every change |
| On-phone tests | `.\scripts\test.ps1 -OnPhone -Phone pixel` | Installs a temporary "Mavick Debug" app and its test app, and removes both afterwards |
| Build and install the release app | `.\scripts\install.ps1 -Phone pixel` | Needs the user's signing key (`keystore.properties`) |
| Build without installing | `.\scripts\install.ps1 -BuildOnly` | Add `-DebugBuild` for the debug app |
| Phone resource report | `.\scripts\usage.ps1 -Phone poco` | Storage, memory, CPU, background services, jobs, alarms |
| Connected phones | `.\scripts\devices.ps1` | |
| Live log | `.\scripts\logs.ps1` | Mavick never logs task or message content |
| Plain Gradle | `.\gradlew.bat <task>` | Needs `JAVA_HOME` = Android Studio's `jbr` and `ANDROID_HOME` = the SDK. The scripts set both. |

**AI sessions:** PowerShell's `*>` redirection cuts off Kotlin compiler errors. To see the full `e:` lines, run Gradle through Bash:
`JAVA_HOME="S:/Programming/Android Studio/jbr" ANDROID_HOME="$LOCALAPPDATA/Android/Sdk" ./gradlew.bat :app:compileDebugKotlin > build.log 2>&1`

### 0.7 Rules for every change

**Definition of done**
1. Tests added or updated. The user's rule: well-tested code is non-negotiable, so err towards more tests and more edge cases.
2. `.\scripts\test.ps1` passes: all tests, Lint "No issues found", and both permission checks.
3. The release APK is within its budget (§5.8).
4. This document is updated: §0 (state, waiting on, next actions), the §7 progress tables, and §10 for any decision.
5. Committed on `develop` with a clear message ending in the same `Co-Authored-By` line as earlier commits, then pushed.

**The user's engineering preferences**
- Flag repetition (DRY). Prefer explicit code to clever code. "Engineered enough": not hacky, not over-abstracted. Handle more edge cases, not fewer.
- Give one clear recommendation with reasons. Ask before decisions that are the user's to make.

**Privacy and phone-light rules**
- **Permissions:** never add one silently. Add it to the manifest, to `allowedPermissions` in `app/build.gradle.kts`, and to this plan (§5.6, §5.8), with the reason. The build fails otherwise. No `INTERNET` permission, ever.
- **WorkManager:** its manifest adds the `WAKE_LOCK` and `FOREGROUND_SERVICE` permissions and a service. Prefer the existing alarms (for example, retention cleanup inside the daily briefing alarm). If WorkManager is really needed (perhaps the Phase 3 AI queue), update the allow-list and §5.8 on purpose.
- **Background work:** no polling, no foreground service, no wake locks. Screens watch the database only while visible (`collectAsStateWithLifecycle` with `WhileSubscribed`).
- **Main thread:** never open the database on it. Screens use `container.openTasks()`; receivers use `runInBackground` (in `Receivers.kt`).
- **Logs:** never log message or task content. Log exception class names only.
- **Git:** real messages never go into git. Test fixtures use fake messages sent between the two phones; private AI evaluation data lives in `/eval/private/`, which is git-ignored.
- **Builds:** the release build is the everyday app. The debug build is a separate app (`.debug`) with separate data, for development and tests.

**Data rules**
- **Stored formats are contracts:** enum names, `RepeatRule` storage strings, ISO date and time text, epoch-millisecond instants. Never rename or change them; only add new ones.
- **Schema changes:** bump the `MavickDatabase` version, add an `AutoMigration` (or a hand-written `Migration`) and a test in `MigrationTest`, and commit the new `app/schemas/.../N.json`.
- **Task IDs and deletes:** IDs are UUIDs. Deletes are soft (`deletedAt`), and deleted tasks are purged after 30 days.
- **Dates and times:** "floating" local values (`LocalDate`, `LocalTime`, `LocalDateTime`), converted to an instant only when setting an alarm. Always use the injected `clock: () -> Clock`, which gives a fresh clock on each call, so time-zone changes apply at once.

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
| SQLCipher's native library can't load on a PC | PC tests use in-memory Room without SQLCipher; encryption is tested on the phone (`EncryptedDatabaseTest`). App code also catches `LinkageError`, so a missing library shows an error instead of crashing. |
| Room's `MigrationTestHelper` (the SupportSQLite constructor) fails on Windows paths | Use the driver-based constructor with `AndroidSQLiteDriver`, as `MigrationTest` does. |
| Robolectric's `ScheduledAlarm` has no getter for the alarm's PendingIntent | Use the deprecated `operation` field with `@Suppress("DEPRECATION")`. |
| Compose's `createComposeRule` (v1) is deprecated | Use `androidx.compose.ui.test.junit4.v2.createComposeRule`. |
| Lint says `setExactAndAllowWhileIdle` needs `SCHEDULE_EXACT_ALARM` | A false positive: the app uses `USE_EXACT_ALARM` and checks `canScheduleExactAlarms()`. Suppressed with a comment. |
| Removing the emoji initializer from the startup provider made Lint report `MissingClass` | `androidx.startup:startup-runtime` is declared as a direct dependency. |
| Windows PowerShell 5.1 fails when a native program writes to stderr while `$ErrorActionPreference = 'Stop'` | Use the helpers in `_common.ps1`. For functions that return lists, callers wrap the result in `@()`; don't use `return ,$list`. |
| `gradlew` must be executable on Linux and macOS | The executable bit is stored in git (`git update-index --chmod=+x gradlew`). |
| No `gh` CLI on this PC | Pull requests, if wanted, are opened on github.com. |

### 0.9 About the user

- **Account:** GitHub `MaahdiCodes`. Asks "what do you suggest?": answer with one clear recommendation and the reasons. Asked to review the plan before any code was written. Wants work committed and pushed.
- **Priorities, in order:** free; private (messages never leave the phone); light on the phone (storage, CPU and battery, "the phone must never hang").
- **Phones:** Pixel 7 Pro (12 GB RAM) and Poco X7 Pro (12 GB RAM, HyperOS). Both are daily phones with different accounts, and each has several WhatsApp accounts. Time zone Asia/Dhaka. Messages are mostly English. The work week is Sunday to Thursday, but the assistant must work every day.
- **PC:** Windows 11, PowerShell 5.1 (execution policy RemoteSigned), Git Bash available. Android Studio is at `S:\Programming\Android Studio`.

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
| **WhatsApp** (`com.whatsapp`) | Notification listener, `MessagingStyle` (Phase 2) | Sender, chat/group name, text, time, and **which WhatsApp account** received it (several accounts per phone; see §5.1). May include replies you send from the notification itself. | No messages you send in the app, no muted chats, no history before install, nothing from the chat that's open on screen |
| **Messenger** (`com.facebook.orca`) | Notification listener, `MessagingStyle` (Phase 2) | Same as WhatsApp | Same as WhatsApp |
| **Gmail** (`com.google.android.gm`) | Notification listener, `BigText` / `Inbox` styles (Phase 2) | Sender, subject, preview snippet, which account | Only emails Gmail notifies you about (usually Primary). Only the preview, not the full body. |
| **Google Keep** (`com.google.android.keep`) | **No API for personal accounts** (the Keep API is for Google Workspace only). Three routes instead:<br>**(A)** Keep → ⋮ → *Send* → *Mavick* (share sheet): ✅ built in Phase 1 (`share/SharedText.kt`)<br>**(B)** one-time import of a **Google Takeout** export, in Phase 3<br>**(C)** Keep's own reminder notifications are captured when they fire, in Phase 2 | Note title + text or checklist | The Takeout export may not include reminder times. The parser will be built against **your real export**. |
| **Manual** | Quick-add box and share sheet (✅ Phase 1). Later: home-screen widget and Quick Settings tile (Phase 4). | Anything | — |

> Tip: for *new* dated to-dos, quick-add in Mavick is faster than Keep. Keep Keep for free-form notes.

---

## 4. Architecture

Everything runs on the phone. There is no server.

```
WhatsApp / Messenger / Gmail / Keep reminders
        │  incoming notifications                       (Phase 2)
        ▼
NotificationListener   → only the source apps above; everything else ignored
        ▼
Per-app parser         → sender, chat, text, time  (noise like summaries/calls dropped)
        ▼
Exclusion filter       → excluded = dropped in memory: never stored, never seen by AI
        ▼
Dedup + encrypted DB   → raw messages auto-deleted after N days
        ▼
Rule prefilter         → skip "ok 👍"; keep dates, times, "tomorrow", "pay", "meet", questions
        ▼                                               (Phase 3)
Gemma (on-device)      → { title, when_text, person, confidence } as JSON
        ▼
WhenParser (code)      → "Thu 5pm" → 2026-10-08 17:00
        ▼
Suggestions inbox      → [Add] [Edit] [Ignore] [Never from this chat]
        ▼                                        ▲
Tasks ◄──── manual quick-add / share from Keep ──┘      (Phase 1, built)
  │
  ├─► exact-alarm reminders  [Done] [Snooze] [Tomorrow]  (Phase 1, built)
  ├─► morning briefing                                   (Phase 1, built)
  └─► phone calendar                                     (Phase 4)
```

### Packages (single `app` module — split only if it grows)

**Built (Phases 0–1)**

| Package | Responsibility |
|---|---|
| `data` | Room database + SQLCipher (`MavickDatabase`), converters, `security/` (database key), `settings/` (`SettingsRepository`), `task/` (entity, DAO, `TaskDraft`, `TaskRepository`) |
| `time` | `WhenParser` (English dates, times, repeats), `RepeatRule`, `DueFormatter` |
| `reminders` | `AlarmReminderScheduler`, `SystemNotifier`, `ReminderEngine`, receivers (alarm, notification buttons, restart/time change), intent constants |
| `security` | `AppLock` (when to lock), `DeviceAuthentication` (fingerprint or phone PIN available?) |
| `share` | `SharedText`: text shared from Keep or any app becomes a task draft |
| `health` | `StorageHealthCheck` (more checks arrive in Phase 2) |
| `ui` | Compose: `MavickRoot`, `Navigation`, `tasks/` (Today, Upcoming, Done, quick-add), `editor/`, `settings/` (with Health), `lock/`, `components/`, `theme/` |

**Planned**

| Package | Responsibility | Phase |
|---|---|---|
| `capture` | `NotificationListenerService`, per-app parsers, noise filter, dedup | 2 |
| `filter` | Exclusion rules engine (pure Kotlin, heavily tested) | 2 |
| `ai` | `TaskExtractor` interface, `RuleExtractor`, `GemmaExtractor`, output validation, model import | 3 |
| `importers` | Keep Takeout import (the share sheet already lives in `share`) | 3 |
| `ui` additions | Inbox, Exclusions, Suggestions screens | 2–3 |

---

## 5. Key designs

### 5.1 Message capture (Phase 2)

`onNotificationPosted(sbn)` does the following, in order:

1. **Source check:** drop the notification unless its package is one of the supported apps. Banking, OTP and other apps are therefore never read at all.
2. **Noise filter:** skip group summaries (`FLAG_GROUP_SUMMARY`), ongoing/foreground notifications, calls (`CATEGORY_CALL`), "Checking for new messages", backup progress, and "N messages from M chats".
3. **Parse:** each app has a `NotificationParser` that produces `IncomingMessage(app, conversationKey, conversationTitle, sender, text, postedAt, isFromMe, isGroup)`. It tries `MessagingStyle` first, then falls back to `EXTRA_BIG_TEXT`, `EXTRA_TEXT_LINES` and `EXTRA_TEXT`.
4. **Exclusion filter (in memory):** an excluded message is dropped. Only a counter is incremented.
5. **Dedup:** the key is `sha256(app | conversationKey | sender | text | messageTimestamp)`. WhatsApp re-posts the recent conversation on every new message, so dedup is required.
6. **Store and enqueue:** save the message, then queue it for AI processing (Phase 3).

Further rules:
- **`accountKey` (several WhatsApp accounts per phone):** every message is tagged with the account that received it, so two accounts never mix in dedup, exclusions or chat history. There are two possible setups, and which one each phone uses is **verified in Phase 2 with test messages**:
  - **WhatsApp's own account switcher:** same package and same Android user. The account must be read from the notification itself (channel, group or sub-text). Exactly which field holds it is to be confirmed.
  - **Xiaomi "Dual apps" / app clone:** same package, different Android user (`sbn.user`). We must confirm that HyperOS delivers the clone's notifications to Mavick's listener.
- **`conversationKey`:** `accountKey` + the notification's shortcut ID when present, because the shortcut ID stays the same when a contact or group is renamed. Otherwise, `accountKey` + the conversation title.
- **Listener health:** record connect/disconnect times and call `requestRebind` after a disconnect. Warn if nothing has arrived for N hours. The warning is configurable and silent at night.
- **Logging:** message text is **never logged**.
- **Permission:** the listener service is protected by `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` (declared on the service, not requested). The user grants "Notification access" in Android settings; on an app installed over USB, Android may first need App info → ⋮ → *Allow restricted settings*.

### 5.2 Exclusions ("don't read these") (Phase 2)

- **Rule types:** app, **account** (e.g. "nothing from my second WhatsApp number"), chat (`conversationKey`), sender, keyword (case-insensitive, whole word). Chat, sender and keyword rules can apply to every account or to just one.
- **Per-app mode:** *read all except…* (default) or *read only…*.
- **Global pause:** 1 hour / until tomorrow / until turned back on.
- **Default keyword rules:** `OTP`, `password`, `PIN`, `verification code`. Android 15+ already hides OTPs from listener apps.
- **Guarantee:** excluded content is never stored, never shown to the AI and never logged. A test proves this (§8).
- **Shortcut:** the "Never from this chat" button on a suggestion creates a chat rule.
- **Edge case:** a chat without a shortcut ID loses its rule when renamed. The rules screen therefore shows each rule's *last matched* time, so a dead rule is easy to spot.

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
- **One alarm per pending reminder**, plus one for the morning briefing. Each task's alarm is addressed by `mavick://task/<id>` in the intent data, so one task's alarm can never replace another's.
- **Rescheduling:** every alarm is set again on `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED`, and once each time the app's screen starts in a new process (Android wipes alarms when an app is force-stopped).
- **After a reboot:** the encrypted database can't be read until the first unlock. Reminders due in that window appear right after unlocking, labelled **missed**.
- **Which tasks remind:** a task with a time reminds at that time (or at a reminder time you set in the editor). A task with only a date has no separate reminder; it appears in the morning briefing. You can still add a reminder to it in the editor.
- **Notification buttons:** Done · Snooze 10 min · Tomorrow. Android shows at most three, so "Snooze 1 h" was dropped. "Tomorrow" moves the task to tomorrow, reminding at its usual time (09:00 if it has none). The buttons need the phone unlocked before they act.
- **Lock-screen privacy:** reminders show only "Mavick reminder" until the phone is unlocked.
- **Repeats:** every day / every N days / every work day / weekly (one or more days, or every N weeks) / monthly / yearly. "Monthly on the 31st" falls on the last day of shorter months, and 29 February on 28 February outside leap years. Marking a repeating task done moves it to its next occurrence after today (or after its due date, if done early).
- **Time zones:** a reminder keeps its local clock time (e.g. 09:00) when the phone changes time zone.
- **Notification channels:** Reminders (high importance) and Morning briefing (default). Suggestions (Phase 3) and Health warnings (Phase 2) are added later.
- **Morning briefing:** every day, weekends included, at a time you choose (default 08:00). Lists overdue and today's tasks, and only appears if there are any. Pending AI suggestions join it in Phase 3.

**Quick-add language** (`time/WhenParser.kt`, 119 tested phrases). Recognised phrases are removed and the rest becomes the title. Rules worth knowing:
- A weekday ("friday", "this friday", "next friday") means the next one after today.
- A time without a date means today, or tomorrow if that time has passed.
- An hour without am/pm: 1–6 and 12 are afternoon, 7–11 morning ("at 5" = 17:00), unless "morning", "evening" and so on say otherwise. "06:30" (leading zero) is taken as written.
- Numbers like 12/10 follow the date-order setting (day/month by default). Only "/" is a date separator, so "1.5k" stays text.
- "tonight" typed after 20:00 still means today, without a time. "remind me to …" and "don't forget to …" are dropped from the title.

### 5.5 Data model

**Built: database version 2**, table `task` (schemas in `app/schemas/`):

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

**Settings** are not in the database: SharedPreferences file `settings` (briefing on and its time, default 08:00; app lock, default on; work days, default Sunday–Thursday; date order, default day/month; whether notification permission was already requested).

**Planned tables**

| Table | Key fields | Phase |
|---|---|---|
| `message` | id, app, **accountKey**, conversationKey, conversationTitle, sender, text, postedAt, isFromMe, dedupHash (unique), aiState (pending/skipped/done/failed) | 2 |
| `exclusion_rule` | id, type (app/account/chat/sender/keyword), value, **accountKey** (null = all accounts), displayName, mode, lastMatchedAt | 2 |
| `health_event` | id, type, at (**no message content**) | 2 |
| `suggestion` | id, messageId, kind, title, whenText, resolvedAt, person, confidence, state (new/accepted/ignored) | 3 |

Each task keeps a short `sourceExcerpt`, so deleting old messages never breaks a task. Deleted tasks are hidden, then purged after 30 days. The tombstones let a restore or a later sync know the task was deleted rather than missing.

### 5.6 Security checklist

**Data leaving the phone**
- [x] No `INTERNET` or network-state permission. The manifest strips them, and a Gradle permission allow-list fails the build if any library adds any permission (Phase 0).
- [x] No analytics, crash-reporting or ad SDKs.
- [x] `allowBackup="false"` plus data-extraction rules that exclude everything, so nothing goes to Google cloud backup or phone-to-phone transfer (Phase 0).
- [x] Allowed permissions (Phase 1): `POST_NOTIFICATIONS`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `USE_BIOMETRIC`, plus the AndroidX-internal broadcast permission. Nothing else.

**Data on the phone**
- [x] SQLCipher database. Its 256-bit random key is encrypted with a non-exportable Keystore AES-GCM key and passed to SQLCipher as a raw key, so there is no slow key stretching (Phase 0; the on-phone tests confirm it).
- [x] Fingerprint or phone-PIN app lock when Mavick opens and after 5 minutes away (Phase 1). A phone without a screen lock can't use it; Settings says so.
- [x] `FLAG_SECURE` on the whole app, so no screenshots and a blank preview in Recents (Phase 0).
- [x] Reminder and briefing notifications show no content on the lock screen, and their buttons need the phone unlocked (Phase 1).
- [x] Mavick's broadcast receivers are not exported: other apps can't trigger them (Phase 1).
- [ ] Raw messages auto-deleted after **14 days** (configurable 1–90). Tasks are kept (Phase 2).

**Code and keys**
- [x] Real messages are never committed to git. Private eval data lives in a git-ignored folder (`/eval/private/` in `.gitignore`). Test fixtures use **fake messages sent between your two phones**.
- [ ] Release builds signed with your own key: the script is ready (`new-signing-key.ps1`). Waiting on the user to create the key and back up the keystore and its password (§5.7). Losing them means the app can't be updated without uninstalling it, which wipes its data.
- [ ] Encrypted backups to Google Drive (§5.7, Phase 5).

### 5.7 Backups (Google Drive)

**A. App data (tasks, exclusion rules, settings)** — Phase 5
- **Back up now** encrypts a backup file inside Mavick, then opens Android's standard *Save to…* screen. You pick **Google Drive**, and the Drive app uploads the file. Mavick never touches the network.
- **Encryption:** AES-256-GCM with a key derived from a **backup password you choose** (PBKDF2-HMAC-SHA256, high iteration count). Google only ever sees an encrypted file. If you forget this password, the backup can't be opened, by design.
- **Contents:** tasks, including soft-deleted ones (tombstones), exclusion rules and settings, in a versioned format (`formatVersion` field) so old backups still import after app updates. **Raw messages are not included**: they are short-lived (14 days) and the most sensitive data.
- **Restore** (new phone, reinstall, or a package-ID change): *Import* → pick the file from Drive → enter the password. The backup is **merged by task UUID, newest `updatedAt` wins**. This same merge code will power Phase 6.
- **Automatic weekly backup:** Mavick keeps permission to the Drive file you picked and overwrites it weekly in the background. Phase 5 must first **verify on both phones that the Drive app accepts background overwrites**. If it doesn't, Mavick shows a weekly *"Back up now"* notification that needs one tap.
- **Not used: Android's built-in Google backup.** The database key lives in the phone's secure hardware and can't move to another phone, so a copied database would be unreadable. That backup would also copy raw messages to the cloud.

**B. Signing key** — Phase 0 (script ready, key not yet created)
- `scripts/new-signing-key.ps1` creates `%USERPROFILE%\.mavick\mavick-signing.p12` with a long random password. JDK 21 protects the key with AES-256. The script also writes a git-ignored `keystore.properties` in the project folder. **The user runs this script**, so the password never appears in a Claude session. It refuses to replace an existing key.
- **Upload the `.p12` file to Google Drive** (drive.google.com → New → File upload). It's safe there because it's useless without the password.
- **Keep the password somewhere other than Drive**: a password manager (e.g. free Bitwarden) or a paper copy. That way one hacked Google account doesn't expose both.
- Keep a second copy of the `.p12` file off the PC as well (e.g. a USB drive).
- Make sure 2-Step Verification is on for that Google account.
- On a new PC: copy the `.p12` back and recreate `keystore.properties` as shown in README.md.

### 5.8 Phone resource budget (your requirement: never slow the phone down)

| Resource | Budget | How it is enforced or checked |
|---|---|---|
| App size | Release APK **≤ 8 MB** (Phase 0: 3.3 MB, Phase 1: 4.5 MB) | The build fails above budget (`checkReleaseApkSize`). Raising a budget needs a deliberate change here. |
| Code shipped | 64-bit ARM native code only, English resources only, unused code stripped (R8) | `abiFilters`, `localeFilters` and `isMinifyEnabled` in `app/build.gradle.kts` |
| App data | Typically a few MB | Raw messages deleted after 14 days (§5.6). `usage.ps1` shows data + cache. |
| AI model (Phase 3) | **Gemma 3 1B, about 0.5 GB**. E2B (about 3 GB) only with your OK. | Optional import. Mavick works without a model. |
| Memory | The AI model is in memory only while it is processing | Unloaded 1 minute after the queue empties. `usage.ps1` shows memory (PSS). |
| CPU at startup | One small database open, off the main thread, with no slow key stretching and no emoji-font loading | `DatabaseKeyRepository` (raw key) and the startup trimming in `AndroidManifest.xml` |
| Background, Phases 0–1 | **Nothing runs** except reminder alarms at their exact times and one morning-briefing alarm a day (Phase 1) | The release manifest declares 0 services. `usage.ps1` counts services, jobs and alarms. |
| Background, Phase 2+ | The notification listener wakes only when a notification arrives. No polling, no wake locks. Retention cleanup runs when the daily briefing alarm fires (no extra job). | `usage.ps1` plus Android's battery stats |
| CPU for AI, Phase 3 | ≤ 2 threads, one message at a time. Pauses on low battery, Battery Saver, or a warm phone. | §5.3 Runtime. Measured on both phones. |

**Rule:** every phase ends by running `.\scripts\usage.ps1` on both phones, and the numbers are recorded in this plan.

---

## 6. Phone setup

**Both phones**
1. Settings → About phone → tap **Build number** (Pixel) or **OS version** (Poco) 7 times. This unlocks Developer options.
2. Developer options → **USB debugging** ON.
3. After installing the app, set these:
   - Allow **Notifications** (Mavick asks once on first start).
   - Set **Battery** to *Unrestricted* (Settings → Health → Fix).
   - From Phase 2: grant **Notification access**. If Android blocks it with "Restricted setting", go to App info → ⋮ → *Allow restricted settings*.

**Poco X7 Pro (HyperOS) — extra steps, because Xiaomi closes background apps aggressively**
- Developer options → **Install via USB** ON and **USB debugging (Security settings)** ON. These may require signing in to a Xiaomi account.
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

**Phase 2 adds:** notification access, listener last seen, and a manual checkbox for Xiaomi's Autostart (it can't be detected) with a button that opens that settings page.

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
| Quick-add language (dates, times, repeats, "in 2 hours", "every work day") | ✅ Done. 119 example phrases tested. |
| Repeats: every day / N days / work days / weekly / monthly / yearly | ✅ Done, including month-end and 29 February |
| Reminders: exact alarms, Done / Snooze / Tomorrow, missed after restart, reset on time-zone change | ✅ Done. Notifications are private on the lock screen. |
| Morning briefing, every day | ✅ Done. Only appears when something is due. |
| App lock (fingerprint / phone PIN, after 5 min away) | ✅ Done |
| Share from Google Keep (or any app) into a new task | ✅ Done |
| Database upgrade 1 → 2 | ✅ Done. Migration test shows Phase 0 tasks are kept. |
| PC tests + Lint | ✅ 274 tests passing, Lint: no issues |
| Release APK | ✅ 4.5 MB (budget 8 MB). Permissions: notifications, exact alarms, restart, fingerprint, nothing else. 0 services. |
| On-phone end-to-end reminder test | ⏳ Written (`ReminderDeliveryTest`). Runs with `.\scripts\test.ps1 -OnPhone`. |
| Phone check on both phones | ⏳ [PHONE_CHECKLIST.md](PHONE_CHECKLIST.md) |

---

## 8. Testing strategy

**As built (Phases 0–1): 274 PC tests and 3 on-phone test classes.** Shared conventions: a fixed "now" of Monday 2026-10-05 10:00 in Asia/Dhaka (`MutableClock` in `testing/TestDoubles.kt`), fakes for the alarm scheduler and notifier (same file), and the `task()` fixture (`sharedTest`).

| Test class | Runs on | Covers |
|---|---|---|
| `WhenParserTest` | PC (JVM) | 119 quick-add phrases: relative days, weekdays, parts of the day, times, written dates, day of the month, repeats, and text that must stay unparsed |
| `RepeatRuleTest` | PC (JVM) | Stored forms never change, damaged forms are rejected, next occurrence, month-end, 29 February |
| `DueFormatterTest`, `TaskDraftTest`, `EditorStateTest`, `TasksUiStateTest`, `SharedTextTest`, `AppLockTest` | PC (JVM) | Labels, draft clean-up, editor mapping (custom repeats kept), list sections, Keep sharing, lock timing |
| `DatabaseKeyRepositoryTest`, `WrappedSecretCodecTest`, `ConvertersTest`, `StorageHealthCheckTest` | PC (JVM) | Database key creation and failure cases, stored formats, health check |
| `TaskRepositoryTest` | PC (Robolectric) | Every task rule: create, complete, repeats, reopen, delete, undo, purge, snooze, tomorrow, alarm handover, restart, edit, briefing |
| `ReminderEngineTest` | PC (Robolectric) | Alarm → notification, notification buttons, resync, briefing timing (weekends too) |
| `AlarmReminderSchedulerTest`, `SystemNotifierTest` | PC (Robolectric) | Real AlarmManager / NotificationManager calls: times, time zones (including a daylight-saving gap), replacing, privacy on the lock screen, buttons |
| `TaskDaoTest`, `MigrationTest`, `SettingsRepositoryTest` | PC (Robolectric) | Queries and ordering, database upgrade 1 → 2, settings persistence and damaged values |
| `ScreensTest`, `AppSafetyTest` | PC (Robolectric + Compose) | Quick-add preview and add, task row, tabs, lock screen; no `INTERNET`, no backup, `FLAG_SECURE` |
| `EncryptedDatabaseTest`, `AndroidKeystoreKeyWrapperTest` | Phone | The database file is really encrypted; Keystore wrapping; wrong or lost keys |
| `ReminderDeliveryTest` | Phone | A real exact alarm wakes Mavick and shows the notification |

**Build checks** (run with every build and by `test.ps1`): the permission allow-list, `allowBackup=false`, the APK size budget, and Android Lint with no issues.

**Manual:** [PHONE_CHECKLIST.md](PHONE_CHECKLIST.md) for each phase on both phones: restart, battery saver, time-zone change, force-stop, lock screen, Keep sharing, and `usage.ps1` numbers.

**Planned additions**

| Layer | What | Phase |
|---|---|---|
| PC (Robolectric) | Per-app notification parsers against recorded fixtures (fake messages from the two phones) | 2 |
| PC (JVM) | Exclusion engine: every rule type × mode × pause; dedup | 2 |
| Phone | Listener end-to-end with test notifications; "excluded text never stored" end-to-end | 2 |
| PC (JVM) | AI JSON validation | 3 |
| AI eval | Precision and recall on the private labelled set, after every prompt or model change (on-device runner + report) | 3 |

Test fixtures are fake messages sent between your two phones, so no real conversation ever goes into git.

---

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| HyperOS kills the listener or delays alarms on the Poco | High | Setup checklist, Health screen, rebind on disconnect, "no messages for N h" warning |
| WhatsApp / Messenger / Gmail change their notification layout | Medium | Fixture tests per app. Generic fallback parser. |
| Can't tell which WhatsApp account a notification belongs to (multi-account), or HyperOS hides a cloned app's notifications | Medium | Verify early in Phase 2 with the notification recorder. Worst case: messages are tagged "unknown account" and per-account rules fall back to chat rules. |
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
| Testing on the phones? | Check each phase on both phones before starting the next | Phase 0 + 1 check now ([PHONE_CHECKLIST.md](PHONE_CHECKLIST.md)). Phase 2 is built from real notifications captured on your phones. |
| Git workflow? | Push to GitHub; work on `develop`, merge to `main` after the phone check | §0.1 |
| Work days? | Usually Sunday to Thursday, but messages come every day | "Every work day" repeats use Sun–Thu (changeable in Settings). Everything else, including the morning briefing and message reading, runs all 7 days. |
| What does 12/10 mean? | 12 October (day/month) | Quick-add reads numeric dates as day/month (changeable in Settings) |

**Decided during Phase 1 (2026-10-05)**

| Decision | Why |
|---|---|
| Three notification buttons (Done, Snooze 10 min, Tomorrow); "Snooze 1 h" dropped | Android shows at most three |
| Date-only tasks have no reminder of their own; the morning briefing covers them | Fewer interruptions; a reminder can still be added in the editor |
| The briefing only appears when something is due | No empty notifications |
| Deleting a task asks for confirmation (no undo) | Undo exists for "done"; delete is rarer and confirmed instead |
| Platform `BiometricPrompt` instead of the AndroidX biometric library | No extra library or fragment dependency (minSdk 33 has everything needed) |
| Text-only English formatting in code (`DueFormatter`) | Testable on the PC; the app is English-only |

**Still open**

1. **How are the multiple WhatsApp accounts set up on each phone?** WhatsApp's own account switcher, or a clone such as Xiaomi "Dual apps"? Phase 2's notification recorder will verify this either way.
2. **Merge `develop` into `main`:** after the phone check passes (the user's OK).
3. **Background queue for the AI (Phase 3):** WorkManager (needs `WAKE_LOCK` and `FOREGROUND_SERVICE` on the allow-list) or an alarm-driven loop. Decide in Phase 3 after measuring.

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
- Keep API is Google Workspace-only: https://developers.google.com/keep/api/guides · https://workspaceupdates.googleblog.com/2021/05/keep-audit-logs-and-api.html
- LiteRT-LM: https://github.com/google-ai-edge/LiteRT-LM · https://ai.google.dev/edge/litert-lm/overview
- Gemini API free-tier data use: https://simonwillison.net/2024/Oct/17/gemini-terms-of-service
- Android developer verification timeline: https://www.helpnetsecurity.com/?p=364358
- Pixel update schedule: https://androidcentral.com/phones/when-will-your-pixel-phone-stop-receiving-updates
- Poco X7 Pro specs: https://www.mi.com/global/poco-x7-pro/specs
- Xiaomi background-app killing and workarounds: https://dontkillmyapp.com/xiaomi

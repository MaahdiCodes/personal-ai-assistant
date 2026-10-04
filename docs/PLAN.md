# Mavick — Personal AI Assistant — Plan

> **Status:** Phase 0 code is done and passes its PC tests (§7). Waiting on you: create the signing key, connect the phones, install, and run the on-phone tests.
> **Last updated:** 2026-10-05
> **Next step:** finish Phase 0 on the phones, then start Phase 1.

### Status at a glance

✅ done · 🧪 code done, phone check pending · 🔨 in progress · ⬜ not started

| Phase | What it delivers | Status |
|---|---|---|
| Plan | Decisions, design, roadmap (this document) | ✅ |
| 0. Foundation | Project, encrypted storage, safety checks, scripts | 🧪 Code + 43 PC tests done. Phone check pending (§7 "Phase 0 progress"). |
| 1. Tasks + reminders | Task lists, quick-add, reminders, morning briefing, app lock | ⬜ |
| 2. Message capture | Reading WhatsApp / Messenger / Gmail notifications, exclusions | ⬜ |
| 3. AI suggestions | On-device AI turning messages into suggested tasks | ⬜ |
| 4. Calendar + planning | Calendar sync, clashes, widget | ⬜ |
| 5. Backups + hardening | Encrypted Google Drive backups, reliability | ⬜ |
| 6. Combined task list | One list across both phones | ⬜ |

**Git:** new work is pushed to the `develop` branch. It is merged into `main` once checked on the phones, so `main` always holds phone-verified code.

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
| Name | **Mavick**. Proposed package ID: `dev.maahdi.mavick` | The package ID can't change after the first install without moving data across through a backup (§5.7), so confirm it before Phase 0 |
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
| Dates | The AI extracts the **phrase** ("next Thu 5pm"). **Deterministic Kotlin code** turns it into a date. | Small models are bad at date arithmetic. Code can be tested. The same parser serves quick-add (DRY). |
| Android versions | minSdk **33** (Android 13); target/compile SDK **36** | Both phones run Android 15+. API 33 provides `USE_EXACT_ALARM`. |

---

## 3. Sources — what can be read, and how

| Source | How | What we get | Limits |
|---|---|---|---|
| **WhatsApp** (`com.whatsapp`) | Notification listener, `MessagingStyle` | Sender, chat/group name, text, time, and **which WhatsApp account** received it (several accounts per phone; see §5.1). May include replies you send from the notification itself. | No messages you send in the app, no muted chats, no history before install, nothing from the chat that's open on screen |
| **Messenger** (`com.facebook.orca`) | Notification listener, `MessagingStyle` | Same as WhatsApp | Same as WhatsApp |
| **Gmail** (`com.google.android.gm`) | Notification listener (`BigText` / `Inbox` styles) | Sender, subject, preview snippet, which account | Only emails Gmail notifies you about (usually Primary). Only the preview, not the full body. |
| **Google Keep** (`com.google.android.keep`) | **No API for personal accounts** (the Keep API is for Google Workspace only). Three routes instead:<br>**(A)** Keep → ⋮ → *Send* → *Assistant* (share sheet), in Phase 1<br>**(B)** one-time import of a **Google Takeout** export, in Phase 3<br>**(C)** Keep's own reminder notifications are captured when they fire, in Phase 2 | Note title + text or checklist | The Takeout export may not include reminder times. The parser will be built against **your real export**. |
| **Manual** | Quick-add box and share sheet. Later: home-screen widget and Quick Settings tile. | Anything | — |

> Tip: for *new* dated to-dos, quick-add in the assistant will be faster than Keep. Keep Keep for free-form notes.

---

## 4. Architecture

Everything runs on the phone. There is no server.

```
WhatsApp / Messenger / Gmail / Keep reminders
        │  incoming notifications
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
        ▼
Gemma (on-device)      → { title, when_text, person, confidence } as JSON
        ▼
WhenResolver (code)    → "Thu 5pm" → 2026-10-08 17:00
        ▼
Suggestions inbox      → [Add] [Edit] [Ignore] [Never from this chat]
        ▼                                        ▲
Tasks ◄──── manual quick-add / share from Keep ──┘
  │
  ├─► exact-alarm reminders  [Done] [Snooze] [Tomorrow]
  ├─► morning briefing
  └─► phone calendar (Phase 4)
```

### Packages (single `app` module — split only if it grows)

| Package | Responsibility |
|---|---|
| `capture` | `NotificationListenerService`, per-app parsers, noise filter, dedup |
| `filter` | Exclusion rules engine (pure Kotlin, heavily tested) |
| `data` | Room + SQLCipher, DAOs, repositories, retention cleanup |
| `time` | `WhenResolver` (English date/time phrases), repeat rules |
| `ai` | `TaskExtractor` interface, `RuleExtractor`, `GemmaExtractor`, output validation, model import |
| `reminders` | Alarm scheduling, notification actions, boot/time-change receivers, morning briefing |
| `importers` | Share-sheet receiver, Keep Takeout import |
| `health` | Permission/listener/battery checks, HyperOS checklist |
| `ui` | Compose screens: Today, Upcoming, Done, Suggestions, Inbox, Exclusions, Health, Settings |

---

## 5. Key designs

### 5.1 Message capture

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

### 5.2 Exclusions ("don't read these")

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
5. **Resolve the date.** `WhenResolver` (the same code as quick-add) resolves `when_text` relative to the **message's** timestamp, not the processing time.
   - If it can't parse the phrase, the suggestion asks you to pick a time.
   - Default times: a date with no time → 09:00, morning 09:00, afternoon 14:00, evening 18:00, tonight 20:00. All configurable.
6. **Suggestion card:** [Add] [Edit] [Ignore] [Never from this chat]. Near-duplicates (same chat, similar title, same day) are merged.
7. **Auto-add (later, Phase 4):** only if the eval numbers are good. It adds suggestions above a confidence threshold and shows an **[Undo]** notification.

**Runtime** (kept light, per §5.8)
- A WorkManager job processes the queue one message at a time, on at most 2 CPU threads.
- The model is loaded only while there is work, and unloaded 1 minute after the queue empties to free its memory.
- Processing pauses while the battery is below 20% and not charging, while Battery Saver is on, or while the phone is warm (Android thermal status "moderate" or higher). It resumes automatically.
- Expect a few seconds per message. The rule prefilter keeps the number of messages small.

**Model install** (no network needed on the phone)
1. On the PC, download the free Gemma 3 1B `.litertlm` model from Hugging Face (Gemma 3n E2B only if approved, §2). You'll need to accept the Gemma terms.
2. `scripts/push-model.ps1` copies it to the phone's Download folder.
3. The app's **Import model** button checks the SHA-256 and copies the file into private storage.

**Swappable engine:** a `TaskExtractor` interface with `RuleExtractor` and `GemmaExtractor` implementations, so the AI engine can change without touching the rest of the app.

### 5.4 Reminders

- **Scheduling:** `AlarmManager.setExactAndAllowWhileIdle` with `USE_EXACT_ALARM`. That permission is granted automatically on API 33+, and Play Store policy doesn't apply to an app you install yourself. If exact alarms are ever unavailable, the app falls back to inexact alarms and shows a warning on the Health screen.
- **Rescheduling:** every alarm is rescheduled on `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED`, and at app start.
- **After a reboot:** the encrypted database can't be read until the first unlock. Reminders due in that window fire right after unlocking, labelled **missed**.
- **Notification actions:** Done · Snooze 10 min · Snooze 1 h · Tomorrow 09:00.
- **Repeats:** daily / weekdays / weekly / monthly / every N days. "Monthly on the 31st" falls on the last day of shorter months.
- **Time zones:** a reminder keeps its local clock time (e.g. 09:00) when the phone changes time zone.
- **Notification channels:** Reminders (high), Suggestions (default), Morning briefing (low), Health warnings (default).
- **Morning briefing:** at a time you choose (default 08:00). Lists today's tasks, overdue tasks and pending suggestions.

### 5.5 Data model (first cut)

| Table | Key fields |
|---|---|
| `task` | id (**UUID**), title, notes, dueAt (local date-time + zone), remindAt, repeatRule, priority, status (open/done/archived), source (manual/message/keep/share), sourceExcerpt, createdAt, updatedAt, **deletedAt** (soft delete) |
| `message` | id, app, **accountKey**, conversationKey, conversationTitle, sender, text, postedAt, isFromMe, dedupHash (unique), aiState (pending/skipped/done/failed) |
| `suggestion` | id, messageId, kind, title, whenText, resolvedAt, person, confidence, state (new/accepted/ignored) |
| `exclusion_rule` | id, type (app/account/chat/sender/keyword), value, **accountKey** (null = all accounts), displayName, mode, lastMatchedAt |
| `health_event` | id, type, at (**no message content**) |

Each task keeps a short `sourceExcerpt`, so deleting old messages never breaks a task. Deleted tasks are hidden, then purged after 30 days. The tombstones let a restore or a later sync know the task was deleted rather than missing.

### 5.6 Security checklist

**Data leaving the phone**
- [x] No `INTERNET` or network-state permission. The manifest strips them, and a Gradle permission allow-list fails the build if any library adds any permission (Phase 0).
- [x] No analytics, crash-reporting or ad SDKs.
- [x] `allowBackup="false"` plus data-extraction rules that exclude everything, so nothing goes to Google cloud backup or phone-to-phone transfer (Phase 0).

**Data on the phone**
- [x] SQLCipher database. Its 256-bit random key is encrypted with a non-exportable Keystore AES-GCM key and passed to SQLCipher as a raw key, so there is no slow key stretching (Phase 0; on-phone tests confirm it).
- [ ] Fingerprint or device-PIN lock when the app opens and after 5 minutes in the background.
- [x] `FLAG_SECURE` on the whole app, so no screenshots and a blank preview in Recents (Phase 0).
- [ ] Raw messages auto-deleted after **14 days** (configurable 1–90). Tasks are kept.

**Code and keys**
- [x] Real messages are never committed to git. Private eval data lives in a git-ignored folder (`/eval/private/` in `.gitignore`). Test fixtures use **fake messages sent between your two phones**.
- [ ] Release builds are signed with your own key (debug builds are the separate "Mavick Debug" app). **Back up the keystore and its password** (§5.7). Losing them means you can't update the app without uninstalling it, which wipes its data.
- [ ] Encrypted backups to Google Drive (§5.7).

### 5.7 Backups (Google Drive)

**A. App data (tasks, exclusion rules, settings)** — Phase 5
- **Back up now** encrypts a backup file inside Mavick, then opens Android's standard *Save to…* screen. You pick **Google Drive**, and the Drive app uploads the file. Mavick never touches the network.
- **Encryption:** AES-256-GCM with a key derived from a **backup password you choose** (PBKDF2-HMAC-SHA256, high iteration count). Google only ever sees an encrypted file. If you forget this password, the backup can't be opened, by design.
- **Contents:** tasks, including soft-deleted ones (tombstones), exclusion rules and settings, in a versioned format (`formatVersion` field) so old backups still import after app updates. **Raw messages are not included**: they are short-lived (14 days) and the most sensitive data.
- **Restore** (new phone, reinstall, or a package-ID change): *Import* → pick the file from Drive → enter the password. The backup is **merged by task UUID, newest `updatedAt` wins**. This same merge code will power Phase 6.
- **Automatic weekly backup:** Mavick keeps permission to the Drive file you picked and overwrites it weekly in the background. Phase 5 must first **verify on both phones that the Drive app accepts background overwrites**. If it doesn't, Mavick shows a weekly *"Back up now"* notification that needs one tap.
- **Not used: Android's built-in Google backup.** The database key lives in the phone's secure hardware and can't move to another phone, so a copied database would be unreadable. That backup would also copy raw messages to the cloud.

**B. Signing key** — Phase 0
- `scripts/new-signing-key.ps1` creates `mavick-signing.p12` with a long random password. JDK 21 protects the key with AES-256. The script also writes a git-ignored `keystore.properties`. **You run this script yourself**, so the password never appears in a Claude session.
- **Upload the `.p12` file to Google Drive** (drive.google.com → New → File upload). It's safe there because it's useless without the password.
- **Keep the password somewhere other than Drive**: a password manager (e.g. free Bitwarden) or a paper copy. That way one hacked Google account doesn't expose both.
- Keep a second copy of the `.p12` file off the PC as well (e.g. a USB drive).
- Make sure 2-Step Verification is on for that Google account.

### 5.8 Phone resource budget (your requirement: never slow the phone down)

| Resource | Budget | How it is enforced or checked |
|---|---|---|
| App size | Release APK **≤ 8 MB** (Phase 0: 3.3 MB) | The build fails above budget (`checkReleaseApkSize`). Raising a budget needs a deliberate change here. |
| Code shipped | 64-bit ARM native code only, English resources only, unused code stripped (R8) | `abiFilters`, `localeFilters` and `isMinifyEnabled` in `app/build.gradle.kts` |
| App data | Typically a few MB | Raw messages deleted after 14 days (§5.6). `usage.ps1` shows data + cache. |
| AI model (Phase 3) | **Gemma 3 1B, about 0.5 GB**. E2B (about 3 GB) only with your OK. | Optional import. Mavick works without a model. |
| Memory | The AI model is in memory only while it is processing | Unloaded 1 minute after the queue empties. `usage.ps1` shows memory (PSS). |
| CPU at startup | One small database open, off the main thread, with no slow key stretching and no emoji-font loading | `DatabaseKeyRepository` (raw key) and the startup trimming in `AndroidManifest.xml` |
| Background, Phases 0–1 | **Nothing runs** except reminder alarms at their exact times (Phase 1) | The release manifest declares 0 services. `usage.ps1` counts services, jobs and alarms. |
| Background, Phase 2+ | The notification listener wakes only when a notification arrives. No polling, no wake locks. One daily cleanup job, run while charging. | `usage.ps1` plus Android's battery stats |
| CPU for AI, Phase 3 | ≤ 2 threads, one message at a time. Pauses on low battery, Battery Saver, or a warm phone. | §5.3 Runtime. Measured on both phones. |

**Rule:** every phase ends by running `.\scripts\usage.ps1` on both phones, and the numbers are recorded in this plan.

---

## 6. Phone setup

**Both phones**
1. Settings → About phone → tap **Build number** (Pixel) or **OS version** (Poco) 7 times. This unlocks Developer options.
2. Developer options → **USB debugging** ON.
3. After installing the app, set these:
   - Grant **Notification access**. If Android blocks it with "Restricted setting", go to App info → ⋮ → *Allow restricted settings*.
   - Allow **Notifications**.
   - Set **Battery** to *Unrestricted*.

**Poco X7 Pro (HyperOS) — extra steps, because Xiaomi closes background apps aggressively**
- Developer options → **Install via USB** ON and **USB debugging (Security settings)** ON. These may require signing in to a Xiaomi account.
- App info → **Autostart** ON.
- App info → Battery saver → **No restrictions**.
- Recents → long-press the app card → **Lock**.
- Re-check these after HyperOS updates, which sometimes reset them.

**In-app Health screen (Phase 2)** checks these automatically:
- notification access
- battery-optimization exemption
- exact alarms allowed
- notification permission
- listener last-seen time

Xiaomi's Autostart setting can't be detected, so the screen shows a manual checkbox and a button that opens that settings page.

**Two-phone testing:** each phone sends test WhatsApp, Messenger and Gmail messages to the other. This creates real notifications with fake content.

---

## 7. Roadmap

Times assume part-time work, with Claude writing most of the code.

| Phase | Time | Scope | Done when |
|---|---|---|---|
| **0. Foundation** | 1–2 days | Kotlin/Compose project, Room + SQLCipher, version catalog, manual DI, permission allow-list and APK size checks, test setup (JUnit, Robolectric, coroutines-test). PowerShell scripts: `devices.ps1`, `install.ps1` (build + install on one or both phones), `test.ps1`, `logs.ps1`, `usage.ps1`, `new-signing-key.ps1` (you run this one yourself, §5.7). | `scripts/test.ps1` passes. App installs and opens on both phones, showing "Encrypted storage: Ready". On-phone tests pass on both. `usage.ps1` shows 0 background services, jobs and alarms. Signing key backed up to Drive, with its password stored elsewhere. |
| **1. Tasks + reminders** | 1–2 wks | Today / Upcoming / Done screens, add/edit, quick-add with English date parsing ("pay rent on the 1st 10am"), **share target (Keep → Send → Assistant)**, exact reminders + actions, repeats, rescheduling, missed reminders, morning briefing, app lock, `FLAG_SECURE`. | On both phones, reminders fire within 1 min after 1 h+ with the screen off, with battery saver on, after a reboot and after a time-zone change. Date-parser and repeat tests pass. |
| **2. Message capture** | 1–2 wks | Listener and parsers (WhatsApp with **multiple accounts per phone**, Messenger, Gmail, Keep reminders), account detection, noise filter, dedup, exclusion engine + UI (including per-account rules), pause, Inbox screen, retention cleanup, Health screen + HyperOS checklist, debug-only "save as test fixture". | 50 test messages sent between the phones, **to every WhatsApp account on each phone**, are captured with the right account and no duplicates. A test proves an excluded chat's text never reaches the database. The listener survives 48 h on the Poco. |
| **3. AI suggestions** | 2–3 wks | Model import, prefilter, `GemmaExtractor`, validation, `WhenResolver` reuse, Suggestions inbox, "never from this chat", conversation context, **Keep Takeout import**, private eval set (100–200 of your real messages, labelled) + on-device eval runner, **model choice: Gemma 3 1B first, E2B only if needed and approved**. | Precision ≥ 85% and recall ≥ 70% on your eval set. ≤ 10 s per message on both phones. No noticeable battery drain over a normal day. The phone stays cool, and `usage.ps1` is within §5.8. |
| **4. Calendar + planning** | 1–2 wks | Write tasks/events to a calendar you choose (`CalendarContract`), read the calendar for clashes and the briefing, home-screen widget, Quick Settings tile, optional auto-add with Undo. | Accepted events appear in Google Calendar. The briefing shows clashes. |
| **5. Backups + hardening** | 1–2 wks, then ongoing | **Encrypted backup to Google Drive + restore with merge** (§5.7), test whether Drive accepts automatic weekly overwrites, "ask my assistant" (keyword search first, on-device Q&A later), battery profiling, long-run reliability on HyperOS. | A backup made on the Pixel restores on the Poco with the correct merge. Weekly backups run automatically, or the fallback reminder is in place. |
| **6. Combined task list** (later) | 1–2 wks | One task list shared across both phones. **Preferred design:** a shared, encrypted sync file in Google Drive, reusing the Phase 5 backup format and merge code. Each phone reads, merges and writes it. No internet permission is needed. Alternatives if Drive background access proves unreliable: a shared Google Calendar (dated items only), or Bluetooth sync when the phones are near each other. | A task added, edited, completed or deleted on one phone shows up correctly on the other, including when both phones changed the same task offline. |

**Total:** about 8–11 weeks part-time to finish Phase 4, plus about 2–4 weeks for Phases 5–6. **Phase 1 is useful on its own** as your reminder app.

**Phase 0 progress (2026-10-05)**

| Item | Status |
|---|---|
| Project, encrypted database, home screen, scripts | ✅ Done |
| PC tests + Android Lint | ✅ 43 tests passing (key handling, file format, converters, task queries, storage check, app safety). Lint: no issues. |
| Permission allow-list | ✅ Debug and release request only the AndroidX-internal broadcast permission |
| Release APK | ✅ 3.3 MB, signature verified (tested with a throwaway key, since deleted). R8 keeps the classes SQLCipher's native code needs. |
| Release manifest | ✅ 1 screen, 0 services. Emoji-font loader and Room's cross-process service removed. |
| Your signing key | ⏳ Run `.\scripts\new-signing-key.ps1`, then back it up (§5.7 B) |
| Install on both phones | ⏳ `.\scripts\install.ps1` |
| On-phone tests (real encryption hardware) | ⏳ `.\scripts\test.ps1 -OnPhone` |
| Usage measured on both phones | ⏳ `.\scripts\usage.ps1`. Record the numbers here. |

---

## 8. Testing strategy

| Layer | What | How |
|---|---|---|
| Unit (JVM) | **WhenResolver:** 100+ phrase × reference-time cases (month/year rollover, leap day, "next Monday" said on a Monday, times already past today, 12 am / 12 pm). **Repeat rules:** 31st, Feb 29. **Exclusion engine:** every rule type × mode × pause. **Dedup.** **AI JSON validation.** | JUnit, table-driven |
| Unit (Robolectric) | Per-app notification parsers against recorded fixtures. Alarm scheduling with a fake clock. Boot and time-change rescheduling. | Robolectric shadows |
| Instrumented (on phone) | SQLCipher file is unreadable without the key. Room DAOs. Listener end-to-end with test notifications. "Excluded text never stored" end-to-end. | `connectedAndroidTest` |
| Build | Merged manifest has no `INTERNET` | Gradle check that runs with `test` |
| AI eval | Precision and recall on the private labelled set, after every prompt or model change | On-device runner + report |
| Manual (each release, both phones) | Reboot, battery saver, airplane mode, time-zone change, app swiped from Recents, after a HyperOS update | `docs/RELEASE_CHECKLIST.md` |

Test fixtures are fake messages sent between your two phones, so no real conversation ever goes into git.

---

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| HyperOS kills the listener or delays alarms on the Poco | High | Setup checklist, Health screen, rebind on disconnect, "no messages for N h" warning |
| WhatsApp / Messenger / Gmail change their notification layout | Medium | Fixture tests per app. Generic fallback parser. |
| Can't tell which WhatsApp account a notification belongs to (multi-account), or HyperOS hides a cloned app's notifications | Medium | Verify early in Phase 2 with test messages. Worst case: messages are tagged "unknown account" and per-account rules fall back to chat rules. |
| The Drive app won't accept background overwrites (weekly backups, Phase 6 sync) | Medium | Weekly one-tap "Back up now" reminder. For Phase 6, use one of the sync alternatives. |
| AI suggests wrong tasks or misses some | Medium | You confirm every suggestion first. Eval set. Auto-add only after the numbers are good. |
| AI model too big, slow or hot | Medium | Smallest model first (Gemma 3 1B, about 0.5 GB). Throttling (§5.3). `usage.ps1` measurements. E2B only with your OK. |
| Newer libraries need a newer Android Studio (Compose 1.12+ needs compileSdk 37 and AGP 9.1) | Certain over time | Versions pinned in `gradle/libs.versions.toml`. Update Android Studio, then lift the pins together. |
| A library adds the `INTERNET` permission | Low | The build check fails the build |
| Signing key lost | Low | Back up the keystore and password (password manager + offline copy) |
| Pixel 7 Pro security updates end **Oct 2027** | Certain | Fine until then. The Poco gets security updates until about early 2029. |
| Google's sideloading verification goes worldwide (2027) | Medium | Installing your own build over ADB stays allowed. Free hobbyist developer accounts exist. |

---

## 10. Decisions log and open questions

**Answered on 2026-10-05**

| Question | Answer | Effect on the plan |
|---|---|---|
| Combined task list across both phones? | Yes, later | Phase 6 added. UUID task IDs and soft deletes from Phase 1 onward. |
| Poco RAM? | 12 GB | Both phones could run larger models, but storage and CPU limits (§5.8) come first: Gemma 3 1B is tried first |
| WhatsApp setup? | Regular WhatsApp, **multiple accounts on both phones** | `accountKey` on every message, per-account exclusion rules, verified in Phase 2 |
| App name? | **Mavick** | Proposed package ID `dev.maahdi.mavick` |
| Signing-key backup location? | **Google Drive** | §5.7 B. App-data backups also go to Drive (§5.7 A). |
| Package ID? | `dev.maahdi.mavick` (debug: `dev.maahdi.mavick.debug`) | Used from Phase 0 |
| Phone resource limits? | Storage and CPU must stay low, and the phone must never hang | §5.8 budgets, enforced by build checks. Smallest AI model first. Release build for daily use. |

**Still open (not blocking)**

1. **How are the multiple WhatsApp accounts set up on each phone?** WhatsApp's own account switcher, or a clone such as Xiaomi "Dual apps"? Phase 2 will verify this on the phones either way.

---

## 11. Dev environment (checked 2026-10-05)

- **Android Studio 2025.3.1:** `S:\Programming\Android Studio`. Its bundled JDK 21 is in `jbr\`.
- **Android SDK:** `%LOCALAPPDATA%\Android\Sdk`.
  - Platforms 30, 33–36.1.
  - Build-tools 33.0.1, 35.0.0, 36.1.0.
  - adb 36.0.2. It is **not on PATH**, so the scripts will use the full path.
- `JAVA_HOME` and `ANDROID_HOME` are not set. The scripts will set them for each run.
- Neither phone has been connected over USB yet.
- Google Drive for desktop and 7-Zip are not installed. Neither is needed: upload the signing key at drive.google.com.
- **Toolchain** (pinned in `gradle/libs.versions.toml`): Gradle 9.2.1, AGP 9.0.1, Kotlin 2.3.20, KSP 2.3.12, Compose BOM 2026.06.01 (Compose 1.11), Room 2.8.5, SQLCipher 4.19.1, Robolectric 4.17. AGP 9.0 is the newest line this Android Studio can open. Compose 1.12+ would need Android Studio with AGP 9.1+ and SDK 37.

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

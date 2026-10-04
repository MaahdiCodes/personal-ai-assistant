# Personal AI Assistant — Plan

> **Status:** plan saved for review. No code has been written yet.
> **Last updated:** 2026-10-05
> **Next step:** review this plan → answer the open questions (§10) → start Phase 0.

---

## 1. Goal

A small, private Android app that:

1. Reads incoming **WhatsApp**, **Messenger** and **Gmail** notifications, skipping the chats and keywords you exclude, plus **Google Keep** notes you send to it.
2. Finds tasks, appointments and promises in them and **suggests** them as to-dos.
3. Lets you **add tasks manually**.
4. **Reminds** you at the right time and gives you a **morning briefing**.

**Hard requirements:** costs **$0**, **messages never leave the phone**, and it runs on **both** of your phones.

---

## 2. Decisions (locked)

| Decision | Choice | Why |
|---|---|---|
| Platform | Android only, native **Kotlin + Jetpack Compose** | iOS doesn't let apps read other apps' notifications. The difficult parts (notification listener, exact alarms, background work) are native Android APIs. |
| Phones | Same app on **Pixel 7 Pro** and **Poco X7 Pro**. Each runs independently because each phone has different accounts. | Both are daily phones |
| Reading messages | Android **Notification Listener** | The only safe, legitimate way. No unofficial WhatsApp/Messenger clients: they risk an account ban and need a server. |
| AI | On-device **Gemma 3n E2B** running in **LiteRT-LM**. Fallback: Gemma 3 1B. | Free and offline. Needs about 2 GB of memory. Your messages are mostly English, which it handles well. |
| Cloud AI | **None** | Free cloud tiers such as the Gemini API may use your data to improve their products, and human reviewers may read it |
| Network | App has **no `INTERNET` permission**. A build check enforces this. | Android itself then makes uploading anything impossible |
| Storage | Room + **SQLCipher**, with the database key protected by **Android Keystore** | Data is encrypted on the phone |
| Install | Android Studio / ADB over USB, signed with your own key | No Play Store, no fees, no Play rules about notification access |
| Dependency injection | Manual (`AppContainer`), no Hilt | The app is small, so explicit wiring is easier to read |
| Dates | The AI extracts the **phrase** ("next Thu 5pm"). **Deterministic Kotlin code** turns it into a date. | Small models are bad at date arithmetic. Code can be tested. The same parser serves quick-add (DRY). |
| Android versions | minSdk **33** (Android 13); target/compile SDK **36** | Both phones run Android 15+. API 33 provides `USE_EXACT_ALARM`. |

---

## 3. Sources — what can be read, and how

| Source | How | What we get | Limits |
|---|---|---|---|
| **WhatsApp** (`com.whatsapp`, `com.whatsapp.w4b`) | Notification listener, `MessagingStyle` | Sender, chat/group name, text, time. May include replies you send from the notification itself. | No messages you send in the app, no muted chats, no history before install, nothing from the chat that's open on screen |
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
- **`conversationKey`:** the notification's shortcut ID when present, because it stays the same when a contact or group is renamed. Otherwise, the conversation title.
- **Listener health:** record connect/disconnect times and call `requestRebind` after a disconnect. Warn if nothing has arrived for N hours. The warning is configurable and silent at night.
- **Logging:** message text is **never logged**.

### 5.2 Exclusions ("don't read these")

- **Rule types:** app, chat (`conversationKey`), sender, keyword (case-insensitive, whole word).
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

**Runtime**
- A WorkManager job processes the queue one message at a time.
- The model loads on first use and unloads after 5 minutes idle.
- Processing pauses when battery is below 15% and not charging (configurable).
- Expect a few seconds per message.

**Model install** (no network needed on the phone)
1. On the PC, download the free Gemma 3n E2B `.litertlm` model from Hugging Face. You'll need to accept the Gemma terms.
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
| `task` | id, title, notes, dueAt (local date-time + zone), remindAt, repeatRule, priority, status (open/done/archived), source (manual/message/keep/share), sourceExcerpt, createdAt, updatedAt |
| `message` | id, app, conversationKey, conversationTitle, sender, text, postedAt, isFromMe, dedupHash (unique), aiState (pending/skipped/done/failed) |
| `suggestion` | id, messageId, kind, title, whenText, resolvedAt, person, confidence, state (new/accepted/ignored) |
| `exclusion_rule` | id, type (app/chat/sender/keyword), value, displayName, mode, lastMatchedAt |
| `health_event` | id, type, at (**no message content**) |

Each task keeps a short `sourceExcerpt`, so deleting old messages never breaks a task.

### 5.6 Security checklist

**Data leaving the phone**
- [ ] No `INTERNET` or network-state permission. A Gradle check fails the build if any library adds one.
- [ ] No analytics, crash-reporting or ad SDKs.
- [ ] `allowBackup="false"` plus data-extraction rules that exclude everything, so nothing goes to Google cloud backup.

**Data on the phone**
- [ ] SQLCipher database. Its 256-bit random passphrase is encrypted with a non-exportable Keystore AES-GCM key.
- [ ] Fingerprint or device-PIN lock when the app opens and after 5 minutes in the background.
- [ ] `FLAG_SECURE` on the whole app, so no screenshots and a blank preview in Recents.
- [ ] Raw messages auto-deleted after **14 days** (configurable 1–90). Tasks are kept.

**Code and keys**
- [ ] Real messages are never committed to git. Private eval data lives in a git-ignored folder. Test fixtures use **fake messages sent between your two phones**.
- [ ] Release builds are signed with your own key. **Back up the keystore and its password.** Losing them means you can't update the app without uninstalling it, which wipes its data.
- [ ] Encrypted export/import for your own backups (Phase 5).

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
| **0. Foundation** | 1–2 days | Kotlin/Compose project, Room + SQLCipher, version catalog, manual DI, "no INTERNET" build check, test setup (JUnit, Robolectric, coroutines-test). PowerShell scripts: `devices.ps1`, `install.ps1` (build + install on one or both phones), `test.ps1`, `logs.ps1`. | `scripts/test.ps1` passes. App installs and opens on both phones. |
| **1. Tasks + reminders** | 1–2 wks | Today / Upcoming / Done screens, add/edit, quick-add with English date parsing ("pay rent on the 1st 10am"), **share target (Keep → Send → Assistant)**, exact reminders + actions, repeats, rescheduling, missed reminders, morning briefing, app lock, `FLAG_SECURE`. | On both phones, reminders fire within 1 min after 1 h+ with the screen off, with battery saver on, after a reboot and after a time-zone change. Date-parser and repeat tests pass. |
| **2. Message capture** | 1–2 wks | Listener and parsers (WhatsApp, WhatsApp Business, Messenger, Gmail, Keep reminders), noise filter, dedup, exclusion engine + UI, pause, Inbox screen, retention cleanup, Health screen + HyperOS checklist, debug-only "save as test fixture". | 50 test messages sent between the phones are captured with no duplicates. A test proves an excluded chat's text never reaches the database. The listener survives 48 h on the Poco. |
| **3. AI suggestions** | 2–3 wks | Model import, prefilter, `GemmaExtractor`, validation, `WhenResolver` reuse, Suggestions inbox, "never from this chat", conversation context, **Keep Takeout import**, private eval set (100–200 of your real messages, labelled) + on-device eval runner. | Precision ≥ 85% and recall ≥ 70% on your eval set. ≤ 10 s per message on both phones. No noticeable battery drain over a normal day. |
| **4. Calendar + planning** | 1–2 wks | Write tasks/events to a calendar you choose (`CalendarContract`), read the calendar for clashes and the briefing, home-screen widget, Quick Settings tile, optional auto-add with Undo. | Accepted events appear in Google Calendar. The briefing shows clashes. |
| **5. Hardening + extras** | ongoing | Encrypted backup export/import, "ask my assistant" (keyword search first, on-device Q&A later), battery profiling, signed release build, long-run reliability on HyperOS. | — |

**Total:** about 8–11 weeks part-time to finish Phase 4. **Phase 1 is useful on its own** as your reminder app.

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
| AI suggests wrong tasks or misses some | Medium | You confirm every suggestion first. Eval set. Auto-add only after the numbers are good. |
| Model too slow or uses too much memory | Low (both phones ≥ 8 GB RAM) | Gemma 3 1B fallback. Processing runs in the background only. |
| A library adds the `INTERNET` permission | Low | The build check fails the build |
| Signing key lost | Low | Back up the keystore and password (password manager + offline copy) |
| Pixel 7 Pro security updates end **Oct 2027** | Certain | Fine until then. The Poco gets security updates until about early 2029. |
| Google's sideloading verification goes worldwide (2027) | Medium | Installing your own build over ADB stays allowed. Free hobbyist developer accounts exist. |

---

## 10. Open questions

1. **One combined task list across both phones?** Default: no, each phone is independent. A free option later: both phones write to the **same Google Calendar**. That requires the same Google account on both phones, and the app still needs no network permission.
2. **Poco RAM: 8 GB or 12 GB?** Both work with Gemma 3n E2B. This only affects speed.
3. **WhatsApp Business, or Xiaomi "Dual apps"** (two WhatsApps on one phone)? The parser and listener would need to handle the second copy.
4. **App name and package ID**, e.g. `dev.maahdi.assistant`.
5. **Where to keep the signing-key backup.**

---

## 11. Dev environment (checked 2026-10-05)

- **Android Studio 2025.3.1:** `S:\Programming\Android Studio`. Its bundled JDK 21 is in `jbr\`.
- **Android SDK:** `%LOCALAPPDATA%\Android\Sdk`.
  - Platforms 30, 33–36.1.
  - Build-tools 33.0.1, 35.0.0, 36.1.0.
  - adb 36.0.2. It is **not on PATH**, so the scripts will use the full path.
- `JAVA_HOME` and `ANDROID_HOME` are not set. The scripts will set them for each run.
- Neither phone has been connected over USB yet.

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

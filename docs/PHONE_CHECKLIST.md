# Phone check: Phases 0 to 5

Do this on **each phone**. Sections 1–7 check Phases 0 + 1 (tasks and reminders): about 20
minutes of hands-on time, plus some waiting for the long reminder tests (you can use the phone
normally while you wait). Section 8 checks Phase 2 (reading messages), section 9 Phase 3
(suggested tasks and the AI model), section 10 Phase 4 (your calendar), section 11 Phase 5 (backup).

Run the scripts from the project folder in PowerShell. Add `-Phone pixel` or `-Phone poco` when
both phones are connected.

**Safe for your phone and accounts.** Mavick has no internet access, can't see other apps' data,
and changes no phone setting by itself. Once you give it Notification access (section 8), it
reads WhatsApp, Messenger, Gmail and Keep notifications only, and only reads: it never sends,
marks as read, opens or dismisses anything, so no app can tell it is there. Uninstalling it
removes everything it stored. The only settings that change are the ones you change below, and
section 7 turns the developer settings back off.

## 1. One-time setup

- [ ] **Signing key** (PC, once for both phones): `.\scripts\new-signing-key.ps1`, then back it up
      as it says (key file to Google Drive and a USB drive, password somewhere else).
- [ ] **USB debugging** on the phone: Settings > About phone > tap *Build number* (Pixel) or
      *OS version* (Poco) 7 times, then Developer options > *USB debugging* ON.
- [ ] **Poco only:** Developer options > *Install via USB* ON (may ask you to sign in to Xiaomi).
- [ ] Connect the phone by USB, unlock it, tap **Allow** on the debugging prompt. Only ever allow
      your own PC; *Always allow from this computer* is fine for it.
- [ ] `.\scripts\devices.ps1` shows the phone.

## 2. Install and first look

- [ ] `.\scripts\install.ps1 -Phone pixel` (or `poco`). Mavick opens by itself.
      - The Poco may call Mavick unknown or unverified because it isn't from an app store: tap
        *Install*.
      - If Google Play Protect warns about an unknown app or offers to scan it, install anyway.
        Scanning is fine too: the app file holds none of your data.
- [ ] Allow notifications when asked.
- [ ] Unlock with fingerprint or PIN (app lock is on by default).
- [ ] Settings (gear icon) > Health: **Encrypted storage: Ready**, **Internet access: None**,
      **Notifications: Allowed**, **On-time reminders: Allowed**.
- [ ] **Battery use:** if it says *Optimized*, tap **Fix** and choose *Unrestricted* (Pixel) or
      *No restrictions* (Poco). If Fix opens Mavick's App info instead, open its battery setting
      there.
- [ ] **Poco only:** App info > *Autostart* ON; Recents > long-press Mavick > *Lock*.

## 3. On-phone tests (real encryption hardware, real alarm)

- [ ] `.\scripts\test.ps1 -OnPhone -Phone pixel` passes. It installs a temporary "Mavick Debug"
      app and removes it afterwards; your real Mavick is not touched.
- [ ] **Poco:** before `.\scripts\test.ps1 -OnPhone -Phone poco`, turn on Developer options >
      *USB debugging (Security settings)*. The test needs it to allow notifications for the
      temporary app. Xiaomi shows a strong warning with a countdown; that is normal for this
      switch. Watch the screen and tap *Install* twice (the temporary app, then its test app).
      Turn the switch **off** again when the test is done (section 7).
- If a run stops halfway (for example, the cable comes out), "Mavick Debug" may stay installed.
  Uninstall it from its App info. Your real Mavick is a separate app and keeps its data.

## 4. Everyday use

- [ ] Quick-add `Call the bank tomorrow 5pm`: the box shows **Tomorrow · 17:00** before you add it.
- [ ] Quick-add `Take medicine every day at 9pm`: shows **Every day**.
- [ ] Tick a task: it moves to Done, and **Undo** in the bar at the bottom brings it back.
- [ ] Tap a task: the editor opens; change the time; Save.
- [ ] **Google Keep:** open a note > ⋮ > *Send* > *Mavick*: the editor opens pre-filled; Save
      returns you to Keep.

## 5. Reminders you can rely on

Create each reminder with quick-add, for example `Test in 10 minutes` (it understands "in 10 minutes").

| Test | How | Expected |
|---|---|---|
| Basic | `Test in 2 minutes`, lock the phone | Notification within a minute of the time, with Done / Snooze 10 min / Tomorrow |
| Lock screen privacy | Look at it before unlocking | Shows only "Mavick reminder", not the title |
| Snooze | Tap *Snooze 10 min* | Comes back 10 minutes later |
| Screen off for an hour | `Test in 70 minutes`, lock the phone, leave it | Arrives on time |
| Battery saver | Turn Battery Saver on, `Test in 15 minutes` | Arrives on time |
| Restart | `Test in 10 minutes`, restart the phone, unlock it | Arrives on time (or right after unlocking, marked *Missed*, if the restart took longer) |
| Time-zone change | `Test in 10 minutes`, then Settings > Date & time > turn off *Set time zone automatically* and pick a zone 1 hour ahead (e.g. Bangkok). Change **only the time zone**, never the date or the time: a wrong clock makes sign-in codes and some banking apps fail. | *Missed reminder* appears at once, because it is now past that clock time. Turn *Set time zone automatically* back on afterwards. |
| App force-stopped | `Test in 10 minutes`, swipe Mavick away in Recents (Poco: and tap the clear-all button) | Arrives on time. If not, open Mavick once: it sets alarms again and shows the reminder as missed. |
| Morning briefing | Settings > Briefing time = 2 minutes from now, with a task due today | "Your day" notification. Set the time back afterwards. |

## 6. Light on the phone

- [ ] `.\scripts\usage.ps1 -Phone pixel` with Mavick **closed**: *Running now: No*,
      *Background services: 0 running*, *Scheduled jobs: 0*, *Scheduled alarms* = number of
      pending reminders + 1 (the daily alarm). Once Notification access is on (section 8),
      Android keeps the message listener connected: *Running now: Yes* and *Background
      services: 1*, which is expected.
- [ ] Open Mavick, run it again: note memory and CPU.

## 7. When you're done

- [ ] Date & time: *Set time automatically* and *Set time zone automatically* both ON.
- [ ] Battery Saver back to how you had it.
- [ ] **Poco:** Developer options > *USB debugging (Security settings)* OFF.
- [ ] Developer options > *USB debugging* OFF (Poco: *Install via USB* OFF too), or turn Developer
      options off completely. Mavick keeps working. Turn USB debugging on again only to install
      an update or run a script.
- [ ] Optional: Developer options > *Revoke USB debugging authorizations*, so no computer stays
      trusted. Android also forgets a computer after 7 days without connecting, unless *Disable
      adb authorization timeout* is on (leave that off).
- Good to know: some banking and payment apps refuse to open while Developer options is on.
  Turning Developer options off fixes them and doesn't affect Mavick.

## Results

Fill in a column per phone, then commit this file (or tell Claude the numbers). Mavick blocks
screenshots of itself (a privacy setting, not a fault), so write the values down, or photograph
the screen with the other phone.

| Item | Pixel 7 Pro | Poco X7 Pro |
|---|---|---|
| Android version | | |
| Install OK | | |
| Health all ✓ | | |
| On-phone tests pass | | |
| Quick-add + Keep share OK | | |
| Basic reminder on time | | |
| Screen off 1 h on time | | |
| Battery saver on time | | |
| After restart | | |
| Time-zone change | | |
| After force-stop | | |
| Morning briefing | | |
| App size (usage.ps1) | | |
| Data + cache | | |
| Memory while open | | |
| CPU while idle and open | | |
| Background services / jobs | | |
| Developer settings off again (section 7) | | |
| Problems seen | | |

## 8. Phase 2: reading messages

Test with **fake** messages sent from your other phone. From now on Mavick also reads your real
WhatsApp, Messenger and Gmail notifications: that is what it is for. They stay on the phone,
encrypted, behind the app lock, and are deleted after 14 days.

### Turn it on

- [ ] Install the Phase 2 build: `.\scripts\install.ps1 -Phone pixel`. Settings shows version 0.3.0.
- [ ] Settings › Health › **Notification access** › **Fix**, then turn on **Mavick**. If Android says
      "Restricted setting": App info › ⋮ › *Allow restricted settings*, then try again. Android warns
      that the app can read all notifications: Mavick drops every app except WhatsApp, WhatsApp
      Business, Messenger, Gmail and Keep the moment their notifications arrive, and has no internet.
- [ ] Health shows **Notification access: Allowed** and **Message reading: Working**.
- [ ] **Poco only:** Health › Autostart › **Open**, turn Mavick on there, then tick the box.

### What it reads

- [ ] From the other phone, send `Test call me tomorrow at 5` to **every WhatsApp account** on this
      phone. Each appears in Mavick › **Messages** (the envelope at the top), once, under the right
      account (a clone shows "Clone (user 999)"; WhatsApp Business shows its own name).
- [ ] A message in a group shows the group's name and "Sender: text".
- [ ] Reply from WhatsApp's notification itself: the reply appears as "You: …". On the other phone,
      Mavick caused **no blue ticks**, and you don't show as online.
- [ ] A Messenger message and an email appear. The email says it is only a preview.
- [ ] A Keep reminder appears when it fires.
- [ ] Send several messages to one chat: each appears **once**, no duplicates.
- [ ] Paste a long message (about 1,500 characters) into WhatsApp on the other phone and send it.
      Open it in Mavick: it is whole, or marked "Cut short". Note which.
- [ ] Your notifications look exactly as before: Mavick never removes or changes them.

### Rules

- [ ] `my OTP is 1234` never appears (a default rule).
- [ ] Open a message › **Never read this chat** › **Stop reading**: its messages disappear, and a new
      message from that chat never appears. Messages › ⋮ › What Mavick reads shows the rule with
      "last matched just now".
- [ ] Messages › ⋮ › **Pause for 1 hour**: new messages don't appear. **Resume reading**: they do again.
- [ ] Open `Test call me tomorrow at 5` › **Add as task**: the editor shows tomorrow, 17:00.
- [ ] In Gmail or Chrome, select some text › in the menu, **Add to Mavick**: the editor opens with it.

### Reliability (these take time)

- [ ] Restart the phone, unlock it, send a test message: it arrives.
- [ ] Leave the phone for **48 hours** of normal use (the Poco matters most), then send a test
      message: it arrives. Health › Message reading says how often Android stopped it.
- [ ] `.\scripts\usage.ps1 -Phone poco`: *Background services: 1*, *Scheduled jobs: 0*. Note the
      memory: Mavick now stays running for the listener.

### Recorder session (helps finish Phase 2)

This records what WhatsApp, Messenger and Gmail really put in their notifications, so Claude can
check the parsers and find where WhatsApp names the account.

- [ ] `.\scripts\install.ps1 -DebugBuild -Phone pixel`, then give **Mavick Debug** Notification
      access too.
- [ ] `.\scripts\record-notifications.ps1 -Phone pixel -Start` (records for 60 minutes).
- [ ] Send **fake** messages from the other phone to every WhatsApp account, in a chat and in a group,
      plus Messenger and Gmail, and three long ones (about 300, 1,500 and 5,000 characters).
- [ ] `.\scripts\record-notifications.ps1 -Phone pixel -Stop`. The files land in the `recordings`
      folder (never committed). Tell Claude they are there.
- [ ] Afterwards, Settings › Notification access › turn **Mavick Debug** off (or uninstall it).

### Results (Phase 2)

| Item | Pixel 7 Pro | Poco X7 Pro |
|---|---|---|
| Notification access allowed, reading Working | | |
| WhatsApp accounts: how many, how set up | | |
| Each account's messages captured, under the right account | | |
| No blue ticks or "online" caused by Mavick | | |
| Messenger / Gmail / Keep reminder | | |
| No duplicates | | |
| Long message: whole or cut short (how long) | | |
| OTP rule / Never read this chat / Pause | | |
| Add as task / Add to Mavick | | |
| After restart / after 48 hours | | |
| usage.ps1: services, memory while the listener runs | | |
| Recordings made | | |
| Problems seen | | |

## 9. Phase 3: suggested tasks and the AI model

Mavick now looks for tasks in the messages it reads, on the phone. Without a model, simple rules
do it; with the AI model imported, the AI does. Nothing becomes a task until you tap **Add**.
Still no internet: the model arrives as a file from the PC.

### Without a model first

- [ ] Install the Phase 3 build: `.\scripts\install.ps1 -Phone pixel`. Settings shows version 0.4.0 or newer.
- [ ] Settings › Suggestions: **Suggest tasks from messages** is on, **AI model: None**.
- [ ] From the other phone send `Can you pay the rent tomorrow at 10am?`. Within a minute the task
      list shows **"Mavick found 1 possible task in your messages"** and a quiet "1 suggested task to
      review" notification appears (no sound). The lock screen shows only "Mavick suggestions".
- [ ] **Review**: the card says *Simple rules*, tomorrow · 10:00, and quotes the message.
- [ ] **Add**: the task appears in Upcoming, with a reminder at 10:00; **Undo** takes it back.
- [ ] Send `haha nice photo`: no suggestion (stopped by the prefilter).

### The AI model

- [ ] On the PC, download **gemma3-1b-it-int4.litertlm** (about 557 MB) from
      https://huggingface.co/litert-community/Gemma3-1B-IT (sign in and accept the Gemma terms first).
- [ ] `.\scripts\push-model.ps1 -Model <the file> -Phone pixel`: it ends with "The copy matches".
- [ ] Settings › Suggestions › **Import model**, pick the file in Downloads. It copies (progress shown),
      then checks itself: **"Imported. It works: a test message took … s."** Note the time.
- [ ] Accept **Delete the downloaded copy** (Mavick keeps its own).
- [ ] Send `Can you send me the signed form by Thursday 5pm?`. The suggestion appears without
      *Simple rules*, with a short to-do title, Thursday · 17:00.
- [ ] Send `Reminder: school fees must be paid by the end of the month`. If suggested, the card shows
      the words and **Add…** opens the editor to pick a date.
- [ ] Settings › Suggestions shows "Answers in about … s per message" and the counts.
- [ ] Turn on Battery Saver, send a message with a task: no suggestion yet, and Settings says
      "waiting: Battery Saver is on". Turn it off and open Mavick: the suggestion appears.
- [ ] A suggestion's ⋮ › **Never read this chat**: the chat's messages and suggestions go.
- [ ] The morning briefing (section 5) mentions suggestions waiting.

### Google Keep (Takeout)

- [ ] At takeout.google.com, export **Keep** only, as a .zip. Copy the .zip to the phone's Download
      folder.
- [ ] Settings › Google Keep › **Import notes from Google Takeout…**, pick the .zip. Your notes are
      listed with none ticked; tick a few, **Add N tasks**. Notes in the bin don't appear.

### Measurements (both phones)

- [ ] `.\scripts\push-model.ps1 -Model <the file> -Phone pixel -ForTests`, then
      `.\scripts\test.ps1 -OnPhone -Phone pixel`: the AI runtime tests pass.
- [ ] `.\scripts\eval.ps1 -Phone pixel -Set eval\sample.csv`: it prints a report (made-up messages).
- [ ] With a model loaded (right after a suggestion), `.\scripts\usage.ps1 -Phone pixel`: note the
      memory. A minute later, run it again: the memory should drop back as the model is closed.
- [ ] A normal day with the model: battery use for Mavick (Settings › Battery) and whether the phone
      got warm.
- [ ] The accuracy check on your own messages: [eval/README.md](../eval/README.md).

### Results (Phase 3)

| Item | Pixel 7 Pro | Poco X7 Pro |
|---|---|---|
| Rules suggestion, notification, Add / Undo | | |
| Model import: time to copy, check result and time | | |
| AI suggestion seconds (Settings) | | |
| Battery Saver pause and resume | | |
| Keep Takeout import | | |
| On-phone AI tests pass | | |
| Memory with the model loaded / a minute later | | |
| Battery use over a day, warmth | | |
| Accuracy check: precision / recall / seconds | | |
| Problems seen | | |

## 10. Phase 4: your calendar

Mavick can now write tasks that have a **date and a time** into a calendar you choose, as 30-minute
events, and keep them in step with the task. It is off until you switch it on. Mavick itself still
has no internet; if the calendar you choose syncs with Google, the Calendar app uploads the event
(its title and time). Part 1 is covered here; the clash warnings and the widget come with later parts.

Use a **test calendar** for the first run if you can: in Google Calendar (on the web) make a
calendar called *Mavick test*, which can show on the phone, and choose that one below. Delete it at
the end.

### Switching on

- [ ] Install the Phase 4 build: `.\scripts\install.ps1 -Phone pixel`. Settings shows version 0.5.0.
- [ ] Settings › Calendar: the switch is off. Nothing was asked at install.
- [ ] Switch it on. Android asks for **Calendar** (both permissions in one prompt): allow. The list
      of calendars appears, each with its account. Pick the test calendar.
- [ ] Settings › Calendar now shows the calendar's name and "N tasks are in the calendar".
- [ ] **Refuse** the permission instead (try it once, then allow in App info): Settings says the
      calendar permission is off, with **Fix** opening Mavick's App info. Allowing it there and coming
      back clears the message.

### Following the task

Do each step in Mavick, then open the Calendar app (or Google Calendar) and look.

- [ ] Add `Call the bank today 5pm`: a 30-minute event "Call the bank" appears at 17:00. The event
      shows no alarm of its own, and no notes. On a calendar shared with someone, it shows as private.
- [ ] Edit the task's title and its time: the event changes (no second event).
- [ ] Add `Pay rent tomorrow` (a date, no time): **no** event. Then give it a time: the event appears.
- [ ] Mark the first task **Done**: its event disappears. **Undo** (or reopen from Done): it comes back.
- [ ] Delete a task and undo the delete: the event goes and comes back.
- [ ] A daily repeating task with a time: mark it done, and its event moves to the next day.
- [ ] In the Calendar app, **delete** one of Mavick's events. Mavick does not put it back by itself.
      Edit that task in Mavick (change the title): the event is written again.
- [ ] Tasks overdue from before today are **not** copied when you switch on (only today and later).

### Switching off, and changing calendar

- [ ] Choose **Change** and pick another calendar: the events move there (gone from the first).
- [ ] Switch the feature off: Mavick's events disappear from the calendar; your own events stay.
- [ ] Switch it on again: the events come back.
- [ ] With the feature on, turn the calendar permission off in App info and reopen Settings: it
      says so. Mark a task done meanwhile, then allow the permission again and open Mavick: the stale
      event is removed (the next restart, time change or morning alarm also tidies up).
- [ ] Change the phone's time zone (Settings › System › Date & time), look at an event, change it
      back: the event keeps the task's clock time (5 pm stays 5 pm).

### Clash warnings (part 2)

In Google Calendar (or the Calendar app) make a timed event for today, say *Dentist* from 17:00 to 18:00,
in a calendar that shows on the phone.

- [ ] Settings › Calendar › **Warn me about clashes**: switch on. If the calendar permission wasn't
      given yet, Android asks (allow). **Calendars to check** says *Every calendar*.
- [ ] Add a task `Call the bank today 5.15pm`. In the lists its row has a red line
      **"Clashes with Dentist at 17:00"** (the time may show as 5:00 PM).
- [ ] Open that task in the editor: the same warning is under the time. Change the time to 7 pm: it
      goes. Back to 5.15 pm: it returns.
- [ ] A new task for 4 pm (it would end at 4:30): **no** warning (the Dentist starts at 5).
- [ ] An **all-day** event today (a holiday, say) does not cause a warning. An event set to *Free*
      doesn't either. One you **declined** doesn't.
- [ ] Mavick's own copy of a task in your calendar (part 1 on) is not a clash with itself.
- [ ] **Calendars to check**: tick only a different calendar; the Dentist warning goes. Untick all:
      back to *Every calendar*, and it returns.
- [ ] Morning briefing: set the briefing time a minute ahead, or wait until morning. It shows
      **"1 clash with your calendar"** and a line "Clash: 17:00 Call the bank, with Dentist" first. The
      lock-screen version still says only "Mavick: your day".
- [ ] Switch clash warnings off: the warnings disappear from the list and the editor.
- [ ] Take the calendar permission away in App info: Settings says so, with **Fix**; no warning
      appears and nothing crashes.

### Widget and Quick Settings tile (part 3)

- [ ] Press and hold on the home screen › Widgets › **Mavick: today**: drag it out. It shows today's
      date, e.g. "Mon 5 Oct", and "N tasks due today".
- [ ] Today's tasks are listed as "17:00  Call the bank". An overdue one is red: "Overdue: …". More
      than five show "+N more".
- [ ] Add a task in Mavick, mark one done, delete one: the widget changes **within a second or two**,
      with no refresh from you.
- [ ] Tap a task line: Mavick opens that task (after the app lock). Tap the heading: Mavick opens. Tap
      **+**: a new empty task opens; closing it returns to the widget's screen.
- [ ] Settings › Home screen › switch **Show task titles on the widget** off: the lines go and the
      widget says "Titles are hidden"; the counts stay. On again: titles return.
- [ ] Restart the phone and **don't unlock it yet**, then unlock: the widget may say "Open Mavick to see
      your tasks" for a moment, then shows the tasks (after the first unlock or opening Mavick).
- [ ] Next morning (or change the date): the widget header may still show yesterday until Mavick's
      morning alarm or any task change: **note whether it did, and how long it stayed.**
- [ ] Quick Settings: pull down twice, tap the pencil, drag **New task** into the panel. Tap it: Mavick
      opens a new task. With the phone locked, it asks to unlock first.
- [ ] `.\scripts\usage.ps1 -Phone pixel`: services now 2 (the message listener and the tile, which
      Android binds only while the panel shows); alarms and jobs unchanged.

### Results (Phase 4, part 1)

| Item | Pixel 7 Pro | Poco X7 Pro |
|---|---|---|
| Permission prompt, calendar list, picking a calendar | | |
| Event appears, edits, done / undo, delete / undo, repeat | | |
| Date-only task: no event; adding a time creates it | | |
| Event deleted in Calendar app: stays gone until the task changes | | |
| Change calendar, switch off and on | | |
| Permission off: message, Fix, tidy-up after allowing | | |
| Time zone change | | |
| Events show as private, no alarm, no notes | | |
| Clash warning: list row, editor, briefing; free, all-day and declined events ignored | | |
| Calendars to check, and permission off | | |
| Widget: shows tasks, follows changes at once, taps, hide titles, "+N more" | | |
| Widget after restart before unlock; how stale after midnight | | |
| Quick Settings tile: add it, tap it, locked phone | | |
| usage.ps1: services (expect 2), alarms, jobs | | |
| On-phone calendar tests pass (`test.ps1 -OnPhone`) | | |
| Problems seen | | |

## 11. Phase 5: backup and restore

A backup is a file protected by a password you choose. It holds your tasks (also finished and
deleted ones), the rules about what Mavick reads and your settings. **Never your messages.** You
save it through Android's *Save to…* screen, for example into Google Drive.

- [ ] Settings › Backup › **Back up now…**. A password shorter than 8 characters, or two passwords that
      differ, are refused with a message. Choose a password you will remember, e.g. `test-backup-1`.
- [ ] Android's *Save to…* screen opens with the name `mavick-backup-<date>.mavickbackup`. Choose **Google
      Drive**. It says "Backup saved: N tasks." and Settings shows **Last backup: just now**.
- [ ] Open the file in Drive: it is unreadable (it is encrypted). Drive's preview shows nothing useful.
- [ ] **Restore from a backup…** on the **same** phone, pick that file. A wrong password says so and lets
      you try again. The right one shows **"This phone already has everything in it."** (nothing changes).
- [ ] Change a task here (rename it), then restore the same file: the preview says **"1 task you changed
      since stays as it is."** Restore: your edit is **kept**.
- [ ] Delete a task here, then restore the older file: the task **stays deleted**.
- [ ] **On the other phone** (install the same version): Settings › Backup › Restore from a backup…, pick
      the file from Drive (it may need a moment to download), type the password. The preview lists the new
      tasks and reading rules. Restore with **Also restore my settings** ticked: the tasks appear, the
      briefing time and other settings match the first phone. **Its calendar choice and reading pause are
      its own and are not changed.**
- [ ] A reminder in the restored tasks that is still ahead rings on the other phone; one already past does not.
- [ ] Restore the same file again: "This phone already has everything in it."
- [ ] Pick a file that is not a backup (a photo): "That isn't a Mavick backup." and nothing changes.
- [ ] Messages: after restoring on the other phone, its Inbox does **not** contain the first phone's messages.
- [ ] Time it: how long did *Make backup* and *Open* take (the password is stretched on purpose)? Note it.
- [ ] Whether Drive lets you **overwrite** the same file from the Save screen next week (this decides
      whether a weekly automatic backup is possible): note what happened.

### Results (Phase 5)

| Item | Pixel 7 Pro | Poco X7 Pro |
|---|---|---|
| Backup saved to Drive, "Last backup" shown | | |
| Wrong password refused, right one opens | | |
| Restore on the same phone: nothing changes; own edits kept; deletions kept | | |
| Restore on the other phone: tasks, rules, settings; own calendar choice kept | | |
| Time to make / open a backup | | |
| Messages not in the backup | | |
| Drive overwrite of the same file | | |
| Problems seen | | |

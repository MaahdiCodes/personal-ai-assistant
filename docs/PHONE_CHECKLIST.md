# Phone check: Phases 0 + 1

Do this on **each phone** before Phase 2 starts. It takes about 20 minutes of hands-on time, plus
some waiting for the long reminder tests (you can use the phone normally while you wait).

Run the scripts from the project folder in PowerShell. Add `-Phone pixel` or `-Phone poco` when
both phones are connected.

## 1. One-time setup

- [ ] **Signing key** (PC, once for both phones): `.\scripts\new-signing-key.ps1`, then back it up
      as it says (key file to Google Drive and a USB drive, password somewhere else).
- [ ] **USB debugging** on the phone: Settings > About phone > tap *Build number* (Pixel) or
      *OS version* (Poco) 7 times, then Developer options > *USB debugging* ON.
- [ ] **Poco only:** Developer options > *Install via USB* ON (may ask you to sign in to Xiaomi).
- [ ] Connect the phone by USB, unlock it, tap **Allow** on the debugging prompt.
- [ ] `.\scripts\devices.ps1` shows the phone.

## 2. Install and first look

- [ ] `.\scripts\install.ps1 -Phone pixel` (or `poco`). Mavick opens by itself.
- [ ] Allow notifications when asked.
- [ ] Unlock with fingerprint or PIN (app lock is on by default).
- [ ] Settings (gear icon) > Health: **Encrypted storage: Ready**, **Internet access: None**,
      **Notifications: Allowed**, **On-time reminders: Allowed**.
- [ ] **Battery use:** if it says *Optimized*, tap **Fix** and choose *Unrestricted* (Pixel) or
      *No restrictions* (Poco).
- [ ] **Poco only:** App info > *Autostart* ON; Recents > long-press Mavick > *Lock*.

## 3. On-phone tests (real encryption hardware, real alarm)

- [ ] `.\scripts\test.ps1 -OnPhone -Phone pixel` passes. It installs a temporary "Mavick Debug"
      app and removes it afterwards; your real Mavick is not touched.

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
| Time-zone change | `Test in 10 minutes`, then Settings > Date & time > set the time zone to one 1 hour ahead (e.g. Bangkok) | *Missed reminder* appears at once, because it is now past that clock time. Set the time zone back to automatic afterwards. |
| App force-stopped | `Test in 10 minutes`, swipe Mavick away in Recents (Poco: and tap the clear-all button) | Arrives on time. If not, open Mavick once: it sets alarms again and shows the reminder as missed. |
| Morning briefing | Settings > Briefing time = 2 minutes from now, with a task due today | "Your day" notification. Set the time back afterwards. |

## 6. Light on the phone

- [ ] `.\scripts\usage.ps1 -Phone pixel` with Mavick **closed**: *Running now: No*,
      *Background services: 0 running*, *Scheduled jobs: 0*, *Scheduled alarms* = number of
      pending reminders + 1 (briefing).
- [ ] Open Mavick, run it again: note memory and CPU.

## Results

Fill in a column per phone, then commit this file (or tell Claude the numbers).

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
| Problems seen | | |

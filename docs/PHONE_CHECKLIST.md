# Phone check: Phases 0 + 1

Do this on **each phone** before Phase 2 starts. It takes about 20 minutes of hands-on time, plus
some waiting for the long reminder tests (you can use the phone normally while you wait).

Run the scripts from the project folder in PowerShell. Add `-Phone pixel` or `-Phone poco` when
both phones are connected.

**Safe for your phone and accounts.** This version of Mavick has no internet access, reads no
messages, can't see other apps or their data, and changes no phone setting by itself.
Uninstalling it removes everything it stored. The only settings that change are the ones you
change below, and section 7 turns the developer settings back off.

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
      pending reminders + 1 (briefing).
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

---
name: mavick-phase-status
description: "Mavick (Android AI assistant) state as of 2026-10-07: Phases 0-3 coded; Phase 4 fully coded; Phase 5 backup core coded; first on-phone automated test pass; manual phone checklist (sections 10-11) still pending"
metadata:
  node_type: memory
  type: project
  originSessionId: ddd8a6d4-1d96-4101-96d8-89e10b16230f
  modified: 2026-10-08T12:24:08.771Z
---

Mavick is the user's private on-device Android assistant. Source of truth: docs/PLAN.md section 0 ("Start here").

**State (2026-10-07):**
- Phases 0-3 coded and pushed. Phase 4 (calendar events, clash warnings, widget, Quick Settings tile) fully coded. Phase 5's core coded: encrypted backup file and restore with merge, Settings > Backup. Version 0.5.0, database v5, 1,081 PC tests, Lint clean, release APK 25.7 MB.
- The user asked (2026-10-07) to finish everything that needs no phone first, then test. Done in order: Phase 4 parts 2 and 3, then the Phase 5 backup core. Left in PLAN section 0.3: keyword search ("ask my assistant"), then the Phase 6 merge core (two phones' lists). After that everything left needs a phone.
- Phase 4: tasks with date AND time become 30-minute events in a calendar the user picks (off by default; READ/WRITE_CALENDAR asked only on switching on; if the calendar syncs with Google the Calendar app uploads them). Clash warnings (off by default) in briefing, list rows, editor. Widget: plain RemoteViews, five lines, no timer, titles shown with a Settings switch to hide them. Tile: Mavick's second service (CLAUDE.md updated). `TaskRepository` has a list of `TaskChangeListener`s (calendar, widget).
- Phase 5 backup: password-encrypted file (AES-256-GCM, PBKDF2 600k rounds) the user saves through Android's Save screen (Drive); holds tasks incl. deleted, rules, travelling settings, never messages. Restore shows a preview, merges by task UUID, newest `updatedAt` wins, same answer from either side (`TaskMerge`, reused by Phase 6). No automatic weekly backup (needs a phone to test Drive overwrite).
- Phase 3 open issue (first phone test 2026-10-06): suggestion titles copy the message wording and run on; the due time may not match. Not diagnosed; measure with the accuracy check before changing the prompt.
- **2026-10-07: first `test.ps1 -OnPhone` run, on the Pixel.** `install.ps1 -Phone pixel` (0.5.0) and the full automated suite both ran clean: 1,081 PC tests, Lint, permission/read-only checks, and 22 on-phone instrumented tests, including `CalendarGatewayDeviceTest` (Phase 4) run for the first time. 2 tests skipped as expected (no AI model imported). Fixed a flaky `TasksViewModelTest` found along the way (a cross-thread race between Room's query executor and the test thread; needed a `CopyOnWriteArrayList`). Pushed to `develop` (commit c7dfa2c). This automated pass does **not** cover PHONE_CHECKLIST.md sections 10-11 (calendar, clashes, widget, tile, backup/restore) — that's a manual, hands-on walkthrough on the real phone, which Claude cannot drive or verify itself (Mavick blocks screenshots of itself, by design, so there's no way to see the screen even via adb).
- 2026-10-07: user reports the calendar switch-on works (permission, picking a calendar, events appear). Rest of §10 not reported yet.
- **2026-10-08 (Pixel): the AI runtime works on the phone.** `LiteRtLmDeviceTest` passes (model pushed with `push-model.ps1 -ForTests`; the Gemma file is at `%USERPROFILE%\Downloads\gemma3-1b-it-int4.litertlm`). Sample accuracy check (`eval.ps1 -Set eval\sample.csv`, 11 made-up messages): AI precision 100%, recall 85.7%, but **about 30 s per message (target 10 s)**, and titles and dates no better than the simple rules. The likely cause: 2 threads at background priority, which Android runs on the small cores, plus re-reading the system prompt for every message. The user must decide on speed (PLAN §0.3 item 4). Widget and tile not placed yet. No crashes on record.
- `eval/private/` is off limits even for sample-set reports (the auto-mode classifier blocked reading details.csv): use only the printed report numbers.
- No phase has passed its full **manual** phone check yet. No `phase-N` tags exist.

**Waiting on the user:** the rest of PHONE_CHECKLIST.md §10 (edits, done/undo, repeat, change calendar, clashes, widget, tile) and §11 (backup/restore) by hand on the Pixel; the AI speed decision; labelling real messages for eval/; the Poco (full checklist); the recorder session (§8).

**Why:** the user pauses between sessions and works on more than one PC; the phone checks decide whether tags get made.
**How to apply:** when resuming, read docs/PLAN.md section 0 first, then ask what the phone checks found before changing code. On a new PC, follow docs/SETUP_NEW_PC.md.
Related: [[mavick-update-memory-every-phase]], [[mavick-other-pc]], [[mavick-explain-phase-first]]

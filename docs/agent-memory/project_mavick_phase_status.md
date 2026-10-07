---
name: mavick-phase-status
description: "Mavick (Android AI assistant) state as of 2026-10-07: Phases 0-3 coded; Phase 4 fully coded; Phase 5 backup core coded; search and Phase 6 merge core remain; no phone check passed yet"
metadata:
  node_type: memory
  type: project
  originSessionId: ddd8a6d4-1d96-4101-96d8-89e10b16230f
  modified: 2026-10-07T00:49:20.324Z
---

Mavick is the user's private on-device Android assistant. Source of truth: docs/PLAN.md section 0 ("Start here").

**State (2026-10-07):**
- Phases 0-3 coded and pushed. Phase 4 (calendar events, clash warnings, widget, Quick Settings tile) fully coded. Phase 5's core coded: encrypted backup file and restore with merge, Settings > Backup. Version 0.5.0, database v5, 1,081 PC tests, Lint clean, release APK 25.7 MB.
- The user asked (2026-10-07) to finish everything that needs no phone first, then test. Done in order: Phase 4 parts 2 and 3, then the Phase 5 backup core. Left in PLAN section 0.3: keyword search ("ask my assistant"), then the Phase 6 merge core (two phones' lists). After that everything left needs a phone.
- Phase 4: tasks with date AND time become 30-minute events in a calendar the user picks (off by default; READ/WRITE_CALENDAR asked only on switching on; if the calendar syncs with Google the Calendar app uploads them). Clash warnings (off by default) in briefing, list rows, editor. Widget: plain RemoteViews, five lines, no timer, titles shown with a Settings switch to hide them. Tile: Mavick's second service (CLAUDE.md updated). `TaskRepository` has a list of `TaskChangeListener`s (calendar, widget).
- Phase 5 backup: password-encrypted file (AES-256-GCM, PBKDF2 600k rounds) the user saves through Android's Save screen (Drive); holds tasks incl. deleted, rules, travelling settings, never messages. Restore shows a preview, merges by task UUID, newest `updatedAt` wins, same answer from either side (`TaskMerge`, reused by Phase 6). No automatic weekly backup (needs a phone to test Drive overwrite).
- Nothing in Phases 4-5 has run on a phone. `CalendarGatewayDeviceTest` is written and compiles but has never run (no phone was connected).
- Phase 3 open issue (first phone test 2026-10-06): suggestion titles copy the message wording and run on; the due time may not match. Not diagnosed; measure with the accuracy check before changing the prompt.
- No phase has passed its full phone check. No `phase-N` tags exist yet.

**Waiting on the user:** install 0.5.0 on the Pixel, the phone checks (PHONE_CHECKLIST.md sections 9, 10 and 11; use a throwaway test calendar first; add the widget and the tile; make a backup and restore it on the other phone), the Poco, the recorder session (section 8), labelling messages for eval/.

**Why:** the user pauses between sessions and works on more than one PC; the phone checks decide whether tags get made.
**How to apply:** when resuming, read docs/PLAN.md section 0 first, then ask what the phone checks found before changing code. On a new PC, follow docs/SETUP_NEW_PC.md.
Related: [[mavick-update-memory-every-phase]], [[mavick-other-pc]], [[mavick-explain-phase-first]]

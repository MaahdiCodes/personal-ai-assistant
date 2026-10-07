---
name: mavick-phase-status
description: "Mavick (Android AI assistant) state as of 2026-10-07: Phases 0-3 coded; Phase 4 (calendar, clashes, widget, tile) fully coded; Phase 5 backup core next; no phone check passed yet"
metadata:
  node_type: memory
  type: project
  originSessionId: ddd8a6d4-1d96-4101-96d8-89e10b16230f
  modified: 2026-10-07T00:32:33.723Z
---

Mavick is the user's private on-device Android assistant. Source of truth: docs/PLAN.md section 0 ("Start here").

**State (2026-10-07):**
- Phases 0, 1, 2, 3 are coded and pushed. Phase 4 is fully coded (parts 1-3), started before Phase 3 passed its phone check, at the user's request (precedent: Phases 2 and 3 were also built before earlier checks). Version 0.5.0, database v5, 947 PC tests, Lint clean, release APK 25.7 MB.
- The user asked (2026-10-07) to finish the work that needs no phone first, then test: Phase 4 parts 2 and 3 (done), then Phase 5's PC-testable core: encrypted backup and restore with merge by task UUID, newest `updatedAt` wins. Then keyword search and the Phase 6 merge. Order is in PLAN section 0.3.
- Part 1: tasks with a date AND time become 30-minute events in a calendar the user picks (off by default; `calendar_link` table). Permissions READ_CALENDAR, WRITE_CALENDAR asked only on switching on. If the calendar syncs with Google, the Calendar app uploads the events.
- Part 2: clash warnings (off by default) in the briefing, task list rows and the editor; free, declined, cancelled, all-day events, Mavick's own events and other tasks never count; calendars to check are chosen.
- Part 3: home-screen widget (plain RemoteViews, five lines, no timer, titles shown with a Settings switch to hide them) and a Quick Settings "New task" tile (Mavick's second service, allowed on purpose; CLAUDE.md updated to say so). `TaskRepository` now has a list of `TaskChangeListener`s (calendar, widget).
- Nothing in Phase 4 has run on a phone. `CalendarGatewayDeviceTest` is written and compiles but has never run (no phone was connected).
- Phase 3 open issue (first phone test 2026-10-06): suggestion titles copy the message wording and run on; the due time may not match. Not diagnosed; measure with the accuracy check before changing the prompt.
- No phase has passed its full phone check. No `phase-N` tags exist yet.

**Waiting on the user:** install 0.5.0 on the Pixel, the phone checks (PHONE_CHECKLIST.md sections 9 and 10; use a throwaway test calendar first; add the widget and the tile), the Poco, the recorder session (section 8), labelling messages for eval/.

**Why:** the user pauses between sessions and works on more than one PC; the phone checks decide whether tags get made.
**How to apply:** when resuming, read docs/PLAN.md section 0 first, then ask what the phone checks found before changing code (they may change Phase 5). On a new PC, follow docs/SETUP_NEW_PC.md.
Related: [[mavick-update-memory-every-phase]], [[mavick-other-pc]], [[mavick-explain-phase-first]]

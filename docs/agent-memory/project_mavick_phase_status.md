---
name: mavick-phase-status
description: "Mavick (Android AI assistant) state as of 2026-10-07: Phases 0-3 coded; Phase 4 part 1 (tasks to calendar) coded, parts 2-3 not started; no phone check passed yet; what the user must do next"
metadata:
  node_type: memory
  type: project
  originSessionId: ddd8a6d4-1d96-4101-96d8-89e10b16230f
  modified: 2026-10-06T19:03:10.204Z
---

Mavick is the user's private on-device Android assistant. Source of truth: docs/PLAN.md section 0 ("Start here").

**State (2026-10-07):**
- Phases 0, 1, 2, 3 are coded and pushed. Phase 4 is in progress, started before Phase 3 passed its phone check, at the user's request (precedent: Phases 2 and 3 were also built before earlier checks).
- Phase 4 part 1 (tasks with a date AND time become 30-minute events in a calendar the user picks; off by default; `calendar/` package, `calendar_link` table, database v5, version 0.5.0) is coded and tested: 812 PC tests, Lint clean, release APK 25.6 MB. New permissions READ_CALENDAR and WRITE_CALENDAR, asked only when the user switches the feature on. If the calendar syncs with Google, the Calendar app uploads the events (title and time): Settings says so.
- Part 1 has NOT run on a phone, and `CalendarGatewayDeviceTest` was written but never run (no phone was connected).
- Part 2 (clashes in the briefing and on tasks) and part 3 (home-screen widget showing titles with a hide setting, plain RemoteViews not Glance; Quick Settings tile as a second allowed service) are planned in PLAN section 5.9. Auto-add with Undo waits for Phase 3 accuracy numbers.
- User's choices for Phase 4 (asked 2026-10-07, took the recommendations): only tasks with a time go to the calendar; widget shows titles with a setting to hide them.
- Phase 3 open issue (first phone test 2026-10-06): suggestion titles copy the message wording and run on ("...is mentioned"); the due time may not match. Not yet diagnosed; measure with the accuracy check before changing the prompt.
- No phase has passed its full phone check. No `phase-N` tags exist yet.

**Waiting on the user:** install 0.5.0 on the Pixel, the phone checks (PHONE_CHECKLIST.md sections 9 and 10; use a throwaway test calendar first), the Poco, the recorder session (section 8), labelling messages for eval/.

**Why:** the user pauses between sessions and works on more than one PC; the phone checks decide whether tags get made.
**How to apply:** when resuming, read docs/PLAN.md section 0 first, then ask what the phone checks found before changing code (they may change the design of Phase 4 parts 2-3). On a new PC, follow docs/SETUP_NEW_PC.md.
Related: [[mavick-update-memory-every-phase]], [[mavick-other-pc]], [[mavick-explain-phase-first]]

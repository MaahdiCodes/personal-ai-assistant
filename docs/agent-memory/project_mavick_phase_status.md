---
name: mavick-phase-status
description: "Mavick (Android AI assistant) state as of 2026-10-07: Phases 0-3 coded; Phase 4 parts 1 (tasks to calendar) and 2 (clash warnings) coded, part 3 (widget, tile) next, then the Phase 5 backup core; no phone check passed yet"
metadata:
  node_type: memory
  type: project
  originSessionId: ddd8a6d4-1d96-4101-96d8-89e10b16230f
  modified: 2026-10-07T00:19:59.102Z
---

Mavick is the user's private on-device Android assistant. Source of truth: docs/PLAN.md section 0 ("Start here").

**State (2026-10-07):**
- Phases 0, 1, 2, 3 are coded and pushed. Phase 4 is in progress, started before Phase 3 passed its phone check, at the user's request (precedent: Phases 2 and 3 were also built before earlier checks).
- The user asked (2026-10-07) to finish the work that needs no phone first, then test: Phase 4 part 2, part 3, then Phase 5's PC-testable core (encrypted backup and restore with merge by task UUID, newest `updatedAt` wins). Order is in PLAN section 0.3.
- Part 1 (tasks with a date AND time become 30-minute events in a calendar the user picks; off by default; `calendar/`, `calendar_link` table, database v5, version 0.5.0) is coded. New permissions READ_CALENDAR and WRITE_CALENDAR, asked only when the user switches the feature on. If the calendar syncs with Google, the Calendar app uploads the events (title and time): Settings says so.
- Part 2 (clash warnings; off by default; Settings > Calendar > Warn me about clashes) is coded: a timed task overlapping a busy, timed event warns in the morning briefing, the task list rows and the editor. Free, declined, cancelled, all-day events, Mavick's own events and other tasks never count. Calendars to check: all visible, a picker narrows. 903 PC tests, Lint clean, release APK 25.6 MB.
- Part 3 (not started): plain RemoteViews widget (no Glance, no service) showing titles with a hide setting, and a Quick Settings tile (second service, allowed on purpose in the build).
- Nothing in Phase 4 has run on a phone. `CalendarGatewayDeviceTest` is written and compiles but has never run (no phone was connected).
- Phase 3 open issue (first phone test 2026-10-06): suggestion titles copy the message wording and run on; the due time may not match. Not diagnosed; measure with the accuracy check before changing the prompt.
- No phase has passed its full phone check. No `phase-N` tags exist yet.

**Waiting on the user:** install 0.5.0 on the Pixel, the phone checks (PHONE_CHECKLIST.md sections 9 and 10; use a throwaway test calendar first), the Poco, the recorder session (section 8), labelling messages for eval/.

**Why:** the user pauses between sessions and works on more than one PC; the phone checks decide whether tags get made.
**How to apply:** when resuming, read docs/PLAN.md section 0 first, then ask what the phone checks found before changing code (they may change the design of Phase 4 part 3 and Phase 5). On a new PC, follow docs/SETUP_NEW_PC.md.
Related: [[mavick-update-memory-every-phase]], [[mavick-other-pc]], [[mavick-explain-phase-first]]

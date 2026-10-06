---
name: mavick-phase-status
description: Mavick (Android AI assistant) state as of 2026-10-06: Phases 0-3 coded and pushed, first phone test done on the Pixel, open issue with suggestion titles, what the user must do next
metadata:
  type: project
---

Mavick is the user's private on-device Android assistant. Source of truth: docs/PLAN.md section 0 ("Start here").

**State (2026-10-06):**
- Phases 0, 1, 2, 3 are coded, tested (704 PC tests, Lint clean) and pushed to `develop` and `main`. Version 0.4.0.
- No phase has passed its full phone check. No `phase-N` tags exist yet.
- First phone test (Pixel 7 Pro, 2026-10-06): model imported with `push-model.ps1` (checksum matched) and imported in Settings. Two suggestions appeared from two test messages. "Pay Rent" looked right. The second title was the whole sentence copied, including "…is mentioned" and the time in a different form. The model copies message wording into titles instead of writing short to-dos. Not yet diagnosed. Next: check whether the due time is right, then tune the prompt with the accuracy check.
- Release APK 25.6 MB; budget raised 8 -> 30 MB on purpose (LiteRT-LM native lib is 21.5 MB).

**Waiting on the user:** the rest of the Pixel checks (PHONE_CHECKLIST.md section 9), the Poco, the recorder session (section 8), and labelling messages for eval/.

**Why:** the user pauses between sessions and works on more than one PC; the phone checks decide whether tags get made.
**How to apply:** when resuming, read docs/PLAN.md section 0 first, then ask what the phone checks found before changing code. On a new PC, follow docs/SETUP_NEW_PC.md.
Related: [[mavick-update-memory-every-phase]], [[mavick-other-pc]]

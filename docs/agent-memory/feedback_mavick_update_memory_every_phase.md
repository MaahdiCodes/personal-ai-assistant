---
name: mavick-update-memory-every-phase
description: "After every piece of Mavick work (not only each phase), update docs/PLAN.md and the memory, copy memory into docs/agent-memory, commit and push, so any other device finds the current state"
metadata:
  node_type: memory
  type: feedback
  originSessionId: ddd8a6d4-1d96-4101-96d8-89e10b16230f
  modified: 2026-10-07T00:04:20.228Z
---

After each finished piece of Mavick work (a part of a phase, a fix, a decision), update docs/PLAN.md, update the memory files, copy them to `docs/agent-memory/` in the repo, then commit and push `develop` (and fast-forward `main`). Do not wait for the end of a phase.

**Why:** the user asked (2026-10-06) "save in memory so that memory is updated every time", and again (2026-10-07) "once done something, must update plan/memory and update git so that I can always find it on another device". They work on more than one PC, and a stale memory or unpushed work means a new session starts from the wrong state.

**How to apply:** at the end of each piece of work: (1) `.\scripts\test.ps1` passes; (2) PLAN.md section 0 and the section 7 tables, and section 10 for decisions; (3) update the local memory files, then copy them to `docs/agent-memory/`; (4) commit and push `develop`, fast-forward `main`, push; (5) check `git status` is clean and nothing is ahead of origin. Remove or correct anything that became wrong. Don't copy the plan into memory; point to it.
Related: [[mavick-phase-status]]

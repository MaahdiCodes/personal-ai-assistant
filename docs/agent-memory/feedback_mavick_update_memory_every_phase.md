---
name: mavick-update-memory-every-phase
description: The user wants Mavick's memory kept current at the end of every phase or piece of work, and copied into git so it travels to other PCs
metadata:
  type: feedback
---

At the end of each phase or significant piece of Mavick work, update the memory files too, not only docs/PLAN.md and the commit.

**Why:** the user asked (2026-10-06) "is everything updated in memory? save in memory so that memory is updated every time." Memory had been left empty and out of date, so a new session would start from the wrong state. The user also works on other PCs, so the memory must also be in git.

**How to apply:** at the end of each phase, after the plan (section 0, sections 7 and 10) and the commit/push: update the memory files in the local memory folder, then copy the same files to `docs/agent-memory/` in the repo, commit and push. Remove or correct anything that became wrong. Don't copy the plan into memory; point to it.

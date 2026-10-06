---
name: mavick-explain-phase-first
description: "When starting a Mavick phase, first tell the user in plain language what the phase builds, and ask only the decisions that are theirs"
metadata:
  node_type: memory
  type: feedback
  originSessionId: ddd8a6d4-1d96-4101-96d8-89e10b16230f
  modified: 2026-10-06T19:03:13.601Z
---

When the user says to start the next phase, begin by explaining what that phase builds (what it does for them, what changes in the safety rules, what is left out), then ask the few decisions that are genuinely theirs with a recommended option first, then build.

**Why:** on 2026-10-07 the user said "let's go for phase 4. please also tell me what we are building in this stage". They took both recommended options without changes, so a clear recommendation plus short reasons is what works. The plan's roadmap row for a phase can be one line, so the design has to be spelled out.

**How to apply:** read docs/PLAN.md section 0, check the code, then write the explanation: parts, permissions or services added (Mavick's rules require deliberate allow-list changes), privacy impact, what is deferred and why. Keep decisions to 2-3 questions. Then record the answers in the plan's decisions log (section 10).
Related: [[mavick-phase-status]]

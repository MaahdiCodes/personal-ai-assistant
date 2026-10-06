---
name: mavick-other-pc
description: How to continue Mavick on another PC or laptop: pull develop, run scripts/sync-memory.ps1, follow docs/SETUP_NEW_PC.md; what is not in git
metadata:
  type: reference
---

On any new PC: `git switch develop; git pull`, then `.\scripts\sync-memory.ps1` (copies docs/agent-memory into the assistant's local memory folder), then follow docs/SETUP_NEW_PC.md.

**Not in git, by design:** the signing key (.p12, keystore.properties), the AI model (.litertlm, about 584 MB, re-download), real messages (eval/private, recordings/), Hugging Face tokens.

**Why:** the user wants to pick up work on a second PC or laptop with a plain pull.
**How to apply:** if the user asks to continue on a new machine, check these steps first, then ask only for what is not in git.

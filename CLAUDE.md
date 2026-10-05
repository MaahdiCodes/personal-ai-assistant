# Mavick: notes for AI coding sessions

Mavick is a private, on-device Android assistant (Kotlin + Jetpack Compose) for the user's two
phones.

**Before doing anything, read [docs/PLAN.md](docs/PLAN.md) §0 ("Start here").** It covers:
- the current state
- what is waiting on the user
- the next actions, in order
- how to build, test and install
- the project rules
- known gotchas

The rest of the plan holds the design, decisions and roadmap.

The rules that matter most:
- **Branch:** work on `develop`, then merge into `main` and push both. A `phase-N` tag marks a
  phase that passed its phone check.
- **Before ending any piece of work:**
  1. `.\scripts\test.ps1` must pass: tests, Lint and permission checks.
  2. Update docs/PLAN.md: §0 and the status tables in §7.
  3. Commit and push `develop`.
- **Permissions:** never add one without also updating `allowedPermissions` in
  `app/build.gradle.kts` and the plan. No `INTERNET` permission, ever.
- **Privacy:** never log task or message content, and never commit real messages.
- **Signing key:** never create the user's release signing key or handle its password. The user
  runs `scripts/new-signing-key.ps1` themselves.
- **The user:** values well-tested, DRY, explicit code and the handling of edge cases. When asked
  what to do, give one clear recommendation and the reasons for it.

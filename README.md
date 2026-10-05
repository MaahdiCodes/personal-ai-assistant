# Mavick

A small, private Android assistant that turns your messages and notes into tasks and reminders,
entirely on your phone: no server, no cloud AI, and no internet permission.

The full plan, decisions and roadmap are in [docs/PLAN.md](docs/PLAN.md).

**Now working (Phase 1):** task lists, quick-add in plain English ("Pay rent every month on the
1st 10am"), reminders with Done / Snooze / Tomorrow, a daily morning briefing, app lock, and
"Send to Mavick" from Google Keep. To check it on a phone, follow
[docs/PHONE_CHECKLIST.md](docs/PHONE_CHECKLIST.md).

## Scripts

Run these from the project folder in PowerShell.

| Script | What it does |
|---|---|
| `.\scripts\new-signing-key.ps1` | **Once, run it yourself.** Creates your release signing key (see "Signing key" below). |
| `.\scripts\devices.ps1` | Lists connected phones: Android version, free storage, RAM, installed Mavick apps. |
| `.\scripts\install.ps1 [-Phone pixel\|poco] [-DebugBuild] [-BuildOnly]` | Builds Mavick and installs it on your phone(s). |
| `.\scripts\test.ps1 [-OnPhone] [-Phone pixel\|poco]` | Runs the tests. `-OnPhone` adds the tests that need a real phone. |
| `.\scripts\usage.ps1 [-Phone pixel\|poco]` | Shows Mavick's storage, memory and CPU use, plus anything it runs in the background. |
| `.\scripts\logs.ps1 [-Phone pixel\|poco]` | Shows Mavick's live log. Message contents are never logged. |

`-Phone` matches part of the phone's name (`pixel`, `poco`) or its serial. You only need it when
both phones are connected.

## Two apps: Mavick and Mavick Debug

- **Mavick** (`dev.maahdi.mavick`): the optimized release build you use every day, signed with
  your key.
- **Mavick Debug** (`dev.maahdi.mavick.debug`): a separate app with its own data, used for
  development. The on-phone tests install it temporarily and remove it afterwards.

## Safety checks built into every build

- **Permission allow-list:** the build fails if the app requests any permission not explicitly
  allowed in `app/build.gradle.kts`, so internet access can never slip in through a library.
- **Size budget:** the build fails if the release APK grows past its budget (docs/PLAN.md §5.8).

## Signing key

`new-signing-key.ps1` creates `%USERPROFILE%\.mavick\mavick-signing.p12` and a git-ignored
`keystore.properties`. Back up the `.p12` file to Google Drive and a USB drive, and keep the
password in a password manager or on paper, not in Drive (docs/PLAN.md §5.7).

On a new PC, copy the `.p12` file back and recreate `keystore.properties` in the project folder:

```properties
storeFile=C:/Users/<you>/.mavick/mavick-signing.p12
storePassword=<your password>
keyAlias=mavick
keyPassword=<your password>
```

## Toolchain

Android Studio 2025.3.1 (with its bundled JDK 21), Gradle 9.2.1, Android Gradle Plugin 9.0.1,
Kotlin 2.3.20. Versions are pinned in `gradle/libs.versions.toml`. Compose is held at 1.11
because 1.12 needs a newer Android Studio.

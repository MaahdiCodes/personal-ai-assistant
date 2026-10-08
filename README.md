# Mavick

A small, private Android assistant that turns your messages and notes into tasks and reminders,
entirely on your phone: no server, no cloud AI, and no internet permission.

The full plan, decisions and roadmap are in [docs/PLAN.md](docs/PLAN.md). **Picking the work
up in a new session? Start with §0 of the plan:** it has the current state, what's next, and how
to build and test.

**Phases 1–2:** task lists, quick-add in plain English ("Pay rent every month on the 1st 10am"),
reminders with Done / Snooze / Tomorrow, a daily morning briefing, app lock, and "Send to Mavick"
from Google Keep. Mavick also reads WhatsApp, Messenger, Gmail and Keep notifications (read-only:
it never sends, marks as read or dismisses anything), skips the chats, people and words you
exclude, and keeps the rest encrypted for 14 days in its Inbox, where any message becomes a task.
"Add to Mavick" works on text selected in any app.

**Phase 3:** Mavick suggests tasks it finds in those messages, for you to add, edit or ignore. An
AI model running on the phone (Gemma 3 1B, imported as a file: the app still has no internet)
does the reading; without one, simple rules do. Keep notes can be imported from a Google Takeout
export, and an accuracy check measures the suggestions against your own labels
([eval/README.md](eval/README.md)).

To check it all on a phone, follow [docs/PHONE_CHECKLIST.md](docs/PHONE_CHECKLIST.md).

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
| `.\scripts\record-notifications.ps1 -Start\|-Stop [-Phone pixel\|poco]` | Records raw notifications with the "Mavick Debug" app, to check the parsers. Fake test messages only; files go to the git-ignored `recordings` folder. |
| `.\scripts\push-model.ps1 -Model <file> [-Phone pixel\|poco] [-ForTests]` | Copies an AI model (.litertlm) to the phone and checks the copy: to Downloads for Mavick to import, or with `-ForTests` for the on-phone tests and the accuracy check. |
| `.\scripts\eval.ps1 [-Fetch] [-Set <file>] [-Threads 1..8] [-Priority background\|low\|normal] [-Phone pixel\|poco]` | The accuracy check ([eval/README.md](eval/README.md)): `-Fetch` brings an export to the git-ignored `eval\private`; without it, runs the labelled file on the phone and prints the report, with where the model's time goes. `-Threads` and `-Priority` measure other settings than the app's. |

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
- **Service allow-list:** the notification listener is the only service, and only Android can bind it.
- **Read-only check:** the build fails if any code could answer, open, dismiss or snooze another
  app's notification, or read the screen (docs/PLAN.md §5.1).
- **Size budget:** the build fails if the release APK grows past its budget (docs/PLAN.md §5.8):
  30 MB, most of it the on-device AI runtime.

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

# Setting up Mavick on a new PC or laptop

Everything the project needs is in git, except the items marked **not in git**. Those are secrets
or large files, and they stay off git on purpose. Follow the steps in order.

## 1. Install the tools (Windows)

| Tool | How | Why |
|---|---|---|
| Git | https://git-scm.com | Get the code |
| Android Studio (2025.3.1 or the version in docs/PLAN.md §11) | https://developer.android.com/studio | Brings the Android SDK, adb and a JDK 21 (`jbr` folder). The scripts find these themselves. |
| Python 3.11 or newer | https://www.python.org/downloads (tick "Add to PATH") | The Hugging Face tool, and small helper scripts |
| Hugging Face CLI | `python -m pip install -U "huggingface_hub[cli]"` | Downloads the AI model. Check with `hf version`. |

Open Android Studio once after installing, so it finishes downloading the SDK.

## 2. Get the code

```powershell
cd C:\Users\<you>\source        # or any folder you like
git clone https://github.com/MaahdiCodes/personal-ai-assistant
cd personal-ai-assistant
git switch develop
git pull
```

Use `develop`: it is where new work goes, and `main` is always the same or behind.

## 3. Restore the signing key (not in git)

The release app is signed with your own key, so updates install over the old one. Without the key,
the phone's app can only be replaced by uninstalling it, which deletes its data.

1. Copy `mavick-signing.p12` from your Google Drive (or the USB drive) to `%USERPROFILE%\.mavick\`.
2. Create `keystore.properties` in the project folder (git ignores it). Use the template in
   README.md ("Signing key"). The password is in your password manager, not in Drive.

## 4. Check the project builds and passes

```powershell
.\scripts\test.ps1
```

It should end with "All tests passed." and "No issues found" from Lint. The first run downloads
Gradle and the libraries, which takes a few minutes.

## 5. Connect to the AI model (only needed to use the AI)

```powershell
hf auth login        # browser sign-in, or paste a read token from huggingface.co/settings/tokens
hf download litert-community/Gemma3-1B-IT gemma3-1b-it-int4.litertlm --local-dir "$HOME\Downloads"
```

Accept the Gemma terms once on https://huggingface.co/litert-community/Gemma3-1B-IT first.
The file is about 584 MB and is **not in git**.

## 6. Restore the assistant's memory on this PC (optional)

The AI assistant keeps notes in a folder outside the project. Copies of those notes are in
`docs/agent-memory/`, so they travel with git. Run:

```powershell
.\scripts\sync-memory.ps1
```

It copies them to the right place for this PC (the folder name depends on the project's path, and
the script works it out). Run it again whenever you pull, so the notes match the code.

## 7. Phone and messages: what is **not** in git

These never go into git, on any PC:

- **Your real messages**: `eval/private/` (the accuracy-check exports and labels), `recordings/`.
- **The signing key** (`.p12`) and `keystore.properties`.
- **The AI model** (`*.litertlm`): re-download it with step 5.
- **Hugging Face tokens**: they live in `%USERPROFILE%\.cache\huggingface`.

If you want your labelled messages on the other PC, copy `eval\private` by hand, over a cable or
a private drive, never through git or a shared cloud folder.

## 8. Phone on the new PC

The phone doesn't change. Plug it in and check with `.\scripts\devices.ps1`. A new PC needs its
own "Allow" on the phone's USB debugging prompt the first time.

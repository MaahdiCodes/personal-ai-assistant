# The accuracy check

How well do Mavick's suggestions match what you would have picked? This folder holds the tools to
measure it on your own messages. The Phase 3 targets (docs/PLAN.md §7) are **precision ≥ 85%**
(of the messages that got a suggestion, how many really had a task), **recall ≥ 70%** (of the
messages with a task, how many got a suggestion), and **at most 10 seconds per message** on both
phones.

**Privacy.** The labelled file holds your real messages in plain text. It lives only in
`eval/private/`, which git never commits, and the scripts delete every copy they put on the
phone. The report (`report.txt`) holds numbers only. `details.csv` holds the AI's task titles,
which come from your messages: keep it private too. AI coding sessions don't open `eval/private/`
unless you ask them to.

## 1. Export

In Mavick: **Settings › Suggestions › Export messages for an accuracy check**, and save the file
to Downloads. It holds up to 300 of your newest messages, each with up to three earlier messages
of its chat. Chats, people and words you excluded are left out.

Then on the PC:

```powershell
.\scripts\eval.ps1 -Fetch -Phone pixel
```

This copies the newest export to `eval\private\` and deletes it from the phone.

## 2. Label

Open the file in a spreadsheet on this PC (Excel or LibreOffice; not Google Sheets, which would
upload your messages). Fill in these columns for 100 to 200 messages, and leave the rest empty
(rows without a label are left out):

| Column | What to write |
|---|---|
| `expected_actionable` | `y` if the message gives you something to do, attend, pay, bring, reply to or remember, or holds a promise you made; `n` if not |
| `expected_title` | Optional: the task in a few words, like `Send Sam the form`. Checked loosely, by its words. |
| `expected_when` | Optional: when it's due, as `2026-10-08` or `2026-10-08 17:00` |
| `label_note` | Optional: anything for yourself; ignored |

Leave every other column as it is. Save as **CSV UTF-8 (comma delimited)**, keeping the name or
any name ending in `.csv`.

[`sample.csv`](sample.csv) shows the format with twelve made-up messages.

## 3. Run

Copy the model to the phone for tests once (it stays in `/data/local/tmp/mavick`):

```powershell
.\scripts\push-model.ps1 -Model $HOME\Downloads\gemma3-1b-it-int4.litertlm -Phone pixel -ForTests
```

Then run the check. It uses the newest `.csv` in `eval\private\` unless you give `-Set`:

```powershell
.\scripts\eval.ps1 -Phone pixel
.\scripts\eval.ps1 -Phone pixel -Set eval\sample.csv     # a quick try with the made-up messages
```

It installs "Mavick Debug" and its test app, runs every labelled message through the real pipeline
on the phone (prefilter, AI model, dates), and prints a report like this, also saved under
`eval\private\reports\`:

```
Mavick accuracy check: gemma3-1b-it-int4.litertlm
Labelled messages: 180
Prefilter: 110 stopped, of which 3 had a task (prefilter misses)
Precision: 87.0% (40 of 46 suggested had a task; meets the 85.0% target)
Recall: 81.6% (40 of 49 with a task got a suggestion; meets the 70.0% target)
Titles close to yours: 31 of 40
Dates right: 25 of 32
Unusable answers or errors: 2
Model load: 4.2 s
Time per message (AI only): median 6.1 s, 90% within 8.9 s, slowest 12.0 s (meets the 10.0 s target)
```

A second report below it does the same with the simple rules, to show what the AI adds.

Keep the phone unlocked and charging while it runs: about 10 seconds per message that passes the
prefilter.

## After a change

Run the check again after any change to the prompt (`ai/Prompt.kt`), the prefilter
(`ai/Prefilter.kt`) or the model, and compare the reports.

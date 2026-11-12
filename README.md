# Time Tracker

A fast, private, offline Android time tracker (Kotlin · Jetpack Compose · Room · Glance widgets).
No internet permission, no cloud backup — all data stays on the device.

## Build & install

```sh
./gradlew testDebugUnitTest assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk   # signed with the debug key, for sideloading
```

## Features

- **Track** — grid of activities (emoji + color). Tap switches (only one runs at a time), long-press edits, Undo snackbar for mis-taps, "Suggested now" chips.
- **Goals** — goals group activities and have an optional daily target or limit; day-by-day navigation.
- **Log** — per-day list, newest first. Tap an entry to edit; tap an *Untracked* gap to backfill it; ＋ adds an entry.
  The editor has −30/−15/+15/+30, **Last** (previous entry's end), **Next** (next entry's start) and **Now** anchors, a time picker, notes, and "still running".
  Saving trims/splits overlapping entries automatically.
- **Analysis** — Day / Week / Month with ‹ › navigation (tap the label to jump back to today), by activity or by goal, 24h timeline or daily stacked bars, CSV export of the shown period.
- **Persistence** — the running activity is just an open entry in the DB, so the timer continues across app kills and reboots.
- **Ignore short entries** — optional threshold (30s/1m/2m/5m); short entries stay in the log but don't count.
- **CSV export** — all data from Settings, or the current period from Analysis; opens the share sheet.
- **Notification** — ongoing, silent, live chronometer with Stop + two suggested switches.
- **Widgets** — *Activities* (current + full grid) and *Smart suggestions* (rule- and habit-based; refreshes every 15 min).
- **Smart rules** — suggest activity X on certain days/time windows (can cross midnight), optionally only while Y is running. Empty slots are filled from what you usually start around this time (last 4 weeks).
- **NFC** — Settings → Activities → (activity) → *Write NFC tag*. Tapping the tag toggles that activity.
- **Launcher shortcuts** — long-press the app icon for the first 4 activities.

## Link scheme (NFC tags, shortcuts, Tasker/automation)

| URI | Action |
|---|---|
| `ttrack://activity/<id>` | switch to activity |
| `ttrack://activity/<id>?toggle=1` | stop if running, else switch |
| `ttrack://stop` | stop tracking |

## Layout

- `data/` Room entities, DAO, prefs · `domain/` pure logic (periods, stats, overlap resolution, suggestions, CSV) — unit-tested in `app/src/test`
- `tracking/Tracker.kt` the only writer; keeps the one-running invariant and refreshes widgets/notification/shortcuts
- `ui/` Compose screens · `widget/` Glance widgets · `notify/` notification + boot receiver · `link/` URI handler + NFC writer

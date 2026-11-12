# Time Tracker

A fast, private, offline Android time tracker (Kotlin · Jetpack Compose · Room · Glance widgets · Lucide icons).
No internet permission, no cloud backup — all data stays on the device.

## Build & install

```sh
./gradlew testDebugUnitTest assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk   # signed with the debug key, for sideloading
```

## Features

- **Groups vs goals** — a *group* organizes activities into sections (each activity is in at most one group).
  A *goal* is a daily target or limit over any set of activities, independent of groups and possibly overlapping
  (e.g. "Screen time ≤ 2h" tracking only TV, while Reading is also in the Leisure group).
- **Built for dozens of activities** — one shared picker everywhere (search by name or group, group chips,
  grouped list; multi-select with per-group All/None and ordered selection where it matters).
- **Track** — pinned header (one-line running bar, search, group chips) over a scrolling grid of activities ([Lucide](https://lucide.dev) icon + color), sectioned by group, with a compact tile size
  (top-bar toggle). Tap switches (only one runs at a time), long-press edits, Undo snackbar for mis-taps, "Suggested now" chips,
  and a search bar (name or group; Enter on a single match starts it).
- **Goals** — each goal's time today vs its target/limit; day-by-day navigation.
- **Log** — per-day list, newest first, one line per entry (activity · start – end · minutes); entries below the
  short-entry threshold are hidden. Tap an entry to edit; ＋ adds/backfills an entry (search the activity list in the editor).
  The editor has −30/−15/+15/+30, **Last** (previous entry's end), **Next** (next entry's start) and **Now** anchors, a time picker, notes, and "still running".
  Saving trims/splits overlapping entries automatically.
- **Analysis** — tap a group (Groups view) to expand its per-activity breakdown. Day / Week / Month with ‹ › navigation (tap the label to jump back to today), by activity, group or goal, 24h timeline or daily stacked bars, CSV export of the shown period.
- **Persistence** — the running activity is just an open entry in the DB, so the timer continues across app kills and reboots.
- **Ignore short entries** — optional threshold (30s/1m/2m/5m); short entries stay in the log but don't count.
- **CSV export** — all data from Settings, or the current period from Analysis; opens the share sheet.
- **Notification** — ongoing, silent, live chronometer with Stop + two suggested switches.
- **Widgets** — *Activities* (current + all activities, sectioned by group), *Smart suggestions* (rule- and habit-based;
  refreshes every 15 min) and a 1×1 *Quick pick* (current activity; tap for top 3 suggestions + searchable list).
  Each widget has its own settings (background opacity, compact tiles, sectioning, and for *Activities* which groups
  to show), shown when placed and via reconfigure.
- **Smart rules** — two kinds, each suggesting one or more activities in a chosen order: *time of day* (days + window,
  may cross midnight) and *while activity X is running* (e.g. while Work → Email, Commute; these rank first). Empty slots are filled from what you usually start around this time (last 4 weeks).
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

## Icons

All icons come from [Lucide](https://lucide.dev) via `com.composables:icons-lucide`. Activities store the icon *name*
(curated list in `ui/Icons.kt`); surfaces that can't draw an `ImageVector` (widgets, notification, launcher
shortcuts) get a bitmap from `iconBitmap()`. DB v2 migrates v1 emoji to the closest Lucide icon.

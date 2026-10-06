---
name: build-gleiswechsel-android
description: Execution brief for building Gleiswechsel (io.github.buerlino.gleiswechsel) — a native Kotlin/Jetpack Compose app for Swiss commuters that looks for faster connections than the official planner shows (changes shorter than the official minimum transfer time; how risky they are comes later), distributed via F-Droid and Obtainium. Use this skill whenever working in the gleiswechsel repo: the timetable API client and the local search in :core, the Compose page, building/installing on the phone, and releases. The stack (native Android, no Flutter/React Native/KMP) is already decided — do not re-open it; just execute.
---

# Build Gleiswechsel

How-tos and what's still untested. `CLAUDE.md` at the repo root is the source of truth for the
stack, the data source and the decisions. Read it first. If this skill and `CLAUDE.md` disagree,
`CLAUDE.md` wins; update this skill to match. The history is in git. The research behind the app
is in `research/`.

## Where things are

- `core/.../Opendata.kt`: `Stop`, `Leg`, `Connection` (with `duration`, `efficiency` and
  `changes`, the change stations: the search and the page both use it), the client
  `connections()` and its parser. Tests in `OpendataTest` with made-up JSON shaped like a real response.
- `core/.../Minimums.kt`: `Minimums`, the official minimum transfer time per station from the
  HRDF `UMSTEIGB` text (`MinimumsTest`, made-up lines). The real file is the app's
  `res/raw/umsteigb.txt`, see [Official minimums](#official-minimums-each-timetable-change).
- `core/.../Search.kt`: `Find`, the local search `search()`. It takes the official connections,
  the transfer time per station and the request as a function, so `SearchTest` runs it on a fake
  API with made-up connections.
- `core/.../LiveTest.kt`: the Horw → Sursee test case, live. Excluded from `:core:test` (and so
  from CI); run it with `./gradlew :core:test -Plive`, which also prints the finds and the number
  of requests. It asks for the next weekday, so a public holiday or a timetable change can fail it.
- `app/.../MainActivity.kt`: `App` holds all state and shows the search page, Help or Settings
  (`Screen`); the search page and the cards (`FindCard`, `Trip`, `MinutesBox`, `MinutesField`).
  SharedPreferences `commute`: `from`, `to`, `leaving`, `offset` and the transfer times keyed by
  station id, all as typed. The rows come from `Result.Found.changes` and stay in `changes` while
  a time or the offset is edited. `app/.../Pages.kt`: Settings, Help (its text) and `SubPage`
  (← and the back gesture). The search runs on `Dispatchers.IO`; a failure shows its message and logs under
  the tag `Gleiswechsel`. `buildConfig` is on for the version name in the User-Agent.
- Driving the page over adb: `uiautomator dump` lists the texts and bounds; `input tap` a field,
  `input text` (`%s` for a space), `input keyevent 123` moves to the end and `67` deletes,
  `input keyevent 4` closes the keyboard (a second one goes back from Help or Settings). On the
  test phone (1116×2484): ? at (87, 182), ⚙ at (1029, 182), fields at y 350, 566, 782, Search at
  (186, 974); the first "Your change" field at x 1000, y 1250 under "nothing faster", y 2198
  under the test case's card. In Settings: ← at (87, 182), the offset at (912, 338). Rows below
  the screen aren't in the dump: `input swipe 558 2000 558 400` first.

## Working on the phone

- Build and test: `ANDROID_HOME=~/Android/Sdk ./gradlew :core:test :app:lintDebug :app:assembleDebug :app:assembleRelease`.
  Add `:app:lintAnalyzeDebug --rerun` when a lint warning looks stale.
- Real phone over USB, no emulator. `adb` isn't on PATH:
  `~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk`. If
  `adb devices` is empty, USB debugging is off or not authorised, or the phone is in "Charging
  only" mode: ask the user. With two phones connected pass `-s <serial>`.
- Debug and release builds are signed with different keys, so switching needs an uninstall.
- Release build on the phone (R8 isn't covered by unit tests): sign the unsigned release APK with
  `~/.android/debug.keystore` via `zipalign -p 4` + `apksigner`; that installs over debug builds.
- A new install may have its network blocked (`UnknownHostException` in the app); the user allows
  it in App info → Mobile data & Wi-Fi.
- Don't pipe Gradle into `tail` before `&& adb install`: the pipe hides a failed build and the old
  APK gets installed.
- Logs: `adb logcat -d -s Gleiswechsel`; crashes: `adb logcat -d | grep AndroidRuntime`.
- If the phone is locked, ask the user; don't try to unlock it.

## Official minimums: each timetable change

`app/src/main/res/raw/umsteigb.txt` is HRDF `UMSTEIGB` of one timetable year (now 2026, valid
to 12 Dec 2026). Refresh it for each new one (next: 2027, from 13 Dec 2026), then update the
year in Help (`Pages.kt`) and CLAUDE.md:

1. On data.opentransportdata.swiss, dataset `timetable-54-<year>-hrdf`: the newest
   `oev_sammlung_ch_hrdf_*.zip` (about 555 MB). The portal's API refuses curl; its pages and
   downloads work with a browser User-Agent. The download link redirects to a signed URL that
   expires after 60 s.
2. `unzip -p <zip> UMSTEIGB | tr -d '\r' | grep -E '^(85|9999999)' > app/src/main/res/raw/umsteigb.txt`
   (UTF-8, CRLF in the zip; `9999999` is the standard for every other station).
3. Check a few: Luzern `8505000`, Zürich HB `8503000`, and `git diff --stat`.

The zip allows range requests, so UMSTEIGB (366 KB) can be read without the whole download:
Python's `zipfile` over a file object that reads with `Range` headers (done 2026-10-06).

## Trying the API by hand

```
curl -s 'https://transport.opendata.ch/v1/connections?from=Luzern&to=Lausanne&limit=4' | jq '.connections[].sections[] | {j: (.journey.category + .journey.number), dep: .departure.departure, depPl: .departure.platform, arr: .arrival.arrival, arrPl: .arrival.platform}'
curl -s 'https://transport.opendata.ch/v1/stationboard?station=8500218&type=arrival&limit=6' | jq -r '.stationboard[] | [.category+.number, .stop.departure, .stop.platform] | @tsv'
```

On an arrival board the arrival time is in `stop.departure` (research/data_sources.md). Keep
real responses for tests' shape in `private/`; tests themselves use made-up data. `private/` has
Horw → Sursee 08:50, Luzern → Sursee 09:05 (the onward request) and one with a walk, all for
Wed 7 Oct 2026.

## Still untested

- Settings, Help and the new cards on the phone (2026-10-06, R8 release build signed with the
  debug key): ? and ⚙ open Help and Settings, ← and the back gesture return with the result
  kept. With nothing set, Luzern shows a grey 4 (5 − 1) and Horw → Sursee finds the test case.
  Offset 2 → Luzern grey 3 and the result cleared; offset 3 survives a force-stop. Luzern 6 →
  nothing faster, after a force-stop too. Horw → Bern, Bundesplatz: the false find is gone
  (nothing faster), rows Luzern, Olten grey 4, Bern grey 5. Lint: no issues; release APK
  1.21 MB. Not tried: a find with a walk or two changes drawn as a timetable (only the test
  case's), a long station name in a row or a card.
- Transfer times on the phone (2026-10-06, R8 release build signed with the debug key), Horw →
  Sursee 08:50 for Wed 7 Oct: 4 at Luzern → Sursee 09:26 instead of 09:40; 4 survives a
  force-stop; 6 → nothing faster; clearing it brings the grey default back. Typing a time
  cleared the old result and kept the row; editing To cleared both. Horw → Bern, Bundesplatz
  shows three rows (Luzern, Olten, Bern). Not tried: a set time at a second change finding something, a non-digit typed (the filter
  drops it), rotating with rows shown.
- On the phone (2026-10-06, R8 release build signed with the debug key): Horw → Sursee 850
  finds the test case for Wed 7 Oct (Sursee 09:26 instead of 09:40, S4 track 12 → RE24 track 9),
  so R8 with kotlinx.serialization works; the fields survive a force-stop; editing a field
  clears the result; "8" marks the time red and disables Search; "Xyzzyq" says no connections
  found. Lint: no issues; release APK 1.16 MB (the text fields; 899 KB before). After the rename
  (new applicationId, so a new install; the old `io.github.buerlino.umsteiger` was uninstalled):
  label Gleiswechsel, same find.
  Not tried: the debug build on the phone, the error text (no network), rotating while
  searching (probably cancels the search and loses the result).
- Efficiency (2026-10-06): a unit test (40 minutes against 40 and 30: 100%, 75%) and on the
  phone (release build), 100% vs 70% for the test case, as worked out by hand. Not tried: a search
  where an official connection or another find is the fastest.
- The search: `:core:test` 13 tests green, `-Plive` 3 green on 2026-10-06 (rerun after the
  false-find filter);
  the walk rule once by hand at Zürich HB (CLAUDE.md). On the phone, Horw → Bern, Bundesplatz
  08:50 (three changes): nothing at Olten and Bern, with 5 minutes or the defaults 4 and 5. Not
  tried: a find at a second change.
- The release workflow (no keystore or secrets yet) and the reproducible build (two clean builds
  with the same sha256).

## Releasing (as in gridload and APODroid)

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (max 500 characters).
3. Commit and tag `vX.Y.Z` only when the user asks; the user pushes. The tag builds the signed
   GitHub Release for Obtainium.
4. F-Droid rebuilds the tag and must get a byte-identical APK apart from the signature. For
   build-only changes, compare the unsigned release APK's sha256 before and after.
5. Push order: `master`, then the tag.

## F-Droid

Not submitted yet. The first submission is a merge request to fdroiddata with a recipe
`metadata/io.github.buerlino.gleiswechsel.yml`, made like APODroid's and gridload's (`Binaries` +
`AllowedAPKSigningKeys`, `UpdateCheckMode: Tags`, `AutoUpdateMode: Version`), from a branch in
`../fdroiddata` off upstream `master`, checked with `fdroid lint` and `fdroid rewritemeta`.
Likely anti-feature `NonFreeNet` for transport.opendata.ch, as APODroid has for its data source;
decide with the user. Reviewer comments: Claude drafts, the user posts.

## Store listing

`fastlane/metadata/android/en-US/`: title, short (max 80 characters) and full description, drafted
2026-10-06. Still missing: `images/icon.png` (512 px, from the logo), `featureGraphic.png`,
`phoneScreenshots/`. Screenshots with SystemUI demo mode, as in APODroid's skill.

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

- `core/.../Opendata.kt`: `Stop`, `Leg`, `Connection` (with `duration` and `changes`, the change
  stations: the search and the page both use it), the client `connections()` and its parser.
  Tests in `OpendataTest` with made-up JSON shaped like a real response.
- `core/.../Minimums.kt`: `Minimums`, the official minimum transfer time per station from the
  HRDF `UMSTEIGB` text (`MinimumsTest`, made-up lines). The real file is the app's
  `res/raw/umsteigb.txt`, see [Official minimums](#official-minimums-each-timetable-change).
- `core/.../Search.kt`: `Find` (with `saved` and `moreEfficient`), the local search `search()`.
  It takes the official connections, the transfer time per station and the request as a
  function, so `SearchTest` runs it on a fake API with made-up connections.
- `core/.../LiveTest.kt`: the Horw → Sursee test case, live. Excluded from `:core:test` (and so
  from CI); run it with `./gradlew :core:test -Plive`, which also prints the finds and the number
  of requests. It asks for the next weekday, so a public holiday or a timetable change can fail it.
- `app/.../MainActivity.kt`: `App` holds all state and shows the search page, Help or Settings
  (`Screen`); the search page and the cards (`FindCard`, `Trip`, `MinutesBox`, `MinutesField`).
  SharedPreferences `commute`: `from`, `to`, `leaving`, `offset` and the transfer times keyed by
  station id, all as typed. The rows come from `Result.Found.changes` and stay in `changes` while
  a time or the offset is edited. `app/.../Pages.kt`: Settings, Help and `SubPage`
  (← and the back gesture). A symbol on a button gets `Modifier.spokenAs(…)`, the word a screen
  reader says instead (`uiautomator dump` shows it as the child's `content-desc`). Every text is
  in `res/values*/strings.xml` (en, de, fr, it); a new one goes in all four, or lint fails on
  the missing translation. The search runs on
  `Dispatchers.IO`; a failure shows one text (`search_failed`) and logs the exception under the
  tag `Gleiswechsel`, in the message too (Android's `Log` drops the stack trace of an
  `UnknownHostException`). `buildConfig` is on for the version name in the User-Agent.
- Driving the page over adb: `uiautomator dump` lists the texts and bounds; `input tap` a field,
  `input text` (`%s` for a space), `input keyevent 123` moves to the end and `67` deletes,
  `input keyevent 4` closes the keyboard (a second one goes back from Help or Settings). On the
  test phone (1116×2484): ⚙ at (87, 182), ? at (1029, 182), fields at y 350, 566, 782, ⇅ at
  (960, 470), Search at (186, 974); the first "Track switch time" field at x 1000, y 1250 under
  "nothing faster", y 2198 under the test case's card. In Settings: ← at (87, 182), the offset
  at (912, 338). Rows below the screen aren't in the dump: `input swipe 558 2000 558 400` first.
  TalkBack (FOSS build `app.talkbackfoss`) can be turned on with `settings put secure
  enabled_accessibility_services app.talkbackfoss/com.google.android.marvin.talkback.TalkBackService`
  and `accessibility_enabled 1`; off with `settings delete secure enabled_accessibility_services`
  and `accessibility_enabled 0` (both were unset, 2026-10-06). Its tutorial opens first: back key.
- Another language without changing the phone's (Android 13+; the app has no language picker,
  but this works anyway): `adb shell cmd locale set-app-locales io.github.buerlino.gleiswechsel
  --locales de-CH` (`fr-CH`, `it-CH`); `""` goes back to the phone's. It restarts the page, so
  the result is gone: Search again.

## Working on the phone

- Build and test:
  `ANDROID_HOME=~/Android/Sdk ./gradlew :core:test :app:lintDebug :app:assembleDebug :app:assembleRelease`.
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
year in Help (`help_data_text` in each `strings.xml`) and CLAUDE.md:

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

Everything else was tried on the phone with the R8 release build signed with the debug key.

- On the page: an orange or red box in a card (no such find at hand); the offset at 0 (orange);
  a find with a walk, or at a second change, drawn as a timetable (only the test case's); a set
  time at a second change finding something; a long station name in a row or a card; a
  non-digit typed (the filter drops it).
- The rule between the finds (2026-10-06): only the unit tests; no real search has shown two
  finds yet.
- Languages: a phone set to German itself (only `set-app-locales`); French and Italian since the
  formal texts (only the error text seen); the French row's long label.
- HTTP 429 with the plain error text (got once before it, after many searches).
- TalkBack's speech: the words are in the accessibility tree and TalkBack (FOSS build, turned on
  over adb and off again) frames ← as one element, but nothing was heard (neither it nor eSpeak
  logs the text).
- The dark bar icons with three-button navigation.
- Turning the phone in Help or Settings, split screen.
- The debug build on the phone.
- The themed icon in a launcher that shows themed icons (Niagara doesn't); only checked as a
  render.
- The release workflow (the secrets are in place, user 2026-10-06; never run).

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
`../fdroiddata` is the user's fork clone (`origin` gitlab.com/buerlino/fdroiddata, `upstream`
fdroid/fdroiddata); the user makes the merge request (user, 2026-10-06). APODroid's commit
was "New app: APODroid". If a push is rejected with "shallow update not allowed":
`git fetch --shallow-since=<date before the fork> upstream master`.
`AllowedAPKSigningKeys` is the release APK's certificate SHA-256 (`apksigner verify
--print-certs`), lowercase without colons.
Likely anti-feature `NonFreeNet` for transport.opendata.ch (it runs on search.ch's data), as
APODroid has for its data source; decide with the user. Category: likely `Public Transport`
(in fdroiddata's `config/categories.yml`). Reviewer comments: Claude drafts, the user posts.

## Store listing

`fastlane/metadata/android/en-US/`: title, short (max 80 characters) and full description, drafted
2026-10-06; `de-DE`, `fr-FR`, `it-IT`: short and full description (the title falls back to
en-US), same day. `images/icon.png`: both logo SVGs at 512 px (`rsvg-convert -w 512 -h 512` each,
`magick back.png front.png -composite -strip`). `images/featureGraphic.png` (1024×500): source
`logo/featureGraphic.svg` (the signs, cropped, on the back's grey, "Gleiswechsel" in Inter Bold
and the subtitle in Inter Medium, dark text: white on this grey is too faint), render command in
its header comment. `images/phoneScreenshots/1.png` (user, 2026-10-06: one is enough): the test
case's find and the green Luzern row, the whole page on one screen; `README.md` embeds it. Taken
with SystemUI demo mode: `settings put global sysui_demo_allowed 1`, then broadcasts
(`am broadcast -a com.android.systemui.demo -e command …`) `enter`, `clock -e hhmm 1200`,
`notifications -e visible false`, `network -e wifi show -e level 4 -e fully true -e mobile hide`,
`battery -e level 100 -e plugged false`, `status -e volume hide -e bluetooth hide -e location
hide -e alarm hide -e sync hide -e mute hide -e speakerphone hide` (the VPN key goes with
them); after the shot `exit` and the setting back to 0. `magick … -strip` for the PNG.

## Icon

The user's SVGs in `logo/` (108×108: `gleiswechsel_back.svg` grey, `gleiswechsel_front.svg` two
blue signs "Gleis" and "Wechsel" and the grey connector, inside the 66-unit safe circle) as
`res/drawable/ic_launcher_{background,foreground}.xml`, combined with
`ic_launcher_monochrome.xml` in `res/mipmap-anydpi/ic_launcher.xml` (2026-10-06). The SVGs have
no transforms: each rect and path became a `<path>` with the root's inherited style (`evenOdd`
only where a path has holes; the strokes keep caps, joins and miter limit); the transparent
artboard rect is skipped. Check: the drawables turned back into SVG render pixel-identical to
the originals at 1080 px (rsvg-convert + `magick compare -metric AE`: 0).

The themed layer is computed with shapely (in a venv, with svgelements to flatten the curves):
the union of the signs, their shadows and the connector, minus the union of the white parts
(borders, lettering, symbols; the strokes buffered with their caps), simplified at 0.005. One
path would be 14,600 characters (lint VectorPath), so it's cut into 43 tiles under 900
characters that overlap by 0.05 (no seam: every pixel 2 px inside the shape is opaque). Check:
against a mask of the front's non-white pixels, 33 of 1,166,400 pixels differ, all at edges.
The user's launcher (Niagara) doesn't show themed icons; App info shows the normal one
(`adb shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d
package:io.github.buerlino.gleiswechsel`). If the SVGs change, regenerate all of these.

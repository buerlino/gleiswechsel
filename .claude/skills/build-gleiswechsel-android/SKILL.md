---
name: build-gleiswechsel-android
description: Execution brief for building Gleiswechsel (io.github.buerlino.gleiswechsel) — a native Kotlin/Jetpack Compose app for Swiss commuters that looks for faster connections than the official planner shows (changes shorter than the official minimum transfer time), distributed via F-Droid and Obtainium. Use this skill whenever working in the gleiswechsel repo: the timetable API client and the local search in :core, the Compose page, building/installing on the phone, and releases. The stack (native Android, no Flutter/React Native/KMP) is already decided — do not re-open it; just execute.
---

# Build Gleiswechsel

How-tos and what's still untested. `CLAUDE.md` at the repo root is the source of truth for the
stack, the data source and the decisions. Read it first. If this skill and `CLAUDE.md` disagree,
`CLAUDE.md` wins; update this skill to match. The history is in git. The research behind the app
is in `research/`.

## Where things are

- `core/.../Opendata.kt`: `Stop` (`delay`, null while not known; `newPlatform`, a changed
  track; `expected`), `tooShort` (a change the delays make shorter than the rider's time), `Leg` (`via`: the ids of the stations a ride passes, from the
  API's `passList`), `Connection` (with `duration`, `transfers` and `changes`, the change stations
  and their minutes: the search, `Minimums` and the page use them, and `doublesBack`), the client
  `connections()` and its parser.
  Tests in `OpendataTest` with made-up JSON shaped like a real response.
- `core/.../Minimums.kt`: `Minimums`, the official minimum transfer time per station from the
  HRDF `UMSTEIGB` text (`MinimumsTest`, made-up lines); `lowered` takes `shortestChanges` of the
  API's answers of a search, where the planner changes faster than the table. The real file is the app's
  `res/raw/umsteigb.txt`, see [Official minimums](#official-minimums-each-timetable-change).
- `core/.../Search.kt`: `Find` (with `saved` and `moreEfficient`), `ticketUrl` (the sbb.ch
  link), the local search `search()` (each question once) and `shortened` (the onward
  connections' changes too, recursively).
  It takes the official connections, the transfer time per station and the request as a
  function, so `SearchTest` runs it on a fake API with made-up connections.
- `core/.../Found.kt`: `Found`, a search's result as the page shows it (`asOf`: when it searched,
  shown as the delays' time while `delaysKnown`), and its JSON
  (`toJson`, `found`; `FoundTest`: written and read back, made-up data). `Stop`, `Leg`,
  `Connection` and `Find` are `@Serializable` for it; times as ISO text (`IsoText`).
- `core/.../Timetable.kt`: `Timetable`, the trains of 14 days for the full search (stations by
  didok, platforms, trips with a bit per day, in-seat `Continuation`s), its gzipped binary
  (`write`; times as steps from the previous departure plus a dwell byte) and the reader
  `timetable()`, which throws on another `FORMAT` or a broken file. `core/.../Gtfs.kt`: `trains()`,
  the GTFS zip → `Timetable` (JDK only; its own CSV reader for the BOM and the quotes), and the
  `main` of `./gradlew :core:timetable -Pgtfs=<zip> -Pout=<file> [-Pfrom=yyyy-MM-dd]` (paths from
  the repo's root). `TimetableTest`: a made-up GTFS in the Swiss export's shape, read, written and
  read back. Not used by the app yet, so R8 strips it from the release APK.
- `core/.../LiveTest.kt`: the Horw → Sursee test case, live. Excluded from `:core:test` (and so
  from CI); run it with `./gradlew :core:test -Plive`, which also prints the finds and the number
  of requests. It asks for the next weekday, so a public holiday or a timetable change can fail it.
- `app/.../MainActivity.kt`: `App` holds all state and shows the search page, Help or Settings
  (`Screen`); the search page (`Heading` for each panel's title, foldable with `open`, also
  Help's topics; `Folded` for the folded Destination, ▴ on the Search row to fold it again, ✕
  on Journey's title to close the result), `MinutesStepper` (− and +, the rows and the offset in
  Settings; `arrows = false` for the offset), `folding` and `FoldMark` (a fold's state for a
  screen reader, the ▾ or ▸ hidden from it), `parseTime` and `find`. SharedPreferences
  `commute`: `from`, `to`, `leaving` as typed, `offset` and the transfer times keyed by station id as numbers in strings (empty:
  unset), `optimize`. The rows come from `Found.changes` (the stations the search asked
  the transfer time at, recorded in `find`) and stay in `changes`, with the lowered minimums in
  `official`, while a time or the offset is edited. The result is set only through `show`, which
  writes `files/result.json` (a result with connections) or deletes it; `onCreate` reads it
  back (`last`). `fade` (a time, the offset or Optimization changed) deletes the file but keeps
  the result on the page at 60% (`stale`) until the next Search. While a search runs, the Search
  button is Cancel (`job`). `app/.../Cards.kt`: the cards (`FindCard`, `Trip`, `StopRow` with the
  delay and a changed track, `TrackSign`), `MinutesBox`, `LateBox` (a change the delays make too
  short) and the colours. `app/.../Pages.kt`: Settings, Help (`help`: emoji, title, text; folded
  until tapped) and `SubPage` (← and the back gesture). A symbol on a button gets
  `Modifier.spokenAs(…)`, the word a screen reader says instead (`uiautomator dump` shows it as the
  child's `content-desc`). Every text is
  in `res/values*/strings.xml` (en, de, fr, it); a new one goes in all four, or lint fails on
  the missing translation. The search runs on `Dispatchers.IO`; a failure of the first request
  shows one text (`search_failed`), of an onward one `not_all_checked` under the day; each
  exception goes to the log under the tag `Gleiswechsel`, in the message too (Android's `Log`
  drops the stack trace of an `UnknownHostException`). `buildConfig` is on for the version name in the User-Agent.
- Before input over adb, check the app is in front (`adb shell dumpsys activity activities | grep
  topResumedActivity`): the user uses the phone meanwhile, and a back key with no keyboard open
  leaves the app, so later taps and text go into whatever app is behind (2026-10-06: they went
  into WhatsApp). Editing the commute clears the result and the rows: dump again before tapping.
- Driving the page over adb: `uiautomator dump` lists the texts and bounds; `input tap` a field,
  `input text` (`%s` for a space), `input keyevent 123` moves to the end and `67` deletes,
  `input keyevent 4` closes the keyboard (a second one goes back from Help or Settings). On the
  test phone (1116×2484): ⚙ at (87, 182), ? at (1029, 182), the rest moves with the panels
  (Destination centred before a search, folded after; Journey; Optimization): dump and tap the
  centre of the bounds. A stepper's − and + show in the dump as their content-desc ("One minute
  less", "One minute more"); a step fades the finds, so the rows stay where they are. A tap on the
  title (558, 182) opens the language menu (Android 13+). In Settings: ← at (87, 182), the offset's
  − and + and the Optimization switch: dump. Rows below the screen aren't in the dump:
  `input swipe 558 2000 558 400` first.
  TalkBack (FOSS build `app.talkbackfoss`) can be turned on with `settings put secure
  enabled_accessibility_services app.talkbackfoss/com.google.android.marvin.talkback.TalkBackService`
  and `accessibility_enabled 1`; off with `settings delete secure enabled_accessibility_services`
  and `accessibility_enabled 0` (both were unset, 2026-10-06). Its tutorial opens first: back key.
- Another language without changing the phone's (Android 13+; the app has no language picker,
  but this works anyway): `adb shell cmd locale set-app-locales io.github.buerlino.gleiswechsel
  --locales de-CH` (`fr-CH`, `it-CH`); `""` goes back to the phone's. It restarts the page; the
  result comes back from its file.

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
- What a link opened in the browser: the default is Brave, a Chromium with the devtools socket.
  `adb forward tcp:9333 localabstract:chrome_devtools_remote`, then
  `curl -s -m 10 http://127.0.0.1:9333/json/list` gives each tab's full URL (Brave must be in
  front, or curl hangs); `adb forward --remove tcp:9333` after. sbb.ch's fields and connections
  are in shadow DOM: read them with `Runtime.evaluate` over the tab's WebSocket (Node 22 has
  `WebSocket` built in), walking `shadowRoot`s.
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
Wed 7 Oct 2026. `private/gtfs/` has the GTFS export of 30 Sep 2026 (289 MB; `curl -L` the
dataset's `/permalink` with a browser User-Agent, 5 s) and `timetable.bin.gz` made from it for
7–20 Oct.

## Still untested

Everything else was tried on the phone with the R8 release build signed with the debug key.

- On the page: an orange or red box in a card (no such find at hand);
  a find with a walk, or at a second change, drawn as a timetable (only the test case's); a set
  time at a second change finding something; a long station name in a row or a card; a
  non-digit typed (the filter drops it).
- The ticket link (2026-10-06): tapped from the app only in German and only in Brave; the
  English, French and Italian URLs were opened in Brave directly. Another browser, and a phone
  without one (the `no_browser` message: not seen, the test phone has a browser).
- Two short changes on one trip (2026-10-06): only `SearchTest`; no live commute at hand that
  has one. The request cache: only the unit test.
- The doubling-back line in a card (2026-10-06): only the unit tests and the parser; no real find
  has shown it.
- The fold states (2026-10-06): `uiautomator dump` doesn't show `stateDescription`; not heard
  in TalkBack.
- CI's lint and app build (2026-10-06): only run locally; the runner's SDK may need to fetch
  platform 37.
- The rule between the finds (2026-10-06): only the unit tests; no real search has shown two
  finds yet.
- Languages: a phone set to German itself (only `set-app-locales`); French and Italian since the
  formal texts (only the error text seen); the long offset label in Settings next to − and +
  (French, Italian).
- HTTP 429 with the plain error text (got once before it, after many searches).
- A failed onward request on the phone (2026-10-06): `not_all_checked` and the kept finds, only
  `SearchTest`; a normal search on the phone shows no such line.
- TalkBack's speech: the words are in the accessibility tree and TalkBack (FOSS build, turned on
  over adb and off again) frames ← as one element, but nothing was heard (neither it nor eSpeak
  logs the text).
- The dark bar icons with three-button navigation.
- Help's topics, Destination's ▴ and Journey's ✕ (2026-10-06): seen on the phone in German only;
  the English, French and Italian texts only built (lint checks they exist), not read on the
  phone. Not heard in TalkBack (it reads the emoji's name before each title).
- The saved result (2026-10-06, R8 release build): kept after a force-stop and the language
  switch, gone after a step in Optimization; the rest only `FoundTest`. Not seen: a file in an
  older format (none exists yet), a failed write, the system killing the app in the background,
  a phone-to-phone transfer, a result with a walk or a second change read back on the phone.
  `run-as` doesn't work on the release build, so the file itself wasn't looked at.
- Turning the phone in Help or Settings, split screen.
- Delays (2026-10-06, R8 release build, German only): real "+1"s and the "as of" line seen live
  (Horw → Sursee at 23:22 and 23:30), kept after a force-stop, absent for tomorrow's 08:50. The
  red "! 1 min", the grey "−2 min" and a changed track (14 instead of 12) only from a made-up
  `result.json` written with `run-as` into the debug build (and read back by the R8 build); no
  real changed track or too-short change seen (`prognosis.platform` was null everywhere). Not
  seen: English, French, Italian; a delay of 10 or more next to the time; a negative delay;
  a cancelled train; the new Help texts on the phone; TalkBack reading the delay words.
- The debug build on the phone.
- Declutter pass 3's fixes (2026-10-07), only built, linted and unit-tested, not seen on the
  phone: Destination in the middle from the first frame, without sliding up (app start, back
  from Help or Settings, the language switch); with Optimization off, the page after a change in
  Settings or Cancel; ✕ as plain text and Journey's title as high as Optimization's; the faded
  finds after a step; the buttons, the switch and the links in the logo's blue (the ⇅ button's
  pale background is still Material's `secondaryContainer`); the titles as headings in TalkBack;
  the French no-break spaces.
- The themed icon in a launcher that shows themed icons (Niagara doesn't); only checked as a
  render.

## Releasing (as in gridload and APODroid)

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` in all four (max 500
   characters). If the page looks different, a new screenshot (Store listing).
3. Commit and tag `vX.Y.Z` only when the user asks; the user pushes. The tag builds the signed
   GitHub Release for Obtainium.
4. F-Droid rebuilds the tag and must get a byte-identical APK apart from the signature. For
   build-only changes, compare the unsigned release APK's sha256 before and after.
5. Push order: `master`, then the tag. While the F-Droid merge request is open, then the
   recipe too: [Before every push to the fork](#before-every-push-to-the-fork).
6. After the workflow: its APK against an unsigned build of the tag from a fresh clone, e.g.
   `apksigcopier compare gleiswechsel-vX.Y.Z.apk --unsigned app-release-unsigned.apk` (pip, in a
   venv; it needs build-tools' `apksigner` on PATH). Done for 0.1.0.

## F-Droid

The merge request is open (user, 2026-10-06), now with 0.1.0 and 0.2.0: the recipe
`metadata/io.github.buerlino.gleiswechsel.yml`, made like APODroid's and gridload's (`Binaries` +
`AllowedAPKSigningKeys`, `UpdateCheckMode: Tags`, `AutoUpdateMode: Version`), on the branch
`io.github.buerlino.gleiswechsel` off upstream `master`. `../fdroiddata` is the user's fork clone
(`origin` gitlab.com/buerlino/fdroiddata, `upstream` fdroid/fdroiddata); the user commits there,
pushes and makes merge requests. If a push is rejected with "shallow update not allowed":
`git fetch --shallow-since=<date before the fork> upstream master`.
`AllowedAPKSigningKeys` is the release APK's certificate SHA-256 (`apksigner verify
--print-certs`), lowercase without colons. Anti-feature `NonFreeNet` (user, 2026-10-06: as
APODroid's, naming the hosts: transport.opendata.ch and sbb.ch); category `Public Transport`
(in fdroiddata's `config/categories.yml`). Reviewer comments: Claude drafts, the user posts.

### Before every push to the fork

The pipeline failed twice on 2026-10-06 for want of this (user: document it): the sbb.ch text
was pushed without `rewritemeta` (a line over about 80 characters), then `v0.2.0` was tagged
while the recipe still ended at 0.1.0 (`checkupdates`). gridload's skill already knew the first.

1. **A new tag since the recipe's `CurrentVersion`** (while the merge request is open, every
   release): add its build block (a copy of the last; `commit` is `git rev-parse
   vX.Y.Z^{commit}`, not the annotated tag object's hash) and raise `CurrentVersion` and
   `CurrentVersionCode`. Step 2's `checkupdates` writes exactly that, too.
2. **Run the pipeline's checks**, from `../fdroiddata`, with what the pipeline uses: fdroidserver
   from git `master` and Debian trixie's `ruamel.yaml` 0.18.10. pip's fdroidserver 2.4.5 brings
   0.17.21, which moves the `Binaries:` URL up onto its key's line, a change the pipeline
   doesn't want. Set up once, in the scratchpad (pip warns that fdroidserver wants an older
   `ruamel.yaml`: ignore it):
   ```
   python3 -m venv fdroid-venv && fdroid-venv/bin/pip install fdroidserver
   fdroid-venv/bin/pip install 'ruamel.yaml==0.18.10'
   git clone --depth 1 https://gitlab.com/fdroid/fdroidserver.git
   ```
   Then, with `FS` the clone and `V` the venv:
   ```
   export PATH="$FS:$PATH" PYTHONPATH="$FS:$FS/examples"
   $V/bin/python $FS/fdroid checkupdates --auto --allow-dirty io.github.buerlino.gleiswechsel
   $V/bin/python $FS/fdroid rewritemeta io.github.buerlino.gleiswechsel
   $V/bin/python $FS/fdroid lint io.github.buerlino.gleiswechsel
   git diff
   ```
   `lint` must exit 0; whatever `checkupdates` and `rewritemeta` changed goes into the commit
   (`git diff` shows it). Checked 2026-10-06: this flags the pushed sbb.ch commit's line exactly
   as the pipeline did, and leaves the fixed recipe (`6cad67402`) unchanged. `fdroid build` isn't
   run locally; the pipeline does it and compares with the GitHub APK.
3. Then the user commits and pushes.

## Store listing

`fastlane/metadata/android/en-US/`: title, short (max 80 characters) and full description, drafted
2026-10-06; `de-DE`, `fr-FR`, `it-IT`: short and full description (the title falls back to
en-US), same day. `images/icon.png`: both logo SVGs at 512 px (`rsvg-convert -w 512 -h 512` each,
`magick back.png front.png -composite -strip`). `images/featureGraphic.png` (1024×500): source
`logo/featureGraphic.svg` (the signs, cropped, on the back's grey, "Gleiswechsel" in Inter Bold
and the subtitle in Inter Medium, dark text: white on this grey is too faint), render command in
its header comment. `images/phoneScreenshots/1.png` (user, 2026-10-06: one is enough): the test
case's find and the green Luzern row (unset, the faded default), in English; since 0.2.0 the page
is longer than the screen, so it's scrolled to the end, Destination's folded line half under the
top bar (user, 2026-10-06). `README.md` embeds it. Taken
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

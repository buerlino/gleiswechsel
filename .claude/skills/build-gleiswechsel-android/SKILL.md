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

- `core/.../Opendata.kt`: `Stop` (`delay`, null while not known; `newPlatform`, a changed track;
  `expected`), `tooShort` (a change the delays make shorter than the rider's time), `Leg` (`via`:
  the ids of the stations a ride passes, from the API's `passList`), `Connection` (with `duration`,
  `transfers`, its changes as `Change`s (station id, arrival, departure), `changes`, the change
  stations, and `doublesBack`), the client `connections()` and its parser, and `download()` (the
  timetable file), both through `get` (the User-Agent). Tests in `OpendataTest` with made-up JSON
  shaped like a real response.
- `core/.../Minimums.kt`: `Minimums`, the official minimum transfer time per station from the
  HRDF `UMSTEIGB` text, and `TrackSwitchTimes`, the one model (CLAUDE.md, One model): `official`
  (the table's), `default` (− the offset, at least 0), `rider` (set, else default), all by
  station id, and `official(change, offered)`, a change the planner makes never below
  (`MinimumsTest`, made-up lines). The real file is the app's
  `res/raw/umsteigb.txt`, see [Official minimums](#official-minimums-each-timetable-change).
- `core/.../Search.kt`: `Find` (with `saved` and `moreEfficient`), `ticketUrl` (the sbb.ch link),
  the local search `search()` (each question once; it returns the stations it changed at,
  `Searched.changes`, and the planner's changes in its answers, `Searched.offered`), `shortened`
  (the onward connections' changes too, recursively, those before the official arrival), `onward` (rides from the change station
  itself) and `best`, the rules between the finds and the official connections, for both searches.
  It takes the official connections, the transfer time per station and the request as a function, so
  `SearchTest` runs it on a fake API with made-up connections.
- `core/.../Found.kt`: `find()` (the next such time, Swiss, or now for a null time; the official connections; `search`
  with `TrackSwitchTimes`, then `fullSearch` on the timetable passed in when it has the day,
  `best` over both, today's first; the request passed in; a failed onward one, a timetable
  without the day and an error of the full search to `failed`; `noTimetable` for the line),
  `parseTime()` (`FoundTest`, also the two surprises of CLAUDE.md, One model, on a fake
  API); `fastestOfTheDay()` (the fastest of the day: the first weekday's 08:00 for the ids,
  `daySearch`, `runs`, `fastest`, a request at each card's first run for its official connection;
  `FoundTest`, a fake API on a made-up timetable); `Found`, a search's result as the page shows it (`offered`; `trips`, what the page
  shows, and `onTrips`, a row's station on one of them) (`asOf`: when it searched,
  shown as the delays' time while `delaysKnown`), and its JSON
  (`toJson`, `found`; `FoundTest`: written and read back, made-up data). `Stop`, `Leg`,
  `Connection`, `Change`, `Find`, `Runs` and `Fastest` are `@Serializable` for it; times as ISO text (`IsoText`).
- `core/.../Timetable.kt`: `Timetable`, the trains of 14 days for the full search (stations by
  didok, platforms, trips with a bit per day, in-seat `Continuation`s), its gzipped binary
  (`write`; times as steps from the previous departure plus a dwell byte) and the reader
  `timetable()`, which throws on another `FORMAT` or a broken file (read to the end: the gzip
  trailer); `localTimetable()`, the phone's copy, downloaded from `TIMETABLE_URL` when it's
  missing, older than 7 days, dated in the future or doesn't read, replaced only by a download
  that reads, and a download that can't be saved used anyway (CLAUDE.md, The full search, step
  4.1).
  `core/.../Gtfs.kt`: `trains()`,
  the GTFS zips → `Timetable` (JDK only; its own CSV reader for the BOM and the quotes; each zip
  for the days from its `feed_start_date` to its `feed_end_date`, a zip without any skipped, a
  day none has throws; `name`, a train's name as the API's, CLAUDE.md step 4.2), and the `main` of
  `./gradlew :core:timetable -Pgtfs=<zip>[,<zip>…] -Pout=<file> [-Pfrom=yyyy-MM-dd]` (paths from
  the repo's root). `TimetableTest`: a made-up GTFS in the Swiss export's shape, read, written and
  read back, and a next year's from 13 Dec; `localTimetable` with a fake download in a temp file.
  `localTimetable` is `@Synchronized`: a search started while a cancelled one still downloads waits.
  The app keeps its copy in `cacheDir/timetable.bin.gz` and reads it on every search.
- `.github/workflows/timetable.yml`: the file on GitHub Pages, Thursdays and Sundays and by hand
  (Actions → Timetable → Run workflow). Downloads the year's GTFS from the dataset's `/permalink`
  with a browser User-Agent (and the next year's when the 14 days reach December), runs
  `:core:timetable` (its summary line goes into `index.html`, with the source and the
  publisher), then `upload-pages-artifact` + `deploy-pages`. Pages' source must be "GitHub
  Actions" (Settings → Pages; on since 2026-10-07). The file:
  https://buerlino.github.io/gleiswechsel/timetable.bin.gz, `Content-Type: application/gzip`,
  no `Content-Encoding` even when asked for gzip (checked with curl 2026-10-07), so the reader
  gets the gzipped bytes as written; `max-age=600`. The run's log needs a login; its summary
  line is on the index page. To try its two scripts locally, take them from the YAML and run
  them with `RUNNER_TEMP` and `GITHUB_ENV` set (done 2026-10-07).
- `core/.../FullSearch.kt`: the full search `fullSearch(timetable, officials, transfer)`, a
  connection scan (`Scan`: the rides of the window by departure, `earliest` forwards, `latest`
  backwards for the trip leaving last, `connection` builds the result; `leavingAt` finds a scan's
  first ride by binary search, and the scans' arrays are reused). Times are minutes on the
  clock from the file's first day (the Swiss GTFS counts so, CLAUDE.md). `daySearch(timetable,
  from, to, day, transfer)`, the day scan of the fastest of the day (CLAUDE.md): the
  same `Scan` once per departure from A on the service day, from the last, until 04:00 the next
  morning; the journeys no other beats. `FullSearchTest`: a made-up `Timetable`, built directly
  (the day scan's tests at its end). `FoundTest` runs it through `find()` on small made-up
  timetables (a find only it has, the same trip as today's, no file or one without the day).
- `core/.../Runs.kt`: how often the fastest of the day runs. `runs(journeys)` groups `daySearch`'s
  journeys into `Runs` (the same lines, a train named by number by its category; the same change
  stations; the same minutes), each with `duration` and `every` (the interval, null if irregular:
  not 15, 30, 60 or 120, once, or under half of the gaps); `fastest(runs)` the cards (the fastest,
  and the fastest regular one if it isn't); `Fastest`, a card in `Found` (its `trip`, the first run;
  the official connection; `offered`; `moreEfficient`). `RunsTest`: made-up `Connection`s.
- `core/.../LiveTest.kt`: the Horw → Sursee test case, live. Excluded from `:core:test` (and so
  from CI); run it with `./gradlew :core:test -Plive`, which also prints the finds and the number
  of requests. It asks for the next weekday, so a public holiday or a timetable change can fail it.
  Its full search runs on the published file with the officials from the API, through
  `localTimetable` into `core/build/timetable.bin.gz` (downloaded again once older than 7 days,
  or after `clean`), and it prints the times.
  `theFastestOfTheDayIsTheHiddenChangeEveryHour`: `fastestOfTheDay` on the same file and the API,
  Horw → Sursee with the defaults (one card: the S4 → RE24, 33 minutes, every 60, 05:53–22:53;
  42% against the planner's S4 → S1 at 05:53; 2 requests).
  `thePublishedTimetableIsDownloadedOnce`: the file on Pages through `localTimetable`; one test
  alone: `--tests '*LiveTest.thePublished*'`.
- `app/.../MainActivity.kt`: `App` holds all state and shows the search page, Help or Settings
  (`Screen`); the search page (`Heading` for each panel's title, foldable with `open`, also Help's
  topics; `Folded` for the folded Destination, ▴ on the Search row to fold it again, ✕ on Journey's
  title to close the result), `MinutesStepper` (− and +, the rows and the offset in Settings;
  `arrows = false` for the offset), `folding` and `FoldMark` (a fold's state for a screen reader,
  the ▾ or ▸ hidden from it) and `search` (core's `find` on the live API, or `fastestOfTheDay`
  with all day on; a failure as `Result.Failed`). `times` (`TrackSwitchTimes`) is made from the offset, the set times and
  `optimize` at each composition. SharedPreferences `commute`: `from`, `to`, `leaving` as typed
  (empty: now), `allDay` (the "all day" button at the time field's right; switching it clears the
  result, so the page tells a fastest of the day's result by it), `offset` and the transfer times
  keyed by station id as numbers in strings (empty: unset), `optimize`. The rows come from `Found.changes` (`Searched.changes`: the stations the search asked
  the transfer time at) and stay in `changes` while a time or the offset is edited; a row whose
  station isn't on a trip shown (`Found.onTrips`) is faded through `MinutesStepper`'s `modifier`
  (D3). The result is set only through `show`, which writes `files/result.json` (a result with
  connections) or deletes it; `onCreate` reads it back (`last`). `fade` (a time, the offset or
  Optimization changed) deletes the file but keeps the result on the page at 60% (`stale`) until the
  next Search. While a search runs, the Search button is Cancel (`job`). The keyboard's key goes to
  the next field, in the time field it searches (`Field`'s `onSearch`, `startSearch`).
  `app/.../Cards.kt`: the cards (`TripCard`, a card's body under a header: the trip, the folded
  official connection and the ticket link; `FindCard` and `FastestCard` on it; `Trip`, `StopRow` with the delay and a changed track,
  `TrackSign`), `MinutesBox`, `LateBox` (a change the delays make too short) and the colours.
  `FindCard` and `Trip` take `Found.offered` and colour each change against `times.official(change,
  offered)` (D4). `app/.../Pages.kt`: Settings, Help (`help`: emoji, title, text; folded until
  tapped) and `SubPage` (← and the back gesture). A symbol on a button gets `Modifier.spokenAs(…)`,
  the word a screen reader says instead (`uiautomator dump` shows it as the child's `content-desc`).
  Every text is in `res/values*/strings.xml` (en, de, fr, it); a new one goes in all four, or lint
  fails on the missing translation. The search runs on `Dispatchers.IO`; a failure of the first
  request shows one text (`search_failed`), of an onward one `not_all_checked` under the day, no
  timetable file with the day `timetable_missing` (`Found.noTimetable`); each
  exception goes to the log under the tag `Gleiswechsel`, in the message too (Android's `Log` drops
  the stack trace of an `UnknownHostException`). `buildConfig` is on for the version name in the
  User-Agent.
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
- A release build signed with the release key and a debug build have different keys, so
  switching needs an uninstall.
- Release build on the phone (R8 isn't covered by unit tests): sign the unsigned release APK with
  `~/.android/debug.keystore` via `zipalign -p 4` + `apksigner`; that installs over debug builds
  and back (`install -r`, data and cache kept; 2026-10-07). So the debug build's `run-as` can
  look at or change the files between two R8 runs, e.g. `run-as io.github.buerlino.gleiswechsel
  touch -d 2026-09-29T12:00:00 cache/timetable.bin.gz` (toybox's `touch` takes no "8 days ago").
- Bytes an app received, e.g. to see whether a search downloaded the timetable (a download
  isn't logged): `dumpsys netstats --poll`, then sum the `rb=` of `dumpsys netstats detail`'s
  entries with the app's `uid=` (`dumpsys package` gives it) and `tag=0x0`; the file is about
  450 KB, a search's answers 10–50 KB.
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
- Something during the download, which takes under a second on a good network: slow it down
  (2026-10-07). A CONNECT proxy on the desktop (Python asyncio, about 30 lines) that relays
  buerlino.github.io at 40 KB/s (the file then takes 12 s) and the rest at full speed, on
  127.0.0.1:8899; `adb reverse tcp:8899 tcp:8899` and `adb shell settings put global http_proxy
  127.0.0.1:8899` (Android's `HttpURLConnection` follows it; it's for the whole phone). After:
  `settings delete global http_proxy` (it was unset) and `adb reverse --remove tcp:8899`. The
  proxy's log shows when the download starts; wait for that, then turn, background, kill or
  Cancel. Turning: `settings put system accelerometer_rotation 0` and `user_rotation 1`, back
  with `user_rotation 0` and `accelerometer_rotation 1` (both as they were).
- An update from an older version: build its tag in a worktree in the scratchpad (`git worktree
  add --detach <dir> vX.Y.Z`, `local.properties` copied in), sign it with the debug key as above,
  `adb uninstall` (an older version can't go over a newer one), install it, set it up, then the
  new R8 build over it with `install -r` (2026-10-07, from 0.2.0). Back up the user's data first
  through the debug build: `adb exec-out run-as … tar cf - shared_prefs files`, and back the same
  way with `exec-in … sh -c 'cat > shared_prefs/commute.xml'`.
- `input keyevent 66` is a hardware Enter, not the keyboard's action key, but it does the same:
  From → To → the time → Search (2026-10-07, since ⇅ left the focus order).
- Typing into the fields: with the keyboard up, Destination moves up (centred in the space above
  it), so take the fields' bounds from a `uiautomator dump` after the first tap, not before
  (2026-10-08: a second tap at the old place typed To's text into From).
- If the phone is locked, ask the user; don't try to unlock it.
- Timing `:core` on the phone without the app or code left in the repo (2026-10-07, the day
  scan): a Java `main` in the scratchpad against `core/build/libs/core.jar` (`./gradlew
  :core:jar`) and the Gradle cache's kotlin-stdlib and kotlinx-serialization core and json jars
  (`Leg` needs them); `javac --release 17`; `build-tools/37.0.0/d8 --release --min-api 26` over
  it and the jars; `classes.dex` zipped, pushed with the timetable and `umsteigb.txt` to
  `/data/local/tmp/<dir>`, then `adb shell 'cd … && dalvikvm -cp <zip> <Main> …'`, and the
  dir removed. No AOT, so close to the app's first search. Pushing to the phone needs the
  user's go (the auto mode blocks it); they ran it themselves.

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
eight: Horw → Sursee 08:50, Luzern → Sursee 09:05 (the onward request) and Horw → Bern,
Bundesplatz (with a walk), all for Wed 7 Oct 2026; Horw → Sursee now and tomorrow 08:50 and
Zürich → Bern now, asked 6 Oct 23:07 (with delays); the arrival boards of Zürich HB and Luzern
a minute later. `private/gtfs/` has the GTFS export of 30 Sep 2026 (289 MB; `curl -L` the
dataset's `/permalink` with a browser User-Agent, 5 s), `timetable.bin.gz` made from it for
7–20 Oct (before the names of step 4.2; nothing reads it now), and the 2027 export of 3 Oct
2026 (`gtfs_fp2027_20261003.zip`, 90 MB, from `timetable-2027-gtfs2020`).

## Still untested

Everything else was tried on the phone with the R8 release build signed with the debug key.

- On the page: a walk before the first train in a card (inside a change, after the last train,
  a second change and the orange and red boxes seen 2026-10-07; a set time at a second change
  finding something too: Zürich HB at 2, Sursee → Zürich Oerlikon); a long station name in a
  row or a card.
- The ticket link (2026-10-06): tapped from the app only in German and only in Brave; the
  English, French and Italian URLs were opened in Brave directly. Another browser, and a phone
  without one (the `no_browser` message: not seen, the test phone has a browser).
- Two short changes on one trip (2026-10-06): only `SearchTest`; no live commute at hand that
  has one. The request cache: only the unit test. The deadline on the onward search
  (2026-10-07): `SearchTest`, the test pass's 46 commutes replayed (the same finds and rows,
  195 → 138 questions) and Bern, Bundesplatz → Horw on the phone.
- The doubling-back line in a card (2026-10-06): only the unit tests and the parser; no real find
  has shown it.
- The fold states (2026-10-06): `uiautomator dump` doesn't show `stateDescription`; not heard
  in TalkBack.
- The rule between the finds (2026-10-06): two real finds seen together, neither beating the
  other (Sursee → Zürich Oerlikon 07:45, Zürich HB at 2, 2026-10-07); a find another one beats,
  and the same trip twice, only in the unit tests.
- Languages: a phone set to German itself (only `set-app-locales` and the title's menu; the
  page, Settings and Help seen in all four on 2026-10-07).
- HTTP 429 with the plain error text (got once before it, after many searches).
- A failed onward request on the phone (2026-10-06): `not_all_checked` and the kept finds, only
  `SearchTest`; a normal search on the phone shows no such line.
- TalkBack's speech: the words are in the accessibility tree and TalkBack (FOSS build, turned on
  over adb and off again) frames ← as one element, but nothing was heard (neither it nor eSpeak
  logs the text).
- The dark bar icons with three-button navigation.
- Help's topics, Destination's ▴ and Journey's ✕: not heard in TalkBack (it reads the emoji's
  name before each title).
- The saved result (2026-10-06, R8 release build): kept after a force-stop and the language
  switch, gone after a step in Optimization; the rest only `FoundTest`. Not seen: a file in an
  older format (none exists yet), a failed write, the system killing the app in the background,
  a phone-to-phone transfer, a result with a walk read back on the phone (one with two finds
  and second changes came back after the language switch, 2026-10-07). Seen since (2026-10-07):
  0.2.0's file ignored after the update to 0.3.0 (logged, no crash); `am kill` in the background
  during a search: the page opens without a result.
  `run-as` doesn't work on the release build, so the file itself wasn't looked at.
- Split screen.
- The fastest of the day's page (2026-10-08): seen on the phone (R8 release) in English for the
  commutes in CLAUDE.md's "Checked" there, Uster → Winterthur (every 30) and Bern → Thun (the
  irregular line and a second card); the texts in German, French and Italian, Help's 🚆 and ⚠️ in all
  four. Switching all day with To focused keeps the keyboard down. Not seen:
  `timetable_missing_all_day` (no copy that reads), a card with a walk or a delay, a result kept
  over a restart (only the language switch, which kept the page on Help), a public holiday, TalkBack
  on the button's state (`uiautomator dump` doesn't show a Compose button's `selected`; the time
  field's `enabled="false"` shows all day is on). Routes for it, found with `daySearch` on the
  published file (a throwaway test against `localTimetable`, as `LiveTest`): every 30 Uster →
  Winterthur, Zürich HB → Uster, → Effretikon, → Meilen, Bern → Biel/Bienne; every 15 Bern →
  Solothurn (RE5); irregular Bern → Thun. Found before the rule of half of all gaps (2026-10-08);
  since, only Uster → Winterthur and Bern → Thun rechecked (live, desktop).
- One model (2026-10-07): `:core` by `MinimumsTest`, `SearchTest`, `FoundTest` (the two
  surprises on a fake API) and `find()` live for Thu 8 Oct in a one-off test (CLAUDE.md, One
  model, step 1). The page on the phone the same day (CLAUDE.md, One model, step 2), in
  English; Help's new 🎨 and ⏱️ texts in all four; an older `result.json` ignored after the
  update. Not seen: whether the faded − and + read as disabled (TalkBack).
- Delays (2026-10-06, R8 release build, German only): real "+1"s and the "as of" line seen live
  (Horw → Sursee at 23:22 and 23:30), kept after a force-stop, absent for tomorrow's 08:50. The
  red "! 1 min", the grey "−2 min" and a changed track (14 instead of 12) only from a made-up
  `result.json` written with `run-as` into the debug build (and read back by the R8 build); no
  real changed track or too-short change seen (`prognosis.platform` was null everywhere). Not
  seen: English, French, Italian; a delay of 10 or more next to the time; a negative delay;
  a cancelled train; the new Help texts on the phone; TalkBack reading the delay words.
- Declutter pass 3's fixes (2026-10-07): seen on the phone the same day, except Destination's
  first frame back from Help or Settings and after the language switch (only a cold start
  recorded), the page after a change in Settings with Optimization off (only Cancel), and the
  titles as headings in TalkBack.
- The fixes after that test pass (2026-10-07), seen on the phone the same day (R8 release): one
  station per change (Luzern → Zürich, Central 08:00: nothing faster, the tram from
  Bahnhofstrasse/HB gone); the keyboard's next keys (From → To → the time, past ⇅) and the number
  keyboard's search key (searches, closes the keyboard); ⇅ between the borders (its grey circle
  seen 2026-10-07, in a screenshot; a hardware Enter past it, over adb); the
  no-break spaces in Help (German, English, French) and the cards; the French day line ("jeu. 8
  oct. :"). Not seen: Italian. The day of the first trip shown (2026-10-07): `FoundTest`, and
  Zürich HB → Bern 23:50 on the phone ("Thu 8 Oct" over the 00:02); not seen with finds on both
  sides of midnight.
- The released APK itself (`gleiswechsel-v0.3.0.apk`, the release key) on a phone: the test
  phone has the R8 build with the debug key. The GitHub APK equals an unsigned build of the tag
  (`apksigcopier compare`, 2026-10-07), and that build, signed with the debug key, took the
  screenshots the same day.
- The themed icon in a launcher that shows themed icons (Niagara doesn't); only checked as a
  render.
- The timetable job (2026-10-07): run once by hand on GitHub (2 min 4 s; the file and page
  1 min 38 s), deployed. Not checked: the schedule (first run Thu 8 Oct, 03:23 UTC), the
  December run with two years' GTFS on GitHub (only on the desktop).
- The full search (2026-10-07): `FullSearchTest`, `FoundTest` and on the phone (CLAUDE.md,
  steps 4.3 and 4.5: the download, the test case, Uster → Horw's find only it has, the read
  time, the line with a 404 URL, ICE000273 in a card, Help's 🚆, ⚠️ and 📡 in all four, Cancel
  1.5 s after Search with the download finished and kept). The test pass (2026-10-07,
  CLAUDE.md step 4.5): 40 commutes on the desktop against an independent Dijkstra at offsets
  0, 1 and 2, the legs against the API's boards, nothing wrong; on the phone the download
  slowed down and interrupted (a turn, Home, `am kill`, Search–Cancel–Search: one download, a
  search started meanwhile waits), 23:50 and 00:20. Not checked: an hour skipped (28 Mar 2027:
  only `FullSearchTest`, made up; the API can't be asked before about 7 Nov 2026), a find
  through an in-seat continuation, a find from it identical to today's on real data (only
  `FoundTest`; the test case's comes from both, shown once), a newly named train in a find only
  it has (none in 15 commutes tried; ICE000273's change is an official one's), the line from a
  copy without the day (unreachable from the page: a search is today or tomorrow), a download
  on Wi-Fi without the VPN (user, 2026-10-07: later, at home; on LTE through Tailscale 3.5 s for
  a whole search with it, the 16 s of step 4.3 not seen again).
  `pm clear --cache-only` hangs on the test phone (Android 16): empty the cache in App info →
  Storage & cache → Clear cache, or with the debug build `run-as io.github.buerlino.gleiswechsel
  rm cache/timetable.bin.gz` and then the R8 build over it (`install -r`; 2026-10-07).
- The train names (2026-10-07, step 4.2): `TimetableTest` and 48 trains of a file made from
  `private/gtfs/` checked against the API's boards by hand; on Pages since the job's run of
  7 Oct, 14:36 UTC (`LiveTest`), ICE000273 seen on the phone (step 4.5).
- The local copy (2026-10-07, `localTimetable`): `TimetableTest` and the live download from
  Pages on the desktop (once, then from the copy); on the phone (step 4.3) Android's
  `HttpURLConnection` on Pages, once and then from the copy, and a copy cleared in App info.
  A copy older than 7 days downloaded again (step 4.5, the debug build). The test pass's fixes
  (2026-10-07): `TimetableTest` (a read-only directory for a full storage, a copy dated a day
  ahead, a file without its trailer or with a wrong CRC) and the same cases on the published file
  on the desktop; on the phone the copy equal to Pages' (md5) after each interrupted download, no
  `.part` left. Not checked: a real full storage, a phone clock set ahead, a real Wi-Fi login
  page, a copy the system cleared itself.

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
   venv; it needs build-tools' `apksigner` on PATH). Done for 0.1.0 and 0.3.0 (identical).

## F-Droid

The merge request is open (user, 2026-10-06), now with 0.1.0, 0.2.0 and 0.3.0 (`c099eb3d5`,
the pipeline green with `fdroid build`, 2026-10-07): the recipe
`metadata/io.github.buerlino.gleiswechsel.yml`, made like APODroid's and gridload's (`Binaries` +
`AllowedAPKSigningKeys`, `UpdateCheckMode: Tags`, `AutoUpdateMode: Version`), on the branch
`io.github.buerlino.gleiswechsel` off upstream `master`. `../fdroiddata` is the user's fork clone
(`origin` gitlab.com/buerlino/fdroiddata, `upstream` fdroid/fdroiddata); the user commits there,
pushes and makes merge requests. If a push is rejected with "shallow update not allowed":
`git fetch --shallow-since=<date before the fork> upstream master`.
`AllowedAPKSigningKeys` is the release APK's certificate SHA-256 (`apksigner verify
--print-certs`), lowercase without colons. Anti-feature `NonFreeNet` (user, 2026-10-06: as
APODroid's, naming the hosts: transport.opendata.ch, sbb.ch and, since 2026-10-07,
buerlino.github.io); category `Public Transport`
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
2026-10-06; `de-DE`, `fr-FR`, `it-IT`: short and full description (the title falls back to en-US),
same day. `images/icon.png`: both logo SVGs at 512 px (`rsvg-convert -w 512 -h 512` each, `magick
back.png front.png -composite -strip`). `images/featureGraphic.png` (1024×500): source
`logo/featureGraphic.svg` (the signs, cropped, on the back's grey, "Gleiswechsel" in Inter Bold and
the subtitle in Inter Medium, dark text: white on this grey is too faint), render command in its
header comment. `images/phoneScreenshots/`, in English, all embedded in `README.md`, 1 and 2 retaken
for 0.3.0 (user, 2026-10-07; the R8 build of the tag, offset 1): `1.png` the test case's find and
the green Luzern row (unset, the faded default), the page scrolled to the end (since 0.2.0 it's
longer than the screen; user, 2026-10-06); `2.png` (user, 2026-10-07: one for the full search) Uster
→ Horw 06:30 with Zürich HB and Luzern set to 2, the top of the page: S9 → IR75 → S4, 15 minutes
earlier (with the defaults nothing faster); `3.png` (user, 2026-10-08: one for the fastest of the
day, 0.4.0's R8 build before the tag) Horw → Sursee all day, Luzern unset, scrolled to the end as 1:
the S4 → RE24, 33 min, every 60, 18 times, 42%. The time search's page didn't change in 0.4.0 (1 and
2 show the folded line), so 1 and 2 stayed. Commute and times typed on the page, after backing up
`commute.xml` with the debug build's `run-as` (put back the same way afterwards, `result.json`
deleted). Taken with SystemUI demo mode: `settings put global sysui_demo_allowed 1`, then broadcasts
(`am broadcast -a com.android.systemui.demo -e command …`) `enter`, `clock -e hhmm 1200`,
`notifications -e visible false`, `network -e mobile hide` and `network -e wifi show -e level 4 -e
fully true` as two broadcasts (in one, Android 16 showed two Wi-Fi icons; 2026-10-07), `battery -e
level 100 -e plugged false`, `status -e volume hide -e bluetooth hide -e location hide -e alarm hide
-e sync hide -e mute hide -e speakerphone hide` (the VPN key goes with them); after the shot `exit`
and the setting back to 0. `magick … -strip` for the PNG.

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

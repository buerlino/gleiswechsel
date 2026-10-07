# CLAUDE.md

Guidance for working in this repo. It is the source of truth for later sessions: decisions with
the date and a short "why", what was tested and rejected, and open questions. Keep it to current
facts; finished-work narratives go (git keeps them).

## Project

An open-source Android app for Swiss commuters: a precise research tool that checks whether their
daily trip has a faster connection than the one the official planner shows, e.g. by a change that
is shorter than the official minimum transfer time but still doable (user, 2026-10-06). Small
gains count: 5 minutes a day add up to hours a year.

Name **Gleiswechsel**, subtitle **Schneller umsteigen** (user, 2026-10-06). The name never
includes the subtitle (app label, title, applicationId, repo: just Gleiswechsel); the subtitle
goes where a store shows a line under the name: the F-Droid summary
(`fastlane/.../short_description.txt`; the French and Italian ones add their own after a dash,
"Schneller umsteigen – Changer plus vite", user 2026-10-06: respectful) and the README's first
line. The top bar shows the name in the texts' language (user, 2026-10-06): Track switch,
Gleiswechsel, Changement de voie, Cambio binario (`title`); the launcher label stays
Gleiswechsel. applicationId and namespace `io.github.buerlino.gleiswechsel`; Kotlin packages
`io.github.buerlino.gleiswechsel` (app) and `io.github.buerlino.gleiswechsel.core`. Repo
https://github.com/buerlino/gleiswechsel (GPLv3, as the user's other apps). Release APKs are
named `gleiswechsel-vX.Y.Z.apk`. Not affiliated with SBB: no SBB name, logo or colours in the
app or the listing; the one exception is sbb.ch, named as the ticket link's target (user,
2026-10-06).

- How-tos, phone testing and what's still untested: the
  [build skill](.claude/skills/build-gleiswechsel-android/SKILL.md). Where it and this file
  disagree, this file wins.
- Declutter passes: [declutter.md](.claude/skills/build-gleiswechsel-android/declutter.md).
- Research from the first session (2026-10-06), in `research/`:
  [hidden_connections.md](research/hidden_connections.md) (why connections go missing),
  [data_sources.md](research/data_sources.md) (APIs, limits, what was checked),
  [existing_tools.md](research/existing_tools.md) (what to reuse, what not),
  [architecture.md](research/architecture.md) (the proposal, not decided),
  [missing_features.md](research/missing_features.md) (what the app can't do yet, ideas only).
- [research/harmonize.md](research/harmonize.md) (2026-10-07): one model for the track switch
  times, the plan before step 4 of the full search; decided 2026-10-07, see
  [One model](#one-model-for-the-track-switch-times-decided-2026-10-07-being-built).

## How the user works (2026-10-06)

- **Least effort, most open and free, KISS** (user, 2026-10-06, the rules for this app).
- Simplest approach that works. No extra screens, options, layers or abstractions until they are
  needed. Don't clutter the UI or the code. No dead code.
- When unsure, or when a simpler or better idea comes up, ask the user before deciding. Don't ask
  about what's settled below.
- Step by step: do one step, show it working, then wait for the next instruction.
- Commit only when asked. The user pushes; never `git push`.
- When reporting back, say what was actually tested and what wasn't.

## Stack (as the user's other apps, 2026-10-06)

Copied from `~/Documents/source99/APODroid` and `~/Documents/source99/gridload`, two released
apps built this way; their CLAUDE.md files explain each choice.

- Native Android: Kotlin + Jetpack Compose, single Activity. No Flutter, React Native or KMP.
- No proprietary dependencies (Firebase, Play Services, analytics, ads). As few permissions as
  possible; ask the user before adding any. Now: `INTERNET` only.
- Two modules: `:core` (plain Kotlin/JVM, no Android: the logic, parsing and network code,
  unit-tested with JUnit4 via `kotlin-test-junit`) and `:app` (Compose UI, depends on `:core`).
- Few libraries: kotlinx.serialization, `HttpURLConnection` (no Retrofit/OkHttp), activity-compose,
  material3, core-ktx (declared, as in gridload and APODroid: the code uses `prefs.edit { }`).
  Before any other dependency, check whether the JDK or Android already covers it.
- Storage: SharedPreferences for small settings, one JSON file for larger data. No database until
  it's really needed. No background work unless a feature can't work without it.
- Personal data (the commutes) stays on the phone. Real responses and API keys go in the
  gitignored `private/`; tests use made-up data.
- UI in English, German, French and Italian (user, 2026-10-06), every text in `strings.xml`
  (`values`, `values-de`, `-fr`, `-it`), following the phone's language. Formal wherever the
  language has it (user, 2026-10-06): German "Sie" (Swiss spelling: ss, no ß), French "vous",
  Italian "lei". Short texts: one idea per line, explain each concept in one place only. No
  language setting (user, 2026-10-06: people keep the system language), but a hidden one: a tap
  on the top bar's title picks English, Deutsch, Français, Italiano or the phone's language (user,
  2026-10-06, an easter egg), Android 13+ only (`LocaleManager`; older phones: the title does
  nothing). It recreates the page; the result comes back from its file. The day of a
  search is in the texts' language (`language`, `day_pattern`), so a Spanish phone gets English
  throughout. The store listing in the same four (`fastlane/metadata/android/<locale>/`).
- Migrations: remove migration code two releases after F-Droid has shipped past the version that
  needed it (gridload's rule).

## Setup and distribution (copied from gridload and APODroid)

- Gradle wrapper 9.8.0, `gradle/libs.versions.toml` (AGP 9.4.1, Kotlin 2.4.20, Compose BOM
  2026.09.00), `gradle/gradle-daemon-jvm.properties` (Gradle runs on JDK 21; the system `java` is
  too new).
- AGP 9 has built-in Kotlin: in `:app` apply only `com.android.application` +
  `org.jetbrains.kotlin.plugin.compose`. `compileSdk 37`, `targetSdk 37`, `minSdk 26` (as
  gridload), Java 17.
- Light theme (`Theme.Material.Light.NoActionBar` + Compose `lightColorScheme()`), as gridload;
  not following the system (user, 2026-10-06). Its `primary` is the logo's blue `00179B` (user,
  2026-10-07: the buttons, the switch and the links were Material's purple).
  The bars' icons are always dark (`SystemBarStyle.light`, 2026-10-06): `enableEdgeToEdge()`
  alone made them white on a phone in dark mode.
- Backup: no cloud backup, phone-to-phone transfer allowed (`data_extraction_rules.xml`;
  `allowBackup="false"` covers Android 8–11).
- Release signing from gitignored `keystore.properties` or env vars (`GLEISWECHSEL_KEYSTORE_FILE`,
  `_KEYSTORE_PASSWORD`, `_KEY_ALIAS`, `_KEY_PASSWORD`), unsigned without either (what F-Droid
  wants). Its own keystore (the user keeps it) and the CI secrets `KEYSTORE_BASE64`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` are in place (user, 2026-10-06); the releases
  are signed with them.
- `.github/workflows/release.yml` builds a signed APK on a `vX.Y.Z` tag and attaches it to a
  GitHub Release (Obtainium); `test.yml` runs the `:core` tests, lint and the debug build on branch
  pushes and pull requests (2026-10-06: a broken app build first showed on a tag); `release.yml`
  runs lint too. `timetable.yml` makes the full search's file (step 3 below).
  `fastlane/metadata/android/<locale>/` for F-Droid (en-US, de-DE, fr-FR, it-IT), each with
  `changelogs/<versionCode>.txt`.
- Release build uses R8 (minify + shrinkResources). The build must be reproducible: no
  timestamps, build paths or machine-specific values in the APK; `dependenciesInfo` off.
- Icon: the user's logo (2026-10-06), `logo/gleiswechsel_back.svg` and `_front.svg` on the
  108×108 adaptive grid, as vector drawables in `res/drawable/` (background, foreground and the
  themed monochrome layer); the listing's `icon.png` and `featureGraphic.png` from the same
  SVGs. How: the skill, Icon.
- Android SDK in `~/Android/Sdk`. The user tests on a real phone over adb, no emulator.
- Git branch `master`, remote `origin` https://github.com/buerlino/gleiswechsel.git.

## Data source (see research/data_sources.md)

- **transport.opendata.ch**, in use: no key (nothing secret in an open-source app), JSON, the
  official connections. Checked with real requests 2026-10-06; its quirks are in the research
  file. It computes them with MOTIS on the same Swiss GTFS as the full search, plus GTFS-RT
  (its docs, read 2026-10-07), over a window of days (27 Sep 2026 to 25 Feb 2027 that day).
- Proposed (2026-10-06): **OJP 2.0** only as an option with the user's own free key (20,000
  requests a day per key, so no shared key in the app).
- **The Swiss GTFS** (opentransportdata.swiss, no key), for the full search: decided 2026-10-06,
  not in the app yet, see [The full search](#the-full-search-decided-2026-10-06-being-built).
- No server of our own.

## Test case: Horw → Sursee (user, 2026-10-06)

The user's own hidden connection, the basis of the first version. Checked with
transport.opendata.ch for Wed 7 Oct 2026:

- **Hidden:** S4 (zb) Horw 08:53 → Luzern 09:01 (track 12, 13, 14 or 15: it varies) → RE24 (SBB)
  Luzern 09:05, track 9 → Sursee 09:26. The planned change is **4 minutes**.
- **Official for the 08:53:** S4 → S1 Luzern 09:14 (track 3) → Sursee 09:40. The hidden one is
  **14 minutes faster**.
- Luzern's official minimum (zb → SBB) is 5 or 6 minutes: 6-minute changes are offered (S41
  08:38 → S1 08:44), the 4-minute one isn't.
- Every hour: S4 Horw :53 → Luzern :01; RE24 Luzern :05 → Sursee :26. The RE24 arrives on
  track 9 at :55 and turns around there, so it's already waiting (why it's "almost always 9").
  The S4 at :23 has no such train.
- **The user's record:** 50+ times, missed once. They feel it as "arrive :00, 5 minutes"; the
  timetable says :01 and 4. With a late S4 (:02–:03) they run.

## The app

The search page has three panels, each with its title (user, 2026-10-06): **Destination** (the
commute and Search), **Journey** (the result) and **Optimization** (the rider's track switch time
at each change station). A top bar: **⚙** (top left) opens
Settings, **?** (top right) Help (user, 2026-10-06), text buttons as in gridload; each has ← and
takes the back gesture. A screen reader says a word for each symbol (⚙, ?, ⇅, ←, ▴, ✕, −, +:
`spokenAs`), and the titles (the panels', Help's topics) are headings it can jump between
(`Heading`, 2026-10-07).
The page's state lives above the three, so Help and Settings don't lose the result. Turning the
phone doesn't recreate the activity (`configChanges`, 2026-10-06: one line, no ViewModel), so
the result and a running search stay.

**The last result is kept** (user, 2026-10-06: e.g. to read the track on a platform with poor
reception): a search with connections writes it to `result.json` in the app's own files (`Found`
in `:core`: the day, the first official connection, the change stations, the finds, the shortest
change at each station the planner's answers made, `incomplete` and when it searched (`asOf`);
times as ISO text), and
whatever clears or fades the result deletes it (editing the commute, a time, the offset or
Optimization, Search, Cancel), so it never shows another commute's finds. The page opens with it,
Destination folded, after a restart, the system killing the app or the language switch; the
Optimization rows and the lowered minimums come back with it. Never dropped by time (user,
2026-10-06): the day line says which day it's for, and yesterday's tracks are usually today's. A
file that can't be read (e.g. an older format after an update) is ignored quietly and logged (user,
2026-10-06); no migration. No cloud backup; a phone-to-phone transfer takes it.

Destination (user, 2026-10-06): before a search it sits in the middle of the page (of the
space above the keyboard while typing); Search moves it to the top and folds the three fields
into one line (from → to, time ▾), a tap on which opens them again. Opened after a search, a ▴ on the Search button's row
folds them again (user, 2026-10-06: once opened they couldn't be hidden; one mark each way,
none on the title). A ✕ on the right of Journey's title (plain text, as the fold marks,
2026-10-07: a button made the title taller) closes the result and the Optimization rows (and
deletes the saved file), so Destination is back in the middle, as on start (user,
2026-10-06); while a search runs it isn't there (Search is Cancel). The Search button stays in
view, folded or not (to search again after editing a time); while a search runs it reads Cancel
(user, 2026-10-06: a slow API meant a long, locked wait), which unlocks the fields at once and
drops the late answer (the requests themselves can't be stopped). With no result and no rows
shown (editing the commute clears both; Optimization off hides the rows) it goes back to the
middle. A screen reader says whether a
fold (this line, Optimization, a card's official connection) is open or folded, not the ▾ or ▸
(`folding`, `FoldMark`).

**Track switch time** (user, 2026-10-06) is the name, in every text, of the minutes from one
train to the next, walk included: Gleiswechselzeit, temps de changement de voie, tempo di cambio
binario; at a change in a card just "track switch" (Gleiswechsel, changement de voie, cambio
binario). The planner's minimum is the official track switch time; the global offset shifts it
at every station the rider hasn't set.

- **The commute** (2026-10-06): three text fields, each saved in SharedPreferences (`commute`) as
  it is typed. Stations go to the API as typed: it matches loosely ("horw", "Luzern Bhf" and even
  "Surseexq" find the station); a name it can't match gives no connections, and the page says so.
  The time takes `8:50`, `08:50`, `850` or `0850` (the number keyboard has no colon). Search asks
  for the next such time, Swiss time (today or tomorrow), and shows the day. Editing a field
  clears the result, so it never shows another commute's finds; the fields are locked while it
  searches. A ⇅ button swaps from and to (user, 2026-10-06): on the right, centred over the gap
  between the two fields, which keep their distance; a plain blue symbol, as ⚙ and ? (user,
  2026-10-07: its pale background was Material's). The keyboard's key goes From → To → the time,
  and there searches (user, 2026-10-07: it only closed the keyboard).
- **Optimization** (user, 2026-10-06), under Journey: a line saying to lower the time and search
  again to find more, then a row per station the search changed at (the official connections'
  changes and those of the onward connections, see the local search), each station once, in
  the order searched (Horw → Bern, Bundesplatz: Luzern, Olten, Bern): the station, then its
  time in the box between − and +. So a commute with nothing faster can be set too; tapping a
  find's change, the first idea, couldn't do that. Saved in `commute` keyed by station id
  (`8505000` → `4`). A tap on the title folds the panel (▸) or opens it (▾). A switch in Settings
  turns the panel off (key `optimize`, on until set); off, the times set there are kept but not
  used. Editing a time fades the finds to 60% until the next Search and keeps the rows (user,
  2026-10-07: removing the finds moved the rows, so a second tap on − or + missed); editing the
  commute clears both. The rows come only with a search that has connections (user, 2026-10-06:
  without one they don't help) and are kept with its result, so after a failed search, or a restart
  once the result was cleared, they're back with the next one.
- **− and +** (`MinutesStepper`, user, 2026-10-06) set every track switch time the rider sets, in
  Optimization and the offset in Settings: a box to type in didn't look changeable. Plain − and
  +, no circle around them (user, 2026-10-06). A step is a minute (0 to 99). Unset shows the
  default faded, the box's text colour at 60%; stepping onto the default unsets it again, so a
  row follows the offset.
- **The track switch time is the number the app is about** (user, 2026-10-06): always in the
  same box (`MinutesBox`), in the cards, Optimization and Settings, coloured against the official
  one at that station (user, 2026-10-06): green below, orange the same, red above, gridload's
  three with its text colours (green `2E7D32` and red `C62828` with white, orange `FFA000` with
  black), no border (user, 2026-10-06). A row's colour follows its value or its default; the
  offset in Settings is coloured against 0 (−1 green, 0 orange). A find's own change is so green,
  the official ones in its card orange or red. Not by colour alone (user, 2026-10-06, for
  colour-blind riders): ↓ before the minutes below the official one, ↑ above, none the same, and a
  screen reader says it (`box_below`, `box_same`, `box_above`); the offset has its sign instead
  (`arrows = false`).
- **The finds** (user, 2026-10-06: before, "a wall of text"): a card each with the minutes saved,
  the arrival instead of the official one, how much more efficient, then the trip as a
  timetable: a row per stop (time, station, track), the train in between, and at each change its
  minutes in the box. Stations underlined, the train (RE24) in a black outline without fill, the
  track's number as a platform sign: white on the logo's blue `00179B`, square corners, with a
  rounded white line inside, a bit bigger (user, 2026-10-06). A walk between two trains is part of the change (the box, then
  the walk's minutes); one before the first train or after the last is a row of its own. Under
  the trip, a small grey line (▸) opens the official connection it beats, the same timetable at
  60% (user, 2026-10-06: there but not in the way). With nothing faster, Journey shows the
  official connection leaving first in a card of its own, so the rider sees where it changes.
  A find that passes a station twice (`Connection.doublesBack`, from the API's `passList`) says
  under its efficiency to check the ticket (user, 2026-10-06): a route going back over itself may
  need another one (research/hidden_connections.md).
- **Delays and changed tracks** (user, 2026-10-06), from the same answers, no extra request
  (`Stop.delay`, `Stop.newPlatform`): in every card's timetable (the finds, the folded official
  connection, the official one alone), a delay of a minute or more as a red "+3" after the
  planned time (it pushes that row's station a little to the right), and a changed track on the
  sign with the planned one struck through before it; a screen reader says both (`late`,
  `track_changed`). Where the delays make a change shorter than planned and than the rider's
  time there (`tooShort` in `:core`), its box is red with a "!" and the minutes left
  (`LateBox`), instead of the usual one; below zero grey without the "!", the next train gone
  (user, 2026-10-06). They go stale (user, 2026-10-06): kept in result.json, with a line under
  the day giving the search's time (`delays_as_of`, `Found.asOf`) whenever a trip has a delay known
  (`delaysKnown`). The API knows them only a few hours ahead (checked 2026-10-06 at 23:07: 2
  hours ahead yes, 5 no, tomorrow morning not), `delay` null until then, 0 on time; the card
  shows nothing for either. The search itself still uses planned times. Cancellations: no field
  in the API (research/data_sources.md); OJP and GTFS-RT have them, both with a key.
- **Ticket on sbb.ch** (user, 2026-10-06): on that grey line's right, a text button opens sbb.ch's
  timetable in the browser with the official connection's from, to and departure, to buy the
  ticket there (`ticketUrl` in `:core`):
  `https://www.sbb.ch/<language>?stops=_I<from id>~_I<to id>&day=yyyy-MM-dd&time=HH_mm&moment=dep`,
  Swiss time, the texts' `language`. Undocumented: the format sbb.ch uses itself (its own URLs put
  the name before `_I`; the id alone is enough, sbb.ch fills in the name), so it can change.
  Checked on the phone 2026-10-06 in de, en, fr and it. SBB's old format
  (`stops=[{"value":…}]&date="…"`) opens with empty fields. www.sbb.ch has no Android app links;
  the SBB Mobile link (`app.sbbmobile.ch/timetable?from=…&to=…`) takes names only and ignores
  date and time, so it isn't used. A phone without a browser gets a short message
  (`no_browser`) instead of a crash.
- **Settings** (user, 2026-10-06): the global offset of the track switch time, shown with a −
  (it's subtracted) and saved without it, key `offset` in `commute`, 1 until set
  (`DEFAULT_OFFSET`; user, 2026-10-06: 1, the careful one, and the test case needs it; 2 would
  find more, e.g. 5-minute changes at Zürich HB), and the Optimization switch. Changing either
  fades the finds and keeps the rows, as a time does; locked while a search runs.
- **Help** (user, 2026-10-06: by topic, foldable, an emoji each, short texts without fluff but
  nothing crucial left out): seven titles, all folded until tapped (`Heading`, as the panels'):
  🚆 what the app does, ⏱️ the track switch time (where it's set, the offset), 🎨 the colours and
  arrows, what official means and the red ! and grey boxes, 📈 how much more efficient, 🎫 which ticket covers a find (a
  normal one, a supersaver only the official train, maybe not one passing a station twice), ⚠️
  delays and changed tracks as known at the search (cancellations not) and that the last result
  stays (check its day and its delays' time), 📡 the data sources
  (opentransportdata.swiss wants to be named). Each concept is explained there once. The emojis
  are in the code, the texts in `strings.xml`.
- **How much more efficient** each find is than its official connection (user, 2026-10-06): the
  official time / the find's − 1, each from the first departure to the last arrival (the wait
  before the first train doesn't count). Efficiency is the fastest trip's time / a trip's, so the
  fastest cancels out; same from and to, so it's also how much faster the find goes.
  `Find.moreEfficient` in `:core`; 42% for the test case (47 and 33 minutes). Replaced, same day,
  each one's own efficiency (a find at 100% said nothing), which had replaced the share of time on
  board (it favoured slow trains with short changes).
- **The client:** `connections(from, to, time, version)` asks transport.opendata.ch
  `/v1/connections` (4 connections leaving at or after `time`), parses only trains, stations,
  planned times, platforms and each departure's and arrival's `delay` and `prognosis.platform`
  (its expected times are the planned ones plus `delay`). User-Agent
  `Gleiswechsel/<version> (+https://github.com/buerlino/gleiswechsel)`.
  If the first request (the official connections) fails, for any reason (no network, HTTP 429
  after many searches), the page shows one text, `search_failed` (2026-10-06: the raw reason was
  English on every page). If an onward one fails, only that change is skipped: the page keeps the
  official connections and the other finds, with a line under the day, `not_all_checked` (user,
  2026-10-06). Each exception goes to the log.
- **The local search** (`search`): given the official connections A → B (the page asks for them
  first, so it can tell "no connections" from "nothing faster"); for each change station X on
  them (where a ride ends and the next begins; a walk belongs to the change), the connections
  X → B from the arrival at X plus the rider's transfer time at X; of the rides leaving X itself
  no earlier, the one arriving first (a tie goes to the later one: more time to change); then the same at
  that onward connection's own changes, and so on (`shortened`; user, 2026-10-06: the API keeps
  the official minimum at every later change, so two short changes on one trip, e.g. Luzern and
  Olten, were never combined). Each question goes to the API once per search (two official
  connections on the same train to the same change asked it twice; the API answers too many
  with 429). A find if it reaches B earlier than the official one, unless the planner already
  offers it: an official connection leaves no earlier and arrives no later (user, 2026-10-06;
  Horw → Bern, Bundesplatz showed the same trains as a bus instead of a walk, 4 minutes
  "earlier"). The same rule between the finds (2026-10-06): a card another find beats goes, an
  identical trip shows once, against the official connection arriving first (the smaller saving).
  A change whose request fails gives nothing and the others go on; `search` returns the finds,
  the errors and the stations it changed at (`Searched`).
  **Changes within one station only** (user, 2026-10-07, as the full search): an onward
  connection that starts with a walk or at another stop isn't one. Before, the search asked again
  from the stop the API walked to, and the API also offered stops nearby: Zürich HB → a tram at
  Bahnhofstrasse/HB counted as a 5-minute track switch at Zürich HB, no walk shown (an open question
  until then).
  **Asked again where a time fell** (2026-10-07): a rider's default follows the answers, so a
  change asked before an answer lowered its station's minimum ran with more time than its row
  then shows (Sursee → Oerlikon 07:45, offset 2: Zürich HB asked with 3, its row 2, the IR13
  09:08 → Oerlikon 09:14 never found). `search` runs again until every change was asked with the
  time it ends with; what it asked before is answered from memory. Checked live for 8 Oct: 3 more
  requests, 12 in all, and the IR13 found (3 minutes saved).
- **Transfer time per station, covering the whole change, walks included** (user, 2026-10-06:
  "a fixed estimated time we need for a specific trainstation"). `search` takes it as a function
  of the station. **At a station the rider hasn't set: the official minimum there minus the
  offset in Settings, at least 0** (user, 2026-10-06: "the defaults are a fixed amount lower than
  the official ones"; a flat 5 was too high: riders who don't know their times would find nothing
  and think the app doesn't work). With the offset at 1, Horw → Sursee finds the test case with
  nothing set (Luzern 5 − 1 = 4).
- **Official minimums** (2026-10-06): HRDF `UMSTEIGB` from opentransportdata.swiss (timetable
  2026, export of 29 Sep 2026), the Swiss stations (`85…`) and the standard (`9999999`, 2
  minutes, everywhere else) as `app/src/main/res/raw/umsteigb.txt` (2,971 lines, 106 KB), read
  by `Minimums` in `:core`. Luzern 5, Olten 5, Bern 6, Zürich HB 7,
  Basel SBB 6, Sursee 3, most bus and tram stops 0–1. HRDF also has times per operator, line
  and train pair (`UMSTEIGV`, `UMSTEIGL`, `UMSTEIGZ`); none at Luzern, and not used. Refreshed by
  hand at each timetable change (next: 13 Dec 2026), see the skill.
  **The planner's own changes count** (user, 2026-10-06): it offers RE24 → IR16 at Olten in 4
  minutes (track 11 → 8), the table says 5, and `UMSTEIGV`/`L`/`Z` have nothing there (checked in
  the 29 Sep 2026 export: Olten only has "999" pairs), so it uses finer per-track times that
  aren't published. So in each search a station's official minimum is the table's or the
  shortest change any of the API's answers makes there, whichever is less
  (`shortestChanges`, `Minimums.lowered`): the official card shows Olten's 4 orange, its row starts at 3. A rider's
  default at a station follows the answers, and the search asks again where it fell.
- **Requests:** 1 + one per change searched (those of the onward connections too, each question
  once), + one per change asked again where its time fell (5 for Horw → Sursee).
- **Proven live** 2026-10-06 for Wed 7 Oct (`LiveTest`): 08:50 with 4 minutes finds the
  [test case](#test-case-horw--sursee-user-2026-10-06) (14 minutes saved, RE24 track 9), with 6
  minutes nothing; 14:50 finds the same change from the 14:53 (it repeats every hour).
- **Limits, known:** the search uses planned times only (delays are shown, not searched with); only stations the official
  connections or the onward ones touch; changes only within a station; the live check doesn't skip public holidays; the official
  minimums are a copy of one timetable year's, lowered only where an answer shows the planner's
  finer time; `doublesBack` sees only stations by id (a train station and its bus stop differ).

## The first version (user, 2026-10-06)

The scope: one saved commute, the rider's transfer time per station, the local search, one result
list (user, 2026-10-06: "focus on the core utility").

Released: 0.2.0 (tag `v0.2.0`, versionCode 2), after 0.1.0. The F-Droid merge request is open
(user, 2026-10-06), from the branch `io.github.buerlino.gleiswechsel` in `../fdroiddata`, with
0.2.0 (`6cad67402`, pushed); its `NonFreeNet` text names sbb.ch too (user, 2026-10-06: the
ticket link). Before every push to the fork: the skill's checks (user, 2026-10-06: the pipeline
failed twice without them). README's "Soon on F-Droid" stays until F-Droid has it.

## The full search (decided 2026-10-06, being built)

Today's search tries other trains only where the official connections change: the API has no
transfer time setting, so it never suggests a route through other stations. The full search
finds the fastest train route A → B through any stations, with the rider's track switch time at
every change (theirs where set, else the official minimum minus the offset, as today).
research/architecture.md, phase 2.

Decided (user, 2026-10-06, after a research session):

- **A GitHub Actions job**, weekly and by hand, downloads the Swiss GTFS, keeps the trains of the
  next 14 days, writes a compact file (about 1 MB) and deploys it to GitHub Pages. The same file
  for everyone, so the commute never leaves the phone; no server. Not a release or a tag: those
  would show up in Obtainium and in F-Droid's tag check.
- **Trains only** (GTFS `route_type` 100–117): buses are 12× the data.
- **The app** downloads the file during a search when its copy is missing or older than 7 days
  (keeping the old copy if that fails) and runs a connection scan (CSA) over it on the phone.
- **Both searches run:** today's stays; the finds of both go through the same filters.
- **Changes within one station only** (same station id), no walks between stations, as today's
  search (user, 2026-10-07).
- **Only when A and B** (the official connections' first departure and last arrival) are both in
  the file; otherwise today's search alone.
- **Later, not now:** checking each find with the API leg by leg (delays, changed tracks, trains
  the file has that no longer run).

Allowed (opentransportdata.swiss's terms, checked 2026-10-06, research/data_sources.md): the
data may be processed and published; the published file must name opentransportdata.swiss as its
source (§5.1) and be published under the project's name (§5.3). GitHub Pages: 1 GB a site, 100 GB a month (soft), so about
100,000 downloads of 1 MB.

Measured (2026-10-06, a Python prototype, not kept): the trains of 14 days are 51,382 trips,
544k stop events and 5,010 platforms; a plain binary (u16 platform, u16 arrival minutes, u8
dwell per stop) 3.0 MB, 0.9 MB gzipped; reading the GTFS about a minute. A CSA over one day's
trains took 3–10 ms per search and found the test case with Luzern at 4 minutes. 377 random
trips between the 150 busiest stations, offset 1: 33 faster, 13 of them through stations today's
search can't reach (overcounted: it compared against `UMSTEIGB`, and the planner has finer
times, e.g. 6 minutes at Zürich HB where the table says 7).

Steps (user, 2026-10-06), one at a time, each shown working:

0. These notes (done).
1. `:core`, the timetable file (done 2026-10-07, `Timetable`, `trains`, the skill): a writer
   (GTFS zip → the trains of 14 days from a given date), a reader, JDK only; a format number
   first, so an older app ignores a newer file quietly and logs it; a Gradle task runs the
   writer; tests on a made-up GTFS. On the real GTFS (the 30 Sep export, 7–20 Oct): 51,382
   trips, 544,085 stops, 5,010 platforms, 3,023 stations, 4,822 continuations; **453 KB**, made
   in 25 s; read in 80–120 ms on a desktop JVM, about 18 MB of heap. The test case's S4 and
   RE24 are in it with their tracks. Each stop's times as the step from the previous departure
   plus a dwell byte: plain times made it 1.38 MB.
2. `:core`, the CSA (done 2026-10-07, `fullSearch`, the skill): for each official connection
   whose A and B are in the file, the earliest arrival at B leaving A no earlier, then a scan
   backwards for the latest departure from A arriving then. It scans only the rides between the
   first official departure and the last official arrival (a find arrives before its official
   one), of the trips of the day before (past 24:00) to the last day. An in-seat continuation is
   one ride under the first train's name, as the API sends it. The finds go through `best`, now
   shared with `search`. `transfer` is asked once per station, so per-call work doesn't matter.
   On the real file (7–20 Oct; live officials for Thu 8 Oct): the test case with the app's
   defaults (Luzern 5 − 1), nothing with 5 there. 10 ms for Horw → Sursee, 16–21 ms for long
   trips and overnight windows, on a desktop JVM, warm; the first search about 200 ms (JIT).
   Checked on the way (2026-10-07):
   - **The Swiss GTFS counts on the clock**, not from noon minus 12 hours as GTFS says: on
     25 Oct 2026 (clocks back) it has the same night trains as on 1 Nov, and the API reads them on
     the clock (SN1 Winterthur 02:35 +02:00, 03:35 +01:00). The scan does the same; Java takes an
     hour that comes twice as the first, as the API does. **28 Mar 2027** (an hour skipped,
     checked in the 2027 GTFS 2026-10-07): 72 train trips of that day and 21 of the 27th stop
     between 02:00 and 03:00, an hour that doesn't exist (SN1 Winterthur 02:35, S3 Basel SBB
     02:45; 574 stop events that hour, about 795 on the Sundays around it). The scan reads them as
     an hour later (Java). How the API shows them: not known, it can't be asked before its window
     reaches 28 Mar (about 7 Nov 2026).
   - **In-seat continuations in the API:** one section each (6 tried: IR70 → IR13 at Zürich HB,
     RE6 → R20, S7 → RE7, RE3 → RE13, RhB RE24 → RE4, S17 → S4), so they never lower a minimum
     through `shortestChanges`.
   - **12 of 4,822 continuations** go on into the next service day (Italian S50 and S into the
     S10 at Mendrisio, 00:34): not modelled, so a change there.
   - **Trips are split by days**: the RE24 Luzern 09:05 is five trips over 7–20 Oct, every day
     between them.
   - **Train names** (`route_short_name`): Swiss lines as the API's (S, SN, R, RE, IR, IC,
     IRLEX); foreign ones not: ICE `651A` (API `ICE651A`), TER `K23` (`TERK23`), Jungfraubahn
     `65` (`CC65`), EC and TGV without a number (API `EC000015`, `TGV009210`): open question 1.
3. `.github/workflows/timetable.yml` (done 2026-10-07; the skill):
   Thursdays and Sundays at 03:23 UTC, the day after the GTFS is mostly updated, and by hand
   (user, 2026-10-07: twice a week, as the terms' §5.2 says). It downloads the year's dataset
   and, when the 14 days reach December, the next year's; `trains` takes the days each one has
   (its `feed_start_date` to `feed_end_date`), and a day none has fails the job, so the old file
   stays on Pages. The two meet without overlap: 2026 has the service days to 12 Dec 2026, 2027
   from 13 Dec. A file for 6–19 Dec from both: 62,802 trips, 572 KB, 31 s; the test case found
   on 11–14 Dec. Then `timetable.bin.gz` and `index.html`, which names opentransportdata.swiss
   as the source and Gleiswechsel as the publisher (§5.1, §5.3), to Pages
   (`upload-pages-artifact@v5`, `deploy-pages@v5`). Its two scripts run on the desktop: 22 s
   download, 38 s file and page. First run on GitHub, by hand 2026-10-07: 2 minutes (5 s
   download, 1 min 38 s file and page); https://buerlino.github.io/gleiswechsel/timetable.bin.gz,
   453,334 bytes, the same bytes as the desktop's from the same GTFS and day; Pages sends it as
   `application/gzip` without `Content-Encoding`, so the app reads it as is. Read with
   `timetable()`: 7–20 Oct, 51,382 trips, 55–140 ms, about 18 MB of heap (desktop JVM).
   **GitHub switches off scheduled workflows in a public repo after 60 days without activity**
   (a commit counts): the file then runs out of days within 14. Switch it on again in the
   Actions tab, or commit.
4. The app, after [One model](#one-model-for-the-track-switch-times-decided-2026-10-07-being-built):
   the download, both searches, Optimization rows (today's plus the finds' change stations),
   Help and README in all four languages, F-Droid's `NonFreeNet`, which version; on the phone,
   and how long loading the file takes there. `best(today's finds + the full search's,
   officials)`, today's first: of two the same, the first stays, and it has the API's names and
   delays. The full search runs after today's, with the same rider function (the table's
   minimums, nothing lowered); its `transfer` is asked at every station it scans, so its rows
   are only its finds' change stations (today's are `Searched.changes`). Its `error` (should the
   two scans disagree) is caught and logged.

### One model for the track switch times (decided 2026-10-07, being built)

[research/harmonize.md](research/harmonize.md) traces the two surprises on the phone
([research/optimization_rows.md](research/optimization_rows.md): Brugg AG a row no card goes
through, Luzern at 4 orange in one search and green in the next) to one root: the official time
at a station was worked out anew in each search from the planner's answers. Decided (user,
2026-10-07: "show the best option … precise, while always showing the fastest path"; the
colours matter less):

- **D1 The official time at a station is the table's** (`UMSTEIGB`, which the GTFS repeats), the
  same in every search and in both searches. Why: the planner's own changes are official
  connections, so a find has to beat them anyway; lowering a station's time from one train
  pair's change assumes it for every pair there, other tracks too, so a find from it may not be
  doable. The precise way to such finds: the GTFS's per-pair times in the full search (later).
- **D2 The default is the table's − the offset, at least 0**, stable; the rider's time is the
  set one, else the default. One function in `:core` for both searches, the cards and the rows.
  Cost, accepted: the IR13 at Zürich HB (Sursee → Oerlikon, offset 2) needs Zürich HB set to 3.
- **D3 Rows: every station the search asked a time for, as now (A)**, plus the full search's
  finds' change stations; those not on a trip on the page (each find and the official
  connection it beats; with nothing faster, the official connection shown) faded (user: "the
  user cannot find a faster route (potentially) by lowering one of the stations … not in the
  current journey … lower the opacity so it's clear that it's not part of the current route").
- **D4 A change the planner itself makes is official, per change:** its box is never green,
  orange at or below the station's time, red above, in the official connection's timetable and
  at a find's change the answers also make. The S1 → S41 in 4 doesn't make Luzern 4 for the
  S4 → RE24. Kept with the result: each answer's changes (station, arrival, departure). Help
  says once that the planner sometimes allows less for particular trains.
- **D5 The table stays bundled** (it equals the GTFS's and works offline before the first
  download); from the timetable file later.

What goes: `Minimums.lowered`, `shortestChanges`, the re-run loop in `search` ("Asked again
where a time fell" and "The planner's own changes count" under The app describe it until then),
`Found.shortest`, the page's `official` state. `result.json` gets a new shape; an older one is
ignored (no migration). `find()` and `parseTime()` move to `:core` with tests (declutter pass
3, 3.1).

Steps, one at a time: the model in `:core` with tests; the page on it (colours, faded rows,
Help's 🎨 and ⏱️ in all four languages; on the phone the two surprises again: Luzern 4 green in
both, Brugg AG faded); then step 4.

## After the first release

Each timetable change (next: 13 Dec 2026, timetable 2027): refresh the official minimums (the
skill), the year in Help, and release, or the app compares against last year's minimums. Later
versions need no F-Droid merge request: F-Droid picks up the tag (`AutoUpdateMode`), as for
APODroid.

Ideas, not decided (Claude, 2026-10-06; ask the user first):

- Search the next weekday: on a Friday evening the next 08:50 is Saturday's (the day is shown,
  but a commuter wants a weekday).
- More than one commute (now one, and ⇅ for the way back).
- Station suggestions from `/v1/locations`, only if typing the names is annoying.
- From [missing_features.md](research/missing_features.md) (2026-10-06): changes at stops the
  train only passes through,
  "arrive by", more than four official connections, "now", the saving in a year, parallel
  requests.

Dropped (0.2.0; user, 2026-10-07): the risk of each change, how often it worked, from the
published actual times (research/architecture.md). The cards show the delays known at the search
instead.

## Open questions (for the user)

1. **Foreign train names in the full search** (2026-10-07): GTFS `route_desc` is the category
   (ICE, TER, CC, TGV). Putting it before a `route_short_name` that doesn't start with it would
   match the API for ICE, TGV, TER and the Jungfraubahn (`ICE651A`, `TERK23`, `CC65`), but make
   `PEGEX`, `REN1`, `IRVAE`, `SEV` of the Glacier Express, the Nachtnetz, the Voralpen-Express and
   rail replacement. EC and TGV the API names by train number, which needs `trip_short_name`.
   Swiss lines match already; a find shows the API's name where today's search found it too.

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
app or the listing.

- How-tos, phone testing and what's still untested: the
  [build skill](.claude/skills/build-gleiswechsel-android/SKILL.md). Where it and this file
  disagree, this file wins.
- Declutter passes: [declutter.md](.claude/skills/build-gleiswechsel-android/declutter.md).
- Research from the first session (2026-10-06), in `research/`:
  [hidden_connections.md](research/hidden_connections.md) (why connections go missing),
  [data_sources.md](research/data_sources.md) (APIs, limits, what was checked),
  [existing_tools.md](research/existing_tools.md) (what to reuse, what not),
  [architecture.md](research/architecture.md) (the proposal, not decided).

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
  nothing). It recreates the page, so the result is gone. The day of a
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
- Light theme (`Theme.Material.Light.NoActionBar` + Compose `lightColorScheme()`), as gridload.
  The bars' icons are always dark (`SystemBarStyle.light`, 2026-10-06): `enableEdgeToEdge()`
  alone made them white on a phone in dark mode.
- Backup: no cloud backup, phone-to-phone transfer allowed (`data_extraction_rules.xml`;
  `allowBackup="false"` covers Android 8–11).
- Release signing from gitignored `keystore.properties` or env vars (`GLEISWECHSEL_KEYSTORE_FILE`,
  `_KEYSTORE_PASSWORD`, `_KEY_ALIAS`, `_KEY_PASSWORD`), unsigned without either (what F-Droid
  wants). Its own keystore (the user keeps it) and the CI secrets `KEYSTORE_BASE64`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` are in place (user, 2026-10-06); no release
  has used them yet.
- `.github/workflows/release.yml` builds a signed APK on a `vX.Y.Z` tag and attaches it to a
  GitHub Release (Obtainium); `test.yml` runs the `:core` tests on branch pushes and pull requests.
  `fastlane/metadata/android/en-US/` for F-Droid, with `changelogs/<versionCode>.txt` from the
  first release on.
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
  file.
- Proposed (2026-10-06): **OJP 2.0** only as an option with the user's own free key (20,000
  requests a day per key, so no shared key in the app).
- Proposed (2026-10-06): **Ist-Daten** (every actual arrival and departure, daily CSV, archive
  since 2016) for the risk of a change, processed off the phone (a nightly GitHub Actions job),
  never on it.
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
  timetable says :01 and 4. With a late S4 (:02–:03) they run. A reference for the risk
  statistics later.

## The app

The search page has three panels, each with its title (user, 2026-10-06): **Destination** (the
commute and Search), **Journey** (the result) and **Optimization** (the rider's track switch time
at each change station). A top bar: **⚙** (top left) opens
Settings, **?** (top right) Help (user, 2026-10-06), text buttons as in gridload; each has ← and
takes the back gesture. A screen reader says a word for each symbol (⚙, ?, ⇅, ←: `spokenAs`).
The page's state lives above the three, so Help and Settings don't lose the result. Turning the
phone doesn't recreate the activity (`configChanges`, 2026-10-06: one line, no ViewModel), so
the result and a running search stay.

Destination (user, 2026-10-06): before a search it sits in the middle of the page (of the
space above the keyboard while typing); Search moves it to the top and folds the three fields
into one line, "Horw → Sursee, 08:50 ▾", a tap on which opens them again. The Search button stays
in view, folded or not (to search again after editing a time). With no result and no rows
(editing the commute clears both) it goes back to the middle.

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
  between the two fields, which keep their distance.
- **Optimization** (user, 2026-10-06), under Journey: a line saying to lower the time and search
  again to find more, then one row "X (−) [4 min] (+)" per change station of the official connections, each station once, in route order
  (Horw → Bern, Bundesplatz: Luzern, Olten, Bern). So a commute with nothing faster can be set
  too; tapping a find's change, the first idea, couldn't do that. − and + step a minute (0 to 99): a
  box to type in didn't look changeable (user, 2026-10-06). Unset shows the default (official
  minimum − offset) faded, the box's text colour at 60%; stepping onto the default unsets it
  again, so it follows the offset. Saved in `commute` keyed by station id (`8505000` → `4`). A
  tap on the title folds the panel (▸) or opens it (▾). A switch in Settings turns the panel off
  (key `optimize`, on until set); off, the times set there are kept but not used. Editing a time clears the finds but keeps the rows (Search again);
  editing the commute clears both. The rows come only with a search that has connections (user,
  2026-10-06: without one they don't help), so after a restart they're back with the next one.
- **The track switch time is the number the app is about** (user, 2026-10-06): always in the
  same box, in the cards, Optimization and Settings (`MinutesBox`, `MinutesStepper`,
  `MinutesField` for the offset), coloured against
  the official one at that station (user, 2026-10-06): green below, orange the same, red above,
  gridload's three with its text colours (green `2E7D32` and red `C62828` with white, orange
  `FFA000` with black), no border (user, 2026-10-06).
  A row's colour follows its value or, empty, its default; the offset in Settings is coloured
  against 0 (−1 green, 0 orange). A find's own change is so green, the official ones in its card
  orange or red.
- **The finds** (user, 2026-10-06: before, "a wall of text"): a card each, "14 min earlier",
  "Sursee 09:26 instead of 09:40", how much more efficient, then the trip as a timetable: a row
  per stop (time, station, track), the train in between, and at each change its minutes in the
  box. Stations underlined, the train (RE24) in a black outline without fill, the track's number
  as a platform sign: white on the logo's blue `00179B` with a white line inside, a bit bigger
  (user, 2026-10-06). A walk between two trains is part of the change ("7 min track switch, 6 min walk"); one
  before the first train or after the last is a row of its own. Under the trip, a small grey
  "Official connection ▸" opens the official connection it beats, the same timetable at 60%
  (user, 2026-10-06: there but not in the way). With nothing faster, Journey shows the official
  connection leaving first in a card of its own, so the rider sees where it changes.
- **Settings** (user, 2026-10-06): "Global offset of the track switch time [−_ min]" (the − in
  the box: it's subtracted), key `offset` in `commute`, 1 until set (`DEFAULT_OFFSET`, proposed,
  see open question 4), and the Optimization switch. Changing either clears the finds and keeps
  the rows; locked while a search runs.
- **Help:** what the app does, the track switch time and its colours, how much more efficient,
  that times are planned only, and the data sources (opentransportdata.swiss wants to be named).
  Each concept is explained there once.
- **How much more efficient** each find is than its official connection (user, 2026-10-06): the
  official time / the find's − 1, each from the first departure to the last arrival (the wait
  before the first train doesn't count). Efficiency is the fastest trip's time / a trip's, so the
  fastest cancels out; same from and to, so it's also how much faster the find goes.
  `Find.moreEfficient` in `:core`; 42% for the test case (47 and 33 minutes). Replaced, same day,
  each one's own efficiency (a find at 100% said nothing), which had replaced the share of time on
  board (it favoured slow trains with short changes).
- **The client:** `connections(from, to, time, version)` asks transport.opendata.ch
  `/v1/connections` (4 connections leaving at or after `time`), parses only trains, stations,
  planned times and platforms. User-Agent
  `Gleiswechsel/<version> (+https://github.com/buerlino/gleiswechsel)`.
  Any failure (no network, HTTP 429 after many searches) shows one text, `search_failed`
  (2026-10-06: the raw reason was English on every page); the exception goes to the log.
- **The local search** (`search`): given the official connections A → B (the page asks for them
  first, so it can tell "no connections" from "nothing faster"); for each change station X on
  them (where a ride ends and the next begins; a walk belongs to the change), the connections
  X → B from the arrival at X plus the rider's transfer time at X; of those leaving no earlier,
  the one arriving first (a tie goes to the later one: more time to change); a find if it
  reaches B earlier than the official one, unless the planner already offers it: an official
  connection leaves no earlier and arrives no later (user, 2026-10-06; Horw → Bern, Bundesplatz
  showed the same trains as a bus instead of a walk, 4 minutes "earlier"). The same rule between
  the finds (2026-10-06): a card another find beats goes, an identical trip shows once, against
  the official connection arriving first (the smaller saving).
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
  Where the API starts an onward connection with a walk to another stop (Zürich HB →
  Bahnhofplatz/HB, 5 minutes on top), the search asks again from that stop, so the rider's time
  replaces the API's walk.
- **Requests:** 1 + one per change, + one per stop the API walks to (5 for Horw → Sursee).
- **Proven live** 2026-10-06 for Wed 7 Oct (`LiveTest`): 08:50 with 4 minutes finds the
  [test case](#test-case-horw--sursee-user-2026-10-06) (14 minutes saved, RE24 track 9), with 6
  minutes nothing; 14:50 finds the same change from the 14:53 (it repeats every hour).
- **Tried live once** (2026-10-06, not kept as a test): Luzern → Zürich, Central at 08:00 with 3
  minutes at Zürich HB. Asked from Bahnhofplatz/HB, the API also offers the neighbouring
  Bahnhofstrasse/HB, so the search found the IR70 (track 9, 08:51) → T10 Bahnhofstrasse/HB 08:54
  (3 minutes saved): probably too short a walk, see open question 1.
- **Limits, known:** planned times only (delays not read); only stations the official
  connections touch; the live check doesn't skip public holidays; the official minimums are a
  copy of one timetable year's.

## The first version (user, 2026-10-06)

The scope: one saved commute, the rider's transfer time per station, the local search, one result
list (user, 2026-10-06: "focus on the core utility").

Left before the first release (0.1.0):

- The reproducible build passed on the release's code (2026-10-06: two clean copies in different
  paths and the repo's own build, the same sha256); after the tag, Claude compares the
  workflow's APK with an unsigned build of the tag (`apksigcopier compare`).
- The F-Droid merge request after the tag: the user, from their fork in `../fdroiddata`.

## After the first release

Wanted (user, 2026-10-06), one at a time:

- **The risk of a find's change:** after the core utility; how is open question 3.
- **Routes through stations the official connections don't touch** (research/architecture.md,
  phase 2): only if the local search finds too little.
- **Delays:** the search reads planned times only; transport.opendata.ch has the live ones. How
  they'd show is open.

Each timetable change (next: 13 Dec 2026, timetable 2027): refresh the official minimums (the
skill), the year in Help, and release, or the app compares against last year's minimums. Later
versions need no F-Droid merge request: F-Droid picks up the tag (`AutoUpdateMode`), as for
APODroid.

Ideas, not decided (Claude, 2026-10-06; ask the user first):

- Search the next weekday: on a Friday evening the next 08:50 is Saturday's (the day is shown,
  but a commuter wants a weekday).
- More than one commute (now one, and ⇅ for the way back).
- Station suggestions from `/v1/locations`, only if typing the names is annoying.
- A change to a different stop: open question 1.

## Open questions (for the user)

1. **A change to a different stop** (train → tram stop, as in Zürich): its own time, a flag in
   the result, or nothing? Left for when a commute needs it (user, 2026-10-06).
2. **The theme:** light (as gridload), or follow the system.
3. **How the risk indicator works** (a later version). Proposed (research/architecture.md):
   from Ist-Daten, the share of past weekdays on which `actual arrival + transfer time ≤ actual
   departure`, worked out by a nightly GitHub Actions job and downloaded by the app as a small
   file. The most work of all: the job, the file, matching trains between the API and
   Ist-Daten. To decide: this, or something simpler first (e.g. how far a change is below the
   official minimum: the app has the minimums now, but it's not a real risk).
4. **The offset until set: 1 minute or 2?** Built with 1 (Claude, 2026-10-06): the test case
   needs it (Luzern 5 → 4, the user's own time), and it's the careful one. 2 finds more, e.g.
   5-minute changes at Zürich HB (7).

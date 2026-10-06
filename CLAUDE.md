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
(`fastlane/.../short_description.txt`) and the README's first line. applicationId and namespace
`io.github.buerlino.gleiswechsel`; Kotlin packages `io.github.buerlino.gleiswechsel` (app) and
`io.github.buerlino.gleiswechsel.core`. Repo https://github.com/buerlino/gleiswechsel (GPLv3, as
the user's other apps); local folder `gleiswechsel` (renamed from the working name `umsteiger`,
2026-10-06). Release APKs are named
`gleiswechsel-vX.Y.Z.apk`. Not affiliated with SBB: no SBB name, logo or colours in the app or
the listing.

- How-tos, phone testing and what's still untested: [.claude/skills/build-gleiswechsel-android/SKILL.md](.claude/skills/build-gleiswechsel-android/SKILL.md).
  Where it and this file disagree, this file wins.
- Declutter passes: [.claude/skills/build-gleiswechsel-android/declutter.md](.claude/skills/build-gleiswechsel-android/declutter.md).
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
  material3. Before any other dependency, check whether the JDK or Android already covers it.
- Storage: SharedPreferences for small settings, one JSON file for larger data. No database until
  it's really needed. No background work unless a feature can't work without it.
- Personal data (the commutes) stays on the phone. Real responses and API keys go in the
  gitignored `private/`; tests use made-up data.
- English UI, short texts: one idea per line, explain each concept in one place only.
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
- Backup: no cloud backup, phone-to-phone transfer allowed (`data_extraction_rules.xml`;
  `allowBackup="false"` covers Android 8–11).
- Release signing from gitignored `keystore.properties` or env vars (`GLEISWECHSEL_KEYSTORE_FILE`,
  `_KEYSTORE_PASSWORD`, `_KEY_ALIAS`, `_KEY_PASSWORD`), unsigned without either (what F-Droid
  wants). **Not set up yet:** its own keystore (alias `gleiswechsel`, PKCS12, RSA 4096; the user keeps
  it) and the CI secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- `.github/workflows/release.yml` builds a signed APK on a `vX.Y.Z` tag and attaches it to a
  GitHub Release (Obtainium); `test.yml` runs the `:core` tests on branch pushes and pull requests.
  `fastlane/metadata/android/en-US/` for F-Droid, with `changelogs/<versionCode>.txt` from the
  first release on.
- Release build uses R8 (minify + shrinkResources). The build must be reproducible: no
  timestamps, build paths or machine-specific values in the APK; `dependenciesInfo` off.
- Icon: a placeholder (two arrows on dark teal, as vectors in `res/drawable/`) until the user
  makes a logo in `logo/`, as for the other apps.
- Android SDK in `~/Android/Sdk`. The user tests on a real phone over adb, no emulator.
- Git branch `master`, remote `origin` https://github.com/buerlino/gleiswechsel.git (added
  2026-10-06). The first local commit (2026-10-06) sits on the remote's "Initial commit" (only
  `LICENSE`, the same GPLv3 text), so the user can push without a force.

## Data source (proposed 2026-10-06, see research/data_sources.md)

- **transport.opendata.ch** first: no key (nothing secret in an open-source app), JSON, the
  official connections and the stations' arrival and departure boards. Checked with real requests
  2026-10-06; its quirks are in the research file.
- **OJP 2.0** only as an option with the user's own free key (20,000 requests a day per key, so
  no shared key in the app).
- **Ist-Daten** (every actual arrival and departure, daily CSV, archive since 2016) for the risk
  of a change, processed off the phone (a nightly GitHub Actions job), never on it.
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

The search page: the commute (from, to, leaving at), a Search button, the finds as cards, and
under them the rider's transfer time at each change station. A top bar: **?** (top left) opens
Help, **⚙** (top right) Settings (user, 2026-10-06), text buttons as in gridload (which has them
the other way round); each has ← and takes the back gesture. The page's state lives above the
three, so Help and Settings don't lose the result.

- **The commute** (2026-10-06): three text fields, each saved in SharedPreferences (`commute`) as
  it is typed. Stations go to the API as typed: it matches loosely ("horw", "Luzern Bhf" and even
  "Surseexq" find the station); a name it can't match gives no connections, and the page says so.
  The time takes `8:50`, `08:50`, `850` or `0850` (the number keyboard has no colon). Search asks
  for the next such time, Swiss time (today or tomorrow), and shows the day. Editing a field
  clears the result, so it never shows another commute's finds; the fields are locked while it
  searches.
- **The transfer times** (user, 2026-10-06): under the result, one row "Your change at X [_ min]"
  per change station of the official connections, each station once, in route order (Horw →
  Bern, Bundesplatz: Luzern, Olten, Bern). So a commute with nothing faster can be set too;
  tapping a find's change, the first idea, couldn't do that. Empty shows the default (official
  minimum − offset) in light grey (`outline`: the usual placeholder grey looked like a set
  value); no "Your change: …" line. Saved in `commute` keyed by station id (`8505000` → `4`) as
  typed: digits only, up to 2. Editing a time clears the finds but keeps the rows (Search again);
  editing the commute clears both. The rows come only with a search that has connections (user,
  2026-10-06: without one they don't help), so after a restart they're back with the next one.
- **The change time is the number the app is about** (user, 2026-10-06): always in the same box,
  in the cards, the rows and Settings (`MinutesBox`, `MinutesField`), coloured
  (`primaryContainer`) where it's the rider's: their fields, and the change a find relies on.
- **The finds** (user, 2026-10-06: before, "a wall of text"): a card each, "14 min earlier",
  "Sursee 09:26 instead of 09:40", the efficiency, then the trip as a timetable: a row per stop
  (time, station, track), the train in between, and at each change its minutes in the box. A walk
  between two trains is part of the change ("7 min change, 6 min walk"); one before the first
  train or after the last is a row of its own.
- **Settings** (user, 2026-10-06): the offset, "Your change: official minimum minus [_ min]", key
  `offset` in `commute`, 1 until set (`DEFAULT_OFFSET`, proposed, see open question 4). Changing
  it clears the finds and keeps the rows; locked while a search runs.
- **Help:** what the app does, the change time, efficiency, that times are planned only, and the
  data sources (opentransportdata.swiss wants to be named). Each concept is explained there once.
- **Efficiency** of each connection shown (user, 2026-10-06): the fastest trip's time / this
  one's, each from the first departure to the last arrival (the wait before the first train
  doesn't count). The fastest is the shortest of the official connections and the finds of the
  search. Same from and to, so it's also the share of the fastest trip's speed.
  `Connection.efficiency` in `:core`; each find shows its own and the official one's, e.g. 100% vs
  70% for the test case (33 and 47 minutes). Replaced the share of time on board (same day): it favoured slow trains
  with short changes.
- **The client:** `connections(from, to, time, version)` asks transport.opendata.ch
  `/v1/connections` (4 connections leaving at or after `time`), parses only trains, stations,
  planned times and platforms. User-Agent `Gleiswechsel/<version> (+https://github.com/buerlino/gleiswechsel)`.
- **The local search** (`search`): given the official connections A → B (the page asks for them
  first, so it can tell "no connections" from "nothing faster"); for each
  change station X on them (where a ride ends and the next begins; a walk belongs to the
  change), the connections X → B from the arrival at X plus the rider's transfer time at X; of
  those leaving no earlier, the one arriving first (a tie goes to the later one: more time to
  change); a find if it reaches B earlier than the official one, unless the planner already
  offers it: an official connection leaves no earlier and arrives no later (user, 2026-10-06;
  Horw → Bern, Bundesplatz showed the same trains as a bus instead of a walk, 4 minutes
  "earlier"). `catchable()` and the boards are gone: not needed for this.
- **Transfer time per station, covering the whole change, walks included** (user, 2026-10-06:
  "a fixed estimated time we need for a specific trainstation"). `search` takes it as a function
  of the station. **At a station the rider hasn't set: the official minimum there minus the
  offset in Settings, at least 0** (user, 2026-10-06: "the defaults are a fixed amount lower than
  the official ones"; a flat 5 was too high: riders who don't know their times would find nothing
  and think the app doesn't work). With the offset at 1, Horw → Sursee finds the test case with
  nothing set (Luzern 5 − 1 = 4).
- **Official minimums** (2026-10-06): HRDF `UMSTEIGB` from opentransportdata.swiss (timetable
  2026, export of 29 Sep 2026), the Swiss stations (`85…`) and the standard (`9999999`, 2
  minutes, everywhere else) as `app/src/main/res/raw/umsteigb.txt` (2,971 lines, 106 KB; the APK
  grew 1.16 → 1.21 MB), read by `Minimums` in `:core`. Luzern 5, Olten 5, Bern 6, Zürich HB 7,
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

## Plan for the first version (user, 2026-10-06)

The scope: one saved commute, the rider's transfer time per station, the local search, one result
list (user, 2026-10-06: "focus on the core utility"). Steps, one at a time:

1. ~~The search in `:core`~~ and ~~the page with the test case hard-coded~~ (both 2026-10-06).
2. ~~Edit and save the commute~~ (2026-10-06, [the app](#the-app)). Suggestions from
   `/v1/locations` only if typing the names turns out to be annoying.
3. ~~Set the transfer time per station~~ (2026-10-06, [the app](#the-app)), with
   ~~the official minimum − an offset as the default~~, ~~Settings and Help~~, ~~the finds as a
   timetable~~ (all 2026-10-06).
4. **Before the first release:**
   - ~~The name~~: Gleiswechsel (user, 2026-10-06).
   - The logo: the user makes it in `logo/`.
   - The reproducible-build check (two clean unsigned `assembleRelease` builds with the same
     sha256): Claude does it.
   - Later: the keystore and CI secrets, the F-Droid merge request.

Later versions: a risk indicator for each find (user, 2026-10-06: wanted, but after the core
utility; how is open question 3), routes through stations the official connections don't touch,
delays.

## Open questions (for the user)

1. **A change to a different stop** (train → tram stop, as in Zürich): its own time, a flag in
   the result, or nothing? Left for when a commute needs it (user, 2026-10-06). Per-station time
   is decided above.
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
5. **A switch to turn off the per-station rows**, the offset only (user, 2026-10-06: "maybe").
   Claude's take: not yet. Untouched rows already mean "offset only"; with the switch a time set
   at a station would be silently ignored.

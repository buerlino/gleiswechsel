# Declutter

A periodic pass, as in gridload and APODroid: scan everything, report what could be removed,
rewritten or is deprecated, plus bugs and design flaws; the user decides; then work through the
checklist and tick items off.

## What to check in a pass

- **Code:** dead or duplicated code, logic repeated between `:core` and `:app`, state the page
  keeps that can go stale or is lost (rotation), raw errors shown to the user.
- **Migrations:** remove migration code two releases after F-Droid has shipped past the version
  that needed it (`CLAUDE.md`, Stack).
- **Docs:** `CLAUDE.md` loads into every session, so keep it to current facts and decisions;
  finished-work narratives go (git keeps them). Look for stale lines, links to headings that
  moved, duplicates between `CLAUDE.md`, the skill and `research/`, and UI text copied into docs
  (it drifts from the code).
- **Repo:** files nothing uses, fastlane changelogs for versions F-Droid never built, loose git
  objects (`git gc`).
- **Build/CI:** `./gradlew :core:test :app:lintDebug :app:lintAnalyzeDebug --rerun` (lint can
  repeat a stale report otherwise), `:core:test -Plive`, Kotlin compiler warnings (lines
  starting `w:`), `--warning-mode all` (deprecations; `Configuration.setVisible` comes from a
  plugin, ignore), two clean unsigned `assembleRelease` builds from copies that include `.git`
  with the same sha256 (the APK records the git commit), the latest AGP, Kotlin, libraries,
  Gradle and action versions, redundant `gradle.properties`.

## Pass 2026-10-06

The first pass, report only, of 8297ea8. The user decides on each item.

Ran: `clean :core:test :app:lintDebug :app:lintAnalyzeDebug :app:assembleDebug
:app:assembleRelease --rerun-tasks --warning-mode all`: 13 tests green, lint "No issues found",
no `w:` lines, the only deprecation `Configuration.setVisible`. `:core:test -Plive`: 3 green
(Wed 7 Oct, 5 requests each, S4 → RE24, 14 minutes). Two clean unsigned `assembleRelease`
builds from fresh clones in different paths: same sha256 (`2d1f567f…`, also the repo's own
build). AGP 9.4.1, Kotlin 2.4.20, Compose BOM 2026.09.00, activity-compose 1.13.0,
kotlinx.serialization 1.11.0, Gradle 9.8.0, `checkout@v7`, `setup-java@v6`: all the latest.
On the phone (the installed build of 8297ea8, Horw → Sursee 08:50): the test case's card, then
turned to landscape and back (`user_rotation`; auto-rotate stayed off, restored): 1.1.

### Checked, no change needed
- **The search:** `onward`'s walk rule, the tie going to the later departure, the "planner
  already offers it" filter. Past midnight it asks the right day (the API's times carry Swiss
  offsets, so `toLocalDateTime()` is Swiss time).
- **Minimums:** the two minute columns are equal on every line, so reading the first is enough.
  A few odd values (Roveredo GR, CRS 99; Vezia, Paese 15) matter only to a commute changing
  there.
- **The page:** the fields are locked while a search runs, so a result can't belong to another
  commute; Help and Settings keep the search page's scroll position.
- **Texts:** every string in all four languages (lint); the French no-break spaces; short
  descriptions within 80 characters (German exactly 80).
- **`hourMinute`** in `:core` (the URL) and `:app` (display): one line each; sharing it would
  make `:core`'s public for that. Left.
- **`tools:ignore="DataExtractionRules"`:** as gridload and APODroid; it's for `allowBackup`
  (Android 8–11).
- **Links** in `CLAUDE.md` and the skill resolve; no `umsteiger` left in the code.

### Bugs
- [x] 1.1 Turning the phone lost the result and a running search: `configChanges` on the
  activity (no ViewModel). On the phone: rotated with the find shown, and while a search ran.
- [x] 1.2 Errors showed raw and in English: one text in all four, `Result.Failed` without a
  reason, the exception in the log. On the phone: airplane mode, all four languages. 429 not
  seen.
- [ ] 1.3 Cards that say nothing new. With finds at two changes of one official connection,
  both leave at the same time and one arrives later: that card is beaten by the other. Two
  official connections sharing the ride to a change (one faster, one with fewer changes) give
  the same trip twice. Found by reading, not seen. Fix: apply the "planner already offers it"
  rule to the finds too (drop a find another find leaves no earlier than and arrives no later
  than; an identical trip once, against the official that arrives first, so the saving isn't
  overstated). A few lines in `search` and a test.
- [x] 1.4 Found during 1.1: on a phone in dark mode the status bar's icons were white on the
  light page. `SystemBarStyle.light` for both bars (user: dark icons, not the system theme).

### Code
- [ ] 2.1 `Find.arrival` and `Find.departure` are read only by the tests; the page draws the
  change from the trip's legs. Drop them (`Find(official, faster)`), the tests check
  `faster.legs` instead.
- [ ] 2.2 Screen-reader labels for ⚙, ?, ⇅ and ← (TalkBack reads the symbols), as APODroid did
  for ☆ and ▾: the "Settings" and "Help" strings, plus two new ones ("Swap from and to",
  "Back") in all four languages. Optional.
- [ ] 2.3 `prefs.edit { }` is core-ktx, which comes in only through activity-compose (1.18.0).
  gridload and APODroid declare it at 1.18.0. Recommended: the same (the code uses it directly).

### Migrations
- None: nothing released yet.

### Docs
- [ ] 4.1 `CLAUDE.md`, finished work: the plan's done steps 1–3 and the name (keep the scope,
  what's left before the first release, and later versions; the `/v1/locations` idea moves to
  The app, the commute); "`catchable()` and the boards are gone"; "the APK grew 1.16 → 1.21
  MB" (now 1.23 MB: sizes drift); "renamed from the working name `umsteiger`".
- [ ] 4.2 `CLAUDE.md`, stale: the heading "Data source (proposed …)" (transport.opendata.ch is
  in use; OJP and Ist-Daten are still proposals); open question 1's "Per-station time is
  decided above."; the plan's "Later: the keystore and CI secrets" repeats Setup's "Not set up
  yet".
- [ ] 4.3 Skill, Still untested: seven bullets that mostly list what *was* tested. Rewrite as
  in APODroid, only what's untested: an orange or red box in a card; the offset at 0; a phone
  set to German itself; French and Italian since the formal texts; the French row's long label;
  a find with a walk or at a second change drawn as a timetable; a long station name; a
  non-digit typed; the error text; the debug build on the phone; TalkBack; the release
  workflow. The reproducible build is done (above); rotating is 1.1.
- [ ] 4.4 `research/architecture.md`, Open points: "one number for all stations, or per
  station" is decided (per station, with the offset); the first point repeats `CLAUDE.md`'s
  plan. Drop both.
- [ ] 4.5 README, Data: "transport.opendata.ch for the timetable and real-time data", but the
  app reads planned times only: "for the connections".
- [ ] 4.6 Lines over 100 characters (`CLAUDE.md` 7, the skill 8, README 1; not counting
  commands, the skill's description and the research tables): reflow along with 4.1–4.3.

### Repo, build and CI
- [ ] 5.1 `git gc` (148 loose objects, 704 KB). Optional.
- Nothing else: no unused files, no changelogs yet (nothing released), `gradle.properties`
  has only `jvmargs` and the code style, the workflows are on the latest actions.

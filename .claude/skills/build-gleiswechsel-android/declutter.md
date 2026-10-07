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
- [x] 1.3 Cards that said nothing new: the "planner already offers it" rule between the finds
  too; an identical trip once, against the official arriving first. Two tests; not seen live.
- [x] 1.4 Found during 1.1: on a phone in dark mode the status bar's icons were white on the
  light page. `SystemBarStyle.light` for both bars (user: dark icons, not the system theme).

### Code
- [x] 2.1 `Find(official, faster)`; the tests check `faster.legs`.
- [x] 2.2 Screen-reader words for ⚙, ?, ⇅ and ← (`spokenAs`), two new strings in all four.
  Seen in the accessibility tree; TalkBack's speech not heard.
- [x] 2.3 core-ktx declared at 1.18.0 (the same version as before, via activity-compose).

### Migrations
- None: nothing released yet.

### Docs
- [x] 4.1 `CLAUDE.md`, finished work: the plan's done steps and items, `catchable()`, the APK
  sizes, the rename, the first push. The `/v1/locations` idea stays in "Ideas, not decided",
  where the user's later edit put it.
- [x] 4.2 `CLAUDE.md`, stale: "Data source" (transport.opendata.ch in use, OJP and Ist-Daten
  proposed, no boards), open question 1; the keystore line was already fixed by the user.
- [x] 4.3 Skill, Still untested: only what's untested.
- [x] 4.4 `research/architecture.md`: the two settled open points dropped.
- [x] 4.5 README: transport.opendata.ch "for the connections".
- [x] 4.6 Lines over 100 characters reflowed (`CLAUDE.md`, the skill, README; commands left).

### Repo, build and CI
- [x] 5.1 `git gc`: 175 loose objects (828 KB) into one 163 KB pack; `git fsck` clean.
- Nothing else: no unused files, no changelogs yet (nothing released), `gradle.properties`
  has only `jvmargs` and the code style, the workflows are on the latest actions.

## Pass 2026-10-06 (2)

The second pass, of 69a13bd, without the phone; the user decided on each item.

Ran: `clean :core:test :app:lintDebug :app:lintAnalyzeDebug :app:assembleDebug
:app:assembleRelease --rerun-tasks --warning-mode all`: 15 tests green, lint "No issues found",
no `w:` lines, the only deprecation `Configuration.setVisible`. `:core:test -Plive`: 3 green
(Wed 7 Oct, 5 requests each, S4 → RE24, 14 minutes). A fresh clone of 69a13bd in another path
and the repo's own build: the same sha256 (`1d77a060…`). **v0.1.0:** the workflow's
`gleiswechsel-v0.1.0.apk` against an unsigned build of the tag from a fresh clone:
`apksigcopier compare` OK; its certificate (`f85dc7d9…`) is the recipe's
`AllowedAPKSigningKeys`, the recipe's commit the tag's; the fdroiddata branch is pushed
(`ba1e95f6c`). AGP 9.4.1, Kotlin 2.4.20, Compose BOM 2026.09.00, activity-compose 1.13.0,
kotlinx.serialization 1.11.0, Gradle 9.8.0, `checkout@v7`, `setup-java@v6`: the latest stable.

### Checked, no change needed
- **The search, the client, `Minimums`:** unchanged since the first pass.
- **The page:** a walk-only official connection would draw without its departure row, but the
  API sends none (Zürich HB → Bahnhofplatz/HB, Bern → Bern, Bahnhof: no connections). Left.
- **Texts:** no unused string; every one in all four (lint).

### Bugs
- [—] 1.1 A failed search (offline, 429) empties the Optimization rows until the next search
  that works. User: not a bug, as before (now in `CLAUDE.md`, Optimization).

### Code
- [x] 2.1 `MinutesField` only served the offset. User: the offset as a stepper too, and plain
  − and + without the circle, everywhere. `MinutesField` gone; `MinutesStepper` takes a range
  (the offset: −99 to 0, saved without the −); `MinutesBox` writes − for a negative. On the
  phone (release build): −1 faded, + to 0 (a second + does nothing), − to −1 and −2, back to
  −1 (unset).
- [x] 2.2 `search`'s doc says "two where the API walks"; it's one more per stop it walks to
  (as `CLAUDE.md`).

### Migrations
- None: nothing has changed a saved key since 0.1.0.

### Docs
- [x] 4.1 `CLAUDE.md`, "Left before the first release": both done (release verified above, the
  recipe pushed). Keep the scope; whether the merge request is open: ask the user.
- [x] 4.2 Skill, stale: F-Droid "Not submitted yet"; Still untested "the release workflow
  (never run)" and "the French row's long label" (rows show only the station now); Where things
  are: `MinutesStepper`, `Heading`, `Folded` missing, `MinutesField` is the Settings offset's.
- [x] 4.3 `CLAUDE.md` quotes UI texts that drift from `strings.xml`: "Horw → Sursee, 08:50 ▾",
  "X (−) [4 min] (+)", "14 min earlier", "Sursee 09:26 instead of 09:40", "Official connection
  ▸", "Global offset of the track switch time [−_ min]". Keep the decisions, drop the quotes.
- [x] 4.4 The screenshot (listing and README) is 0.1.0's page; since 69a13bd it has three
  panels, steppers, track signs and the title in the texts' language. A line in the skill's
  Releasing.
- [x] 4.4b The new screenshot: user, after the next features (they change the page again).
  Done in 47b2ced.
- [x] 4.5 Lines over 100 characters: `CLAUDE.md` 171, 178, 195; skill 50, 51, 53, 90.

### Repo, build and CI
- [x] 5.1 core-ktx 1.18.0 → 1.19.1 (needs compileSdk 37 and AGP 9.1: both met). Changes the APK;
  build, tests and lint green.
- [x] 5.2 `git gc`: 132 loose objects (756 KB) packed, none left.

## Pass 2026-10-06 (3)

The third pass, report only, of 47b2ced (0.2.0) and the uncommitted CLAUDE.md, with a closer
look at the UI elements and their movements. Without the phone: another session was using it
(one dump and screenshots of the test case's card, two scroll swipes, then stopped).

Ran: `clean :core:test :app:lintDebug :app:lintAnalyzeDebug :app:assembleDebug
:app:assembleRelease --rerun-tasks --warning-mode all`: 29 tests green, lint "No issues found",
no `w:` lines, the only deprecation `Configuration.setVisible`. `:core:test -Plive`: 3 green
(Wed 7 Oct, 5 requests each, S4 → RE24, 14 minutes). Fresh clones in two paths and the repo's
own build: the same sha256 (`4b1dbad6…`). CI: every Test run on master green up to 47b2ced,
Release v0.2.0 green, both releases have their APK. AGP 9.4.1, Kotlin 2.4.20, Compose BOM
2026.09.00, activity-compose 1.13.0, core-ktx 1.19.1, kotlinx.serialization 1.11.0, Gradle
9.8.0, `checkout@v7`, `setup-java@v6`: the latest stable.

### Checked, no change needed
- **The search:** `shortened`'s recursion ends (each level starts after a ride, so later); the
  order of the finds and the rule between them; Cancel drops the late answer (`withContext`
  checks for cancellation before it returns); the state read on the IO thread.
- **Minimums:** 2,971 lines, all `id mm mm name`, the two columns equal; Luzern 5, Zürich HB 7,
  Olten 5, Bern 6, Sursee 3, standard 2. The saved answers in `private/` have no change of 0–2
  minutes and none on the same train (see 1.3).
- **Touch targets:** Compose widens the small clickables (a title, the official line) to 44–48
  dp (`uiautomator dump`).
- **Texts:** no unused string; short descriptions ≤ 80 (German 80), changelogs ≤ 500.
- **Migrations:** none in the code, none needed.

### Bugs
- [x] 1.1 Destination slides into the middle each time the page shows without a result (app
  start, back from Help or Settings, the language switch): `formHeight` starts at 0, so the
  first frame puts the form's top at the middle, then `animateDpAsState` moves it up by half
  its height. From the code, not seen. Fix: animate a fraction (½ or 0) and place the form in a
  `layout` modifier with its measured height, in the same frame; `formHeight`,
  `onSizeChanged` and `LocalDensity` go.
  Done (2026-10-07): a fraction animated, the form placed in `layout`. Not seen on the phone.
- [x] 1.2 With Optimization off, a change in Settings (or Cancel) leaves only the folded line
  at the top of an empty page: the rows are hidden, but `centred` counts them. Fix:
  `centred = result == null && (changes.isEmpty() || !optimize)`. From the code.
  Done (2026-10-07). Not seen on the phone.
- [x] 1.3 To check: a train that only changes its number at a station could come as two rides
  with a 0–1-minute "change"; `shortestChanges` would then lower that station's official
  minimum for the search (defaults near 0, false finds, colours off). Not in the 8 saved
  answers; a live check with such a train decides. Checked 2026-10-07: the API sends each of six
  in-seat continuations as one section (CLAUDE.md, the full search, step 2), so no.
  Moved to the full search's step 2 (CLAUDE.md), which handles in-seat continuations anyway.

### UI and movements
- [x] 2.1 Journey's title row is taller than Optimization's: ✕ is a TextButton with 8 dp on
  top, so "Journey" has more space above and below (the store screenshot). Fix: ✕ as plain
  clickable text in the title's row, like the fold marks; Compose still gives it ~48 dp to tap.
  Done (2026-10-07).
- [x] 2.2 A step in Optimization removes the cards at once, so the rows jump (to just under
  Search when the rest fits): a second tap on − or + lands where the row was. User: keep, keep
  the cards faded until Search, or something else?
  User (2026-10-07): faded until Search. Done (`fade`, `stale`), also for a change in Settings;
  the saved file still goes.
- [x] 2.3 Buttons, ⚙, ?, ✕, the ticket link, ⇅ and the switch are Material's default purple
  (`lightColorScheme()` unchanged); the app's own blue `00179B` is only on the track signs.
  User: purple, or the logo's blue as `primary`?
  User (2026-10-07): the logo's blue. Done (`BLUE`); ⇅'s pale background was still Material's
  `secondaryContainer`: a plain blue ⇅ since (user, 2026-10-07).
- [x] 2.4 Fold marks in three sizes and places: a title's ▾/▸ at the far right, the folded
  line's ▾ at the far right, the official line's ▸ right after its small text; ▴ at 22 sp.
  User: leave, or one size?
  User (2026-10-07): leave.
- [x] 2.5 The titles (the panels', Help's topics) aren't headings for a screen reader
  (`semantics { heading() }`), so TalkBack can't jump between them. One modifier in `Heading`.
  Done (2026-10-07), on the title's text, so it merges into a foldable row.
- [x] 2.6 French: 15 plain spaces before ":" or ";" in the newer texts (Help,
  `not_all_checked`), against the file's rule (no-break, as in the 5 older ones): a line can
  start with the colon.
  Done (2026-10-07), with "42 %" and "Rouge !": 15 in all.
- [x] 2.7 German: "Min." in Help, "min" in the boxes and cards. User: leave, or one?
  User (2026-10-07): leave ("Min." in sentences, "min" in a box).
- Noted, settled: a faded default is 2.9:1 against green and red (4.5:1 on orange); the
  official connection at 60% has grey text at 2.9:1.

### Code
- [x] 3.1 `find()` and `parseTime()` are logic in `:app` without tests: the next such time
  (today or tomorrow), the rows, the lowered minimums, `Found`; `8:50`, `850`, `24:00`. Move to
  `:core` with tests, `connections` passed in as for `search`?
  User (2026-10-07): with the full search's step 4, which rewrites `find()` anyway (CLAUDE.md).
  Done with One model (2026-10-07): both in `Found.kt`, tested in `FoundTest`.
- [x] 3.2 Unused import `width` in MainActivity.kt (Kotlin doesn't warn).
  Done (2026-10-07).
- [x] 3.3 "official" names three things: `Minimums` in `App`, the fold in `FindCard`, the
  `Connection` in `Find`. Rename the fold (`officialOpen`).
  Done (2026-10-07).
- [x] 3.4 MainActivity.kt has 725 lines; the cards (`FindCard`, `Trip`, `StopRow`, `TrackSign`,
  `Indented`, `MinutesBox`, `LateBox`, the colours) could go to `Cards.kt`. User: split or not?
  User (2026-10-07): split. Done, a move only (`folding` and `FoldMark` became internal):
  MainActivity.kt 501 lines, Cards.kt 283.

### Docs
- [x] 4.1 The risk indicator left the plan in 0.2.0 (its commit message), but README still
  promises it ("Later it will also tell you how often…", "later the actual arrival and
  departure times… for how reliable a change is"), as do the skill's description ("how risky
  they are comes later"), architecture.md (the risk part, the diagram) and missing_features.md
  ("What CLAUDE.md already lists (risk, …)"); CLAUDE.md doesn't record the decision. User:
  dropped for good? Then a line in CLAUDE.md and those fixed.
  User (2026-10-07): dropped for good. A line in CLAUDE.md; README, the skill's description,
  architecture.md (step 5, the risk section, its job) and missing_features.md fixed.
  hidden_connections.md, data_sources.md and existing_tools.md keep it as research.
- [x] 4.2 CLAUDE.md, stale: the signing secrets "no release has used them yet" (0.1.0 and 0.2.0
  did); the fdroiddata commit "not pushed yet" (the fork's branch is at `afaac5d23`).
  Done (2026-10-07); the fdroiddata half was already current (the fork is at `6cad67402`, `git
  ls-remote`).
- [x] 4.3 CLAUDE.md quotes UI texts again (pass 2's 4.3): "Delays as of 23:07", "! 1 min",
  "−2 min", "3 min late", "track 14 instead of 12", "4 min, below the official 5".
  Done (2026-10-07): the string names instead.
- [x] 4.4 CLAUDE.md, finished work: the 0.1.0 and 0.2.0 release lines (apksigcopier, the
  changelogs, the screenshot) → the current state only; "Tried live once" repeats open
  question 1 → into it.
  Done (2026-10-07).
- [x] 4.5 CLAUDE.md, small: `spokenAs` also covers − and +; the changelogs are in all four
  locales, not only `en-US/`.
  Done (2026-10-07).
- [x] 4.6 missing_features.md: the struck-through items and the all-done "Suggested order" go.
  Done (2026-10-07).
- [x] 4.7 This file, pass 2's 4.4b (the new screenshot): done in 47b2ced.
  Done (2026-10-07).

### Repo, build and CI
- [x] 5.1 `git gc`: 365 loose objects (1.9 MB).
  Done (2026-10-07): 438 loose objects (2.3 MB) packed, none left.
- Nothing else: the latest versions, reproducible, CI green.

## Pass 2026-10-07 (4)

The fourth pass, report only (no code changed), of 46e251e. Without the phone (another session
may be using it).

Ran: `clean :core:test :app:lintDebug :app:lintAnalyzeDebug :app:assembleDebug
:app:assembleRelease --rerun-tasks --warning-mode all`: 56 tests green, lint "No issues found",
no `w:` lines, the only deprecation `Configuration.setVisible`. `:core:test -Plive`: 5 green
(Thu 8 Oct; 5 requests each, S4 → RE24, 14 minutes; the full search on
`private/gtfs/timetable.bin.gz` finds it with the defaults in 193 ms and nothing with 5 at
Luzern, the file read in 68–122 ms). Fresh clones in two paths (one with a space) and the repo's
own build: the same sha256 (`dcbf7cbc…`). CI: every Test run on master green up to 46e251e, the
Timetable run by hand green; Pages serves the file of 7 Oct (453,334 bytes, `application/gzip`).
AGP 9.4.1, Kotlin 2.4.20, Compose BOM 2026.09.00, activity-compose 1.13.0, core-ktx 1.19.1,
kotlinx.serialization 1.11.0, Gradle 9.8.0, `checkout@v7`, `setup-java@v6`,
`upload-pages-artifact@v5`, `deploy-pages@v5`: the latest stable.

### Checked, no change needed
- **No dead code:** every declaration is used (checked with a script), no unused imports, every
  string used and in all four languages with the same placeholders.
- **The full search** (`fullSearch`), read through: boarding at A, a change needing the rider's
  time, in-seat continuations (a trip either goes on or is left, never both), rides of 0 minutes
  kept in their order, the early stop; the backward scan agrees with the forward one. Its times
  carry the same offsets as the API's, so `official(change, offered)` will also match its finds.
- **A search day the file doesn't have:** the scan has no rides and finds nothing, the same as
  today's search alone, so step 4 needs no extra check for it.
- **`timetable.yml`'s years:** between 13 and 31 Dec it also downloads the ending year's zip,
  which `trains` skips (none of the days): one extra download of about 5 s. Left.
- **The page:** `show`, `fade` and `stale`; the rows, `centred`, Cancel. A dark-mode switch
  (`uiMode` isn't in `configChanges`) recreates the activity, as the language switch does: the
  result comes back from its file, a running search is lost, as when the system kills the app.
- **The parser:** every walk in the 8 saved answers in `private/` has both times (the parser
  throws without one).
- **Migrations:** none in the code; an older `result.json` is ignored (`FoundTest`).
- **Links** in `CLAUDE.md`, the skill, README and `research/`, anchors included, all resolve.

### Bugs
- None found.

### UI
- [ ] 2.1 60% opacity now means four things: a default (the box's text), a row whose station
  isn't on the trips shown (D3, the whole row with − and +), finds faded after a step, and the
  official connection under a find. A default in a faded row is at 36%. Question 3 of
  research/optimization_rows.md ("should a set row be easier to tell from a default?") is still
  open there, but missing from CLAUDE.md's Open questions. User: leave, or mark a default
  another way? At least move the question into CLAUDE.md.

### Code
- [ ] 3.1 `trains()` takes each trip's days from the calendar and doesn't limit them to its
  zip's `feed_start_date`–`feed_end_date`, though its doc, the skill and CLAUDE.md (step 3) say
  it does. No difference with the real data: in both exports the `calendar.txt` and
  `calendar_dates.txt` ranges equal the feed range (2026: 14 Dec 2025–12 Dec 2026; 2027:
  13 Dec 2026–11 Dec 2027). Fix: `and feed` on the service masks (then a calendar running past
  its feed can't count a day twice), or reword the three docs. Recommended: `and feed`.

### Docs
- [ ] 4.1 CLAUDE.md says "no SBB name … in the app or the listing; the one exception is
  sbb.ch", but Help's 📡 ("Not affiliated with SBB", in all four) and the full descriptions ("Not
  affiliated with SBB or any other transport company") name SBB in a disclaimer, there since
  the first version. User: add the disclaimer as a second exception in CLAUDE.md, or leave SBB
  out of the texts ("not affiliated with any transport company")? README ("the SBB app") is
  neither the app nor the listing.
- [ ] 4.2 research/optimization_rows.md and harmonize.md are out of date since One model:
  optimization_rows.md says "Nothing is decided yet" and "as now" about the lowered minimum, and
  points to a CLAUDE.md heading that no longer exists ("Asked again where a time fell");
  harmonize.md says data_sources.md "still says search.ch" (fixed since), and its "Tested, not
  tested" numbers the steps differently from CLAUDE.md. Their findings are already in CLAUDE.md
  (D1–D5, the steps) and data_sources.md (the GTFS times equal `UMSTEIGB`, Winterthur, MOTIS).
  User: delete both once step 2 has been checked on the phone (git keeps them; move question 3
  first, 2.1), or add a status line at the top of each?
- [ ] 4.3 CLAUDE.md, finished work (its own rule: git keeps it):
  - Official minimums: "Until 2026-10-07 each search lowered the station's time to it, …".
  - One model, step 1: the "Gone: …" list and the live check, which step 2's phone check
    repeats.
  - The full search, step 3: the run times (22 s and 38 s on the desktop, 2 minutes on GitHub),
    "the same bytes as the desktop's", the read times and heap; Pages' `Content-Type` is also in
    the skill.
  - "Measured (2026-10-06, a Python prototype, not kept)": steps 1 and 2 have the real numbers
    (453 KB, not 0.9 MB; 10–21 ms). Its "377 random trips … 13 through stations today's search
    can't reach" is the reason for the full search: into research/architecture.md?
- [ ] 4.4 Skill, out of date: "Trying the API by hand" lists three answers in `private/`; there
  are eight (also Horw → Sursee now and tomorrow and Zürich → Bern now, 6 Oct 23:07, and the
  arrival boards of Zürich HB and Luzern). Still untested, "The fixes after that test pass":
  "the search asking again where a time fell (gone since …)" is about code that no longer
  exists.
- [ ] 4.5 Lines over 100 characters: CLAUDE.md 175, 245, 315, 339, 362, 363, 405, 480;
  research/data_sources.md 66, hidden_connections.md 41, harmonize.md 90, 145 (the commands
  left as they are).

### Repo, build and CI
- [ ] 5.1 `git gc`: 231 loose objects (1.1 MB), 2 packs.
- Nothing else: the latest versions, reproducible, CI green, no unused files, changelogs only
  for 1 and 2 (both in the F-Droid recipe).

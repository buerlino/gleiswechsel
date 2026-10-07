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
  User (2026-10-07): the logo's blue. Done (`BLUE`); ⇅'s pale background is still Material's
  `secondaryContainer`.
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

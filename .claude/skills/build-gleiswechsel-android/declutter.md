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
- [ ] 4.4b The new screenshot: user, after the next features (they change the page again).
- [x] 4.5 Lines over 100 characters: `CLAUDE.md` 171, 178, 195; skill 50, 51, 53, 90.

### Repo, build and CI
- [x] 5.1 core-ktx 1.18.0 → 1.19.1 (needs compileSdk 37 and AGP 9.1: both met). Changes the APK;
  build, tests and lint green.
- [x] 5.2 `git gc`: 132 loose objects (756 KB) packed, none left.

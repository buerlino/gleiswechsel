# Optimization rows and the official time: two surprises (2026-10-07)

The user found two surprises on the phone. Both come from rules the code follows on purpose, and
both matter for step 4 of the full search (CLAUDE.md, "The full search"): that step adds the full
search's change stations to the rows, and its finds are coloured against the same official
times. This file records what was seen, why it happens, and what has to be decided.
Nothing is decided yet.

## What the user saw

Release build, offset 1 (the default), Luzern set to 4 by the rider, Thu 8 Oct.

1. **Sursee → Zürich Oerlikon 07:45.** The only find goes IR27 Sursee 08:12 → Luzern 08:30, IR75
   08:35 → Zürich HB 09:22, S8 09:25 → Oerlikon 09:29 (instead of 09:38). The Optimization rows
   are **Olten 4, Zürich HB 3, Brugg AG 2, Luzern 4**, all green. The card never goes to Olten or
   Brugg. *"Why is Brugg AG shown?"*
2. **Sursee → Horw 07:45.** "Nothing faster". The official connection is S1 Sursee 07:48 →
   Luzern 08:15 (track 10B), S41 08:19 (track 13) → Horw 08:26. Luzern's row shows **4, orange**.
   *"Isn't Luzern's standard time 5? Then 4 should be green."*

## Reproduced (debug build, 2026-10-07)

Fresh install, nothing set, offset 1, steps driven over adb, `uiautomator dump`:

- Sursee → Horw 07:45: the official card's Luzern box reads "4 min, the official time". Luzern's
  row reads "3 min, below the official 4" (default 4 − 1, faded).
- I pressed + on Luzern. The row now reads "4 min, the official time", orange, as in the user's
  screenshot.
- Sursee → Zuerich Oerlikon 07:45: the same find as above. The rows read "Olten 4, below the
  official 5", "Zürich HB 3, below the official 4", "Brugg AG 2, below the official 3" and
  "Luzern 4, below the official 5".
- In the user's own screenshots, the text colour of the boxes (pixel check) is faded for Olten,
  Zürich HB and Brugg (defaults) and full white for Luzern (set by the rider).

## Why Brugg AG is a row

The rows are `Searched.changes` ([Search.kt](../core/src/main/kotlin/io/github/buerlino/gleiswechsel/core/Search.kt)):
every station the search asked `transfer` for. That covers each change of **all four** official
connections, plus the changes of the onward connections it tried. It is not just the journey in
the card.

The four official connections for Sursee → Zürich Oerlikon 07:45 (transport.opendata.ch, 8 Oct):

| Leaves | Route | Arrives |
|---|---|---|
| 07:48 | IR27 → Olten, IC5 → Zürich HB, S6 | 09:08 |
| 07:51 | **S29 → Brugg AG 08:52, IR36 09:22** | 09:57 |
| 07:51 | S29 → Olten, IR35 → Zürich HB, S14 | 09:17 |
| 08:12 | IR27 → Luzern, IR75 → Zürich HB, S6 | 09:38 |

Brugg comes from the second connection, which waits 30 minutes at Brugg. Lowering Brugg's time
can't make anything faster there. Yet the line above the rows says "lower it and search again to
find more", which suggests every row is worth lowering.

So the rows depend on all of the planner's answers, not on what the page shows. Three effects:

- A station can be a row without appearing in any card.
- The four official connections are in neither card, so where a row comes from is invisible.
- How many rows there are depends on how many different routes the planner mixes into its four
  answers, which has nothing to do with the rider's trip.

## Why Luzern at 4 is orange in one search and green in the other

Luzern's table minimum (HRDF `UMSTEIGB`) is 5. But the colour reference is not the table: it is
the table **lowered by the planner's own changes in this search**. That rule was decided by the
user on 2026-10-06 (the Olten case: the planner offers RE24 → IR16 in 4 minutes, while the table
says 5). The code:
`shortestChanges(answers)` → `Minimums.lowered` in
[Minimums.kt](../core/src/main/kotlin/io/github/buerlino/gleiswechsel/core/Minimums.kt), applied in
`find()` and kept in `Found.shortest` (`official = minimums.lowered(it.shortest)` in
[MainActivity.kt](../app/src/main/kotlin/io/github/buerlino/gleiswechsel/MainActivity.kt)).

- In **Sursee → Horw**, the planner itself changes at Luzern in 4 minutes (S1 10B 08:15 → S41 13
  08:19). So that search's official time at Luzern is 4. The rider's 4 equals it: orange. The
  default there is 4 − 1 = 3.
- In **Sursee → Oerlikon**, no answer changes at Luzern in under 5, so the official time is 5. The
  same 4 is below it: green ↓. The default there is 5 − 1 = 4.

So the same saved value at the same station changes colour, and the same unset station changes
its default, from one search to the next. The rule is correct about what the planner offers in
each search, but a rider can't see why. All the page says is "the official 4" in one search and
"the official 5" in the other.

### Direction and tracks

The planner's finer times are per track pair, so they depend on the direction. At Luzern:

- **SBB → zb**, S1 10B → S41 13: the planner offers 4 (above).
- **zb → SBB**, S4 13–15 → RE24 9 (the test case): the planner keeps 5 or 6 and doesn't offer 4
  (CLAUDE.md, "Test case").

The rider's time is one number per station, keyed by station id, so it covers both directions.
The lowered minimum is also per station, from whichever direction the search happened to see.

### Telling set from default

A row set by the rider differs from a default only by its text opacity (100% vs 60%, `FADED`).
The user didn't expect Luzern to be set. `MinutesStepper` unsets a row only when the rider steps
onto the default *of the current search*. Because the default moves between searches (above), a
value can stay "set" while it equals today's default, and the reverse.

## Why it matters for the full search (step 4)

As CLAUDE.md plans step 4 now:

- **Rows** = `Searched.changes` (today's search) + the change stations of the full search's
  finds. The full search scans every station, so its `transfer` calls can't serve as the row
  list. That was already noted.
- **Minimums** for the full search: the table lowered by today's search's answers.

What this report adds:

1. **The row rule needs a purpose before it grows.** Today's rows already include stations
   irrelevant to the card (Brugg). With the full search, lowering a time anywhere could help, so
   "every station we looked at" stops being a usable list. What the rows are for needs deciding
   first (options below).
2. **The full search's finds go through stations the API never answered for.** There the official
   time is the plain table, though the planner may have a finer one (the step-2 measurement:
   Zürich HB 6 in the planner vs 7 in the table, so finds overcounted). Such a find's change box
   shows green ↓ against a number the planner itself beats. Within one search, a station
   the API answered for and one it didn't are measured differently.
3. **The colour flip gets more common.** The more stations a search covers, the more often the
   same rider time is green in one search and orange in another.
4. **Direction.** The GTFS file has a platform per stop event (5,010 platforms), so the full
   search knows the tracks of each change. One time per station stays the simple model; per
   direction or per track pair would be finer but adds UI and settings (KISS says no until
   needed).

## Options (for the user and the implementing session)

### What the rows show

- **A. As now (+ the full search's finds' stations, as planned).** Most to tune, including
  stations that can't help (Brugg). No change to today's search.
- **B. Only the stations in the cards:** the changes of each find and of the official connection
  each find is compared against; with nothing faster, the changes of the official card shown.
  Every row then appears in a card on the page, and the full search's stations come in through
  its finds. Cost: a station only on a slower official connection, or only tried by an onward
  search that found nothing, can't be set from that search. The line "lower it and search again
  to find more" works best for the stations of the shown journey (Luzern and Zürich HB here),
  and those stay.
- **C. B plus the official connection leaving first.** Close to B; only matters when a find is
  compared against a later official connection.

The user leaned towards something like B when asking "do you mean the alternative route
includes Brugg AG, but it's not used in the current journey?"

### What the colour compares against

- **1. As now: the table, lowered by this search's answers.** Truthful per search, but it flips
  between searches, and in the full search it compares a station the API answered for with the
  lowered time and one it didn't with the plain table.
- **2. Always the table (`UMSTEIGB`).** Stable: Luzern 4 is always green ↓. Cost: a time equal to
  what the planner itself offers looks like a gain, and the Olten case (the planner's 4 shown
  orange) goes back to green. The *search* could still use the lowered minimum for defaults
  (the 2026-10-06 rule is about defaults and finding changes the planner already makes).
- **3. As now, but say it.** E.g. the screen reader text and/or a small note "the planner itself
  changes here in 4" where the official time is lowered. More text, which goes against "short
  texts".

### Defaults

Whether a station's *default* should follow the lowered minimum (as now) or the table is a
separate question from the colour. Following it is what made the search find the IR13 at Zürich
HB (CLAUDE.md, "Asked again where a time fell"), so keeping that seems right whatever the colour
does.

## Open questions for the user

Decided 2026-10-07 (user), see [harmonize.md](harmonize.md): 1. A, with the rows off the
page's trips faded; 2. always the table (2), the planner's own changes never green, and the
defaults follow the table, not the lowered minimum; 3. still open.


1. Rows: A, B or C?
2. Colour reference: 1, 2 or 3? Should defaults keep following the lowered minimum?
3. Should a set row be easier to tell from a default (it's only 60% vs 100% text now)?

## Tested, not tested

- Tested: both searches on the phone with the debug build (2026-10-07, 8 Oct timetable). Both
  surprises were reproduced from a fresh install. The four official connections were checked
  with a direct API request.
- Not tested: the Horw → Sursee direction's row at Luzern in the same session, and any of the
  options above.
- Phone state left behind: debug build installed (the release build had been removed), Luzern
  set to 4, commute Sursee → Zuerich Oerlikon 07:45.

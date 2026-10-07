# One model for the track switch times (plan, 2026-10-07)

[optimization_rows.md](optimization_rows.md) found two surprises on the phone: Brugg AG was a
row though no card goes there, and Luzern at 4 was orange in one search and green in the next.
Step 4 of the full search (CLAUDE.md) would build on the same rules. This file traces both to one
root and proposes one model for the search, the cards, the rows and both searches, then the
order to build it. **Decided by the user 2026-10-07** (at the end): as proposed, except the rows
(D3: A, with the stations off the page's trips faded).

## Found today (2026-10-07)

- **transport.opendata.ch is MOTIS on the Swiss GTFS**, the same data the full search reads, plus
  GTFS-RT. Its docs, read today: "transfer times are those of the national timetable, per
  station; where the timetable gives a walk to a neighbouring stop that is shorter than the
  station's transfer time, the walk counts". It holds a window of days, moved weekly (today 27
  Sep 2026 to 25 Feb 2027). [data_sources.md](data_sources.md) still says search.ch: outdated.
- **The GTFS's station times are the table's.** `transfers.txt` type 2 between the platforms of
  one station: the same minutes for every pair at each of its 8,962 stations. At 2,948 of the
  2,949 Swiss ones they equal `UMSTEIGB` (Winterthur: 0 in the GTFS, 3 in the table). So the
  bundled table and the timetable file agree.
- **Finer official times exist only per train pair** (702k rows of type 2 with trip ids, HRDF
  `UMSTEIGZ`): Zürich HB 5 for 6,595 train pairs (the table says 7), Aarau 3. None at Luzern or
  Olten. Guaranteed connections (type 1, 292): buses only (Baar).
- So **the planner's 4 at Olten (RE24 → IR16) and at Luzern (S1 10B → S41 13) are in no
  published time**: they're MOTIS's own handling, cause not known. Whether sbb.ch offers them too
  was not checked.

## The root

One concept, "the official time at a station", is worked out anew in each search from whatever
the planner's answers happened to contain (`Minimums.lowered(shortestChanges(answers))`), and
four things hang on it:

1. the box colours, in the cards and the rows;
2. the defaults (official − offset), so the search itself;
3. the search's re-run loop (`search` asks again where a time fell during the search);
4. what's kept: `Found.shortest` and the page's `official` state.

The rows are also a by-product of the search (every station it asked a time for), not of the
page. Hence both surprises, and the set/default confusion: a set time is unset only by stepping
onto *this search's* default, which moves.

Step 4 would make this worse. The full search changes at stations the API never answered for,
so those would use the plain table while others use the lowered time. Its rows would come from
anywhere.

## The proposal: one model, stable, in `:core`

Each concept defined once, in `:core`, the same in every search and in both searches. The page
shows only what it can explain.

1. **The official time at a station is the timetable's** (`UMSTEIGB`, which the GTFS repeats),
   the same in every search. (D1)
2. **A change the planner itself makes is official**, whatever its minutes. Its box is never
   green: orange at or below the station's time, red above. This holds in the official
   connection's timetable (folded or alone) and at a find's change the planner's answers also
   make. Per change, not per station: the S1 → S41 in 4 doesn't make Luzern 4 for the S4 →
   RE24. Kept with the result: each answer's changes (station, arrival, departure). (D4)
3. **The default is the official time − the offset, at least 0**, stable. The rider's time is the
   set one, else the default. One function in `:core`, used by both searches, the cards and the
   rows. (D2)
4. ~~The rows are the change stations of the trips on the page (option B).~~ **Decided
   otherwise (D3, A):** every station the search asked a time for, as now, plus the full
   search's finds' change stations; those not on a trip on the page (each find and the official
   connection it beats; with nothing faster, the official connection shown) faded. Kept with the
   result. (D3)
5. **The table stays bundled** for now: it equals the GTFS's and works offline before the first
   download. (D5)

What goes: `Minimums.lowered`, `shortestChanges`, the re-run loop in `search`,
`Found.shortest`, the page's `official` state. `Searched.changes` and `Found.changes` stay (D3,
A): the rows. The search
asks each change once with a time that can't move. `result.json` gets a new shape; an older one
is ignored, as decided (no migration).

What it costs:

- A find that came only from a lowered default goes. Example: the IR13 at Zürich HB (Sursee →
  Oerlikon, offset 2), whose 3-minute change was found because another answer changed there in
  4. With the table's 7 − 2 = 5, the rider sets Zürich HB to 3 to get it. The default stays as
  careful as the offset says.
- A planner change below the table (Olten 4) shows orange beside a row that says 5. Help says
  once that the planner sometimes allows less for particular trains.

## Steps (one at a time, each shown working)

0. The user decides D1–D5.
1. **Finish step 3**, unaffected by this: notes, then the user pushes, turns Pages on and runs
   the job; check the file, its headers, and that `timetable()` reads it.
2. **The model in `:core`**, with tests: the official time, the default and the rider's time in
   one place. `find()` and `parseTime()` move to `:core` (declutter pass 3, 3.1) and return the
   result with the planner's changes; the lowering and the loop go. Tests: the same time is the
   same colour in two searches; which rows are on the page's trips (Brugg isn't, so faded).
3. **The page on it**: the box rule (1 and 2), the rows faded off the page's trips, Help's 🎨
   and ⏱️ in all four languages. On the phone: the two surprises again. Expected: Luzern 4
   green in both searches; for Sursee → Oerlikon, Luzern and Zürich HB full, Brugg AG faded.
4. **Step 4, the full search in the app**, now smaller: the download, `fullSearch` with the same
   rider function, `best` of both, the rows follow by themselves; Help 🚆 and 📡, a new version,
   the phone, the measurements.
5. **Later, each only when needed:**
   - the station times from the timetable file instead of the bundled table, which ends the
     hand refresh each December and is right across 13 Dec on its own;
   - the train-pair times for the full search;
   - 28 Mar 2027 in the API (asked from about 7 Nov, when its window reaches it);
   - open question 1 (foreign train names in the full search);
   - set and default easier to tell apart (optimization_rows.md, question 3), if stable defaults
     aren't enough.

## Decisions (user, 2026-10-07)

Asked as below. The user's answers: D1+D2 "the most important part is that we show the best
option, I don't care about the colors that much … pick the solution that is precise, while
always showing the fastest path". Claude picked the proposal: the planner's own changes are
official connections, so a find has to beat them anyway; a lowered default only extrapolates
one train pair's time to every pair at the station (other tracks), so a find from it may not be
doable. The precise way to such finds is the per-pair times in the full search (step 5). D3: A,
"if we don't show the options for the others, the user cannot find a faster route (potentially)
by lowering one of the stations … maybe lower the opacity so it's clear that it's not part of
the current route". D4 and D5 as proposed.

- **D1** the timetable's. **D2** the timetable's − the offset. **D3** A, rows off the page's trips
  faded. **D4** never green, per change. **D5** bundled.

As asked:

- **D1** The official time at a station: the timetable's, the same in every search (proposed) |
  lowered by this search's answers (as now).
- **D2** Defaults: the timetable's − the offset (proposed, follows D1) | the lowered time − the
  offset (as now, keeps finds like the IR13 at Zürich HB).
- **D3** Rows: B, the stations of the trips on the page (proposed) | A, as now plus the full
  search's | C, B plus the official connection leaving first.
- **D4** A change the planner itself makes: never green, per change (proposed) | coloured against
  the station's time like any other (simplest; Olten 4 green in an official card).
- **D5** The table: bundled, as now (proposed for now) | from the timetable file (later, step 5).

## Tested, not tested

- Tested: the API's docs (read 2026-10-07); `transfers.txt` of the 30 Sep 2026 export against
  `UMSTEIGB`, in Python, not kept; the code paths above, read.
- Not tested: nothing of the proposal is built. Not known: why MOTIS changes in 4 at Olten and
  Luzern, and whether sbb.ch offers those changes.

# Missing features (investigated 2026-10-06)

What the app (0.1.0 and the commits after it) can't do yet, found by reading the code, the other
research files and the skill's "Still untested". Ideas from Claude, none decided: ask the user
before building any. What CLAUDE.md already lists (risk, delays, next weekday, more commutes,
station suggestions, open questions) isn't repeated here. Nothing was tried live for this.

## What the search can't find

- ~~**Only one tight change per find.**~~ Done 2026-10-06 (`shortened` in `Search.kt`): the
  onward connection's changes are searched too, each question asked once.
- **Only stations where the official trip changes.** The intermediate stops of each ride (the
  API's `passList`, not parsed) aren't tried: getting off where the train only stops, to catch a
  faster one there (an IR overtaking the S-Bahn). A cheap step towards phase 2
  ([architecture.md](architecture.md)), still on the phone, but more requests per search.
- **No "arrive by".** A commuter due at 09:30 wants the latest departure that still makes it
  (sleep longer instead of arriving earlier). The API has `isArrivalTime`; `connectionsUrl` doesn't
  use it. A find would then be "leaves later" instead of "arrives earlier".
- **Only four official connections.** The search looks only at the four the API sends from the
  time. No "later" button, no range ("the best between 07:30 and 09:00").

## Using it every day

- **The result is gone after a restart.** It lives in memory only. Kept in the one JSON file the
  stack allows, it could be read on the platform with poor reception, when the track matters.
- **No "now".** The time is required; a trip other than the commute needs the clock typed in.
- **The rider's own record of a change** (✓ made it, ✗ missed it, after riding a find). The user
  keeps one in their head (50+ times, missed once). On the phone, no Ist-Daten, no nightly job, no
  matching trains: a candidate for "something simpler first" in open question 3.
- **The saving in a year.** The app's pitch is that 5 minutes a day add up to hours a year; a
  line like "≈ 50 h a year" (14 minutes × about 220 workdays) in the card says it. Small.

## Robustness

- **One failed request loses the whole search.** `find` in `MainActivity.kt` catches any
  exception and shows `search_failed`, also when only an onward request failed (HTTP 429 after many
  searches): the official connections already fetched are dropped too. Better: keep them and the
  finds that came back. Since 2026-10-06 a search asks each question only once, so fewer 429s,
  but more questions on long trips (the onward changes).
- **The requests run one after another** (5 for Horw → Sursee). In parallel they'd be faster, but
  the API throttles, so carefully.
- **Changed platforms aren't read.** Luzern's track varies (12–15); the card shows the planned one.
  `prognosis.platform` is already in the answer, so it fits with "Show delays in the card".

## Suggested order (Claude, 2026-10-06)

1. Keep partial results when a request fails (small, every rider gains).
2. Save the last result in the JSON file (small).
3. ~~Two tight changes in a row~~ (done 2026-10-06).
4. Decide the ✓/✗ record before any work on the Ist-Daten risk.

# Missing features (investigated 2026-10-06)

What the app (0.1.0 and the commits after it) can't do yet, found by reading the code, the other
research files and the skill's "Still untested". Ideas from Claude, none decided: ask the user
before building any. What CLAUDE.md already lists (next weekday, more commutes, station
suggestions, open questions) isn't repeated here. Nothing was tried live for this.

## What the search can't find

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

- **No "now".** The time is required; a trip other than the commute needs the clock typed in.
- **The saving in a year.** The app's pitch is that 5 minutes a day add up to hours a year; a
  line like "≈ 50 h a year" (14 minutes × about 220 workdays) in the card says it. Small.

## Robustness

- **The requests run one after another** (5 for Horw → Sursee). In parallel they'd be faster, but
  the API throttles, so carefully.

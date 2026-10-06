# Architecture (proposed 2026-10-06, not decided)

A proposal from the first research session. Nothing here is decided until `CLAUDE.md` says so.

## The idea: search around the official connection

A full search with shorter changes needs the whole timetable and a server with a lot of memory
(naviqore: about 64 MB of stop times per service day). A commuter asks about one trip they take
every day, so a **local search** is enough for most of the gain, and it runs on the phone:

1. Ask for the official connections A → B (`/v1/connections`).
2. At each station where the rider changes, ask for the arrival and departure boards
   (`/v1/stationboard`).
3. With the rider's own transfer time per station ("at Olten I need 3 minutes"), find the
   departures they can still catch and follow them to B (the official connections from that
   station, or the train's `passList`). Step 1 (2026-10-06, `search` in `:core`) skips the boards:
   it asks for the official connections from the change station from arrival + transfer time.
4. Keep what arrives earlier, or leaves later and arrives at the same time, than the official
   connection.
5. Say how risky each change is (below), and what the fallback is if it fails.

What this misses: a route through stations the official connections never touch. Phase 2.

## The risk: from Ist-Daten, made in CI

The phone can't read millions of rows a day. A nightly **GitHub Actions** job (free for a public
repo, nothing to run or pay for) reads yesterday's Ist-Daten and updates a small file of delay
statistics, e.g. per station, train (category + number) and weekday: how late it arrived and
left. The job publishes it as a GitHub Release asset; the app downloads it now and then. The
probability that a change works is then the share of past days where
`actual arrival + transfer time ≤ actual departure`.

```
┌──────────────── Android app (Kotlin, Compose) ────────────────┐
│ saved commutes + the rider's transfer time per station        │
│ 1. official connections      ← transport.opendata.ch          │
│ 2. boards at the changes     ← transport.opendata.ch          │
│ 3. local search (:core)                                       │
│ 4. risk from the delay statistics (a downloaded file)         │
│ 5. "+6 min, worked on 91% of weekdays, else the S3 at 08:21"  │
└───────────────────────────────────────────────────────────────┘
                 ▲ download now and then
┌──── GitHub Actions, nightly ─────┐
│ yesterday's Ist-Daten → delay    │
│ statistics → GitHub Release      │
└──────────────────────────────────┘
```

## Phase 2: the full search (only if the local search finds too little)

naviqore (MIT, Java) or MOTIS (MIT, C++) on the Swiss GTFS with shorter transfer times: either
on a small server, or in the same nightly job for the saved commutes only (no server, but the
commutes would leave the phone, so ask the user first).

## What has to be written

1. The app: saved commutes, the API client, the local search, one result screen.
2. The statistics job: a script that reads Ist-Daten and writes the statistics, kept small.
3. Matching trains between the live API and Ist-Daten (category + number + planned time +
   station, to be checked).

Rough effort for a first version: 3–6 weeks part-time. Phase 2: 2–4 weeks more.

## Open points

- Whether the statistics job needs a database or a plain file is enough (KISS: a file).

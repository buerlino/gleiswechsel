# Architecture (proposed 2026-10-06)

A proposal from the first research session. Nothing here is decided until `CLAUDE.md` says so.
Decided so far: the local search (built) and phase 2, the full search (2026-10-06, being built).
Dropped (0.2.0; user, 2026-10-07): the risk of each change from Ist-Daten, with its nightly
statistics job; the app shows delays as known at the search instead.

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
   it asks for the official connections from the change station from arrival + transfer time,
   and does the same at that connection's own changes (two short changes on one trip).
4. Keep what arrives earlier, or leaves later and arrives at the same time, than the official
   connection.

What this misses: a route through stations the official connections never touch. Phase 2.

## Phase 2: the full search (decided 2026-10-06)

The local search tries other trains only where the official connections change, and the API has
no transfer time setting, so it never finds a route through other stations. Decided (user,
2026-10-06; the details in `CLAUDE.md`, The full search): a connection scan (CSA) on the phone
over a small timetable file that a GitHub Actions job makes from the Swiss GTFS.

```
┌──── GitHub Actions, weekly ──────┐
│ Swiss GTFS (289 MB zip)          │
│ → the trains of the next 14 days │
│ → a compact file, about 1 MB     │
│ → GitHub Pages                   │
└──────────────────────────────────┘
                 ▼ downloaded during a search, when the
                   app's copy is missing or older than 7 days
┌──────────────── Android app ─────────────────────────────────┐
│ the local search (as now) + a CSA over the file, with the    │
│ rider's time at every change; the finds of both through the  │
│ same filters                                                 │
└──────────────────────────────────────────────────────────────┘
```

Why this and not the earlier idea, naviqore (MIT, Java) or MOTIS (MIT, C++) with shorter
transfer times: those need a server, or a job that searches the saved commutes, which would then
leave the phone. One file for everyone keeps the commute on the phone, and the trains are small
enough for it: one weekday's are 206k stop events, and a CSA over them took 3–10 ms in a Python
prototype. Buses would be 12× that, so trains only. In the prototype, 13 of 377 random trips
were faster through stations the local search can't reach.

What it leaves out for now: walks between stations (a change stays within one station id), and
checking a find's legs with the API (delays, changed tracks, trains that no longer run).

## Effort

A first version (the app: saved commutes, the API client, the local search, one result screen):
3–6 weeks part-time, estimated. Phase 2: 2–4 weeks more.

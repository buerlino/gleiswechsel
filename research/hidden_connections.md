# Why connections go missing (researched 2026-10-06)

The starting question: do SBB's planners hide train connections, and can an app find them?

**Short answer:** nothing points to SBB hiding connections on purpose (for example to spread
the load). Connections go missing because of documented rules, mostly the minimum transfer time.
A "hidden" connection is almost always a change that is physically doable but shorter than the
official minimum. That's what this app looks for, and why it has to say how risky each one is.

## The causes

1. **Minimum transfer times.** The national timetable has a default of 2 minutes per station.
   It isn't written in the GTFS file, but routers must apply it. Big stations get more: the SBB
   app doesn't show a connection at Zürich HB if the next train leaves in under about 7 minutes,
   so an easy 6-minute change on the same platform is dropped (nau.ch). **This is the main lever.**
2. **Transfer times per line and per train pair.** HRDF (the raw format GTFS is made from) has a
   transfer time per station (`UMSTEIGB`) and per pair of trains (`UMSTEIGZ`), among others. Some
   changes are deliberately made longer or shorter than the station's time.
3. **Result filtering.** A planner shows the few "best" answers. A connection that leaves earlier
   and arrives at the same time as another, or needs more changes, is dropped even though it works.
4. **Footpaths and odd routings.** Walks between nearby stops (Zürich HB ↔ Stadelhofen, a bus stop
   to a station) or riding one stop too far and coming back are sometimes left out.
5. **Guaranteed connections.** Since October 2025 the GTFS has `transfer_type=1` for connections
   where the next train waits for changing passengers. That's an input for the risk.

## What riders can set today

- **sbb.ch:** Settings → "Longer interchange time": the recommended time, +50% or ×2. Only
  longer, never shorter.
- **SBB Mobile:** no transfer time setting at all.
- A community feature request for *shorter* personal transfer times exists; it isn't offered.

## A real example (checked 2026-10-06, transport.opendata.ch)

Luzern → Lausanne, 14:05: RE24 arrives Olten 14:52 on platform 11, IR16 leaves Olten 14:56 from
platform 8. The official planner already offers this 4-minute change, so Olten's minimum is at
most 4 minutes. `UMSTEIGB` says 5, and the tables per operator, line and train pair (`UMSTEIGV`,
`UMSTEIGL`, `UMSTEIGZ`, export of 29 Sep 2026) have nothing that gives 4: Olten's only lines there
are "999" (no connection) pairs, Luzern has none. So the planner behind transport.opendata.ch
uses finer times, probably per track, that aren't published (checked again 2026-10-06, Wed 7 Oct
14:05 and 15:05). The app counts such a change as official for those trains only (CLAUDE.md, Official
minimums). Big hubs are where the official minimums are longest, so that's where to look.

## What to expect

- **The gains are small and local.** The Swiss timetable is built around hubs where trains meet
  at fixed times, so most good connections are already official. Wins cluster at stations with
  long official minimums (Zürich HB, Bern) and on specific routes.
- **"Your connection is already the best" is a valid, useful answer.** The user's goal (5 minutes
  a day, hours a year) needs only one good find on a daily trip.
- **The risk is the rider's.** A tight change fails when the first train is late, so every
  suggestion needs how often it worked in the past, and the fallback if it doesn't.
- **Tickets** in Switzerland aren't tied to a train, so tight changes rarely matter for the
  ticket. Routes that go back on themselves might; check before suggesting one. The app flags a
  find that passes a station twice (2026-10-06).

## Sources

- [GTFS cookbook](https://opentransportdata.swiss/en/cookbook/timetable-cookbook/gtfs/) (2-minute default, `transfer_type`)
- [HRDF cookbook](https://opentransportdata.swiss/en/cookbook/timetable-cookbook/hafas-rohdaten-format-hrdf/) (`UMSTEIGB`, `UMSTEIGZ`)
- [GTFS Profile Switzerland (PDF)](https://www.oev-info.ch/sites/default/files/2024-04/gtfs_profil_switzerland_version_0_16_en.pdf)
- [SBB – Hilfe zum Fahrplan](https://www.sbb.ch/de/hilfe-und-kontakt/produkte-services/fahrplan.html)
- [nau.ch – SBB lässt Pendler warten](https://www.nau.ch/news/schweiz/sbb-lasst-pendler-warten-wegen-langsamen-passagieren-66856018) (Zürich HB, 7 minutes)
- [myswissalps – the Swiss timetable in 2026](https://www.myswissalps.com/travel/public-transport/timetable/) (+50% / ×2)
- [SBB Community – Personalizing transfer times to be faster](https://community.sbb.ch/d/27036-feature-request-personalizing-transfer-times-to-be-faster)

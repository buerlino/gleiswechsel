# Existing tools (researched 2026-10-06)

No app found that does what this one wants: look for changes shorter than the official minimum
and say how often each one worked. Several tools cover a part.

| Tool | License | What it does | For this app |
|---|---|---|---|
| sbb.ch / SBB Mobile | proprietary | The official planner. sbb.ch can make changes longer (+50%, ×2); the app can't change them. | What we compare against. Never shorter. |
| [Öffi](https://de.wikipedia.org/wiki/%C3%96ffi_(Software)) | the app isn't open | Fast / few changes / short walks, a walking speed. | Asks the operators' planners, so their minimums still apply. |
| [Transportr](https://github.com/grote/Transportr) | GPLv3 | Open-source transit app on F-Droid. | **Don't fork:** its data layer, [public-transport-enabler](https://github.com/schildbach/public-transport-enabler) (GPLv3, Java), has no transfer time control. UI ideas at most. |
| [transport.opendata.ch](https://transport.opendata.ch/docs.html) | open data | Official connections and boards, no key. | **Data source for the first version** ([data_sources.md](data_sources.md)). |
| [OJP 2.0](https://opentransportdata.swiss/en/cookbook/open-journey-planner-ojp-landing-page/) | open data | The official API; a key, 20,000 requests a day per key. | Optional, with the user's own key. |
| [DelayBahn](https://github.com/sha2nkt/delay_bahn) | see repo | Adds the last 7 days' median delay and a transfer risk to official results (DE, AT, CH, FR); uses Ist-Daten for CH. | Ideas for the risk. Scores official connections only, never finds new ones. |
| [Uncertainty-aware route planner](https://github.com/m-doru/Uncertainty-aware-route-planner) | see repo | "The fastest route that works Q% of the time", from Ist-Daten. | The closest idea. A student project: Zurich area, 2017–18, keeps the official rules. |
| [swiss-transit-analytics](https://github.com/SantiagoTeranMoreno/swiss-transit-analytics) | see repo | Reads the daily Ist-Daten, punctuality. | Ideas for reading Ist-Daten. |
| [naviqore](https://github.com/naviqore/public-transit-service/) | MIT, Java, on Maven Central | RAPTOR router for Swiss GTFS with minimum transfer times you can set. About 64 MB of stop times per service day; "needs significant memory". | **Phase 2:** the full search on a server or in CI. Not on the phone. |
| [MOTIS](https://github.com/motis-project/motis) / [Transitous](https://github.com/public-transport/transitous) | MIT, C++ | Fast router; Transitous is a public instance. `transferTimeFactor` below 1.0 isn't supported, so only self-hosted with edited GTFS. | Phase 2 alternative; harder to change than naviqore. |
| [OpenTripPlanner](https://github.com/opentripplanner/OpenTripPlanner) | LGPL, Java | Large multi-modal server. | Skip: heavier than naviqore for the same job. |

**Licenses:** the app is GPLv3 like the user's other apps. MIT, Apache-2.0 and LGPL code can be
used in it; GPLv3 code (Transportr, public-transport-enabler) can too, as the licenses match.
The data needs attribution.

**Why nobody built it, probably:** the audience is narrow (daily commuters on certain routes),
and official providers avoid recommending risky changes on purpose. Fine for an open-source
tool that shows the risk.

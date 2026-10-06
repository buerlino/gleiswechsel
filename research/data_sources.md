# Data sources (researched 2026-10-06)

All Swiss public transport data is open: free to use with attribution, under the
[opentransportdata.swiss terms of use](https://opentransportdata.swiss/en/terms-of-use/).
"Checked" means a real request was made on that date; the rest is from the documentation.

## transport.opendata.ch (no key) — the first choice

A community API on top of the national timetable and its real-time data (search.ch). No key, so
nothing secret to ship in an open-source app.

- **Limits:** "no fixed limit per client … please cache what you can"; the same question asked
  again within seconds gets throttled.
- **`/v1/connections?from=&to=&date=&time=&isArrivalTime=&limit=&via=&transportations=`**: the
  official connections. **No transfer time parameter**: it applies the national timetable's
  per-station times. Checked 2026-10-06 (Luzern → Bern, Luzern → Lausanne): each connection has
  `transfers` and `sections`; a section has either a `journey` (`category` + `number`, e.g. `IR` +
  `16`, `operator`, `to`, `passList`) with `departure`/`arrival` (`station.id`, ISO time,
  `platform`, `delay`, `prognosis`), or a `walk`.
- **`/v1/stationboard?station=<id>&type=departure|arrival&limit=`**: a board. Checked 2026-10-06
  at Olten (8500218): **with `type=arrival` the arrival time is still in `stop.departure`**
  (S29 shows 13:48 on the arrival board, 13:49 on the departure board), and `to` is where the
  train ends, not where it came from. `stop.prognosis.arrival` held the time of the request on
  almost every row; don't rely on it until understood.
- `/v1/connections`, checked 2026-10-06 (Horw → Sursee, Luzern → Sursee): times look like
  `2026-10-07T08:53:00+0200` (no colon in the offset, so not plain ISO); without `limit` it sends
  4 connections, none leaving before `time` (asked 09:06, the 09:05 was left out); `from`/`to`
  take ids too, and `+` for a space. Three identical requests a second apart all got 200.
- **Walks at the start**, checked 2026-10-06 from a station id to a tram or bus stop: Bern,
  Luzern and Olten board at "…, Bahnhof" right at the requested time, no walk; Zürich HB starts
  every connection with a 5-minute walk to Bahnhofplatz/HB. Asked from a stop, the API also
  uses its neighbours (from Bahnhofplatz/HB it offered trams from Bahnhofstrasse/HB).
- Station ids are the national ids (`8505000` Luzern, `8507000` Bern, `8500218` Olten).
- CORS is open (`access-control-allow-origin: *`), responses are plain JSON.

## Open Journey Planner (OJP 2.0) — optional, the user's own key

The official API of opentransportdata.swiss (XML).

- **Key:** free with registration. **Limits per key:** 50 requests/min and 20,000/day; more costs
  CHF 500 or 1,000 a month. So the app can't ship one shared key: each user would bring their own.
- **`OJPTripRequest`**: the official trips. `AdditionalTransferTime` only adds time; `WalkSpeed`
  (percent of normal) is documented as "not available". No way to ask for shorter changes.
- **`OJPStopEventRequest`**: departure and arrival boards for any stop.
- Richer than transport.opendata.ch (guaranteed connections, situations), but XML and a key.
  [openTdataCH/ojp-adapter](https://github.com/openTdataCH/ojp-adapter) (Apache-2.0, Java) maps
  the XML to Java classes; it needs Spring Boot, so only its model is a reference for Android.

## Ist-Daten (actual times) — for the risk

Every planned and actual arrival and departure of the day before, as CSV, one file per day.

- **Where:** [dataset v2](https://data.opentransportdata.swiss/dataset/ist-daten-v2) (since
  13 July 2025, adds foreign stops and SLOIDs), [archive](https://archive.opentransportdata.swiss/)
  with monthly ZIPs back to 2016.
- **Columns used:** `FAHRT_BEZEICHNER` (trip id, format varies by operator), `LINIEN_ID`,
  `LINIEN_TEXT`, `BPUIC` (stop id, the same numbers as above), `SLOID`, `HALTESTELLEN_NAME`,
  `ANKUNFTSZEIT`/`ABFAHRTSZEIT` (planned, minutes), `AN_PROGNOSE`/`AB_PROGNOSE` (actual, seconds),
  `AN_PROGNOSE_STATUS`/`AB_PROGNOSE_STATUS` (`REAL`, `ESTIMATED`, `FORECAST`, `UNKNOWN`: use
  `REAL` only), `FAELLT_AUS_TF` (cancelled), `ZUSATZFAHRT_TF` (extra train).
- A day has millions of rows (swiss-transit-analytics). **Not checked yet:** the size of one day's
  file, and how to match its trips to the live API's (`category` + `number` + planned time +
  station is the guess).
- Swiss punctuality counts an arrival under 3 minutes late as on time.

## Timetable files — only for a full search later

- **GTFS** (weekly): the whole timetable with platforms. `transfers.txt` has
  `min_transfer_time` (`transfer_type=2`) for stations with their own time and `transfer_type=1`
  for guaranteed connections (since October 2025); the 2-minute default isn't in the file.
- **HRDF**: the raw format GTFS is made from, with every transfer table (`UMSTEIGB` per station,
  `UMSTEIGZ` per train pair, …). GTFS carries "most, but not all" of it.
- **GTFS-RT**: real-time updates.
- **Traffic points** ([traffic-point-v2](https://data.opentransportdata.swiss/dataset/traffic-point-v2)):
  platforms with coordinates where `hasGeolocation` is set, for walking times between platforms.
  Coverage not checked.

## Sources

- [transport.opendata.ch docs](https://transport.opendata.ch/docs.html)
- [OJP landing page](https://opentransportdata.swiss/en/cookbook/open-journey-planner-ojp-landing-page/), [OJPTripRequest 2.0](https://opentransportdata.swiss/en/cookbook/open-journey-planner-ojp/ojptriprequest-2-0/)
- [Limits and costs](https://opentransportdata.swiss/en/limits-and-costs/)
- [Actual data cookbook](https://opentransportdata.swiss/en/cookbook/historic-and-statistics-cookbook/actual-data/), [Ist-Daten v2](https://opentransportdata.swiss/en/ist-daten-v2-new-version-2/)
- [GTFS](https://opentransportdata.swiss/en/cookbook/timetable-cookbook/gtfs/), [HRDF](https://opentransportdata.swiss/en/cookbook/timetable-cookbook/hafas-rohdaten-format-hrdf/), [GTFS-RT](https://opentransportdata.swiss/en/cookbook/realtime-prediction-cookbook/gtfs-rt/)
- [Service points and traffic points](https://opentransportdata.swiss/en/cookbook/masterdata-cookbook/servicepoints/)

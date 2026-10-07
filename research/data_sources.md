# Data sources (researched 2026-10-06)

All Swiss public transport data is open: free to use with attribution, under the
[opentransportdata.swiss terms of use](https://opentransportdata.swiss/en/terms-of-use/) (below).
"Checked" means a real request was made on that date; the rest is from the documentation.

## Terms of use (read 2026-10-06)

The terms the GTFS dataset names (its page links them; the FEDRO terms there are for road
traffic data only). They allow publishing a file made from the GTFS:

- §1: "The data obtained can be processed, analysed and published, including incorporating
  additional data."
- Definitions: processed data are "ODMCH data that have been converted, edited or refined by a
  third party": the timetable file is processed data.
- §5.1: "The URL opentransportdata.swiss must be cited as the source for raw data in publications
  and analyses." (§5.1.1: in a database from several sources, once in its list of sources.)
- §5.2, "Updating raw data": "ODMCH data must be updated regularly and at the same frequency as
  the underlying raw data." The GTFS is updated twice a week (below).
- §5.3: "Processed data and analyses must be published under the name of the data user."
- §7: SBB gives no guarantee that the data are "up to date, correct, complete, available or
  accurate", and no liability.

The page shows no version or date.

## transport.opendata.ch (no key) — the first choice

A community API. It computes connections with MOTIS on the Swiss GTFS and GTFS-RT from
opentransportdata.swiss (its docs, read 2026-10-07; before, it asked search.ch): "transfer times
are those of the national timetable, per station; where the timetable gives a walk to a
neighbouring stop that is shorter than the station's transfer time, the walk counts". It holds a
window of days, moved weekly: 27 Sep 2026 to 25 Feb 2027 on 7 Oct (a time outside: HTTP 400
"outside of loaded timetable window"). No key, so nothing secret to ship in an open-source app.

- **Limits:** "no fixed limit per client … please cache what you can"; the same question asked
  again within seconds gets throttled.
- **`/v1/connections?from=&to=&date=&time=&isArrivalTime=&limit=&via=&transportations=`**: the
  official connections. **No transfer time parameter**: it applies the national timetable's
  per-station times. Checked 2026-10-06 (Luzern → Bern, Luzern → Lausanne): each connection has
  `transfers` and `sections`; a section has either a `journey` (`category` + `number`, e.g. `IR` +
  `16`, `name` (the train number, e.g. `021431`), `operator`, `to`, `passList`) with
  `departure`/`arrival` (`station.id`, ISO time, `platform`, `delay`, `prognosis`), or a `walk`.
  `passList` is every stop of the ride, its departure and arrival included; the app reads its
  station ids (2026-10-06, for a trip that passes a station twice). A `passList` station can have
  no id.
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
- **Real time**, checked 2026-10-06 (Luzern → Zürich HB at 21:51): every departure and arrival
  has `delay` (minutes, `1`) and `prognosis.departure`/`.arrival` (the expected time, 22:10 for
  the 22:09) and `prognosis.platform`. Checked again 2026-10-06 at 23:07 (saved in `private/`,
  `*-20261006-2307.json`): not known yet is `delay: null` with every `prognosis` field null (trains
  5 hours ahead, tomorrow 08:50: its answer was byte-identical to the afternoon's); on time is
  `delay: 0` with `prognosis` = planned; trains up to 2 hours ahead had values (the exact window
  not checked by day). Values seen 0, 1, 2, 5, none negative. `prognosis.platform` was null in
  every answer and on the Zürich HB and Luzern boards (no changed track at hand). No field for a cancelled train or a disruption anywhere in
  the answer; how a cancelled train shows (left out, or as planned) is untested. `from`/`to` with
  a raw "ü" got "Invalid HTTP request": encode it.
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

## Timetable files

- **GTFS**: the whole timetable with platforms, for the full search (decided 2026-10-06,
  `CLAUDE.md`). Details below.
- **HRDF**: the raw format GTFS is made from, with every transfer table (`UMSTEIGB` per station,
  `UMSTEIGZ` per train pair, …). GTFS carries "most, but not all" of it. In the 2026 export of
  29 Sep: `UMSTEIGB` 366 KB, `UMSTEIGV` (per operator pair) 425 lines, `UMSTEIGL` (per line pair)
  761, `UMSTEIGZ` (per train pair, train numbers and operators) 2,193; mostly foreign and bus
  stops. Nothing at Luzern, only "999" pairs (no connection) at Olten, so they don't explain the
  planner's 4 minutes there (hidden_connections.md). Read without the whole 555 MB zip, see the
  skill.
- **GTFS-RT**: real-time updates.
- **Traffic points** ([traffic-point-v2](https://data.opentransportdata.swiss/dataset/traffic-point-v2)):
  platforms with coordinates where `hasGeolocation` is set, for walking times between platforms.
  Coverage not checked.

### GTFS

- **Where:** dataset
  [timetable-2026-gtfs2020](https://data.opentransportdata.swiss/dataset/timetable-2026-gtfs2020);
  `…/timetable-2026-gtfs2020/permalink` is the newest zip (289 MB, the export of 30 Sep 2026;
  `stop_times.txt` 3.7 GB unpacked). No key. The permalink redirects to the file's download
  link, and that to a signed Cloudflare R2 URL valid 60 s (`X-Amz-Expires=60`); range requests
  work. The portal wanted a browser User-Agent in the prototype (curl's default refused);
  checked 2026-10-06, the permalink and both redirects also took curl's default and sent the
  zip's first bytes.
- **Updated** "zweimal pro Woche" (the dataset page), not on holidays: the files are mostly from
  Wednesdays and Saturdays. On 6 Oct 2026 the 2026 dataset's newest was from 30 Sep.
- **The next timetable** has its own dataset, `timetable-2027-gtfs2020` (from 13 Dec 2026),
  already published alongside (checked 2026-10-06: files since 17 Jun 2026, the newest 3 Oct,
  its permalink works). **How they meet** (checked 2026-10-07): 2026's `feed_start_date` to
  `feed_end_date` is 20251214–20261212, and its calendars end there; 2027's is
  20261213–20271211. No overlap, by service day: 2026 has the night trains of 12 Dec past 24:00.
  The 2027 export of 3 Oct is 90 MB (2026's 289 MB) but not a draft: 19,184 train trips on Wed
  16 Dec, about as many as on a 2026 weekday.
- **Files** (checked on the 30 Sep 2026 export, 2026-10-06 and 07, in a Python prototype and
  `trains` in `:core`): each starts with a UTF-8 BOM; the header isn't quoted, every field of
  the rows is. Train trips are grouped and in `stop_sequence` order in `stop_times.txt`; none is
  in `frequencies.txt`. Every stop has a `didok`, one name per `didok`; foreign ones start with
  80 (Germany), 81, 82, 83 (Italy), 87 (France), 88 or 71. One weekday (Wed 7 Oct): 104 stops
  where riders can't get on, 92 where they can't get off, none where the train only passes;
  3,843 times past 24:00. `agency_timezone` is `Europe/Berlin` (the same offsets as Zurich).
  - `stops.txt`: `stop_id` is a SLOID (`ch:1:sloid:5000:4:9`); the column `didok` is the API's
    station id (`8505000`), `platform_code` the track (`9`, `2CD`, `41/42`); parents
    `Parentch:1:sloid:5000`. Foreign stops are in it too.
  - `routes.txt`: `route_type` 100–117 are trains (101 TGV, 102 IC/EC/ICE, 103 IR, 106
    R/RE/RB/TER, 109 S/SN, 116 rack, 117 EXT); `route_short_name` is the line as the app shows it
    (`S1`, `R55`, `IC6`); `trips.txt`'s `trip_short_name` is the train number.
  - `trips.txt` `service_id` → `calendar.txt` (weekday flags, start and end date) and
    `calendar_dates.txt` (`exception_type` 1 added, 2 removed).
  - `stop_times.txt`: `trip_id`, `arrival_time`, `departure_time` (can be past 24:00),
    `stop_id`, `stop_sequence`, `pickup_type`, `drop_off_type` (1: not possible). Every field is
    quoted.
  - `transfers.txt`: `transfer_type` 2, minimum times (811k rows; for stations with their own
    time, the 2-minute default isn't in the file); 4, in-seat continuations (301k): a train that
    continues under another number is not a change; 1, guaranteed connections (292, since
    October 2025). An extra column `service_id`: every in-seat continuation has its own days.
    The 4,822 between trains of 7–20 Oct all go from a trip's last stop to the next one's first,
    at the same station. Checked 2026-10-07 (for CLAUDE.md, One model): type 2 between the
    platforms of one station has the same minutes for every pair, at each of its 8,962 stations,
    and equals `UMSTEIGB` at 2,948 of the 2,949 Swiss ones (Winterthur 0, the table 3); 702k
    rows of type 2 are per train pair (`UMSTEIGZ`: Zürich HB 5 for 6,595 pairs, Aarau 3; none
    at Luzern or Olten); 59,571 link two stations (walks). The guaranteed connections are buses
    (Baar).
- **Times on the clock** (checked 2026-10-07): not from noon minus 12 hours as GTFS says. On
  25 Oct 2026 (the clocks go back) the night trains have the same times as on 1 Nov (SN1
  Winterthur 00:35 … 02:35, 03:35), and the API shows them on the clock (02:35 +02:00, 03:35
  +01:00), the hour that comes twice taken as the first. 28 Mar 2027 (the clocks go forward,
  checked in the 2027 export 2026-10-07): 72 train trips of that service day and 21 of the 27th
  stop between 02:00 and 03:00, an hour that doesn't exist (SN11 Winterthur 02:05, SN1 02:35,
  S3 Basel SBB 02:45; 574 stop events in that hour, about 795 on the Sundays around it). How the
  API shows them can't be checked before its window reaches 28 Mar.
- **Trips by days:** one train is often several trips with the same times, each on some of the
  days (the RE24 Luzern 09:05: five trips over 7–20 Oct).
- **Names:** `route_desc` is the category (`S`, `IC`, `ICE`, `TER`, `CC`, `PE`). Swiss lines'
  `route_short_name` starts with it (`S4`, `IC21`, `IRLEX`) and matches the API. Foreign ones
  don't: ICE `651A` (API `ICE651A`), TER `C10`/`K23` (`TERK23`), Jungfraubahn `65` (`CC65`), and
  EC, TGV, NJ only the category (the API adds the train number: `EC000015`, `TGV009210`). A few
  Swiss ones don't either: PE `GEX`, RE `N1`, IR `VAE`, S `EV`.
- **In-seat continuations** cross midnight 12 times of 4,822 (7–20 Oct: S50 and S from Italy
  into the S10 at Mendrisio, 00:34, on the next service day). The API sends a continuation as one
  section under the first train's name (checked 2026-10-07 on six).
- **Size** (the prototype, 2026-10-06): one weekday's trains are 20,007 trips and 206k stop
  events, all modes 3.05M (buses 2.5M, so 12× the trains). The trains of 14 days: 51,382 trips,
  544k stop events, 5,010 platforms; a plain binary (u16 platform, u16 arrival minutes, u8 dwell
  per stop) 3.0 MB, 0.9 MB gzipped. Reading the GTFS took about a minute.

## Sources

- [transport.opendata.ch docs](https://transport.opendata.ch/docs.html)
- [OJP landing page](https://opentransportdata.swiss/en/cookbook/open-journey-planner-ojp-landing-page/), [OJPTripRequest 2.0](https://opentransportdata.swiss/en/cookbook/open-journey-planner-ojp/ojptriprequest-2-0/)
- [Limits and costs](https://opentransportdata.swiss/en/limits-and-costs/)
- [Actual data cookbook](https://opentransportdata.swiss/en/cookbook/historic-and-statistics-cookbook/actual-data/), [Ist-Daten v2](https://opentransportdata.swiss/en/ist-daten-v2-new-version-2/)
- [Terms of use](https://opentransportdata.swiss/en/terms-of-use/)
- [GTFS](https://opentransportdata.swiss/en/cookbook/timetable-cookbook/gtfs/), [HRDF](https://opentransportdata.swiss/en/cookbook/timetable-cookbook/hafas-rohdaten-format-hrdf/), [GTFS-RT](https://opentransportdata.swiss/en/cookbook/realtime-prediction-cookbook/gtfs-rt/)
- [Service points and traffic points](https://opentransportdata.swiss/en/cookbook/masterdata-cookbook/servicepoints/)
- [GitHub Pages limits](https://docs.github.com/en/pages/getting-started-with-github-pages/github-pages-limits): 1 GB a site, 100 GB a month (soft)

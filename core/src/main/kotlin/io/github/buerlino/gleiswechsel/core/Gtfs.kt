package io.github.buerlino.gleiswechsel.core

import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile

/**
 * The trains (GTFS `route_type` 100–117; buses would be 12× the data) of [days] days from [first]
 * in the Swiss GTFS [zip] (research/data_sources.md). Reads the whole zip, 4.5 GB unpacked.
 */
fun trains(zip: File, first: LocalDate, days: Int): Timetable = ZipFile(zip).use { gtfs ->
    require(days in 1..31) { "$days days don't fit in a trip's 32 bits" }
    val dates = List(days) { first.plusDays(it.toLong()) }
    val dateTexts = dates.map { it.format(gtfsDate) }

    var version = ""
    gtfs.forEachRow("feed_info.txt") { version = it["feed_version"] }

    // The days each service runs.
    val services = HashMap<String, Int>()
    gtfs.forEachRow("calendar.txt") { row ->
        services[row["service_id"]] = dates.indices.fold(0) { mask, d ->
            val runs = row[dates[d].dayOfWeek.name.lowercase()] == "1" && dateTexts[d] in row["start_date"]..row["end_date"]
            if (runs) mask or (1 shl d) else mask
        }
    }
    gtfs.forEachRow("calendar_dates.txt") { row ->
        val d = dateTexts.indexOf(row["date"])
        if (d < 0) return@forEachRow
        val id = row["service_id"]
        val mask = services[id] ?: 0
        services[id] = if (row["exception_type"] == "1") mask or (1 shl d) else mask and (1 shl d).inv()
    }

    val lines = HashMap<String, String>()
    gtfs.forEachRow("routes.txt") { row ->
        if (row["route_type"].toInt() in 100..117) lines[row["route_id"]] = row["route_short_name"]
    }

    class StopTime(val sequence: Int, val stop: String, val arrival: Int, val departure: Int, val pickup: Boolean, val dropOff: Boolean)
    class Kept(val line: String, val days: Int, val stops: MutableList<StopTime> = ArrayList())

    val kept = LinkedHashMap<String, Kept>()
    gtfs.forEachRow("trips.txt") { row ->
        val line = lines[row["route_id"]] ?: return@forEachRow
        val tripDays = services[row["service_id"]] ?: 0
        if (tripDays != 0) kept[row["trip_id"]] = Kept(line, tripDays)
    }
    gtfs.forEachRow("stop_times.txt") { row ->
        val trip = kept[row["trip_id"]] ?: return@forEachRow
        trip.stops += StopTime(
            row["stop_sequence"].toInt(), row["stop_id"], minutes(row["arrival_time"]), minutes(row["departure_time"]),
            pickup = row["pickup_type"] != "1", dropOff = row["drop_off_type"] != "1",
        )
    }
    val used = kept.values.flatMapTo(HashSet()) { trip -> trip.stops.map { it.stop } }
    val stops = HashMap<String, Pair<Timetable.Station, String?>>()
    gtfs.forEachRow("stops.txt") { row ->
        val id = row["stop_id"]
        if (id !in used) return@forEachRow
        val didok = row["didok"].ifEmpty { throw IOException("No didok for stop $id") }
        stops[id] = Timetable.Station(didok, row["stop_name"]) to row["platform_code"].ifEmpty { null }
    }
    val stations = stops.values.map { it.first }.distinctBy { it.id }.sortedBy { it.id }
    val stationIndex = stations.withIndex().associate { it.value.id to it.index }
    val platformIds = used.sorted()
    val platformIndex = platformIds.withIndex().associate { it.value to it.index }
    val platforms = platformIds.map { id ->
        val (station, code) = stops[id] ?: throw IOException("No stop $id")
        Timetable.Platform(stationIndex.getValue(station.id), code)
    }

    val tripIndex = kept.keys.withIndex().associate { it.value to it.index }
    val continuations = ArrayList<Timetable.Continuation>()
    gtfs.forEachRow("transfers.txt") { row ->
        if (row["transfer_type"] != "4") return@forEachRow
        val from = tripIndex[row["from_trip_id"]] ?: return@forEachRow
        val to = tripIndex[row["to_trip_id"]] ?: return@forEachRow
        // The Swiss GTFS gives each in-seat transfer its own service.
        val continuationDays = services[row["service_id"]] ?: 0
        if (continuationDays != 0) continuations += Timetable.Continuation(from, to, continuationDays)
    }

    Timetable(
        source = "opentransportdata.swiss, GTFS $version",
        first = first,
        days = days,
        stations = stations,
        platforms = platforms,
        trips = kept.values.map { trip ->
            val s = trip.stops.sortedBy { it.sequence }
            Timetable.Trip(
                trip.line, trip.days,
                platforms = IntArray(s.size) { platformIndex.getValue(s[it].stop) },
                arrivals = IntArray(s.size) { s[it].arrival },
                departures = IntArray(s.size) { s[it].departure },
                pickup = BooleanArray(s.size) { s[it].pickup },
                dropOff = BooleanArray(s.size) { s[it].dropOff },
            )
        },
        continuations = continuations,
    )
}

private val gtfsDate = DateTimeFormatter.BASIC_ISO_DATE

/** `08:53:00` → 533; past 24:00 after midnight. */
private fun minutes(time: String): Int = time.split(':').let { it[0].toInt() * 60 + it[1].toInt() }

/** A row of a GTFS file: its fields by the header's column names. */
private class Row(private val columns: Map<String, Int>) {
    var fields: List<String> = emptyList()

    operator fun get(column: String): String = fields[columns[column] ?: throw IOException("No column $column")]
}

/** Calls [action] with each row of the file [name] (UTF-8 with a BOM, every field quoted). */
private fun ZipFile.forEachRow(name: String, action: (Row) -> Unit) {
    val entry = getEntry(name) ?: throw IOException("No $name in the GTFS")
    getInputStream(entry).bufferedReader().use { lines ->
        val header = lines.readLine()?.removePrefix("\uFEFF") ?: return
        val row = Row(fields(header).withIndex().associate { it.value to it.index })
        lines.forEachLine {
            row.fields = fields(it)
            action(row)
        }
    }
}

/** A CSV line's fields: quoted or not; a quote inside quotes is doubled. */
private fun fields(line: String): List<String> {
    val fields = ArrayList<String>(10)
    var i = 0
    while (i <= line.length) {
        if (i < line.length && line[i] == '"') {
            val field = StringBuilder()
            i++
            while (true) {
                val quote = line.indexOf('"', i)
                if (quote < 0) throw IOException("No closing quote: $line")
                field.append(line, i, quote)
                i = quote + 1
                if (i < line.length && line[i] == '"') field.append('"').also { i++ } else break
            }
            fields += field.toString()
            i++
        } else {
            val comma = line.indexOf(',', i).let { if (it < 0) line.length else it }
            fields += line.substring(i, comma)
            i = comma + 1
        }
    }
    return fields
}

/**
 * Writes the timetable file for the full search: `<GTFS zip> <out file> [first day, yyyy-MM-dd]`,
 * 14 days from the first (today in Switzerland if not given). Run by `./gradlew :core:timetable`.
 */
fun main(args: Array<String>) {
    require(args.size in 2..3) { "Arguments: <GTFS zip> <out file> [first day, yyyy-MM-dd]" }
    val first = args.getOrNull(2)?.let(LocalDate::parse) ?: LocalDate.now(ZoneId.of("Europe/Zurich"))
    val start = System.nanoTime()
    val timetable = trains(File(args[0]), first, days = 14)
    val out = File(args[1])
    out.outputStream().use { timetable.write(it) }
    println(
        "${timetable.source}, $first + ${timetable.days} days: ${timetable.trips.size} trips, " +
            "${timetable.trips.sumOf { it.platforms.size }} stops, ${timetable.platforms.size} platforms, " +
            "${timetable.stations.size} stations, ${timetable.continuations.size} continuations; " +
            "${out.length()} bytes in ${(System.nanoTime() - start) / 1_000_000_000} s",
    )
}

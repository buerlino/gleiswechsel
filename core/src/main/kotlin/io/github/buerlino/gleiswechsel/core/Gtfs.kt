package io.github.buerlino.gleiswechsel.core

import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile

/**
 * The trains (GTFS `route_type` 100–117; buses would be 12× the data) of [days] days from [first]
 * in the Swiss GTFS [zips] (research/data_sources.md): each timetable year has its own, for the
 * days from its `feed_start_date` to its `feed_end_date`, so around the timetable change in
 * December the days need two. A zip with none of the days is skipped; a day none has throws.
 * Reads each zip it uses whole, 4.5 GB unpacked. A trip is named as transport.opendata.ch names
 * it ([name]).
 */
fun trains(zips: List<File>, first: LocalDate, days: Int): Timetable {
    require(days in 1..31) { "$days days don't fit in a trip's 32 bits" }
    val dates = List(days) { first.plusDays(it.toLong()) }
    val dateTexts = dates.map { it.format(gtfsDate) }

    class StopTime(val sequence: Int, val stop: String, val arrival: Int, val departure: Int, val pickup: Boolean, val dropOff: Boolean)
    class Kept(val line: String, val days: Int, val stops: MutableList<StopTime> = ArrayList())

    val versions = ArrayList<String>()
    var covered = 0
    val kept = ArrayList<Kept>()
    val stops = HashMap<String, Pair<Timetable.Station, String?>>()
    val continuations = ArrayList<Timetable.Continuation>()
    for (zip in zips) ZipFile(zip).use { gtfs ->
        var feed = 0
        gtfs.forEachRow("feed_info.txt") { row ->
            feed = dates.indices.fold(0) { mask, d ->
                if (dateTexts[d] in row["feed_start_date"]..row["feed_end_date"]) mask or (1 shl d) else mask
            }
            if (feed != 0) versions += row["feed_version"]
        }
        if (feed == 0) return@use
        covered = covered or feed

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
        // Only this zip's days, so a calendar running past its feed can't count a day twice.
        services.replaceAll { _, mask -> mask and feed }

        val lines = HashMap<String, Pair<String, String>>()
        gtfs.forEachRow("routes.txt") { row ->
            if (row["route_type"].toInt() in 100..117) lines[row["route_id"]] = row["route_short_name"] to row["route_desc"]
        }

        // This zip's trip ids: each year's are its own.
        val tripIndex = HashMap<String, Int>()
        gtfs.forEachRow("trips.txt") { row ->
            val (line, category) = lines[row["route_id"]] ?: return@forEachRow
            val tripDays = services[row["service_id"]] ?: 0
            if (tripDays != 0) {
                tripIndex[row["trip_id"]] = kept.size
                kept += Kept(name(line, category, row["trip_short_name"]), tripDays)
            }
        }
        gtfs.forEachRow("stop_times.txt") { row ->
            val trip = tripIndex[row["trip_id"]]?.let(kept::get) ?: return@forEachRow
            trip.stops += StopTime(
                row["stop_sequence"].toInt(), row["stop_id"], minutes(row["arrival_time"]), minutes(row["departure_time"]),
                pickup = row["pickup_type"] != "1", dropOff = row["drop_off_type"] != "1",
            )
        }
        val used = tripIndex.values.flatMapTo(HashSet()) { trip -> kept[trip].stops.map { it.stop } }
        gtfs.forEachRow("stops.txt") { row ->
            val id = row["stop_id"]
            if (id !in used || id in stops) return@forEachRow
            val didok = row["didok"].ifEmpty { throw IOException("No didok for stop $id") }
            stops[id] = Timetable.Station(didok, row["stop_name"]) to row["platform_code"].ifEmpty { null }
        }

        gtfs.forEachRow("transfers.txt") { row ->
            if (row["transfer_type"] != "4") return@forEachRow
            val from = tripIndex[row["from_trip_id"]] ?: return@forEachRow
            val to = tripIndex[row["to_trip_id"]] ?: return@forEachRow
            // The Swiss GTFS gives each in-seat transfer its own service.
            val continuationDays = services[row["service_id"]] ?: 0
            if (continuationDays != 0) continuations += Timetable.Continuation(from, to, continuationDays)
        }
    }
    dates.indices.firstOrNull { covered shr it and 1 == 0 }?.let { throw IOException("No GTFS has ${dates[it]}") }

    val used = kept.flatMapTo(HashSet()) { trip -> trip.stops.map { it.stop } }
    val stations = stops.values.map { it.first }.distinctBy { it.id }.sortedBy { it.id }
    val stationIndex = stations.withIndex().associate { it.value.id to it.index }
    val platformIds = used.sorted()
    val platformIndex = platformIds.withIndex().associate { it.value to it.index }
    val platforms = platformIds.map { id ->
        val (station, code) = stops[id] ?: throw IOException("No stop $id")
        Timetable.Platform(stationIndex.getValue(station.id), code)
    }

    return Timetable(
        source = "opentransportdata.swiss, GTFS ${versions.joinToString(" + ")}",
        first = first,
        days = days,
        stations = stations,
        platforms = platforms,
        trips = kept.map { trip ->
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

/**
 * A train's name as transport.opendata.ch gives it (CLAUDE.md, The full search, step 4.2), from
 * the GTFS `route_short_name` [line], `route_desc` [category] and `trip_short_name` [number]:
 * the line where it starts with the category (S4, IR35), the category and the number in six
 * digits where the line is only the category (IC000484), else the category and the line (CC64).
 */
private fun name(line: String, category: String, number: String) = when {
    line == category -> category + number.padStart(6, '0')
    line.startsWith(category) -> line
    else -> category + line
}

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
 * Writes the timetable file for the full search: `<GTFS zip>[,<zip>…] <out file> [first day,
 * yyyy-MM-dd]`, 14 days from the first (today in Switzerland if not given). Run by
 * `./gradlew :core:timetable`.
 */
fun main(args: Array<String>) {
    require(args.size in 2..3) { "Arguments: <GTFS zip>[,<zip>…] <out file> [first day, yyyy-MM-dd]" }
    val first = args.getOrNull(2)?.let(LocalDate::parse) ?: LocalDate.now(ZoneId.of("Europe/Zurich"))
    val start = System.nanoTime()
    val timetable = trains(args[0].split(',').map(::File), first, days = 14)
    val out = File(args[1])
    out.outputStream().use { timetable.write(it) }
    println(
        "${timetable.source}, $first + ${timetable.days} days: ${timetable.trips.size} trips, " +
            "${timetable.trips.sumOf { it.platforms.size }} stops, ${timetable.platforms.size} platforms, " +
            "${timetable.stations.size} stations, ${timetable.continuations.size} continuations; " +
            "${out.length()} bytes in ${(System.nanoTime() - start) / 1_000_000_000} s",
    )
}

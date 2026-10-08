package io.github.buerlino.gleiswechsel.core

import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The full search (CLAUDE.md): for each of the [officials] whose first departure A and last arrival
 * B are stations in the [timetable], the trains from A, leaving no earlier, that reach B first,
 * through any stations; of the trips arriving together, the one leaving A last. A find if it reaches
 * B before that official connection, in real time: the scan's order on the clock isn't, in the hour
 * the clocks skip (28 Mar 2027, a train at 02:50 is 03:50). Then [best], as for [search]. A
 * connection scan over planned times.
 *
 * The rider changes trains only within a station (same id; no walks), with their [transfer] time
 * there. Staying on through an in-seat continuation isn't a change. [transfer] is asked once per
 * station, with the first arrival there that needs it; only the station counts.
 */
fun fullSearch(timetable: Timetable, officials: List<Connection>, transfer: (station: Stop) -> Duration): List<Find> {
    val index = timetable.stations.withIndex().associate { it.value.id to it.index }
    val searchable = officials.filter { it.departure.id in index && it.arrival.id in index }
    if (searchable.isEmpty()) return emptyList()
    val minutes = { time: OffsetDateTime ->
        ChronoUnit.MINUTES.between(timetable.first.atStartOfDay(), time.atZoneSameInstant(zurich).toLocalDateTime()).toInt()
    }
    val scan = Scan(timetable, searchable.minOf { minutes(it.departure.time) }, searchable.maxOf { minutes(it.arrival.time) }, transfer)
    val finds = searchable.mapNotNull { official ->
        val a = index.getValue(official.departure.id)
        val b = index.getValue(official.arrival.id)
        val leaving = minutes(official.departure.time)
        scan.earliest(a, leaving, b, before = minutes(official.arrival.time))?.let { Find(official, scan.latest(a, leaving, b, it)) }
            ?.takeIf { it.faster.arrival.time.isBefore(official.arrival.time) }
    }
    return best(finds, officials)
}

/**
 * The day scan of the fastest of the day (CLAUDE.md): for each departure from station [from] (its id)
 * on the service [day] of the [timetable], the earliest arrival at [to], with the rider's [transfer]
 * times as in [fullSearch]; the journeys no other beats (leaves no earlier, arrives no later), each
 * the one leaving last, in the order they leave. Empty if a station or the day isn't in the file.
 *
 * The departures from the last of the day: one counts only if it arrives before the next one kept,
 * so its scan stops there and a beaten journey isn't built. Arriving by 04:00 the next morning,
 * after the night's last trains, before the next day's first: a night's wait isn't a commute, and
 * the day's last departures (S4 Horw 25:08) don't reach the next day's 05:14. Not by the day's last
 * arrival: night trains from abroad count from their first station's day (NJ Feldkirch 31:41 →
 * Zürich HB 35:36; checked 2026-10-07).
 */
fun daySearch(timetable: Timetable, from: String, to: String, day: LocalDate, transfer: (station: Stop) -> Duration): List<Connection> {
    val a = timetable.stations.indexOfFirst { it.id == from }
    val b = timetable.stations.indexOfFirst { it.id == to }
    val d = ChronoUnit.DAYS.between(timetable.first, day).toInt()
    if (a < 0 || b < 0 || d !in 0 until timetable.days) return emptyList()
    val zero = d * DAY
    val departures = timetable.trips.filter { it.days shr d and 1 == 1 }.flatMap { trip ->
        (0 until trip.platforms.size - 1).filter { trip.pickup[it] && timetable.platforms[trip.platforms[it]].station == a }
            .map { zero + trip.departures[it] }
    }.distinct().sortedDescending().ifEmpty { return emptyList() }
    var before = zero + DAY + 4 * 60
    val scan = Scan(timetable, zero, before, transfer)
    return departures.mapNotNull { leaving ->
        scan.earliest(a, leaving, b, before)?.let { before = it; scan.latest(a, leaving, b, it) }
    }.reversed()
}

private val zurich = ZoneId.of("Europe/Zurich")

/**
 * The rides from one stop to the next (the connections of the scan) of the trips in the [timetable]
 * that leave at [from] or later and arrive before [until] (a find arrives before its official
 * connection), in the order they leave. Times in minutes from the midnight that starts the file's
 * first day, on the clock: the Swiss GTFS counts so, not from noon minus 12 hours as GTFS says
 * (checked 2026-10-07: on 25 Oct 2026, when the clocks go back, it has the night trains of any
 * Sunday, and the API's departure boards read them on the clock; its connections don't, CLAUDE.md).
 * A trip on one of the days is an instance: trip × the number of days + the day.
 */
private class Scan(private val timetable: Timetable, from: Int, until: Int, private val transfer: (Stop) -> Duration) {
    // From the day before (its trips past 24:00) to the last, those in the file.
    private val days = (Math.floorDiv(from, DAY) - 1..Math.floorDiv(until, DAY)).filter { it in 0 until timetable.days }.toIntArray()
    private val instance: IntArray
    private val stop: IntArray
    private val dep: IntArray
    private val arr: IntArray
    private val into = timetable.continuations.groupBy { it.to }
    private val transfers = IntArray(timetable.stations.size) { -1 }

    init {
        // Counted first, then filled.
        fun rides(each: (x: Int, i: Int, dep: Int, arr: Int) -> Unit) {
            timetable.trips.forEachIndexed { t, trip ->
                for (k in days.indices) {
                    val zero = days[k] * DAY
                    if (trip.days shr days[k] and 1 == 0 || zero + trip.arrivals.last() < from || zero + trip.departures[0] >= until) continue
                    for (i in 0 until trip.platforms.size - 1) {
                        val d = zero + trip.departures[i]
                        val a = zero + trip.arrivals[i + 1]
                        if (d >= from && a < until) each(t * days.size + k, i, d, a)
                    }
                }
            }
        }
        var n = 0
        rides { _, _, _, _ -> n++ }
        check(n < 1 shl 21) { "$n rides" }
        val x0 = IntArray(n)
        val i0 = IntArray(n)
        val d0 = IntArray(n)
        val a0 = IntArray(n)
        n = 0
        rides { x, i, d, a -> x0[n] = x; i0[n] = i; d0[n] = d; a0[n] = a; n++ }
        // By departure, then arrival, then as filled: a trip's rides stay in their order, also those
        // of zero minutes.
        val order = LongArray(n) { (d0[it] - from).toLong() shl 42 or ((a0[it] - from).toLong() shl 21) or it.toLong() }
        order.sort()
        val j = IntArray(n) { (order[it] and 0x1FFFFF).toInt() }
        instance = IntArray(n) { x0[j[it]] }
        stop = IntArray(n) { i0[j[it]] }
        dep = IntArray(n) { d0[j[it]] }
        arr = IntArray(n) { a0[j[it]] }
    }

    private val instances = timetable.trips.size * days.size

    // Reused by each scan: the day scan runs one per departure.
    private val arrival = IntArray(timetable.stations.size)
    private val on = BooleanArray(instances)
    private val leave = IntArray(timetable.stations.size)
    private val exit = IntArray(instances)
    private val stay = IntArray(instances)

    /** The first ride leaving at [minute] or later. */
    private fun leavingAt(minute: Int): Int {
        var low = 0
        var high = dep.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (dep[mid] < minute) low = mid + 1 else high = mid
        }
        return low
    }

    private fun trip(x: Int) = timetable.trips[x / days.size]

    private fun station(x: Int, i: Int) = timetable.platforms[trip(x).platforms[i]].station

    private fun pickup(j: Int) = trip(instance[j]).pickup[stop[j]]

    private fun dropOff(j: Int) = trip(instance[j]).dropOff[stop[j] + 1]

    /** The in-seat continuations into instance [x] on its day, as instances of the trips that go on as it. */
    private fun continued(x: Int): List<Int> {
        val k = x % days.size
        return into[x / days.size].orEmpty().filter { it.days shr days[k] and 1 == 1 }.map { it.from * days.size + k }
    }

    private fun transferAt(s: Int, arrival: Int): Int {
        if (transfers[s] < 0) {
            val station = timetable.stations[s]
            transfers[s] = transfer(Stop(station.name, station.id, time(0, arrival))).toMinutes().toInt()
        }
        return transfers[s]
    }

    /** The earliest arrival at station [b] from [a], leaving at [leaving] or later, if it's [before]. */
    fun earliest(a: Int, leaving: Int, b: Int, before: Int): Int? {
        arrival.fill(Int.MAX_VALUE)
        on.fill(false)
        var first = before
        for (j in leavingAt(leaving) until dep.size) {
            if (dep[j] >= first) break
            val x = instance[j]
            if (!on[x]) {
                val s = station(x, stop[j])
                on[x] = stop[j] == 0 && continued(x).any { on[it] } ||
                    pickup(j) && (s == a || arrival[s] != Int.MAX_VALUE && arrival[s] + transferAt(s, arrival[s]) <= dep[j])
            }
            if (!on[x] || !dropOff(j)) continue
            val s = station(x, stop[j] + 1)
            if (arr[j] < arrival[s]) arrival[s] = arr[j]
            if (s == b) first = minOf(first, arr[j])
        }
        return first.takeIf { it < before }
    }

    /**
     * The trip from station [a], leaving at [leaving] or later, that reaches [b] [by] the [earliest]
     * arrival and leaves last: a scan backwards from [by], keeping the latest departure from each
     * station that still gets there.
     */
    fun latest(a: Int, leaving: Int, b: Int, by: Int): Connection {
        leave.fill(-1)
        exit.fill(-1)
        stay.fill(-1)
        for (j in leavingAt(by + 1) - 1 downTo 0) {
            if (dep[j] < leaving) break
            if (arr[j] > by) continue
            val x = instance[j]
            if (exit[x] < 0 && stay[x] < 0 && dropOff(j)) {
                val s = station(x, stop[j] + 1)
                if (s == b || leave[s] >= 0 && arr[j] + transferAt(s, arr[j]) <= dep[leave[s]]) exit[x] = j
            }
            if (exit[x] < 0 && stay[x] < 0) continue
            if (stop[j] == 0) continued(x).forEach { if (exit[it] < 0 && stay[it] < 0) stay[it] = x }
            if (!pickup(j)) continue
            val s = station(x, stop[j])
            if (s == a) return connection(j, b)
            if (leave[s] < 0) leave[s] = j
        }
        error("No trip by the earliest arrival")
    }

    /** The trip boarding ride [board] and following [leave], [exit] and [stay] to [b]. */
    private fun connection(board: Int, b: Int): Connection {
        val legs = ArrayList<Leg>()
        var j = board
        while (true) {
            var x = instance[j]
            var i = stop[j]
            val departure = stopAt(x, i, trip(x).departures[i])
            val via = ArrayList<String>()
            while (stay[x] >= 0) {
                for (m in i + 1 until trip(x).platforms.size) via += timetable.stations[station(x, m)].id
                x = stay[x]
                i = 0
            }
            val e = exit[x]
            for (m in i + 1..stop[e]) via += timetable.stations[station(x, m)].id
            legs += Leg(trip(instance[j]).line, departure, stopAt(x, stop[e] + 1, trip(x).arrivals[stop[e] + 1]), via)
            val s = station(x, stop[e] + 1)
            if (s == b) return Connection(legs)
            j = leave[s]
        }
    }

    private fun stopAt(x: Int, i: Int, minutes: Int): Stop {
        val platform = timetable.platforms[trip(x).platforms[i]]
        val station = timetable.stations[platform.station]
        return Stop(station.name, station.id, time(days[x % days.size], minutes), platform.code)
    }

    /** [minutes] on the clock on the file's [day] as Swiss time; of a time the clocks pass twice, the first. */
    private fun time(day: Int, minutes: Int): OffsetDateTime =
        timetable.first.atStartOfDay().plusMinutes(day.toLong() * DAY + minutes).atZone(zurich).toOffsetDateTime()
}

private const val DAY = 24 * 60

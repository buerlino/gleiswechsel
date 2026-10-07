package io.github.buerlino.gleiswechsel.core

import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class FullSearchTest {
    // Made-up stations, tracks and trains, from Monday 2 March 2026 for 14 days; searched on Tuesday.
    private val stations = listOf("Aach", "Xberg", "Bstadt", "Yfeld").mapIndexed { i, name -> Timetable.Station("${i + 1}", name) }
    private val platforms = listOf(0 to "1", 1 to "1", 1 to "9", 2 to null, 3 to "3").map { Timetable.Platform(it.first, it.second) }
    private val aach = 0
    private val xberg1 = 1
    private val xberg9 = 2
    private val bstadt = 3
    private val yfeld = 4
    private val everyDay = (1 shl 14) - 1

    private class At(val platform: Int, val arrival: Int, val departure: Int, val pickup: Boolean, val dropOff: Boolean)

    private fun hhmm(text: String) = text.split(':').let { it[0].toInt() * 60 + it[1].toInt() }

    /** A stop at [platform], arriving at [arrival] and leaving at [departure] (GTFS times, past 24:00 after midnight). */
    private fun at(platform: Int, arrival: String, departure: String = arrival, pickup: Boolean = true, dropOff: Boolean = true) =
        At(platform, hhmm(arrival), hhmm(departure), pickup, dropOff)

    private fun trip(line: String, vararg stops: At, days: Int = everyDay) = Timetable.Trip(
        line, days,
        IntArray(stops.size) { stops[it].platform }, IntArray(stops.size) { stops[it].arrival },
        IntArray(stops.size) { stops[it].departure }, BooleanArray(stops.size) { stops[it].pickup },
        BooleanArray(stops.size) { stops[it].dropOff },
    )

    private fun timetable(vararg trips: Timetable.Trip, continuations: List<Timetable.Continuation> = emptyList(), first: LocalDate = monday) =
        Timetable("Made up", first, 14, stations, platforms, trips.toList(), continuations)

    private val monday = LocalDate.of(2026, 3, 2)

    // Aach 08:00 → Xberg 08:10 (track 1); the RE3 leaves track 9 at 08:14, the IR2 at 08:20.
    private val s1 = trip("S1", at(aach, "08:00"), at(xberg1, "08:10"))
    private val re3 = trip("RE3", at(xberg9, "08:14"), at(bstadt, "08:30"))
    private val ir2 = trip("IR2", at(xberg1, "08:20"), at(bstadt, "08:40"))

    // The planner's S1 → IR2, as the API sends it, on Tuesday 3 March.
    private fun stop(station: Int, time: String) = stations[station].let { Stop(it.name, it.id, OffsetDateTime.parse("2026-03-03T$time:00+01:00")) }
    private fun official(departure: String, arrival: String, from: Int = 0, to: Int = 2) =
        Connection(listOf(Leg("IR2", stop(from, departure), stop(to, arrival))))

    private val official = official("08:00", "08:40")

    private fun minutes(n: Long): (Stop) -> Duration = { Duration.ofMinutes(n) }

    private fun Connection.text() = legs.joinToString(" | ") { leg ->
        "${leg.train} ${leg.departure.text()} → ${leg.arrival.text()}" + leg.via.joinToString("") { " via $it" }
    }

    private fun Stop.text() = "$station ${time.toLocalDateTime().toLocalTime()}${time.offset}" + (platform?.let { " ($it)" } ?: "")

    @Test
    fun aShorterChangeCatchesAnEarlierTrain() {
        val finds = fullSearch(timetable(s1, re3, ir2), listOf(official), minutes(4))
        assertEquals(
            listOf("S1 Aach 08:00+01:00 (1) → Xberg 08:10+01:00 (1) | RE3 Xberg 08:14+01:00 (9) → Bstadt 08:30+01:00"),
            finds.map { it.faster.text() },
        )
        assertEquals(Duration.ofMinutes(10), finds.single().saved)
        assertEquals(emptyList(), fullSearch(timetable(s1, re3, ir2), listOf(official), minutes(5)))
    }

    @Test
    fun throughAStationTheOfficialConnectionDoesntTouch() {
        val s2 = trip("S2", at(aach, "08:02"), at(yfeld, "08:09"))
        val r7 = trip("R7", at(yfeld, "08:12"), at(bstadt, "08:25"))
        val find = fullSearch(timetable(s1, s2, r7, ir2), listOf(official), { Duration.ofMinutes(if (it.id == "4") 3 else 10) }).single()
        assertEquals(listOf("S2", "R7"), find.faster.legs.map { it.train })
        assertEquals(Duration.ofMinutes(15), find.saved)
    }

    @Test
    fun eachStationIsAskedOnce() {
        val asked = mutableListOf<String>()
        fullSearch(timetable(s1, re3, ir2, trip("IR4", at(xberg1, "08:16"), at(bstadt, "08:35"))), listOf(official)) { asked += it.station; Duration.ofMinutes(4) }
        assertEquals(listOf("Xberg"), asked)
    }

    @Test
    fun stayingOnIsNoChange() {
        // The S1 goes on as the RE6 at Xberg on Tuesdays (day 1): no 4 minutes needed for the 08:11.
        val re6 = trip("RE6", at(xberg1, "08:11"), at(yfeld, "08:15"), at(bstadt, "08:25"))
        val continuation = { days: Int -> listOf(Timetable.Continuation(0, 1, days)) }
        val find = fullSearch(timetable(s1, re6, ir2, continuations = continuation(1 shl 1)), listOf(official), minutes(4)).single()
        assertEquals("S1 Aach 08:00+01:00 (1) → Bstadt 08:25+01:00 via 2 via 4", find.faster.text())
        // Not on Tuesdays: a change, too short.
        assertEquals(emptyList(), fullSearch(timetable(s1, re6, ir2, continuations = continuation(1 shl 2)), listOf(official), minutes(4)))
    }

    @Test
    fun onlyWhereRidersCanGetOffAndOn() {
        val noDropOff = trip("S1", at(aach, "08:00"), at(xberg1, "08:10", dropOff = false), at(yfeld, "08:20"))
        assertEquals(emptyList(), fullSearch(timetable(noDropOff, re3, ir2), listOf(official), minutes(4)))
        val noPickup = trip("RE3", at(xberg9, "08:14", pickup = false), at(bstadt, "08:30"))
        assertEquals(emptyList(), fullSearch(timetable(s1, noPickup, ir2), listOf(official), minutes(4)))
    }

    @Test
    fun ofTwoArrivingTogetherTheOneLeavingLater() {
        // The S9 leaves Aach 4 minutes after the S1 and also reaches the RE3.
        val s9 = trip("S9", at(aach, "08:04"), at(xberg9, "08:10"))
        val find = fullSearch(timetable(s1, s9, re3, ir2), listOf(official), minutes(4)).single()
        assertEquals(listOf("S9", "RE3"), find.faster.legs.map { it.train })
        assertEquals("08:04", find.faster.departure.time.toLocalTime().toString())
    }

    @Test
    fun aTripThePlannerAlreadyOffersIsNoFind() {
        val alsoOfficial = Connection(listOf(Leg("S1", stop(0, "08:00"), stop(1, "08:10")), Leg("RE3", stop(1, "08:14"), stop(2, "08:30"))))
        assertEquals(emptyList(), fullSearch(timetable(s1, re3, ir2), listOf(official, alsoOfficial), minutes(4)))
    }

    @Test
    fun onlyWhenBothEndsAreInTheFile() {
        val fromBusStop = Connection(listOf(Leg("B5", Stop("Aach, Post", "9", stop(0, "08:00").time), stop(2, "08:40"))))
        assertEquals(emptyList(), fullSearch(timetable(s1, re3, ir2), listOf(fromBusStop), minutes(4)))
    }

    @Test
    fun theDayBeforePast24AndTheNextDay() {
        // A search at 00:05 on Tuesday: Monday's S1 at 24:10 (Tuesday 00:10), then the RE3 at 00:14.
        val late = trip("S1", at(aach, "24:10"), at(xberg1, "24:20"), days = 1 shl 0)
        val early = trip("RE3", at(xberg9, "00:24"), at(bstadt, "00:40"), days = 1 shl 1)
        val find = fullSearch(timetable(late, early), listOf(official("00:05", "01:00")), minutes(4)).single()
        assertEquals("S1 Aach 00:10+01:00 (1) → Xberg 00:20+01:00 (1) | RE3 Xberg 00:24+01:00 (9) → Bstadt 00:40+01:00", find.faster.text())
        // A search at 23:50 on Tuesday reaches Wednesday's 00:24.
        val tuesday = trip("S1", at(aach, "24:10"), at(xberg1, "24:20"), days = 1 shl 1)
        val wednesday = trip("RE3", at(xberg9, "00:24"), at(bstadt, "00:40"), days = 1 shl 2)
        val toWednesday = Connection(listOf(Leg("IR2", stop(0, "23:50"), stops(2, "2026-03-04T01:00:00+01:00"))))
        assertEquals("2026-03-04T00:40+01:00", fullSearch(timetable(tuesday, wednesday), listOf(toWednesday), minutes(4)).single().faster.arrival.time.toString())
    }

    private fun stops(station: Int, time: String) = stations[station].let { Stop(it.name, it.id, OffsetDateTime.parse(time)) }

    @Test
    fun onTheClockAlsoWhenTheClocksGoBack() {
        // 25 October 2026 (from Monday the 19th, day 6): 02:00 to 03:00 comes twice; the API reads
        // the GTFS times on the clock and an hour that comes twice as the first (checked 2026-10-07).
        val night = trip("SN1", at(aach, "02:35"), at(bstadt, "02:50"), days = 1 shl 6)
        val later = trip("SN1", at(aach, "03:35"), at(bstadt, "03:50"), days = 1 shl 6)
        val timetable = timetable(night, later, first = LocalDate.of(2026, 10, 19))
        fun first(departure: String) = fullSearch(
            timetable, listOf(Connection(listOf(Leg("IR2", stops(0, "2026-10-25T$departure"), stops(2, "2026-10-25T05:00:00+01:00"))))), minutes(4),
        ).single().faster.legs.single().departure.time.toString()
        assertEquals("2026-10-25T02:35+02:00", first("02:00:00+02:00"))
        assertEquals("2026-10-25T03:35+01:00", first("03:00:00+01:00"))
    }
}

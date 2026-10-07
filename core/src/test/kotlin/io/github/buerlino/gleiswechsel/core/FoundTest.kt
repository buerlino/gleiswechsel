package io.github.buerlino.gleiswechsel.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FoundTest {
    // Made-up stations (the name is the id) and trains.
    private fun stop(station: String, hhmm: String, platform: String? = null, delay: Long? = null, newPlatform: String? = null) =
        Stop(station, station, OffsetDateTime.parse("2026-03-03T$hhmm:00+01:00"), platform, delay, newPlatform)

    // Aach → Xberg, official change to the IR2; the find changes to the RE3, after a walk.
    private val s1 = Leg("S1", stop("Aach", "08:00", "2"), stop("Xberg", "08:10", "1"), via = listOf("Wil", "Zell"))
    private val ir2 = Leg("IR2", stop("Xberg", "08:20", "3"), stop("Bstadt", "08:40"))
    private val official = Connection(listOf(s1, ir2))
    private val faster = Connection(listOf(
        s1,
        Leg(null, stop("Xberg", "08:10"), stop("Xberg, Bahnhof", "08:12")),
        Leg("RE3", stop("Xberg, Bahnhof", "08:14", "9", delay = 2, newPlatform = "7"), stop("Bstadt", "08:30", delay = 0)),
    ))
    private val found = Found(
        LocalDateTime.parse("2026-03-03T07:55"),
        official,
        listOf(stop("Xberg", "08:10", "1"), stop("Yfeld", "08:30")),
        listOf(Find(official, faster)),
        setOf(Change(s1.arrival, ir2.departure)),
        incomplete = true,
        noTimetable = false,
        asOf = LocalDateTime.parse("2026-03-03T07:41"),
    )

    @Test
    fun writtenAndReadBackIsTheSame() = assertEquals(found, found(found.toJson()))

    @Test
    fun timesAreText() = assert(""""time":"2026-03-03T08:14+01:00"""" in found.toJson())

    @Test
    fun delaysKnownIfAnyTripHasOne() {
        assert(found.delaysKnown)
        assert(!found.copy(finds = emptyList()).delaysKnown) // the official one has none
    }

    @Test
    fun anotherFormatThrows() {
        assertFailsWith<Exception> { found("""{"day":"2026-03-03T07:55"}""") }
    }

    @Test
    fun theFormatBeforeOneModelThrows() {
        // Until 2026-10-07 a result had the shortest change at each station instead of the planner's changes.
        val older = JsonObject(json.parseToJsonElement(found.toJson()).jsonObject - "offered" + ("shortest" to JsonObject(mapOf("Xberg" to JsonPrimitive(4)))))
        assertFailsWith<Exception> { found(older.toString()) }
    }

    @Test
    fun theTripsOnThePage() {
        assertEquals(listOf(faster, official), found.trips)
        assert(found.onTrips("Xberg"))
        assert(found.onTrips("Xberg, Bahnhof"))
        assert(!found.onTrips("Yfeld"))
        // Nothing faster: the official connection leaving first.
        assertEquals(listOf(official), found.copy(finds = emptyList()).trips)
        assertEquals(emptyList(), found.copy(first = null, finds = emptyList()).trips)
    }

    // find() on a fake API; made-up stations in a made-up table: Xberg 5 minutes, elsewhere 2.
    private val asked = mutableListOf<String>()
    private val minimums = Minimums("9999999 02 02 STANDARD\nXberg 05 05 Xberg\n")
    private val now = LocalDateTime.parse("2026-03-03T07:30")

    private val failed = mutableListOf<Exception>()

    private fun find(
        officials: List<Connection>,
        times: TrackSwitchTimes,
        onward: Map<String, List<Connection>>,
        leaving: String = "07:55",
        timetable: () -> Timetable? = { null },
    ) = find("Aach", "Bstadt", LocalTime.parse(leaving), times, { from, _, time ->
        asked += "$from ${time.toLocalTime()}"
        if (from == "Aach") officials else onward[from].orEmpty()
    }, timetable, now) { failed += it }

    // From Xberg: the RE3 08:14 → Bstadt 08:30, the IR7 08:30 → 08:50.
    private val re3 = Connection(listOf(Leg("RE3", stop("Xberg", "08:14", "9"), stop("Bstadt", "08:30"))))
    private val ir7 = Leg("IR7", stop("Xberg", "08:30", "4"), stop("Bstadt", "08:50"))
    private val fromXberg = mapOf("Xberg" to listOf(re3, Connection(listOf(ir7))))

    // An official connection on which the planner itself changes at Xberg in 4 minutes (the table: 5).
    private val inFour = Connection(listOf(Leg("S5", stop("Aach", "08:16"), stop("Xberg", "08:26", "2")), ir7))

    @Test
    fun theSameTimeIsTheSameColourInTwoSearches() {
        // Nothing set, offset 1: Xberg's default is 4, whether the planner changes there in 4 (the
        // second search) or not (the first). Each search asks once, with 4 (until 2026-10-07 the
        // second lowered Xberg to 4, its default to 3, and asked again).
        val times = TrackSwitchTimes(minimums, 1) { null }
        val first = find(listOf(official), times, fromXberg)
        val firstAsked = asked.toList()
        asked.clear()
        val second = find(listOf(official, inFour), times, fromXberg)
        assertEquals(listOf("Aach 07:55", "Xberg 08:14"), firstAsked)
        assertEquals(listOf("Aach 07:55", "Xberg 08:14", "Xberg 08:30"), asked)
        // The row: 4 against 5 in both.
        assertEquals(listOf("Xberg"), first.changes.map { it.id })
        assertEquals(listOf("Xberg"), second.changes.map { it.id })
        assertEquals(4L to 5L, times.default("Xberg") to times.official("Xberg"))
        // The find's change: 4 against 5 in both.
        val change = Change(s1.arrival, re3.departure)
        assertEquals(listOf(change), first.finds.single().faster.transfers)
        assertEquals(listOf(change), second.finds.single().faster.transfers)
        assertEquals(5, times.official(change, first.offered))
        assertEquals(5, times.official(change, second.offered))
    }

    @Test
    fun thePlannersOwnChangeIsOfficialPerChange() {
        // The planner's S5 → IR7 in 4 is official (4 against 4); the find's S1 → RE3 in 4 isn't
        // (4 against Xberg's 5), as the S1 → S41 at Luzern and the S4 → RE24 (CLAUDE.md).
        val times = TrackSwitchTimes(minimums, 1) { null }
        val found = find(listOf(official, inFour), times, fromXberg)
        val planners = inFour.transfers.single()
        assertEquals(4, planners.minutes)
        assert(planners in found.offered)
        assertEquals(4, times.official(planners, found.offered))
        assertEquals(5, times.official(found.finds.single().faster.transfers.single(), found.offered))
    }

    @Test
    fun theNextSuchTime() {
        val times = TrackSwitchTimes(minimums, 1) { null }
        assertEquals(LocalDateTime.parse("2026-03-03T07:30"), find(emptyList(), times, emptyMap(), "07:30").day)
        val tomorrow = find(emptyList(), times, emptyMap(), "07:29")
        assertEquals(LocalDateTime.parse("2026-03-04T07:29"), tomorrow.day)
        assertNull(tomorrow.first)
        assertEquals(now, tomorrow.asOf)
    }

    @Test
    fun aRowNotOnThePagesTripsIsFaded() {
        // Sursee → Zürich Oerlikon 07:45, made up in the shape of the planner's answers for 8 Oct
        // 2026 (CLAUDE.md, One model), the rider's Zürich HB at 3.
        val ids = mapOf(
            "Sursee" to "1", "Luzern" to "2", "Olten" to "3", "Zürich HB" to "4", "Brugg AG" to "5", "Zürich Oerlikon" to "6",
        )
        fun at(station: String, hhmm: String) = Stop(station, ids.getValue(station), OffsetDateTime.parse("2026-10-08T$hhmm:00+02:00"))
        fun ride(train: String, from: String, leaves: String, to: String, arrives: String) = Leg(train, at(from, leaves), at(to, arrives))
        val viaOlten = Connection(listOf(
            ride("IR27", "Sursee", "07:48", "Olten", "08:08"),
            ride("IC5", "Olten", "08:16", "Zürich HB", "08:48"),
            ride("S6", "Zürich HB", "08:56", "Zürich Oerlikon", "09:08"),
        ))
        val viaBrugg = Connection(listOf(
            ride("S29", "Sursee", "07:51", "Brugg AG", "08:52"),
            ride("IR36", "Brugg AG", "09:22", "Zürich Oerlikon", "09:57"),
        ))
        val ir75 = ride("IR75", "Luzern", "08:35", "Zürich HB", "09:22")
        val viaLuzern = Connection(listOf(ride("IR27", "Sursee", "08:12", "Luzern", "08:30"), ir75, ride("S6", "Zürich HB", "09:31", "Zürich Oerlikon", "09:38")))
        val s8 = ride("S8", "Zürich HB", "09:25", "Zürich Oerlikon", "09:29")
        val onward = mapOf(
            "3" to listOf(Connection(viaOlten.legs.drop(1))),
            "4" to listOf(Connection(viaOlten.legs.drop(2)), Connection(listOf(s8)), Connection(viaLuzern.legs.drop(2))),
            "5" to listOf(Connection(viaBrugg.legs.drop(1))),
            "2" to listOf(Connection(viaLuzern.legs.drop(1))),
        )
        val table = Minimums("9999999 02 02 STANDARD\n2 05 05 Luzern\n3 05 05 Olten\n4 07 07 Zürich HB\n5 03 03 Brugg AG\n")
        val times = TrackSwitchTimes(table, 1, mapOf("4" to 3L)::get)
        val found = find("Sursee", "Zürich Oerlikon", LocalTime.of(7, 45), times, { from, _, _ ->
            if (from == "Sursee") listOf(viaOlten, viaBrugg, viaLuzern) else onward[from].orEmpty()
        }, { null }, LocalDateTime.parse("2026-10-08T07:00"))
        assertEquals(listOf(viaLuzern.legs[0], ir75, s8), found.finds.single().faster.legs)
        assertEquals(listOf("Olten", "Zürich HB", "Brugg AG", "Luzern"), found.changes.map { it.station })
        assertEquals(listOf("Zürich HB", "Luzern"), found.changes.filter { found.onTrips(it.id) }.map { it.station })
    }

    /**
     * A timetable of 14 days from [first], each trip every day, without tracks: a line and its stops
     * (station, the time it arrives and leaves).
     */
    private fun timetable(vararg trips: Pair<String, List<Pair<String, String>>>, first: LocalDate = LocalDate.of(2026, 3, 2)): Timetable {
        val names = trips.flatMap { (_, stops) -> stops.map { it.first } }.distinct()
        return Timetable(
            "Made up", first, 14, names.map { Timetable.Station(it, it) }, names.indices.map { Timetable.Platform(it, null) },
            trips.map { (line, stops) ->
                val minutes = IntArray(stops.size) { LocalTime.parse(stops[it].second).toSecondOfDay() / 60 }
                Timetable.Trip(
                    line, (1 shl 14) - 1, IntArray(stops.size) { names.indexOf(stops[it].first) }, minutes, minutes.copyOf(),
                    BooleanArray(stops.size) { true }, BooleanArray(stops.size) { true },
                )
            },
            emptyList(),
        )
    }

    // Through Yfeld, a station the official connection doesn't touch: Aach 08:05 → Yfeld 08:12 →
    // Bstadt 08:35, with Yfeld's default of 1 (the standard 2 − 1).
    private val viaYfeld = timetable(
        "S9" to listOf("Aach" to "08:05", "Yfeld" to "08:12"),
        "RE8" to listOf("Yfeld" to "08:14", "Bstadt" to "08:35"),
    )

    @Test
    fun aFindOnlyTheFullSearchHas() {
        val times = TrackSwitchTimes(minimums, 1) { null }
        val found = find(listOf(official), times, emptyMap(), timetable = { viaYfeld })
        assertEquals(listOf("S9", "RE8"), found.finds.single().faster.legs.map { it.train })
        assertEquals(LocalTime.of(8, 35), found.finds.single().faster.arrival.time.toLocalTime())
        // Its change station is a row after today's search's, on the trips shown.
        assertEquals(listOf("Xberg", "Yfeld"), found.changes.map { it.id })
        assert(found.onTrips("Yfeld"))
        assert(!found.noTimetable)
        assertEquals(emptyList(), failed)
    }

    @Test
    fun ofTwoTheSameTodaysStays() {
        // The full search finds the S1 → RE3 too, under other names and without tracks.
        val same = timetable(
            "S 1" to listOf("Aach" to "08:00", "Xberg" to "08:10"),
            "RE 3" to listOf("Xberg" to "08:14", "Bstadt" to "08:30"),
        )
        val found = find(listOf(official), TrackSwitchTimes(minimums, 1) { null }, fromXberg, timetable = { same })
        assertEquals(listOf(Find(official, Connection(listOf(s1, re3.legs.single())))), found.finds)
        assertEquals(listOf("Xberg"), found.changes.map { it.id })
    }

    @Test
    fun withoutATimetableTodaysSearchAloneAndTheLine() {
        val times = TrackSwitchTimes(minimums, 1) { null }
        // None that reads.
        val none = find(listOf(official), times, fromXberg)
        assert(none.noTimetable)
        assertEquals(1, none.finds.size)
        assertEquals(1, failed.size)
        // One without the day (Tuesday 3 March).
        assert(find(listOf(official), times, fromXberg, timetable = { timetable(first = LocalDate.of(2026, 3, 4)) }).noTimetable)
        assert(!find(listOf(official), times, fromXberg, timetable = { timetable(first = LocalDate.of(2026, 3, 3)) }).noTimetable)
        // No connections: not asked, no line.
        var asked = false
        assert(!find(emptyList(), times, emptyMap(), timetable = { asked = true; null }).noTimetable)
        assert(!asked)
    }

    @Test
    fun aTimeAsTyped() {
        for (text in listOf("8:50", "08:50", "850", "0850", "8.50")) assertEquals(LocalTime.of(8, 50), parseTime(text), text)
        assertEquals(LocalTime.MIDNIGHT, parseTime("000"))
        assertEquals(LocalTime.of(23, 59), parseTime("2359"))
        for (text in listOf("", "8", "85", "24:00", "8:60", "12345", "abc")) assertNull(parseTime(text), text)
    }
}

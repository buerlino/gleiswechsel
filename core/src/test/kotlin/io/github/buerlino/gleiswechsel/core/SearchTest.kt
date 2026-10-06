package io.github.buerlino.gleiswechsel.core

import java.time.Duration
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchTest {
    // Made-up stations (the name is the id) and trains.
    private fun at(hhmm: String) = OffsetDateTime.parse("2026-03-03T$hhmm:00+01:00")
    private fun stop(station: String, hhmm: String, platform: String? = null) = Stop(station, station, at(hhmm), platform)
    private fun ride(train: String?, from: Stop, to: Stop) = Leg(train, from, to)
    private fun minutes(n: Long) = Duration.ofMinutes(n)

    // Aach 08:00 → Xberg 08:10 (track 1), official change to the IR2 08:20 → Bstadt 08:40.
    private val s1 = ride("S1", stop("Aach", "08:00"), stop("Xberg", "08:10", "1"))
    private val ir2 = ride("IR2", stop("Xberg", "08:20", "3"), stop("Bstadt", "08:40"))
    private val official = Connection(listOf(s1, ir2))
    private val re3 = Connection(listOf(ride("RE3", stop("Xberg", "08:14", "9"), stop("Bstadt", "08:30"))))

    private val asked = mutableListOf<String>()

    /** The fake API sends [onward]'s connections from a stop, whatever the time asked for. */
    private fun search(
        transfer: (Stop) -> Duration,
        official: Connection = this.official,
        onward: Map<String, List<Connection>> = mapOf("Xberg" to listOf(re3, Connection(listOf(ir2)))),
    ) = search(listOf(official), transfer) { from, _, time ->
        asked += "$from ${time.toLocalTime()}"
        onward[from].orEmpty()
    }

    @Test
    fun aShorterChangeCatchesAnEarlierTrain() {
        // Exactly the transfer time is enough: 08:10 + 4 = 08:14.
        val find = search({ minutes(4) }).single()
        assertEquals(listOf("S1", "RE3"), find.faster.legs.map { it.train })
        assertEquals(stop("Xberg", "08:10", "1"), find.arrival)
        assertEquals(stop("Xberg", "08:14", "9"), find.departure)
        assertEquals(minutes(10), find.saved)
        assertEquals(40.0 / 30 - 1, find.moreEfficient)
        assertEquals(listOf("Xberg 08:14"), asked)
    }

    @Test
    fun aLongerChangeFindsNothing() {
        // 08:10 + 5 = 08:15 misses the RE3; the next is the official IR2, no earlier.
        assertEquals(emptyList(), search({ minutes(5) }))
    }

    @Test
    fun aFindThePlannerAlreadyOffersIsNone() {
        // The S1 → RE3 is also an official connection (as Horw → Bern, Bundesplatz, 2026-10-06).
        val alsoOfficial = Connection(listOf(s1, re3.legs.single()))
        assertEquals(emptyList(), search(listOf(official, alsoOfficial), { minutes(4) }) { _, _, _ -> listOf(re3) })
    }

    @Test
    fun theEarliestArrivalWinsNotTheFirstDeparture() {
        // The slow S9 leaves first; the RE3 and the IC4 arrive together, the IC4 leaves later.
        val s9 = Connection(listOf(ride("S9", stop("Xberg", "08:14"), stop("Bstadt", "08:38"))))
        val ic4 = Connection(listOf(ride("IC4", stop("Xberg", "08:16", "7"), stop("Bstadt", "08:30"))))
        val find = search({ minutes(4) }, onward = mapOf("Xberg" to listOf(s9, re3, ic4, Connection(listOf(ir2))))).single()
        assertEquals(listOf("S1", "IC4"), find.faster.legs.map { it.train })
    }

    @Test
    fun eachStationHasItsOwnTransferTime() {
        // Two changes: Xberg 08:10 (4 minutes there), Yfeld 08:30 (2 minutes there).
        val ir2 = ride("IR2", stop("Xberg", "08:20"), stop("Yfeld", "08:30"))
        val re5 = ride("RE5", stop("Yfeld", "08:40"), stop("Bstadt", "08:55"))
        val official = Connection(listOf(s1, ir2, re5))
        assertEquals(listOf("Xberg", "Yfeld"), official.changes.map { it.id })
        search({ minutes(if (it.id == "Xberg") 4 else 2) }, official = official)
        assertEquals(listOf("Xberg 08:14", "Yfeld 08:32"), asked)
    }

    @Test
    fun aWalkIsPartOfAChangeAndTheLastRideHasNone() {
        val walk = ride(null, stop("Xberg", "08:10"), stop("Xberg, Bahnhof", "08:13"))
        val bus = ride("B4", stop("Xberg, Bahnhof", "08:20"), stop("Bstadt", "08:40"))
        val official = Connection(listOf(s1, walk, bus))
        assertEquals(listOf(s1.arrival), official.changes)
        assertEquals(1, search({ minutes(4) }, official = official).size)
        assertEquals(listOf("Xberg 08:14"), asked)
    }

    @Test
    fun theRidersTimeReplacesTheApisWalk() {
        // From Xberg the API walks 5 minutes to the tram stop first, so its tram leaves 08:19.
        // Asked from the stop itself, the 08:15 tram is there: the rider's 4 minutes cover the walk.
        val walkThenTram = Connection(listOf(
            ride(null, stop("Xberg", "08:14"), stop("Xberg, Platz", "08:19")),
            ride("T8", stop("Xberg, Platz", "08:19"), stop("Bstadt", "08:35")),
        ))
        val tram = Connection(listOf(ride("T8", stop("Xberg, Platz", "08:15", "B"), stop("Bstadt", "08:31"))))
        val find = search(
            { minutes(4) },
            onward = mapOf("Xberg" to listOf(walkThenTram, Connection(listOf(ir2))), "Xberg, Platz" to listOf(tram)),
        ).single()
        assertEquals(listOf("S1", "T8"), find.faster.legs.map { it.train })
        assertEquals(stop("Xberg, Platz", "08:15", "B"), find.departure)
        assertEquals(listOf("Xberg 08:14", "Xberg, Platz 08:14"), asked)
    }
}

package io.github.buerlino.gleiswechsel.core

import java.io.IOException
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchTest {
    // Made-up stations (the name is the id) and trains.
    private fun at(hhmm: String) = OffsetDateTime.parse("2026-03-03T$hhmm:00+01:00")
    private fun stop(station: String, hhmm: String, platform: String? = null) = Stop(station, station, at(hhmm), platform)
    private fun ride(train: String?, from: Stop, to: Stop, via: List<String> = emptyList()) = Leg(train, from, to, via)
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
    }.finds

    @Test
    fun aShorterChangeCatchesAnEarlierTrain() {
        // Exactly the transfer time is enough: 08:10 + 4 = 08:14.
        val find = search({ minutes(4) }).single()
        assertEquals(listOf(s1) + re3.legs, find.faster.legs)
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
        assertEquals(emptyList(), search(listOf(official, alsoOfficial), { minutes(4) }) { _, _, _ -> listOf(re3) }.finds)
    }

    @Test
    fun aFindAnotherFindBeatsIsNone() {
        // Two changes, a find at each, both leaving 08:00: Xberg's arrives 08:30, Yfeld's 08:50.
        val ir2 = ride("IR2", stop("Xberg", "08:20"), stop("Yfeld", "08:30"))
        val re5 = ride("RE5", stop("Yfeld", "08:40"), stop("Bstadt", "08:55"))
        val r7 = Connection(listOf(ride("R7", stop("Yfeld", "08:35"), stop("Bstadt", "08:50"))))
        val finds = search({ minutes(4) }, Connection(listOf(s1, ir2, re5)), mapOf("Xberg" to listOf(re3), "Yfeld" to listOf(r7)))
        assertEquals(listOf(s1) + re3.legs, finds.single().faster.legs)
    }

    @Test
    fun anIdenticalTripCountsOnceAgainstTheOfficialArrivingFirst() {
        // Both official connections ride the S1 to Xberg: one changes twice to arrive 08:35, the
        // other once to arrive 08:45. The S1 → RE3 beats both; it saves 5 minutes, not 15.
        val twice = Connection(listOf(
            s1,
            ride("IR2", stop("Xberg", "08:20"), stop("Yfeld", "08:25")),
            ride("RE5", stop("Yfeld", "08:28"), stop("Bstadt", "08:35")),
        ))
        val once = Connection(listOf(s1, ride("IC6", stop("Xberg", "08:20"), stop("Bstadt", "08:45"))))
        val find = search(listOf(twice, once), { minutes(4) }) { from, _, _ -> if (from == "Xberg") listOf(re3) else emptyList() }.finds.single()
        assertEquals(twice, find.official)
        assertEquals(minutes(5), find.saved)
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
    fun onlyARideFromTheStationItself() {
        // From Xberg the API offers a walk to the tram stop and, as at Zürich HB, a tram from the
        // stop itself: the rider's time at Xberg is for a change within it (user, 2026-10-07).
        val walkThenTram = Connection(listOf(
            ride(null, stop("Xberg", "08:14"), stop("Xberg, Platz", "08:16")),
            ride("T8", stop("Xberg, Platz", "08:16"), stop("Bstadt", "08:32")),
        ))
        val tram = Connection(listOf(ride("T8", stop("Xberg, Platz", "08:15", "B"), stop("Bstadt", "08:31"))))
        val onward = mapOf("Xberg" to listOf(walkThenTram, tram, Connection(listOf(ir2))))
        assertEquals(emptyList(), search({ minutes(4) }, onward = onward))
        assertEquals(listOf("Xberg 08:14"), asked)
    }

    @Test
    fun thePlannersChangesAreKept() {
        // The official connection's change at Xberg and, in the answer from Xberg, the RE3 → R7 at Yfeld.
        val re3 = ride("RE3", stop("Xberg", "08:14"), stop("Yfeld", "08:24"))
        val r7 = ride("R7", stop("Yfeld", "08:27"), stop("Bstadt", "08:45"))
        val searched = search(listOf(official), { minutes(4) }) { from, _, _ ->
            if (from == "Xberg") listOf(Connection(listOf(re3, r7))) else emptyList()
        }
        assertEquals(setOf(Change(s1.arrival, ir2.departure), Change(re3.arrival, r7.departure)), searched.offered)
        assertEquals(3, Change(re3.arrival, r7.departure).minutes)
    }

    @Test
    fun twoShortChangesOnOneTrip() {
        // Officially S1 → IR2 at Xberg → RE5 at Yfeld, 08:55. From Xberg in 4 minutes the RE3 reaches
        // Yfeld 08:24, but the API changes there to the same RE5; from Yfeld in 4 minutes the R7
        // 08:28 arrives 08:43.
        val ir2 = ride("IR2", stop("Xberg", "08:20"), stop("Yfeld", "08:30"))
        val re5 = ride("RE5", stop("Yfeld", "08:40"), stop("Bstadt", "08:55"))
        val re3 = ride("RE3", stop("Xberg", "08:14"), stop("Yfeld", "08:24"))
        val r7 = ride("R7", stop("Yfeld", "08:28"), stop("Bstadt", "08:43"))
        val searched = search(listOf(Connection(listOf(s1, ir2, re5))), { minutes(4) }) { from, _, time ->
            asked += "$from ${time.toLocalTime()}"
            when ("$from ${time.toLocalTime()}") {
                "Xberg 08:14" -> listOf(Connection(listOf(re3, re5)))
                "Yfeld 08:28" -> listOf(Connection(listOf(r7)))
                else -> listOf(Connection(listOf(re5)))
            }
        }
        assertEquals(listOf(s1, re3, r7), searched.finds.single().faster.legs)
        assertEquals(listOf("Xberg 08:14", "Yfeld 08:28", "Yfeld 08:34"), asked)
        // Yfeld once, as reached first.
        assertEquals(listOf(s1.arrival, re3.arrival), searched.changes)
    }

    @Test
    fun noQuestionAtOrAfterTheOfficialArrival() {
        // As Bern, Bundesplatz → Horw (2026-10-07): from Xberg the API rides to Yfeld and changes
        // there, from Yfeld back to Xberg, an hour later each time, until the day ends. The change
        // at Yfeld 09:10 comes after the official arrival, 08:40: nothing from there is earlier.
        search(listOf(official), { minutes(4) }) { from, _, time ->
            asked += "$from ${time.toLocalTime()}"
            val other = if (from == "Xberg") "Yfeld" else "Xberg"
            val t = time.atOffset(at("00:00").offset)
            if (time.hour >= 23) emptyList() else listOf(Connection(listOf(
                ride("T3", Stop(from, from, t), Stop(other, other, t.plusMinutes(56))),
                ride("T9", Stop(other, other, t.plusMinutes(60)), Stop("Bstadt", "Bstadt", t.plusMinutes(80))),
            )))
        }
        assertEquals(listOf("Xberg 08:14"), asked)
    }

    @Test
    fun aFailedRequestSkipsOnlyItsChange() {
        // Two changes: the question at Xberg fails (HTTP 429); Yfeld's R7 still arrives 08:50, not 08:55.
        val ir2 = ride("IR2", stop("Xberg", "08:20"), stop("Yfeld", "08:30"))
        val re5 = ride("RE5", stop("Yfeld", "08:40"), stop("Bstadt", "08:55"))
        val r7 = Connection(listOf(ride("R7", stop("Yfeld", "08:35"), stop("Bstadt", "08:50"))))
        val searched = search(listOf(Connection(listOf(s1, ir2, re5))), { minutes(4) }) { from, _, _ ->
            if (from == "Xberg") throw IOException("HTTP 429") else listOf(r7)
        }
        assertEquals(listOf(s1, ir2) + r7.legs, searched.finds.single().faster.legs)
        assertEquals(listOf("HTTP 429"), searched.failures.map { it.message })
    }

    @Test
    fun eachQuestionIsAskedOnce() {
        // Both official connections ride the S1 to Xberg and change there.
        val other = Connection(listOf(s1, ride("IC6", stop("Xberg", "08:20"), stop("Bstadt", "08:45"))))
        search(listOf(official, other), { minutes(4) }) { from, _, time -> asked += "$from ${time.toLocalTime()}"; listOf(re3) }
        assertEquals(listOf("Xberg 08:14"), asked)
    }

    @Test
    fun aTripPassingAStationTwiceDoublesBack() {
        // The RE3 passes Yfeld; then the R7 goes back through it.
        val there = ride("RE3", stop("Xberg", "08:14"), stop("Zdorf", "08:24"), via = listOf("Yfeld"))
        val back = ride("R7", stop("Zdorf", "08:28"), stop("Bstadt", "08:43"), via = listOf("Yfeld"))
        assertEquals(true, Connection(listOf(s1, there, back)).doublesBack)
        assertEquals(false, Connection(listOf(s1, there)).doublesBack)
        assertEquals(false, official.doublesBack)
    }

    @Test
    fun theTicketLinkHasTheIdsDayAndTime() {
        val from = Stop("Aach, Platz", "8500001", at("08:05"))
        val to = Stop("Bstadt", "8500002", at("08:40"))
        assertEquals(
            "https://www.sbb.ch/fr?stops=_I8500001~_I8500002&day=2026-03-03&time=08_05&moment=dep",
            ticketUrl(from, to, "fr"),
        )
    }
}

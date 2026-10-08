package io.github.buerlino.gleiswechsel.core

import java.time.Duration
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class RunsTest {
    // Made-up stations (the name is the id) and trains, as SearchTest.
    private fun at(minute: Int) = OffsetDateTime.parse("2026-03-03T00:00:00+01:00").plusMinutes(minute.toLong())
    private fun stop(station: String, minute: Int) = Stop(station, station, at(minute))

    /** Aach at [hhmm] → Bstadt [minutes] later; with [change], at Xberg halfway. */
    private fun journey(hhmm: String, minutes: Int = 30, first: String = "S1", change: String? = "RE3"): Connection {
        val leaving = hhmm.substringBefore(':').toInt() * 60 + hhmm.substringAfter(':').toInt()
        val end = leaving + minutes
        return Connection(
            if (change == null) listOf(Leg(first, stop("Aach", leaving), stop("Bstadt", end)))
            else listOf(Leg(first, stop("Aach", leaving), stop("Xberg", leaving + minutes / 2 - 4)), Leg(change, stop("Xberg", leaving + minutes / 2), stop("Bstadt", end)))
        )
    }

    private fun Runs.text() = "${journeys.first().departure.time.toLocalTime()}–${journeys.last().departure.time.toLocalTime()}, " +
        "${journeys.size} times, every ${every?.toMinutes()}"

    @Test
    fun everyThirtyMinutes() {
        val day = (6..20).flatMap { listOf(journey("$it:12"), journey("$it:42")) }.dropLast(1)
        assertEquals(listOf("06:12–20:12, 29 times, every 30"), runs(day).map { it.text() })
    }

    @Test
    fun aGap() {
        // 07:12 and 07:42 don't run: still every 30 minutes, 2 runs fewer.
        val day = (6..9).flatMap { listOf(journey("$it:12"), journey("$it:42")) }.filter { it.departure.time.hour != 7 }
        assertEquals(listOf("06:12–09:42, 6 times, every 30"), runs(day).map { it.text() })
        // As common as 60: the shorter. Then every 2 hours, the regional lines' interval.
        val twice = listOf("06:00", "06:30", "07:00", "08:00", "09:00").map { journey(it) }
        assertEquals("06:00–09:00, 5 times, every 30", runs(twice).single().text())
        assertEquals("06:05–10:05, 3 times, every 120", runs(listOf(6, 8, 10).map { journey("$it:05") }).single().text())
    }

    @Test
    fun irregular() {
        assertEquals("06:00–07:05, 3 times, every null", runs(listOf(journey("06:00"), journey("06:20"), journey("07:05"))).single().text())
        assertEquals("06:00–06:00, 1 times, every null", runs(listOf(journey("06:00"))).single().text())
        // A gap that comes once: 2 runs, or 3 with two gaps as Zürich HB → Chur's IR35 05:12–22:12.
        assertEquals("06:00–07:00, 2 times, every null", runs(listOf(journey("06:00"), journey("07:00"))).single().text())
        assertEquals("05:12–22:12, 3 times, every null", runs(listOf(journey("05:12"), journey("06:12"), journey("22:12"))).single().text())
        // The most common gap less than half of all, as Bern → Thun's IC61: 30 minutes 5 times of 12, 90 and 120 3 times each.
        val shared = listOf(0, 30, 60, 150, 180, 300, 390, 420, 540, 570, 660, 780, 930).map { journey("%d:%02d".format(6 + it / 60, it % 60)) }
        assertEquals("06:00–21:30, 13 times, every null", runs(shared).single().text())
    }

    @Test
    fun theSameConnection() {
        val day = listOf(
            journey("06:00"),
            journey("06:30", first = "S2"), // another line
            journey("07:00", minutes = 31), // another trip time
            journey("07:30", first = "IC000484", change = null), // straight through
            journey("08:00"),
            journey("08:30", first = "IC000488", change = null), // the same category
            journey("09:00"),
        )
        assertEquals(
            listOf("06:00–09:00, 3 times, every null", "06:30–06:30, 1 times, every null", "07:00–07:00, 1 times, every null", "07:30–08:30, 2 times, every null"),
            runs(day).map { it.text() },
        )
        // A change elsewhere is another connection.
        val other = Connection(listOf(Leg("S1", stop("Aach", 540), stop("Yfeld", 550)), Leg("RE3", stop("Yfeld", 555), stop("Bstadt", 570))))
        assertEquals(2, runs(listOf(journey("08:00"), other)).size)
        // Each journey keeps its name.
        assertEquals("IC000488", runs(day).last().journeys.last().legs.single().train)
    }

    @Test
    fun theFastest() {
        // As Horw → Sursee: 33 minutes once at 05:14 and every hour from 05:53; equally fast, the more often.
        val hourly = (5..22).map { journey("$it:53", 33) }
        val once = journey("05:14", 33, change = "IR27")
        val slower = (6..9).map { journey("$it:00", 40, change = null) }
        assertEquals(listOf("05:53–22:53, 18 times, every 60"), fastest(runs((listOf(once) + hourly + slower).sortedBy { it.departure.time })).map { it.text() })
        // The fastest irregular, then the fastest regular.
        assertEquals(
            listOf("05:14–05:14, 1 times, every null", "06:00–09:00, 4 times, every 60"),
            fastest(runs(listOf(once) + slower)).map { it.text() },
        )
        assertEquals(listOf("05:14–05:14, 1 times, every null"), fastest(runs(listOf(once))).map { it.text() })
        assertEquals(emptyList(), fastest(runs(emptyList())))
    }
}

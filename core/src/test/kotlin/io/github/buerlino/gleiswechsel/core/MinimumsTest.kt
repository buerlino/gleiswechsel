package io.github.buerlino.gleiswechsel.core

import java.time.Duration
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class MinimumsTest {
    // Made-up stations, in the file's format and line ends.
    private val minimums = Minimums("9999999 02 02 STANDARD\r\n1234567 05 05 Xberg\r\n7654321 00 00 Yfeld, Platz\r\n")

    private fun stop(id: String, hhmm: String = "08:00") = Stop(id, id, OffsetDateTime.parse("2026-03-03T$hhmm:00+01:00"))
    private fun at(id: String) = minimums.at(stop(id))

    @Test
    fun aListedStationHasItsOwn() {
        assertEquals(Duration.ofMinutes(5), at("1234567"))
        assertEquals(Duration.ZERO, at("7654321"))
    }

    @Test
    fun everyOtherStationHasTheStandard() = assertEquals(Duration.ofMinutes(2), at("1111111"))

    @Test
    fun theLowerOfTheTableAndThePlannersShortestChange() {
        // The planner changes at Xberg in 4 and 6 minutes (the table: 5), at 1111111 in 3 (the standard: 2).
        fun trip(change: String, arrive: String, leave: String) = Connection(listOf(
            Leg("S1", stop("Aach", "07:50"), stop(change, arrive)),
            Leg("IR2", stop(change, leave), stop("Bstadt", "08:40")),
        ))
        val shortest = shortestChanges(listOf(trip("1234567", "08:10", "08:14"), trip("1234567", "08:20", "08:26"), trip("1111111", "08:10", "08:13")))
        assertEquals(mapOf("1234567" to 4L, "1111111" to 3L), shortest)
        val lowered = minimums.lowered(shortest)
        assertEquals(Duration.ofMinutes(4), lowered.at(stop("1234567")))
        assertEquals(Duration.ofMinutes(2), lowered.at(stop("1111111")))
        assertEquals(Duration.ZERO, lowered.at(stop("7654321")))
    }
}

package io.github.buerlino.gleiswechsel.core

import java.time.Duration
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class MinimumsTest {
    // Made-up stations, in the file's format and line ends.
    private val minimums = Minimums("9999999 02 02 STANDARD\r\n1234567 05 05 Xberg\r\n7654321 00 00 Yfeld, Platz\r\n")

    private fun at(id: String) = minimums.at(Stop(id, id, OffsetDateTime.parse("2026-03-03T08:00:00+01:00")))

    @Test
    fun aListedStationHasItsOwn() {
        assertEquals(Duration.ofMinutes(5), at("1234567"))
        assertEquals(Duration.ZERO, at("7654321"))
    }

    @Test
    fun everyOtherStationHasTheStandard() = assertEquals(Duration.ofMinutes(2), at("1111111"))
}

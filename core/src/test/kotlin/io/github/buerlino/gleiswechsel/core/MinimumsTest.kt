package io.github.buerlino.gleiswechsel.core

import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class MinimumsTest {
    // Made-up stations, in the file's format and line ends.
    private val minimums = Minimums("9999999 02 02 STANDARD\r\n1234567 05 05 Xberg\r\n7654321 00 00 Yfeld, Platz\r\n")

    @Test
    fun aListedStationHasItsOwn() {
        assertEquals(5, minimums.at("1234567"))
        assertEquals(0, minimums.at("7654321"))
    }

    @Test
    fun everyOtherStationHasTheStandard() = assertEquals(2, minimums.at("1111111"))

    @Test
    fun theDefaultIsTheOfficialMinusTheOffsetAtLeast0() {
        val times = TrackSwitchTimes(minimums, 1) { null }
        assertEquals(5, times.official("1234567"))
        assertEquals(4, times.default("1234567"))
        assertEquals(1, times.default("1111111"))
        assertEquals(0, times.default("7654321"))
        assertEquals(0, TrackSwitchTimes(minimums, 6) { null }.default("1234567"))
    }

    @Test
    fun theRidersTimeIsTheSetOneElseTheDefault() {
        val times = TrackSwitchTimes(minimums, 1, mapOf("1234567" to 7L)::get)
        assertEquals(7, times.rider("1234567"))
        assertEquals(1, times.rider("1111111"))
    }

    @Test
    fun aChangeThePlannerMakesIsNeverBelowPerChange() {
        // At Xberg (the table: 5) the planner itself changes in 4 and in 6.
        fun change(arrive: String, leave: String) =
            Change("1234567", OffsetDateTime.parse("2026-03-03T$arrive:00+01:00"), OffsetDateTime.parse("2026-03-03T$leave:00+01:00"))
        val inFour = change("08:15", "08:19")
        val inSix = change("08:30", "08:36")
        val offered = setOf(inFour, inSix)
        val times = TrackSwitchTimes(minimums, 1) { null }
        assertEquals(4, times.official(inFour, offered)) // 4 against 4: the same
        assertEquals(5, times.official(inSix, offered)) // 6 against 5: above
        // Another change there in 4, not the planner's: below its station's 5.
        assertEquals(5, times.official(change("09:01", "09:05"), offered))
        assertEquals(5, times.official("1234567"))
    }
}

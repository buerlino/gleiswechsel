package io.github.buerlino.gleiswechsel.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The test case in CLAUDE.md (Horw → Sursee), asked live from transport.opendata.ch for the next
 * weekday. Not in CI: runs only with `./gradlew :core:test -Plive` (core/build.gradle.kts).
 */
class LiveTest {
    private val day = generateSequence(LocalDate.now(ZoneId.of("Europe/Zurich")).plusDays(1)) { it.plusDays(1) }
        .first { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }

    private fun search(hour: Int, transfer: Long): List<Find> {
        var calls = 1
        val official = connections("Horw", "Sursee", day.atTime(hour, 50), "test")
        val finds = search(official, { Duration.ofMinutes(transfer) }) { from, to, time ->
            calls++
            connections(from, to, time, "test")
        }
        println("$day $hour:50, $transfer min, $calls requests: ${finds.size} found")
        finds.forEach { println("  official ${it.official.text()}\n  faster   ${it.faster.text()}\n  saved ${it.saved.toMinutes()} min") }
        return finds
    }

    private fun Connection.text() = legs.joinToString(" | ") {
        "${it.train ?: "walk"} ${it.departure.text()} → ${it.arrival.text()}"
    }

    private fun Stop.text() = "$station ${time.toLocalTime()}" + (platform?.let { " ($it)" } ?: "")

    private fun findsTheHiddenChange(hour: Int) {
        val find = search(hour, 4).single { it.official.departure.time.toLocalTime() == LocalTime.of(hour, 53) }
        assertEquals(listOf("S4", "RE24"), find.faster.legs.map { it.train })
        assertEquals("Horw", find.faster.departure.station)
        assertEquals("Luzern", find.arrival.station)
        assertEquals(LocalTime.of(hour + 1, 1), find.arrival.time.toLocalTime())
        assertEquals(LocalTime.of(hour + 1, 5), find.departure.time.toLocalTime())
        assertEquals("9", find.departure.platform)
        assertEquals(LocalTime.of(hour + 1, 26), find.faster.arrival.time.toLocalTime())
        assertEquals(LocalTime.of(hour + 1, 40), find.official.arrival.time.toLocalTime())
        assertEquals(Duration.ofMinutes(14), find.saved)
    }

    @Test
    fun fourMinutesAtLuzernIs14MinutesFaster() = findsTheHiddenChange(8)

    @Test
    fun sixMinutesFindNothing() = assertEquals(emptyList(), search(8, 6))

    @Test
    fun itRepeatsEveryHour() = findsTheHiddenChange(14)
}

package io.github.buerlino.gleiswechsel.core

import java.io.File
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The test case in CLAUDE.md (Horw → Sursee), asked live from transport.opendata.ch for the next
 * weekday; the full search on the timetable file in `private/gtfs/`, which must have that day. Not
 * in CI: runs only with `./gradlew :core:test -Plive` (core/build.gradle.kts).
 */
class LiveTest {
    private val day = generateSequence(LocalDate.now(ZoneId.of("Europe/Zurich")).plusDays(1)) { it.plusDays(1) }
        .first { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }

    private fun search(hour: Int, transfer: Long): List<Find> {
        var calls = 1
        val official = connections("Horw", "Sursee", day.atTime(hour, 50), "test")
        val searched = search(official, { Duration.ofMinutes(transfer) }) { from, to, time ->
            calls++
            connections(from, to, time, "test")
        }
        // A skipped change would make "nothing found" a false result.
        searched.failures.firstOrNull()?.let { throw it }
        val finds = searched.finds
        println("$day $hour:50, $transfer min, $calls requests: ${finds.size} found")
        finds.forEach { println("  official ${it.official.text()}\n  faster   ${it.faster.text()}\n  saved ${it.saved.toMinutes()} min") }
        return finds
    }

    private fun Connection.text() = legs.joinToString(" | ") {
        "${it.train ?: "walk"} ${it.departure.text()} → ${it.arrival.text()}"
    }

    private fun Stop.text() = "$station ${time.toLocalTime()}" + (platform?.let { " ($it)" } ?: "")

    // The full search with the app's defaults (the official minimums minus 1) or [luzern] minutes there.
    private val timetable by lazy {
        val start = System.nanoTime()
        File("../private/gtfs/timetable.bin.gz").inputStream().use { timetable(it) }
            .also { println("${it.source}, from ${it.first}: read in ${(System.nanoTime() - start) / 1_000_000} ms") }
    }

    private fun fullSearch(hour: Int, luzern: Long? = null): List<Find> {
        val official = connections("Horw", "Sursee", day.atTime(hour, 50), "test")
        val times = TrackSwitchTimes(Minimums(File("../app/src/main/res/raw/umsteigb.txt").readText()), 1) { id -> luzern?.takeIf { id == "8505000" } }
        val start = System.nanoTime()
        val finds = fullSearch(timetable, official) { Duration.ofMinutes(times.rider(it.id)) }
        println("$day $hour:50, full search, Luzern ${luzern ?: "default"}: ${finds.size} found in ${(System.nanoTime() - start) / 1_000_000} ms")
        finds.forEach { println("  official ${it.official.text()}\n  faster   ${it.faster.text()}\n  saved ${it.saved.toMinutes()} min") }
        return finds
    }

    private fun findsTheHiddenChange(hour: Int, finds: List<Find> = search(hour, 4)) {
        val find = finds.single { it.official.departure.time.toLocalTime() == LocalTime.of(hour, 53) }
        val (s4, re24) = find.faster.legs
        assertEquals(listOf("S4", "RE24"), find.faster.legs.map { it.train })
        assertEquals("Horw", s4.departure.station)
        assertEquals("Luzern", s4.arrival.station)
        assertEquals(LocalTime.of(hour + 1, 1), s4.arrival.time.toLocalTime())
        assertEquals(LocalTime.of(hour + 1, 5), re24.departure.time.toLocalTime())
        assertEquals("9", re24.departure.platform)
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

    @Test
    fun theFullSearchFindsItWithTheDefaults() = findsTheHiddenChange(8, fullSearch(8))

    @Test
    fun theFullSearchWithFiveMinutesAtLuzernMissesIt() =
        assertEquals(false, fullSearch(8, luzern = 5).any { find -> find.faster.legs.any { it.train == "RE24" && it.departure.id == "8505000" } })
}

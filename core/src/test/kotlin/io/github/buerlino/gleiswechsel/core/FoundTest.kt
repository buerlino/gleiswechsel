package io.github.buerlino.gleiswechsel.core

import java.time.LocalDateTime
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FoundTest {
    // Made-up stations (the name is the id) and trains.
    private fun stop(station: String, hhmm: String, platform: String? = null) =
        Stop(station, station, OffsetDateTime.parse("2026-03-03T$hhmm:00+01:00"), platform)

    // Aach → Xberg, official change to the IR2; the find changes to the RE3, after a walk.
    private val s1 = Leg("S1", stop("Aach", "08:00", "2"), stop("Xberg", "08:10", "1"), via = listOf("Wil", "Zell"))
    private val official = Connection(listOf(s1, Leg("IR2", stop("Xberg", "08:20", "3"), stop("Bstadt", "08:40"))))
    private val faster = Connection(listOf(
        s1,
        Leg(null, stop("Xberg", "08:10"), stop("Xberg, Bahnhof", "08:12")),
        Leg("RE3", stop("Xberg, Bahnhof", "08:14", "9"), stop("Bstadt", "08:30")),
    ))
    private val found = Found(
        LocalDateTime.parse("2026-03-03T07:55"),
        official,
        listOf(stop("Xberg", "08:10", "1")),
        listOf(Find(official, faster)),
        mapOf("Xberg" to 4L),
        incomplete = true,
    )

    @Test
    fun writtenAndReadBackIsTheSame() = assertEquals(found, found(found.toJson()))

    @Test
    fun timesAreText() = assert(""""time":"2026-03-03T08:14+01:00"""" in found.toJson())

    @Test
    fun anotherFormatThrows() {
        assertFailsWith<Exception> { found("""{"day":"2026-03-03T07:55"}""") }
    }
}

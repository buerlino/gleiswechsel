package io.github.buerlino.gleiswechsel.core

import java.time.Duration

/**
 * The official minimum transfer time per station, from the national timetable: HRDF `UMSTEIGB` lines
 * like `8505000 05 05 Luzern` (station id, the minutes twice, the name). `9999999` is the standard at
 * every station not listed.
 */
class Minimums(umsteigb: String) {
    private val minutes = umsteigb.lines().filter { it.isNotBlank() }.associate { line ->
        line.split(' ').let { it[0] to it[1].toLong() }
    }
    private val standard = minutes.getValue("9999999")

    fun at(station: Stop): Duration = Duration.ofMinutes(minutes[station.id] ?: standard)
}

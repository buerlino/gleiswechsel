package io.github.buerlino.gleiswechsel.core

/**
 * The official minimum transfer time per station, from the national timetable: HRDF `UMSTEIGB` lines
 * like `8505000 05 05 Luzern` (station id, the minutes twice, the name). `9999999` is the standard at
 * every station not listed.
 */
class Minimums(umsteigb: String) {
    private val minutes = umsteigb.lines().filter { it.isNotBlank() }.associate { line -> line.split(' ').let { it[0] to it[1].toLong() } }

    private val standard = minutes.getValue("9999999")

    /** The minutes at [station] (its id). */
    fun at(station: String): Long = minutes[station] ?: standard
}

/**
 * The track switch times, the same in every search and in both searches (research/harmonize.md,
 * 2026-10-07), each by station id: the [official] one is the table's (D1); the [default], where the
 * rider hasn't set one, the official one minus the [offset], at least 0 (D2); the [rider]'s, the one
 * [set] there, else the default.
 */
class TrackSwitchTimes(private val minimums: Minimums, private val offset: Long, private val set: (station: String) -> Long?) {
    fun official(station: String): Long = minimums.at(station)

    fun default(station: String): Long = maxOf(official(station) - offset, 0)

    fun rider(station: String): Long = set(station) ?: default(station)

    /**
     * The official time [change] is coloured against: its station's; where the planner itself makes
     * it (it's in [offered], [Found.offered]), at most its own minutes, so it's never below (D4). Per
     * change, not per station: the planner's S1 → S41 in 4 at Luzern leaves Luzern 5 for the S4 → RE24.
     */
    fun official(change: Change, offered: Set<Change>): Long =
        official(change.station).let { if (change in offered) minOf(change.minutes, it) else it }
}

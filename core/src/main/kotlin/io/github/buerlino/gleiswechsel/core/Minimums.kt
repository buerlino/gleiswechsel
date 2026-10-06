package io.github.buerlino.gleiswechsel.core

import java.time.Duration

/**
 * The official minimum transfer time per station, from the national timetable: HRDF `UMSTEIGB` lines
 * like `8505000 05 05 Luzern` (station id, the minutes twice, the name). `9999999` is the standard at
 * every station not listed.
 */
class Minimums private constructor(private val minutes: Map<String, Long>) {
    constructor(umsteigb: String) : this(
        umsteigb.lines().filter { it.isNotBlank() }.associate { line -> line.split(' ').let { it[0] to it[1].toLong() } },
    )

    private val standard = minutes.getValue("9999999")

    fun at(station: Stop): Duration = Duration.ofMinutes(minutes[station.id] ?: standard)

    /**
     * Lowered to [shortest] ([shortestChanges]) where the planner itself changes faster (user,
     * 2026-10-06): it has finer times than the table, per track, not published (Olten: RE24 → IR16
     * in 4 minutes, the table says 5; HRDF's times per train pair, line and operator have nothing
     * there).
     */
    fun lowered(shortest: Map<String, Long>): Minimums =
        Minimums(minutes + shortest.mapValues { (id, offered) -> minOf(offered, minutes[id] ?: standard) })
}

/** The shortest change the planner makes in [offered] at each station: its id → minutes. */
fun shortestChanges(offered: List<Connection>): Map<String, Long> =
    offered.flatMap { it.transfers }.groupBy({ it.first.id }, { it.second.toMinutes() }).mapValues { it.value.min() }

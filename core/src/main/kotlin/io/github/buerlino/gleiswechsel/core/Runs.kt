package io.github.buerlino.gleiswechsel.core

import kotlinx.serialization.Serializable
import java.time.Duration

/**
 * One connection over a day (the fastest of the day, CLAUDE.md): its [journeys], on the same lines
 * through the same change stations, as long to the minute, in the order they leave.
 */
@Serializable
data class Runs(val journeys: List<Connection>) {
    val duration: Duration get() = journeys.first().duration

    /**
     * How often it runs: the most common gap between two departures (of two as common, the shorter)
     * if it's 15, 30, 60 or 120 minutes (Claude, 2026-10-07: regional lines often run every 2
     * hours), comes at least twice (user, 2026-10-07: 2 runs, or 3 with two gaps, aren't a train
     * to build a day around) and is at least half of all gaps (user, 2026-10-08: Bern → Thun's IC61,
     * 30 minutes 5 times of 12, read "every 30" over 13 runs); else null, irregular. A gap shows as
     * fewer [journeys] than the first to the last departure at that interval.
     */
    val every: Duration? get() {
        val gaps = journeys.zipWithNext { a, b -> Duration.between(a.departure.time, b.departure.time) }
        val (gap, count) = gaps.sorted().groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return null
        return gap.takeIf { count >= 2 && 2 * count >= gaps.size && it in regular }
    }
}

private val regular = listOf(15L, 30, 60, 120).map(Duration::ofMinutes)

/**
 * The [journeys] of [daySearch] as the [Runs] of each connection: the same lines, the same change
 * stations and the same trip time to the minute. A train named by its number (IC000484) counts by
 * its category (IC), else each run would be a connection of its own; the journeys keep the name.
 * In the order their first runs leave.
 */
fun runs(journeys: List<Connection>): List<Runs> =
    journeys.groupBy { c -> Triple(c.legs.map { it.train?.let(::line) }, c.changes.map { it.id }, c.duration.toMinutes()) }
        .values.map(::Runs)

private fun line(train: String) = numbered.matchEntire(train)?.groupValues?.get(1) ?: train

private val numbered = Regex("(\\D+)\\d{6}")

/**
 * The cards of the fastest of the day (CLAUDE.md): the fastest of the [runs] and, if it isn't
 * regular, the fastest regular one. Equally fast: the one running more often.
 */
fun fastest(runs: List<Runs>): List<Runs> {
    val order = compareBy<Runs>({ it.duration }, { -it.journeys.size })
    val fastest = runs.minWithOrNull(order) ?: return emptyList()
    return listOfNotNull(fastest, if (fastest.every == null) runs.filter { it.every != null }.minWithOrNull(order) else null)
}

/**
 * A card of the fastest of the day: a connection's [runs], the card's [trip] (its first run), the
 * [official] connection it's compared with (the planner's leaving then) and whether the planner
 * [offered] the trip (then the page says so instead of how much more efficient it is).
 */
@Serializable
data class Fastest(val runs: Runs, val official: Connection, val offered: Boolean) {
    val trip: Connection get() = runs.journeys.first()

    /** [Find.moreEfficient]: the [official] connection's time / the [trip]'s − 1. */
    val moreEfficient: Double get() = Find(official, trip).moreEfficient
}

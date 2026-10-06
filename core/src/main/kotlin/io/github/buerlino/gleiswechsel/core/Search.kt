package io.github.buerlino.gleiswechsel.core

import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime

/**
 * A connection that reaches B before [official]: [faster] rides the official connection to a
 * change station and changes there, quicker than the official minimum transfer time but long
 * enough for the rider.
 */
data class Find(val official: Connection, val faster: Connection) {
    val saved: Duration get() = Duration.between(faster.arrival.time, official.arrival.time)

    /**
     * How much more efficient than [official] (user, 2026-10-06): its time / this one's − 1, each
     * from the first departure to the last arrival. 33 minutes instead of 47: 0.42, 42% more. Same
     * from and to, so it's also how much faster [faster] goes.
     */
    val moreEfficient: Double get() = official.duration.toSeconds().toDouble() / faster.duration.toSeconds() - 1
}

/** The request: connections from, to, leaving at or after a time (Swiss local time). */
typealias Connections = (from: String, to: String, time: LocalDateTime) -> List<Connection>

/**
 * The local search (research/architecture.md). For each of the [officials] (the official A → B) and
 * each station where it changes trains, asks [connections] for the connections from there to B
 * that the rider can still catch with their [transfer] time at that station, and keeps the one
 * arriving first if it reaches B earlier. One request per change, and one more per stop the API
 * walks to (see [onward]). A find the planner already offers (an official connection leaves no earlier and
 * arrives no later) isn't one, nor is a find another find beats the same way; of identical trips,
 * the one against the official connection arriving first, so the saving isn't overstated. The
 * finds come in the order they leave (none beats another, so a later one also arrives later).
 */
fun search(
    officials: List<Connection>,
    transfer: (station: Stop) -> Duration,
    connections: Connections,
): List<Find> = officials.flatMap { official ->
    official.changes.mapNotNull { change ->
        val onward = onward(change.id, official.arrival.id, change.time + transfer(change), connections)
        if (onward == null || !onward.arrival.time.isBefore(official.arrival.time)) return@mapNotNull null
        val upToChange = official.legs.take(official.legs.indexOfFirst { it.arrival == change } + 1)
        Find(official, Connection(upToChange + onward.legs))
    }
}.filter { find -> officials.none { it.noWorseThan(find.faster) } }
    .sortedWith(compareBy<Find> { it.faster.arrival.time }.thenByDescending { it.faster.departure.time }.thenBy { it.official.arrival.time })
    .fold(emptyList()) { kept, find -> if (kept.any { it.faster.noWorseThan(find.faster) }) kept else kept + find }

/** Leaves no earlier and arrives no later than [other], so [other] says nothing new. */
private fun Connection.noWorseThan(other: Connection) =
    !departure.time.isBefore(other.departure.time) && !arrival.time.isAfter(other.arrival.time)

/**
 * The connection from [from] to [to] leaving at or after [ready] that arrives first; of two
 * arriving together, the later one (more time to change).
 *
 * The rider's transfer time covers the whole change, walks included. Where the API starts a
 * connection with a walk to another stop (Zürich HB → Bahnhofplatz/HB, 5 minutes), it would add
 * the walk on top, so the rides from that stop are asked for again from [ready].
 */
private fun onward(from: String, to: String, ready: OffsetDateTime, connections: Connections): Connection? {
    val at = ready.toLocalDateTime()
    val (walkFirst, others) = connections(from, to, at).partition { it.legs.size > 1 && it.legs.first().train == null }
    val fromStops = walkFirst.map { it.legs.first().arrival.id }.distinct().flatMap { connections(it, to, at) }
    return (others + fromStops)
        .filter { !it.departure.time.isBefore(ready) }
        .minWithOrNull(compareBy<Connection> { it.arrival.time }.thenByDescending { it.departure.time })
}

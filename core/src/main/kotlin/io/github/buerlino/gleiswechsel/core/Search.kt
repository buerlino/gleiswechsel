package io.github.buerlino.gleiswechsel.core

import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime

/**
 * A connection that reaches B before [official]: [faster] rides the official connection to the
 * change station, gets off at [arrival] and boards at [departure], a change shorter than the
 * official minimum transfer time but long enough for the rider.
 */
data class Find(val official: Connection, val faster: Connection, val arrival: Stop, val departure: Stop) {
    val saved: Duration get() = Duration.between(faster.arrival.time, official.arrival.time)
}

/** The request: connections from, to, leaving at or after a time (Swiss local time). */
typealias Connections = (from: String, to: String, time: LocalDateTime) -> List<Connection>

/**
 * The local search (research/architecture.md). For each of the [officials] (the official A → B) and
 * each station where it changes trains, asks [connections] for the connections from there to B
 * that the rider can still catch with their [transfer] time at that station, and keeps the one
 * arriving first if it reaches B earlier. One request per change (two where the API walks, see
 * [onward]). A find the planner already offers (an official connection leaves no earlier and
 * arrives no later) isn't one.
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
        Find(official, Connection(upToChange + onward.legs), change, onward.departure)
    }
}.filter { find ->
    officials.none { !it.departure.time.isBefore(find.faster.departure.time) && !it.arrival.time.isAfter(find.faster.arrival.time) }
}

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

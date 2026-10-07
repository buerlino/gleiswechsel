package io.github.buerlino.gleiswechsel.core

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * A connection that reaches B before [official]: [faster] rides the official connection to a
 * change station and changes there, quicker than the official minimum transfer time but long
 * enough for the rider, and maybe again at a later change ([search]); or takes any trains, leaving
 * no earlier ([fullSearch]).
 */
@Serializable
data class Find(val official: Connection, val faster: Connection) {
    val saved: Duration get() = Duration.between(faster.arrival.time, official.arrival.time)

    /**
     * How much more efficient than [official] (user, 2026-10-06): its time / this one's − 1, each
     * from the first departure to the last arrival. 33 minutes instead of 47: 0.42, 42% more. Same
     * from and to, so it's also how much faster [faster] goes.
     */
    val moreEfficient: Double get() = official.duration.toSeconds().toDouble() / faster.duration.toSeconds() - 1
}

/**
 * sbb.ch's timetable [from] → [to] at [from]'s time, in [language] (`de`, `en`, `fr`, `it`), to buy
 * the ticket there. Undocumented: the format sbb.ch uses itself (checked 2026-10-06). The station
 * ids are enough, sbb.ch fills in the names.
 */
fun ticketUrl(from: Stop, to: Stop, language: String): String =
    "https://www.sbb.ch/$language?stops=_I${from.id}~_I${to.id}" +
        "&day=${from.time.toLocalDate()}&time=${from.time.format(sbbTime)}&moment=dep"

private val sbbTime = DateTimeFormatter.ofPattern("HH_mm")

/** The request: connections from, to, leaving at or after a time (Swiss local time). */
typealias Connections = (from: String, to: String, time: LocalDateTime) -> List<Connection>

/**
 * The local search (research/architecture.md). For each of the [officials] (the official A → B),
 * the trips that change faster at one or more of its changes ([shortened]); a find if it reaches B
 * earlier. Then [best].
 *
 * [connections] is asked each question once: two official connections on the same train to the same
 * change would ask it twice, and the API answers too many questions with HTTP 429. A change whose
 * question fails is skipped, the others go on; its error is in [Searched.failures].
 */
fun search(
    officials: List<Connection>,
    transfer: (station: Stop) -> Duration,
    connections: Connections,
): Searched {
    val answers = HashMap<Triple<String, String, LocalDateTime>, Result<List<Connection>>>()
    val ask: Connections = { from, to, time ->
        answers.getOrPut(Triple(from, to, time)) { runCatching { connections(from, to, time) } }.getOrThrow()
    }
    val asked = mutableListOf<Stop>()
    val failures = mutableListOf<Exception>()
    val finds = officials.flatMap { official ->
        shortened(official, official.arrival.time, { stop -> asked += stop; transfer(stop) }, ask, failures)
            .filter { it.arrival.time.isBefore(official.arrival.time) }.map { Find(official, it) }
    }
    val offered = (officials + answers.values.flatMap { it.getOrDefault(emptyList()) }).flatMap { it.transfers }.toSet()
    return Searched(best(finds, officials), failures, asked.distinctBy { it.id }, offered)
}

/**
 * The [finds] worth showing, of either search: not one the planner already offers (an official
 * connection leaves no earlier and arrives no later), nor one another find beats the same way; of
 * identical trips, the one against the official connection arriving first, so the saving isn't
 * overstated, and of two the same against it, the first given. In the order they leave (none beats
 * another, so a later one also arrives later).
 */
fun best(finds: List<Find>, officials: List<Connection>): List<Find> =
    finds.filter { find -> officials.none { it.noWorseThan(find.faster) } }
        .sortedWith(compareBy<Find> { it.faster.arrival.time }.thenByDescending { it.faster.departure.time }.thenBy { it.official.arrival.time })
        .fold(emptyList()) { kept, find -> if (kept.any { it.faster.noWorseThan(find.faster) }) kept else kept + find }

/**
 * What [search] found, the errors of the changes it couldn't check (e.g. HTTP 429), the stations it
 * changed at (Optimization's rows, D3), each once, in the order it asked, and the changes the planner
 * itself makes in its answers, the official connections' and the onward ones' ([offered], D4).
 */
class Searched(val finds: List<Find>, val failures: List<Exception>, val changes: List<Stop>, val offered: Set<Change>)

/**
 * [trip] changing faster: at each of its changes, with the rider's [transfer] time there, the
 * connection on to B that arrives first ([onward]); and the same again at that connection's own
 * changes, where the API keeps the official minimum (user, 2026-10-06: two short changes on one
 * trip, e.g. Luzern and Olten). One request per change searched. A change reached at or after
 * [deadline], the official connection's arrival, isn't searched: nothing from there arrives earlier
 * (an onward connection back and forth between two stops, an hour later each time, went on until
 * the next day: Bern, Bundesplatz → Horw, 2026-10-07). A change whose request fails gives nothing;
 * the error goes to [failures].
 */
private fun shortened(
    trip: Connection,
    deadline: OffsetDateTime,
    transfer: (Stop) -> Duration,
    connections: Connections,
    failures: MutableList<Exception>,
): List<Connection> =
    trip.changes.filter { it.time.isBefore(deadline) }.flatMap { change ->
        val rest = try {
            onward(change.id, trip.arrival.id, change.time + transfer(change), connections)
        } catch (e: Exception) {
            failures += e
            null
        } ?: return@flatMap emptyList()
        val upToChange = trip.legs.take(trip.legs.indexOfFirst { it.arrival == change } + 1)
        (listOf(rest) + shortened(rest, deadline, transfer, connections, failures)).map { Connection(upToChange + it.legs) }
    }

/** Leaves no earlier and arrives no later than [other], so [other] says nothing new. */
private fun Connection.noWorseThan(other: Connection) =
    !departure.time.isBefore(other.departure.time) && !arrival.time.isAfter(other.arrival.time)

/**
 * The connection from [from] to [to] leaving at or after [ready] that arrives first; of two
 * arriving together, the later one (more time to change).
 *
 * Only a ride from [from] itself (user, 2026-10-07): the rider's time there is for a change within
 * the station, as in [fullSearch]. Not one that starts with a walk, nor one from a stop nearby that
 * the API offers too (Zürich HB → a tram at Bahnhofstrasse/HB).
 */
private fun onward(from: String, to: String, ready: OffsetDateTime, connections: Connections): Connection? =
    connections(from, to, ready.toLocalDateTime())
        .filter { it.legs.first().train != null && it.departure.id == from && !it.departure.time.isBefore(ready) }
        .minWithOrNull(compareBy<Connection> { it.arrival.time }.thenByDescending { it.departure.time })

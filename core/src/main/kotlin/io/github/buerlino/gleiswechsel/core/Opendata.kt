package io.github.buerlino.gleiswechsel.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** Where a leg starts or ends: the station, the planned time there and the platform, if known. */
data class Stop(val station: String, val id: String, val time: OffsetDateTime, val platform: String? = null)

/**
 * One ride of a connection ([train] e.g. `S4`, `RE24`), or a walk ([train] null). [via]: the ids of
 * the stations a ride passes between its departure and its arrival.
 */
data class Leg(val train: String?, val departure: Stop, val arrival: Stop, val via: List<String> = emptyList())

/** One connection, as the official planner offers it. */
data class Connection(val legs: List<Leg>) {
    val departure: Stop get() = legs.first().departure
    val arrival: Stop get() = legs.last().arrival

    /** From the first departure to the last arrival; the wait before the first train doesn't count. */
    val duration: Duration get() = Duration.between(departure.time, arrival.time)

    /**
     * Where it changes trains and the time it has there: from the end of each ride but the last to
     * the next ride's departure. A walk is part of a change, not one of its own.
     */
    val transfers: List<Pair<Stop, Duration>>
        get() = legs.filter { it.train != null }.zipWithNext { a, b -> a.arrival to Duration.between(a.arrival.time, b.departure.time) }

    val changes: List<Stop> get() = transfers.map { it.first }

    /**
     * Passes a station twice, e.g. rides past a station and back to change there. Such a trip may
     * need another ticket (research/hidden_connections.md).
     */
    val doublesBack: Boolean
        get() = legs.flatMap { listOf(it.departure.id) + it.via + it.arrival.id }
            .fold(emptyList<String>()) { ids, id -> if (ids.lastOrNull() == id) ids else ids + id }
            .let { it.size != it.toSet().size }
}

/**
 * Blocking fetch of the official connections [from] → [to] (station names or ids) leaving at or
 * after [time], Swiss local time; the API sends four. [version] goes in the User-Agent, so the
 * operators can see which client calls them and reach the project. Call off the main thread.
 * Throws on network or format errors.
 */
fun connections(from: String, to: String, time: LocalDateTime, version: String): List<Connection> {
    val conn = URI(connectionsUrl(from, to, time)).toURL().openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "Gleiswechsel/$version (+https://github.com/buerlino/gleiswechsel)")
        if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
        return parseConnections(conn.inputStream.bufferedReader().use { it.readText() })
    } finally {
        conn.disconnect()
    }
}

internal fun connectionsUrl(from: String, to: String, time: LocalDateTime): String =
    "https://transport.opendata.ch/v1/connections?from=${encode(from)}&to=${encode(to)}" +
        "&date=${time.toLocalDate()}&time=${time.toLocalTime().format(hourMinute)}"

private fun encode(s: String) = URLEncoder.encode(s, Charsets.UTF_8)

private val hourMinute = DateTimeFormatter.ofPattern("HH:mm")

/** The API's times have no colon in the offset: `2026-10-07T08:53:00+0200`. */
private val apiTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXX")

private val json = Json { ignoreUnknownKeys = true }

internal fun parseConnections(body: String): List<Connection> =
    json.decodeFromString<Response>(body).connections.filter { it.sections.isNotEmpty() }.map { c ->
        Connection(c.sections.map { s ->
            Leg(
                train = s.journey?.let { it.category + it.number },
                departure = s.departure.stop(s.departure.departure),
                arrival = s.arrival.stop(s.arrival.arrival),
                via = s.journey?.passList.orEmpty().mapNotNull { it.station.id }.drop(1).dropLast(1),
            )
        })
    }

private fun Checkpoint.stop(time: String?) = Stop(
    station = station.name,
    id = station.id,
    time = OffsetDateTime.parse(time ?: throw IOException("No time at ${station.name}"), apiTime),
    platform = platform,
)

// Only the fields the app uses; the API sends many more.

@Serializable
private class Response(val connections: List<Conn> = emptyList())

@Serializable
private class Conn(val sections: List<Section> = emptyList())

/** A ride has a [journey]; a walk has none. */
@Serializable
private class Section(val journey: Journey? = null, val departure: Checkpoint, val arrival: Checkpoint)

/** [passList]: every stop of the ride, its departure and arrival included. */
@Serializable
private class Journey(val category: String = "", val number: String = "", val passList: List<Pass> = emptyList())

@Serializable
private class Pass(val station: PassStation)

@Serializable
private class PassStation(val id: String? = null)

/** At the start of a section only [departure] is set, at its end only [arrival]. */
@Serializable
private class Checkpoint(
    val station: Station,
    val departure: String? = null,
    val arrival: String? = null,
    val platform: String? = null,
)

@Serializable
private class Station(val id: String, val name: String)

package io.github.buerlino.gleiswechsel.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * A search's result, as the page shows it: the [day] and time asked for (Swiss time), the official
 * connection leaving [first] (null if there are none), the stations the search changed at
 * ([changes], each once: Optimization's rows), the [finds], the changes the planner itself makes in
 * its answers ([offered], [TrackSwitchTimes.official]), whether some changes couldn't be checked
 * ([incomplete]), whether the full search had no timetable file with the day ([noTimetable]), and
 * when it searched ([asOf], Swiss time: when its delays were read).
 *
 * The last one is kept as JSON (user, 2026-10-06), so it's still there when the app opens again,
 * e.g. to read the track on a platform with poor reception.
 */
@Serializable
data class Found(
    @Serializable(with = LocalDateTimeText::class) val day: LocalDateTime,
    val first: Connection?,
    val changes: List<Stop>,
    val finds: List<Find>,
    val offered: Set<Change>,
    val incomplete: Boolean,
    val noTimetable: Boolean,
    @Serializable(with = LocalDateTimeText::class) val asOf: LocalDateTime,
) {
    /**
     * The trips the page shows: each find and the official connection it beats; with nothing faster,
     * the official connection leaving [first].
     */
    val trips: List<Connection> get() = if (finds.isEmpty()) listOfNotNull(first) else finds.flatMap { listOf(it.faster, it.official) }

    /**
     * The day of the first of the [trips] to leave, for the line above them; without any, the [day]
     * asked for. A search at 23:50 may show only trips after midnight (2026-10-07: "Wed 7 Oct" above
     * a train at 00:02 on the 8th).
     */
    val firstDay: LocalDate get() = trips.minOfOrNull { it.departure.time }?.toLocalDate() ?: day.toLocalDate()

    /** Whether one of the [trips] stops at [station] (its id); Optimization's other rows are faded (D3). */
    fun onTrips(station: String): Boolean = trips.any { trip -> trip.legs.any { it.departure.id == station || it.arrival.id == station } }

    /** Whether any of its [trips] has a delay known, even 0: then the page says when they were read. */
    val delaysKnown: Boolean
        get() = trips.any { c -> c.legs.any { it.departure.delay != null || it.arrival.delay != null } }

    fun toJson(): String = json.encodeToString(this)
}

/** The [Found] in [text], from [Found.toJson]. Throws if it isn't one, e.g. an older app's format. */
fun found(text: String): Found = json.decodeFromString(text)

/**
 * [from] → [to] at the next [leaving] after [now] (today, or tomorrow once it's past; Swiss time),
 * searched with the rider's [times]: today's search, then the full search on the [timetable] (null
 * if there's none), when it has the day (CLAUDE.md, The full search, step 4.3). Blocking. Throws if
 * the official connections' request fails; a failed onward one skips its change and goes to
 * [failed], as does a timetable without the day and an error of the full search.
 */
fun find(
    from: String,
    to: String,
    leaving: LocalTime,
    times: TrackSwitchTimes,
    connections: Connections,
    timetable: () -> Timetable?,
    now: LocalDateTime = LocalDateTime.now(ZoneId.of("Europe/Zurich")),
    failed: (Exception) -> Unit = {},
): Found {
    val day = now.toLocalDate().atTime(leaving).let { if (it.isBefore(now)) it.plusDays(1) else it }
    val officials = connections(from, to, day)
    val transfer = { stop: Stop -> Duration.ofMinutes(times.rider(stop.id)) }
    val searched = search(officials, transfer, connections)
    searched.failures.forEach(failed)
    val file = if (officials.isEmpty()) null else timetable()?.takeIf { day.toLocalDate() in it.first..<it.first.plusDays(it.days.toLong()) }
    val noTimetable = officials.isNotEmpty() && file == null
    if (noTimetable) failed(IOException("No timetable with ${day.toLocalDate()}"))
    val full = try {
        file?.let { fullSearch(it, officials, transfer) }.orEmpty()
    } catch (e: Exception) {
        failed(e)
        emptyList()
    }
    // Today's first: of two the same, it stays, with the API's names and delays.
    val finds = best(searched.finds + full, officials)
    // Today's search's stations, then the full search's finds' (those of today's are among the first).
    val changes = (searched.changes + finds.flatMap { it.faster.changes }).distinctBy { it.id }
    return Found(
        day, officials.firstOrNull(), changes, finds, searched.offered, searched.failures.isNotEmpty(), noTimetable, now,
    )
}

/** `8:50`, `08:50`, `850` or `0850` (the number keyboard has no colon); null if it isn't a time. */
fun parseTime(text: String): LocalTime? {
    val digits = text.filter { it.isDigit() }.takeIf { it.length in 3..4 }?.padStart(4, '0') ?: return null
    return runCatching { LocalTime.of(digits.take(2).toInt(), digits.drop(2).toInt()) }.getOrNull()
}

/** java.time as ISO text, e.g. `2026-10-07T08:53+02:00`. */
internal abstract class IsoText<T : Any>(private val parse: (String) -> T) : KSerializer<T> {
    override val descriptor = PrimitiveSerialDescriptor(javaClass.name, PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): T = parse(decoder.decodeString())
}

internal object OffsetDateTimeText : IsoText<OffsetDateTime>(OffsetDateTime::parse)

internal object LocalDateTimeText : IsoText<LocalDateTime>(LocalDateTime::parse)

package io.github.buerlino.gleiswechsel.core

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import java.time.LocalDate
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * The trains of [days] days from [first] for the full search (CLAUDE.md): made from the Swiss GTFS
 * by [trains] in a GitHub Actions job, read on the phone by [timetable]. [source] names where it
 * comes from, as opentransportdata.swiss's terms ask.
 */
class Timetable(
    val source: String,
    val first: LocalDate,
    val days: Int,
    val stations: List<Station>,
    val platforms: List<Platform>,
    val trips: List<Trip>,
    val continuations: List<Continuation>,
) {
    /** [id]: the national id the API uses (GTFS `didok`), e.g. `8505000`. */
    data class Station(val id: String, val name: String)

    /** A track at [station] (an index into [stations]): [code] e.g. `9`, `2CD`, null if it has none. */
    data class Platform(val station: Int, val code: String?)

    /**
     * A train as [line] shows it (`S4`, `RE24`), on the days set in [days] (bit d: [first] + d).
     * Its stops in order: [platforms] (indexes into [Timetable.platforms]), [arrivals] and
     * [departures] in minutes from its day's midnight, Swiss time (past 24:00 after midnight), and
     * whether riders can get on ([pickup]) and off ([dropOff]) there.
     */
    class Trip(
        val line: String,
        val days: Int,
        val platforms: IntArray,
        val arrivals: IntArray,
        val departures: IntArray,
        val pickup: BooleanArray,
        val dropOff: BooleanArray,
    )

    /** Trip [from] goes on as trip [to] on the days set in [days]: staying on isn't a change. */
    data class Continuation(val from: Int, val to: Int, val days: Int)

    /**
     * Gzipped, the format number first (see [timetable]). A stop's times as steps: the minutes from
     * the previous departure (from midnight at the first stop), then the dwell; a third of the size
     * of plain times. Doesn't close [out].
     */
    fun write(out: OutputStream) {
        check(platforms.size <= 0xFFFF) { "${platforms.size} platforms don't fit in 16 bits" }
        val gzip = GZIPOutputStream(out)
        DataOutputStream(BufferedOutputStream(gzip, 1 shl 16)).run {
            writeInt(FORMAT)
            writeUTF(source)
            writeUTF(first.toString())
            writeByte(days)
            writeInt(stations.size)
            stations.forEach { writeUTF(it.id); writeUTF(it.name) }
            writeInt(platforms.size)
            platforms.forEach { writeInt(it.station); writeUTF(it.code.orEmpty()) }
            writeInt(trips.size)
            trips.forEach { t ->
                writeUTF(t.line)
                writeInt(t.days)
                writeShort(t.platforms.size)
                for (i in t.platforms.indices) {
                    val step = t.arrivals[i] - (if (i == 0) 0 else t.departures[i - 1])
                    val dwell = t.departures[i] - t.arrivals[i]
                    check(step in 0..0xFFFF && dwell in 0..0xFF) { "${t.line}: times out of order at stop $i" }
                    writeShort(t.platforms[i])
                    writeShort(step)
                    writeByte(dwell)
                    writeByte((if (t.pickup[i]) 0 else NO_PICKUP) or (if (t.dropOff[i]) 0 else NO_DROP_OFF))
                }
            }
            writeInt(continuations.size)
            continuations.forEach { writeInt(it.from); writeInt(it.to); writeInt(it.days) }
            flush()
        }
        gzip.finish()
    }
}

/** Raised with each change of the format, so an app refuses a file it can't read (see [timetable]). */
private const val FORMAT = 1

private const val NO_PICKUP = 1
private const val NO_DROP_OFF = 2

/**
 * The [Timetable] in [input], from [Timetable.write]. Throws on a file of another format (a newer
 * app's) or a broken one, so the app can ignore it and log why. Doesn't close [input].
 */
fun timetable(input: InputStream): Timetable =
    DataInputStream(BufferedInputStream(GZIPInputStream(input), 1 shl 16)).run {
        val format = readInt()
        if (format != FORMAT) throw IOException("Timetable format $format, this app reads $FORMAT")
        val source = readUTF()
        val first = LocalDate.parse(readUTF())
        val days = readUnsignedByte()
        val stations = List(readInt()) { Timetable.Station(readUTF(), readUTF()) }
        val platforms = List(readInt()) { Timetable.Platform(readInt(), readUTF().ifEmpty { null }) }
        val trips = List(readInt()) {
            val line = readUTF()
            val tripDays = readInt()
            val n = readUnsignedShort()
            val trip = Timetable.Trip(line, tripDays, IntArray(n), IntArray(n), IntArray(n), BooleanArray(n), BooleanArray(n))
            for (i in 0 until n) {
                trip.platforms[i] = readUnsignedShort()
                trip.arrivals[i] = (if (i == 0) 0 else trip.departures[i - 1]) + readUnsignedShort()
                trip.departures[i] = trip.arrivals[i] + readUnsignedByte()
                val flags = readUnsignedByte()
                trip.pickup[i] = flags and NO_PICKUP == 0
                trip.dropOff[i] = flags and NO_DROP_OFF == 0
            }
            trip
        }
        val continuations = List(readInt()) { Timetable.Continuation(readInt(), readInt(), readInt()) }
        Timetable(source, first, days, stations, platforms, trips, continuations)
    }

/** Where the timetable job (`.github/workflows/timetable.yml`) publishes the file. */
const val TIMETABLE_URL = "https://buerlino.github.io/gleiswechsel/timetable.bin.gz"

/**
 * The timetable for the full search: the phone's [copy], downloaded anew ([download]) when it's
 * missing, older than 7 days or can't be read, e.g. after an update to a new format (CLAUDE.md, The
 * full search). A download replaces it only once it reads, so a failed one keeps the old copy: no
 * network, a Wi-Fi login page, a newer app's format. Null if there's none that reads; each error
 * goes to [failed]. Blocking.
 */
fun localTimetable(copy: File, download: () -> ByteArray, failed: (Exception) -> Unit): Timetable? {
    fun read() = try {
        copy.inputStream().use { timetable(it) }
    } catch (e: Exception) {
        failed(e)
        null
    }
    val recent = copy.exists() && System.currentTimeMillis() - copy.lastModified() < WEEK
    if (recent) read()?.let { return it }
    try {
        val bytes = download()
        return timetable(bytes.inputStream()).also {
            val part = File("${copy.path}.part")
            part.writeBytes(bytes)
            if (!part.renameTo(copy)) throw IOException("$part not renamed")
        }
    } catch (e: Exception) {
        failed(e)
    }
    return if (copy.exists() && !recent) read() else null
}

private val WEEK = Duration.ofDays(7).toMillis()

package io.github.buerlino.gleiswechsel.core

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class TimetableTest {
    /** A GTFS file as the Swiss export writes it: a BOM, the header, every field quoted, CRLF. */
    private fun file(header: String, vararg rows: List<String>) =
        "﻿$header\r\n" + rows.joinToString("") { row -> row.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" } + "\r\n" }

    // Made-up stations and trains: an S1 Aach → Xberg every day, an RE3 Xberg → Bstadt on weekdays
    // that goes on to Yfeld past midnight on one day, a bus, and an S1 whose service has ended.
    private val files = mapOf(
        "feed_info.txt" to file(
            "feed_publisher_name,feed_publisher_url,feed_lang,feed_start_date,feed_end_date,feed_version",
            listOf("Made up", "https://example.com", "DE", "20251214", "20261212", "20260301"),
        ),
        "routes.txt" to file(
            "route_id,agency_id,route_short_name,route_long_name,route_desc,route_type",
            listOf("r1", "1", "S1", "", "S", "109"),
            listOf("r2", "1", "RE3", "", "RE", "106"),
            listOf("r3", "1", "B5", "", "B", "700"),
        ),
        "calendar.txt" to file(
            "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date",
            listOf("daily", "1", "1", "1", "1", "1", "1", "1", "20251214", "20261212"),
            listOf("weekdays", "1", "1", "1", "1", "1", "0", "0", "20251214", "20261212"),
            listOf("past", "1", "1", "1", "1", "1", "1", "1", "20251214", "20260301"),
        ),
        "calendar_dates.txt" to file(
            "service_id,date,exception_type",
            listOf("weekdays", "20260306", "2"),
            listOf("weekdays", "20260314", "1"),
            listOf("weekdays", "20260401", "2"),
            listOf("once", "20260310", "1"),
        ),
        "stops.txt" to file(
            "stop_id,stop_name,stop_lat,stop_lon,location_type,parent_station,platform_code,original_stop_id,didok",
            listOf("a:1", "Aach", "47", "8", "", "Parenta", "1", "a:1", "1111111"),
            listOf("x:1", "Xberg, Bahnhof", "47", "8", "", "Parentx", "1", "x:1", "2222222"),
            listOf("x:9", "Xberg, Bahnhof", "47", "8", "", "Parentx", "9", "x:9", "2222222"),
            listOf("b", "Bstadt", "47", "8", "", "Parentb", "", "b", "3333333"),
            listOf("y:3", "Yfeld \"See\"", "47", "8", "", "Parenty", "3", "y:3", "8000001"),
            listOf("post", "Aach, Post", "47", "8", "", "", "", "post", "4444444"),
        ),
        "trips.txt" to file(
            "route_id,service_id,trip_id,trip_headsign,trip_short_name,direction_id,block_id,original_trip_id,hints",
            listOf("r1", "daily", "t1", "Xberg", "101", "0", "", "", ""),
            listOf("r2", "weekdays", "t2", "Bstadt", "202", "0", "", "", ""),
            listOf("r3", "daily", "t3", "Post", "5", "0", "", "", ""),
            listOf("r1", "past", "t4", "Xberg", "103", "0", "", "", ""),
            listOf("r2", "once", "t5", "Yfeld", "204", "0", "", "", ""),
        ),
        "stop_times.txt" to file(
            "trip_id,arrival_time,departure_time,stop_id,stop_sequence,pickup_type,drop_off_type",
            listOf("t1", "07:50:00", "07:50:00", "a:1", "1", "0", "1"),
            listOf("t1", "08:10:00", "08:10:00", "x:1", "2", "1", "0"),
            listOf("t2", "23:58:00", "23:58:00", "b", "2", "0", "0"),
            listOf("t2", "23:40:00", "23:40:00", "x:9", "1", "0", "0"),
            listOf("t3", "08:00:00", "08:00:00", "post", "1", "0", "0"),
            listOf("t3", "08:05:00", "08:05:00", "a:1", "2", "0", "0"),
            listOf("t4", "09:50:00", "09:50:00", "a:1", "1", "0", "0"),
            listOf("t4", "10:10:00", "10:10:00", "x:1", "2", "0", "0"),
            listOf("t5", "23:58:00", "23:59:00", "b", "1", "0", "0"),
            listOf("t5", "24:20:00", "24:20:00", "y:3", "2", "0", "0"),
        ),
        "transfers.txt" to file(
            "from_stop_id,to_stop_id,from_route_id,to_route_id,from_trip_id,to_trip_id,transfer_type,min_transfer_time,service_id",
            listOf("x:1", "x:9", "", "", "", "", "2", "240", ""),
            listOf("b", "b", "r2", "r2", "t2", "t5", "4", "", "once"),
            listOf("x:1", "x:1", "r1", "r3", "t1", "t3", "4", "", "daily"),
        ),
    )

    // The next timetable year's, from 13 December 2026: an IR7 Aach → Xberg every day, with the
    // first one's trip and stop ids.
    private val nextYear = files + mapOf(
        "feed_info.txt" to file(
            "feed_publisher_name,feed_publisher_url,feed_lang,feed_start_date,feed_end_date,feed_version",
            listOf("Made up", "https://example.com", "DE", "20261213", "20271211", "20261003"),
        ),
        "routes.txt" to file("route_id,agency_id,route_short_name,route_long_name,route_desc,route_type", listOf("r1", "1", "IR7", "", "IR", "103")),
        "calendar.txt" to file(
            "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date",
            listOf("daily", "1", "1", "1", "1", "1", "1", "1", "20261213", "20271211"),
        ),
        "calendar_dates.txt" to file("service_id,date,exception_type"),
        "trips.txt" to file(
            "route_id,service_id,trip_id,trip_headsign,trip_short_name,direction_id,block_id,original_trip_id,hints",
            listOf("r1", "daily", "t1", "Xberg", "701", "0", "", "", ""),
        ),
        "stop_times.txt" to file(
            "trip_id,arrival_time,departure_time,stop_id,stop_sequence,pickup_type,drop_off_type",
            listOf("t1", "07:52:00", "07:52:00", "a:1", "1", "0", "0"),
            listOf("t1", "08:09:00", "08:09:00", "x:1", "2", "0", "0"),
        ),
        "transfers.txt" to file("from_stop_id,to_stop_id,from_route_id,to_route_id,from_trip_id,to_trip_id,transfer_type,min_transfer_time,service_id"),
    )

    private fun zip(files: Map<String, String>) = File.createTempFile("gtfs", ".zip").apply {
        deleteOnExit()
        ZipOutputStream(outputStream()).use { zip ->
            files.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
    }

    private val gtfs = zip(files)

    // Monday, 2 March 2026, and 13 days more.
    private val timetable = trains(listOf(gtfs), LocalDate.of(2026, 3, 2), 14)

    /** Each trip: its line, days, stops (station, track, arrival/departure, what isn't possible); then the continuations. */
    private fun Timetable.text(): List<String> {
        fun days(mask: Int) = (0 until days).filter { mask shr it and 1 == 1 }.joinToString(",")
        fun hhmm(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)
        return trips.map { trip ->
            "${trip.line} ${days(trip.days)}: " + trip.platforms.indices.joinToString(", ") { i ->
                val platform = platforms[trip.platforms[i]]
                "${stations[platform.station].name} ${platform.code ?: "-"} ${hhmm(trip.arrivals[i])}/${hhmm(trip.departures[i])}" +
                    (if (trip.pickup[i]) "" else " no pickup") + (if (trip.dropOff[i]) "" else " no drop-off")
            }
        } + continuations.map { "trip ${it.from} goes on as trip ${it.to} on ${days(it.days)}" }
    }

    @Test
    fun theTrainsOfTheDays() = assertEquals(
        listOf(
            "S1 0,1,2,3,4,5,6,7,8,9,10,11,12,13: Aach 1 07:50/07:50 no drop-off, Xberg, Bahnhof 1 08:10/08:10 no pickup",
            // Not on Friday the 6th (removed), on Saturday the 14th (added); its stops in their order.
            "RE3 0,1,2,3,7,8,9,10,11,12: Xberg, Bahnhof 9 23:40/23:40, Bstadt - 23:58/23:58",
            // A service only in calendar_dates.txt, past midnight; the bus and the ended S1 are left out.
            "RE3 8: Bstadt - 23:58/23:59, Yfeld \"See\" 3 24:20/24:20",
            // On its own service's days; the one on to the bus is left out.
            "trip 1 goes on as trip 2 on 8",
        ),
        timetable.text(),
    )

    @Test
    fun aroundTheTimetableChange() {
        // Sunday, 6 December 2026, and 13 days more: up to the 12th from the first file, then from the second.
        val change = trains(listOf(gtfs, zip(nextYear)), LocalDate.of(2026, 12, 6), 14)
        assertEquals(
            listOf(
                "S1 0,1,2,3,4,5,6: Aach 1 07:50/07:50 no drop-off, Xberg, Bahnhof 1 08:10/08:10 no pickup",
                "RE3 1,2,3,4,5: Xberg, Bahnhof 9 23:40/23:40, Bstadt - 23:58/23:58",
                "IR7 7,8,9,10,11,12,13: Aach 1 07:52/07:52, Xberg, Bahnhof 1 08:09/08:09",
            ),
            change.text(),
        )
        assertEquals("opentransportdata.swiss, GTFS 20260301 + 20261003", change.source)
        // A file without any of the days is skipped.
        assertEquals(timetable.source, trains(listOf(gtfs, zip(nextYear)), LocalDate.of(2026, 3, 2), 14).source)
    }

    @Test
    fun aDayNoFileHasThrows() {
        assertFailsWith<IOException> { trains(listOf(gtfs), LocalDate.of(2026, 12, 6), 14) }
    }

    @Test
    fun stationsByTheirNationalId() {
        assertEquals(
            listOf(
                Timetable.Station("1111111", "Aach"),
                Timetable.Station("2222222", "Xberg, Bahnhof"),
                Timetable.Station("3333333", "Bstadt"),
                Timetable.Station("8000001", "Yfeld \"See\""),
            ),
            timetable.stations,
        )
        assertEquals(listOf("1", "-", "1", "9", "3"), timetable.platforms.map { it.code ?: "-" })
        assertEquals("opentransportdata.swiss, GTFS 20260301", timetable.source)
    }

    @Test
    fun writtenAndReadBack() {
        val read = timetable(ByteArrayOutputStream().also { timetable.write(it) }.toByteArray().inputStream())
        assertEquals(timetable.text(), read.text())
        assertEquals(timetable.stations, read.stations)
        assertEquals(timetable.platforms, read.platforms)
        assertEquals(listOf(timetable.source, timetable.first, timetable.days), listOf(read.source, read.first, read.days))
    }

    @Test
    fun anotherFormatIsRefused() {
        val newer = ByteArrayOutputStream().also { out -> DataOutputStream(GZIPOutputStream(out)).use { it.writeInt(2) } }
        assertFailsWith<IOException> { timetable(newer.toByteArray().inputStream()) }
    }

    @Test
    fun aBrokenFileIsRefused() {
        val bytes = ByteArrayOutputStream().also { timetable.write(it) }.toByteArray()
        assertFailsWith<IOException> { timetable(bytes.copyOf(bytes.size / 2).inputStream()) }
    }

    // The phone's copy, at first missing; the download sends [published], from 2 March.
    private val copy = File.createTempFile("timetable", ".bin.gz").apply { delete(); deleteOnExit() }
    private val published = ByteArrayOutputStream().also { timetable.write(it) }.toByteArray()
    private var downloads = 0
    private val errors = mutableListOf<String>()

    private fun local(download: () -> ByteArray = { published }) =
        localTimetable(copy, { downloads++; download() }) { errors += it.javaClass.simpleName }?.first

    // A copy from 3 March, [days] old.
    private fun old(days: Long) = copy.run {
        ByteArrayOutputStream().also { trains(listOf(gtfs), LocalDate.of(2026, 3, 3), 14).write(it) }.toByteArray().let(::writeBytes)
        setLastModified(System.currentTimeMillis() - Duration.ofDays(days).toMillis())
    }

    @Test
    fun aMissingCopyIsDownloadedOnce() {
        assertEquals(LocalDate.of(2026, 3, 2), local())
        assertEquals(LocalDate.of(2026, 3, 2), local())
        assertContentEquals(published, copy.readBytes())
        assertEquals(1, downloads)
        assertEquals(emptyList(), errors)
    }

    @Test
    fun aCopyOlderThanAWeekIsReplaced() {
        old(6)
        assertEquals(LocalDate.of(2026, 3, 3), local())
        assertEquals(0, downloads)
        old(8)
        assertEquals(LocalDate.of(2026, 3, 2), local())
        assertContentEquals(published, copy.readBytes())
    }

    @Test
    fun aFailedDownloadKeepsTheOldCopy() {
        old(8)
        assertEquals(LocalDate.of(2026, 3, 3), local { throw IOException("No network") })
        // A Wi-Fi login page, or a newer app's format.
        assertEquals(LocalDate.of(2026, 3, 3), local { "<html>Log in</html>".toByteArray() })
        assertEquals(listOf("IOException", "ZipException"), errors)
        assertEquals(2, downloads)
        // None at all: null.
        copy.delete()
        assertEquals(null, local { throw IOException("No network") })
        assertFalse(copy.exists())
    }

    @Test
    fun aCopyThatDoesntReadIsReplaced() {
        // An older app's format, after an update.
        copy.outputStream().use { out -> DataOutputStream(GZIPOutputStream(out)).use { it.writeInt(0) } }
        assertEquals(LocalDate.of(2026, 3, 2), local())
        assertEquals(listOf("IOException"), errors)
    }
}

package io.github.buerlino.gleiswechsel.core

import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class OpendataTest {
    // Made-up stations and trains, shaped like a real /v1/connections response (more fields than
    // the app reads, times with a `+0100` offset): a ride, a walk, a ride.
    private val body = """
        {
          "connections": [{
            "from": {"station": {"id": "8500001", "name": "Aach"}, "departure": "2026-03-03T08:00:00+0100"},
            "to": {"station": {"id": "8500003", "name": "Bstadt"}, "arrival": "2026-03-03T08:40:00+0100"},
            "duration": "00d00:40:00",
            "transfers": 1,
            "sections": [
              {
                "journey": {"name": "012345", "category": "S", "number": "7", "operator": "XYZ", "to": "Xberg",
                  "passList": [
                  {"station": {"id": "8500001", "name": "Aach"}, "departure": "2026-03-03T08:00:00+0100"},
                  {"station": {"id": "8500004", "name": "Cweil"}, "arrival": "2026-03-03T08:05:00+0100"},
                  {"station": {"id": null, "name": "Nameless"}},
                  {"station": {"id": "8500002", "name": "Xberg"}, "arrival": "2026-03-03T08:10:00+0100"}]},
                "walk": null,
                "departure": {"station": {"id": "8500001", "name": "Aach", "coordinate": {"type": "WGS84", "x": 47.0, "y": 8.0}},
                  "arrival": null, "departure": "2026-03-03T08:00:00+0100", "platform": "2", "delay": null,
                  "prognosis": {"platform": null, "arrival": null, "departure": null}},
                "arrival": {"station": {"id": "8500002", "name": "Xberg"},
                  "arrival": "2026-03-03T08:10:00+0100", "departure": null, "platform": "11A"}
              },
              {
                "journey": null,
                "walk": {"duration": 180},
                "departure": {"station": {"id": "8500002", "name": "Xberg"}, "departure": "2026-03-03T08:10:00+0100", "platform": "11A"},
                "arrival": {"station": {"id": "8509999", "name": "Xberg, Bahnhof"}, "arrival": "2026-03-03T08:13:00+0100", "platform": null}
              },
              {
                "journey": {"category": "B", "number": "42", "operator": "XYZ", "to": "Bstadt"},
                "walk": null,
                "departure": {"station": {"id": "8509999", "name": "Xberg, Bahnhof"}, "departure": "2026-03-03T08:15:00+0100", "platform": "C"},
                "arrival": {"station": {"id": "8500003", "name": "Bstadt"}, "arrival": "2026-03-03T08:40:00+0100", "platform": null}
              }
            ]
          }]
        }
    """.trimIndent()

    private fun at(hhmm: String) = OffsetDateTime.parse("2026-03-03T$hhmm:00+01:00")

    @Test
    fun parsesRidesAndWalks() {
        val c = parseConnections(body).single()
        assertEquals(listOf("S7", null, "B42"), c.legs.map { it.train })
        assertEquals(Stop("Aach", "8500001", at("08:00"), "2"), c.departure)
        assertEquals(Stop("Xberg", "8500002", at("08:10"), "11A"), c.legs[0].arrival)
        assertEquals(Stop("Xberg, Bahnhof", "8509999", at("08:15"), "C"), c.legs[2].departure)
        assertEquals(Stop("Bstadt", "8500003", at("08:40")), c.arrival)
        assertEquals(listOf("8500004"), c.legs[0].via)
        assertEquals(emptyList(), c.legs[2].via)
    }

    @Test
    fun theDurationIsFirstDepartureToLastArrival() {
        // 08:00 to 08:40, the walk and the wait included.
        assertEquals(Duration.ofMinutes(40), parseConnections(body).single().duration)
    }

    @Test
    fun noConnectionsIsAnEmptyList() {
        assertEquals(emptyList(), parseConnections("""{"connections": [], "from": null, "to": null}"""))
    }

    @Test
    fun theUrlAsksFromTheTimeInSwissLocalTime() {
        assertEquals(
            "https://transport.opendata.ch/v1/connections?from=Xberg%2C+Bahnhof&to=8500003&date=2026-03-03&time=08:05",
            connectionsUrl("Xberg, Bahnhof", "8500003", LocalDateTime.of(2026, 3, 3, 8, 5)),
        )
    }
}

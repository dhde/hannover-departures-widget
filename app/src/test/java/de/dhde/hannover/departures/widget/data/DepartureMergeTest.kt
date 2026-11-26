package de.dhde.hannover.departures.widget.data

import de.dhde.hannover.departures.widget.api.DepartureItem
import org.junit.Assert.assertEquals
import org.junit.Test

class DepartureMergeTest {
    private fun item(line: String, bon: String?) =
        DepartureItem(line = "Stadtbahn $line", lineId = null, destination = null, number = line, platformId = bon)

    @Test
    fun selectedPlatformReplacedByDedicatedQuery() {
        val station = listOf(item("3", "p1"), item("7", "p1"), item("1", "p3"), item("4", "p5"))
        val platform = listOf(item("3", "p1"), item("7", "p1"), item("9", "p1"), item("13", "p1"))
        val merged = mergePlatformDepartures(station, platform, "p1")
        // Andere Steige bleiben (für Chips/Liste), p1 kommt komplett aus der Steig-Abfrage
        assertEquals(listOf("1", "4", "3", "7", "9", "13"), merged.map { it.number })
    }

    @Test
    fun foreignPlatformsInPlatformQueryAreIgnored() {
        val merged = mergePlatformDepartures(listOf(item("1", "p3")), listOf(item("3", "p1"), item("X", "p2")), "p1")
        assertEquals(listOf("1", "3"), merged.map { it.number })
    }

    @Test
    fun emptyPlatformResultKeepsStationData() {
        // Steig-Abfrage leer (z.B. Fehler/Nachtpause) → nicht die vorhandenen Abfahrten wegwerfen
        val station = listOf(item("3", "p1"), item("1", "p3"))
        assertEquals(station, mergePlatformDepartures(station, emptyList(), "p1"))
    }
}

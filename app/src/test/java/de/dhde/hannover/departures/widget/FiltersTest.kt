package de.dhde.hannover.departures.widget

import de.dhde.hannover.departures.widget.data.TransportFilter
import de.dhde.hannover.departures.widget.api.MsgItem
import de.dhde.hannover.departures.widget.data.filterMessages
import de.dhde.hannover.departures.widget.data.PlatformOption
import de.dhde.hannover.departures.widget.data.availablePlatforms
import de.dhde.hannover.departures.widget.data.effectivePlatform
import de.dhde.hannover.departures.widget.data.platformLabel
import de.dhde.hannover.departures.widget.data.platformMatches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltersTest {

    @Test
    fun transportFilter_roundTrip() {
        for (f in TransportFilter.entries) {
            assertEquals(f, TransportFilter.fromStorage(f.storageValue))
        }
    }

    @Test
    fun transportFilter_legacyAndDefaults() {
        assertEquals(TransportFilter.ALL, TransportFilter.fromStorage("ALL"))
        assertEquals(TransportFilter.BUS, TransportFilter.fromStorage("BUS"))
        assertEquals(TransportFilter.TRAM, TransportFilter.fromStorage("TRAIN")) // Legacy-Wert
        assertEquals(TransportFilter.ALL, TransportFilter.fromStorage(null))
        assertEquals(TransportFilter.ALL, TransportFilter.fromStorage("quatsch"))
    }

    @Test
    fun storageValues_areLegacyValues() {
        // Sichert die Back-Compat-Speicherform gegen versehentliches Umbenennen ab.
        assertEquals("ALL", TransportFilter.ALL.storageValue)
        assertEquals("BUS", TransportFilter.BUS.storageValue)
        assertEquals("TRAIN", TransportFilter.TRAM.storageValue) // Legacy: Tram wird als "TRAIN" gespeichert
    }

    @Test
    fun platformLabel_usesNameOrFallback() {
        val names = mapOf("de:03241:2021:3:2023" to "Bus 1")
        assertEquals("Bus 1", platformLabel("de:03241:2021:3:2023", names))
        assertEquals("Steig 2024", platformLabel("de:03241:2021:3:2024", names))
        assertNull(platformLabel(null, names))
        assertNull(platformLabel("", names))
    }

    @Test
    fun availablePlatforms_distinctAndSortedByLabel() {
        val result = availablePlatforms(
            listOf(
                "de:x:2" to "Bus 2",
                "de:x:1" to "Bus 1",
                "de:x:2" to "Bus 2",
                null to null,
                "de:x:9" to "SB1"
            )
        )
        assertEquals(
            listOf(PlatformOption("de:x:1", "Bus 1"), PlatformOption("de:x:2", "Bus 2"), PlatformOption("de:x:9", "SB1")),
            result
        )
    }

    @Test
    fun availablePlatforms_naturalSortForNumbers() {
        val result = availablePlatforms(listOf("a" to "Bus 10", "b" to "Bus 2"))
        assertEquals(listOf("Bus 2", "Bus 10"), result.map { it.label })
    }

    @Test
    fun effectivePlatform_fallsBackToAllWhenNotAvailable() {
        val available = listOf(PlatformOption("p1", "Bus 1"), PlatformOption("p2", "Bus 2"))
        assertEquals("p1", effectivePlatform("p1", available))
        assertNull(effectivePlatform("gone", available))
        assertNull(effectivePlatform(null, available))
        assertNull(effectivePlatform("", available))
    }

    @Test
    fun platformMatches_onlySelectedPlatform() {
        assertTrue(platformMatches("p1", null))
        assertTrue(platformMatches(null, null))
        assertTrue(platformMatches("p1", "p1"))
        assertFalse(platformMatches("p2", "p1"))
        assertFalse(platformMatches(null, "p1"))
    }

    @Test
    fun filterMessages_hidesById() {
        val msgs = listOf(
            MsgItem(id = "ems-1", content = "Fahrt entfällt heute"),
            MsgItem(id = "ems-2", content = "Aufzug defekt")
        )
        val result = filterMessages(msgs, setOf("ems-2"))
        assertTrue(result.any { it.id == "ems-1" })
        assertFalse(result.any { it.id == "ems-2" })
    }

    @Test
    fun filterMessages_emptyIgnored_keepsAll() {
        val msgs = listOf(
            MsgItem(id = "ems-1", content = "Aufzug defekt"),
            MsgItem(id = "ems-2", content = "Fahrt entfällt")
        )
        assertEquals(msgs, filterMessages(msgs, emptySet()))
    }

    @Test
    fun filterMessages_legacyTextStillMatchesByContent() {
        // Abwärtskompatibilität: alte text-basierte Ausblendungen (kein ID-Präfix) greifen weiter.
        val msgs = listOf(
            MsgItem(id = "ems-1", content = "Aufzug defekt am Bahnhof"),
            MsgItem(id = "ems-2", content = "Linie 3 fährt planmäßig")
        )
        val result = filterMessages(msgs, setOf("aufzug defekt"))
        assertFalse(result.any { it.id == "ems-1" })
        assertTrue(result.any { it.id == "ems-2" })
    }
}

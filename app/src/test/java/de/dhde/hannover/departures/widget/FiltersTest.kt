package de.dhde.hannover.departures.widget

import de.dhde.hannover.departures.widget.data.DirectionFilter
import de.dhde.hannover.departures.widget.data.TransportFilter
import de.dhde.hannover.departures.widget.data.filterMessages
import de.dhde.hannover.departures.widget.data.isProtectedMessage
import de.dhde.hannover.departures.widget.data.lineDirection
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
        assertEquals("ALL", DirectionFilter.ALL.storageValue)
        assertEquals("H", DirectionFilter.INBOUND.storageValue)
        assertEquals("R", DirectionFilter.OUTBOUND.storageValue)
    }

    @Test
    fun directionFilter_roundTrip() {
        for (f in DirectionFilter.entries) {
            assertEquals(f, DirectionFilter.fromStorage(f.storageValue))
        }
    }

    @Test
    fun directionFilter_legacyAndDefaults() {
        assertEquals(DirectionFilter.ALL, DirectionFilter.fromStorage("ALL"))
        assertEquals(DirectionFilter.INBOUND, DirectionFilter.fromStorage("H"))
        assertEquals(DirectionFilter.OUTBOUND, DirectionFilter.fromStorage("R"))
        assertEquals(DirectionFilter.ALL, DirectionFilter.fromStorage(null))
        assertEquals(DirectionFilter.ALL, DirectionFilter.fromStorage("x"))
    }

    @Test
    fun lineDirection_suffixHandR() {
        assertEquals(DirectionFilter.INBOUND, lineDirection("de:rnv:3:H"))
        assertEquals(DirectionFilter.OUTBOUND, lineDirection("de:rnv:3:R"))
        assertEquals(DirectionFilter.INBOUND, lineDirection("xyzh"))
        assertEquals(DirectionFilter.OUTBOUND, lineDirection("xyzr"))
    }

    @Test
    fun lineDirection_unknownIsNull() {
        assertNull(lineDirection(null))
        assertNull(lineDirection(""))
        assertNull(lineDirection("de:rnv:3:X"))
    }

    @Test
    fun isProtectedMessage_keywordsCaseInsensitive() {
        assertTrue(isProtectedMessage("Fahrt entfällt heute"))
        assertTrue(isProtectedMessage("SCHIENENERSATZVERKEHR Linie 3"))
        assertTrue(isProtectedMessage("Notarzteinsatz am Kröpcke"))
        assertFalse(isProtectedMessage("Wir bitten um Verständnis."))
        assertFalse(isProtectedMessage("Aufzug defekt"))
    }

    @Test
    fun filterMessages_keepsProtectedEvenWhenIgnored() {
        val msgs = listOf("Fahrt entfällt heute", "Aufzug defekt")
        val result = filterMessages(msgs, setOf("entfällt", "aufzug"))
        assertTrue("Fahrt entfällt heute" in result)
        assertFalse("Aufzug defekt" in result)
    }

    @Test
    fun filterMessages_emptyIgnored_keepsAll() {
        val msgs = listOf("Aufzug defekt", "Fahrt entfällt")
        assertEquals(msgs, filterMessages(msgs, emptySet()))
    }
}

package de.dhde.hannover.departures.widget

import de.dhde.hannover.departures.widget.data.DirectionFilter
import de.dhde.hannover.departures.widget.data.TransportFilter
import org.junit.Assert.assertEquals
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
}

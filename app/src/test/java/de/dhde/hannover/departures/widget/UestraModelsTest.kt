package de.dhde.hannover.departures.widget

import de.dhde.hannover.departures.widget.api.DepartureEvent
import de.dhde.hannover.departures.widget.api.DepartureItem
import de.dhde.hannover.departures.widget.api.TransportType
import de.dhde.hannover.departures.widget.api.toFlatRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-Tests für die Datenmodelle (kein Android-Context):
 * - Crash-Absicherung der Verspätungsberechnung bei malformten API-Timestamps
 * - Verkehrsmittel-Klassifikation (transportTypes) und ihre Propagation in FlatDeparture
 */
class UestraModelsTest {

    private fun line(number: String?, line: String? = null, lineId: String? = null) =
        DepartureItem(line = line, lineId = lineId, destination = null, number = number)

    private fun item(planned: String?, estimated: String?) = DepartureItem(
        line = "Stadtbahn 3", lineId = null, destination = "Wettbergen", number = "3",
        events = listOf(DepartureEvent(plannedTime = planned, estimatedTime = estimated))
    )

    @Test
    fun delayMinutes_computesPositiveDelay() {
        val dep = item("2099-01-01T10:00:00Z", "2099-01-01T10:03:00Z")
        assertEquals(3L, dep.delayMinutes)
    }

    @Test
    fun delayMinutes_isNullWhenNoDelay() {
        val dep = item("2099-01-01T10:00:00Z", "2099-01-01T10:00:00Z")
        assertNull(dep.delayMinutes)
    }

    @Test
    fun delayMinutes_isNullOnMalformedTimestamp_doesNotThrow() {
        // Vor dem Fix: DateTimeParseException
        val dep = item("2099-01-01T10:00:00Z", "kaputt")
        assertNull(dep.delayMinutes)
    }

    @Test
    fun toFlatRows_doesNotCrashOnMalformedEstimatedTime() {
        // Vor dem Fix: DateTimeParseException in der Delay-Berechnung
        val rows = item("2099-01-01T10:00:00Z", "kaputt").toFlatRows()
        assertEquals(1, rows.size)
        assertNull(rows.first().delayMinutes)
    }

    @Test
    fun toFlatRows_computesDelayForFutureDeparture() {
        val rows = item("2099-01-01T10:00:00Z", "2099-01-01T10:05:00Z").toFlatRows()
        assertEquals(1, rows.size)
        assertEquals(5L, rows.first().delayMinutes)
        assertTrue(rows.first().departureTime.startsWith("2099"))
    }

    @Test
    fun classify_tram_byNumber() {
        assertEquals(setOf(TransportType.TRAM), line("3").transportTypes)
        assertEquals(setOf(TransportType.TRAM), line("17").transportTypes)
        assertEquals(setOf(TransportType.TRAM), line("E").transportTypes)
    }

    @Test
    fun classify_bus_byHighNumberAndNightPrefix() {
        assertEquals(setOf(TransportType.BUS), line("100").transportTypes)
        assertEquals(setOf(TransportType.BUS), line("300").transportTypes)
        assertEquals(setOf(TransportType.BUS), line("N1").transportTypes)
    }

    @Test
    fun classify_sbahn_byPrefix() {
        assertEquals(setOf(TransportType.SBAHN), line("S3").transportTypes)
    }

    @Test
    fun classify_db_byPrefixAndLineId() {
        assertEquals(setOf(TransportType.DB), line("RE1").transportTypes)
        assertEquals(setOf(TransportType.DB), line(number = null, lineId = "ddb:RE60").transportTypes)
    }

    @Test
    fun classify_fernbus_byFlxPrefix() {
        assertEquals(setOf(TransportType.FERNBUS), line("FLX10").transportTypes)
    }

    @Test
    fun classify_dbAndFernbus_suppressBus() {
        // Nummer >= 100 würde sonst BUS triggern – DB/Fernbus haben Vorrang (Ausschluss-Guard)
        assertEquals(setOf(TransportType.DB), line(number = "190", lineId = "ddb:RB190").transportTypes)
        assertEquals(setOf(TransportType.FERNBUS), line(number = "350", lineId = "flx:350").transportTypes)
    }

    @Test
    fun classify_overlap_dbAndTram() {
        assertEquals(setOf(TransportType.DB, TransportType.TRAM), line(number = "3", lineId = "ddb:3").transportTypes)
    }

    @Test
    fun classify_noMatch_isEmpty() {
        assertEquals(emptySet<TransportType>(), line("??").transportTypes)
    }

    @Test
    fun derivedBooleans_matchTypes() {
        val dep = line("3")
        assertTrue(dep.isTram)
        assertFalse(dep.isBus)
        assertFalse(dep.isDB)
    }

    @Test
    fun toFlatRows_propagatesTransportTypes() {
        val rows = item("2099-01-01T10:00:00Z", "2099-01-01T10:00:00Z").toFlatRows()
        assertEquals(1, rows.size)
        assertEquals(setOf(TransportType.TRAM), rows.first().transportTypes)
        assertTrue(rows.first().isTram)
        assertFalse(rows.first().isBus)
        assertFalse(rows.first().isSBahn)
    }

    @Test
    fun classify_tram_rangeUpperBoundIsExclusiveAbove17() {
        assertEquals(emptySet<TransportType>(), line("18").transportTypes)
    }

    @Test
    fun classify_byLineName_tramAndSbahn() {
        assertEquals(setOf(TransportType.TRAM), line(number = null, line = "Stadtbahn 10").transportTypes)
        assertEquals(setOf(TransportType.SBAHN), line(number = null, line = "S-Bahn").transportTypes)
    }

    @Test
    fun classify_overlap_tramAndBus_byNameAndNumber() {
        // "Stadtbahn"-Name (TRAM) UND Nummer >= 100 (BUS) → beide, pre-existing Verhalten
        assertEquals(setOf(TransportType.TRAM, TransportType.BUS), line(number = "100", line = "Stadtbahn 100").transportTypes)
    }
}

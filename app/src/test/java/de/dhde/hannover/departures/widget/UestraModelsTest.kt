package de.dhde.hannover.departures.widget

import de.dhde.hannover.departures.widget.api.DepartureEvent
import de.dhde.hannover.departures.widget.api.DepartureItem
import de.dhde.hannover.departures.widget.api.toFlatRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressionstests für die Crash-Absicherung der Verspätungsberechnung (malformte API-Timestamps).
 * Reine JVM-Tests auf den Datenmodellen – kein Android-Context erforderlich.
 */
class UestraModelsTest {

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
}

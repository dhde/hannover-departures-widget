package de.dhde.hannover.departures.widget.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopSearchTest {
    @Test
    fun umlautAndTranscriptionMatchEachOther() {
        assertTrue(stopNameMatches("Hannover Kröpcke", "Kroepcke"))
        assertTrue(stopNameMatches("Hannover Kröpcke", "kröpcke"))
        assertTrue(stopNameMatches("Hannover Kroepcke", "Kröpcke"))
        assertTrue(stopNameMatches("Hannover Altenbekener Damm", "altenbek"))
        assertTrue(stopNameMatches("Hannover Müllerstraße", "muellerstrasse"))
        assertTrue(stopNameMatches("Hannover Ägidientorplatz", "aegidien"))
        assertTrue(stopNameMatches("Hannover Lister Platz", "lister p"))
    }

    @Test
    fun nonMatchesStayNonMatches() {
        assertFalse(stopNameMatches("Hannover Kröpcke", "Kropcke"))   // „o“ ≠ „ö“
        assertFalse(stopNameMatches("Hannover Lister Platz", "Limmer"))
    }

    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(stopNameMatches("Hannover Kröpcke", ""))
        assertTrue(stopNameMatches("Hannover Kröpcke", "  "))
    }
}

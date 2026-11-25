package de.dhde.hannover.departures.widget

import de.dhde.hannover.departures.widget.data.TransportFilter
import de.dhde.hannover.departures.widget.api.MsgItem
import de.dhde.hannover.departures.widget.data.filterMessages
import de.dhde.hannover.departures.widget.data.PlatformOption
import de.dhde.hannover.departures.widget.data.LineColor
import de.dhde.hannover.departures.widget.data.lineColor
import de.dhde.hannover.departures.widget.data.platformChipsFit
import de.dhde.hannover.departures.widget.api.FlatDeparture
import de.dhde.hannover.departures.widget.api.TransportType
import de.dhde.hannover.departures.widget.api.cleanDestination
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
    fun platformLabel_isHashPlusNumberFromName() {
        // Echte Namen aus /stops (Kröpcke, Lister Platz)
        val names = mapOf(
            "de:03241:11:1:11" to "#1/SB",
            "de:03241:11:3:16" to "SB6",
            "de:03241:11:4:17" to "Bus17",
            "de:03241:2021:3:2023" to "Bus 1"
        )
        assertEquals("#1", platformLabel("de:03241:11:1:11", names))
        assertEquals("#6", platformLabel("de:03241:11:3:16", names))
        assertEquals("#17", platformLabel("de:03241:11:4:17", names))
        assertEquals("#1", platformLabel("de:03241:2021:3:2023", names))
    }

    @Test
    fun platformLabel_fallbacks() {
        // Unbekannter Steig → letztes ID-Segment; Name ohne Zahl → Name selbst
        assertEquals("#2024", platformLabel("de:03241:2021:3:2024", emptyMap()))
        assertEquals("Nord", platformLabel("p", mapOf("p" to "Nord")))
        assertNull(platformLabel(null, emptyMap()))
        assertNull(platformLabel("", emptyMap()))
    }

    private fun dep(line: String, platform: String?, label: String?, bus: Boolean = false) = FlatDeparture(
        line = if (bus) "Bus $line" else "Stadtbahn $line", lineId = null, destination = null, number = line,
        transportTypes = setOf(if (bus) TransportType.BUS else TransportType.TRAM),
        departureTime = "2099-01-01T10:00:00Z", plannedTime = null, estimatedTime = null,
        delayMinutes = null, lineShort = line, platformId = platform, platformLabel = label
    )

    @Test
    fun availablePlatforms_colorFromMostFrequentLine_sortedByNumber() {
        // Kröpcke-Ausschnitt: #1 A-Strecke (blau), #3 B-Strecke (rot), #17 Bus, #19 SprintH
        val result = availablePlatforms(listOf(
            dep("120", "p17", "#17", bus = true),
            dep("3", "p1", "#1"), dep("7", "p1", "#1"), dep("1", "p1", "#1"),
            dep("2", "p3", "#3"),
            dep("800", "p19", "#19", bus = true),
            dep("9", null, null)
        ))
        assertEquals(listOf("#1", "#3", "#17", "#19"), result.map { it.label })
        assertEquals(
            listOf(LineColor.BLUE, LineColor.RED, LineColor.BUS, LineColor.SPRINT),
            result.map { it.color }
        )
    }

    @Test
    fun availablePlatforms_tramBeforeBusOnSameNumber() {
        // Lister Platz: SB1/SB2 und Bus 1/Bus 2 heißen beide #1/#2 → Bahn zuerst, dann Bus
        val result = availablePlatforms(listOf(
            dep("100", "b2", "#2", bus = true), dep("121", "b1", "#1", bus = true),
            dep("3", "s2", "#2"), dep("7", "s1", "#1")
        ))
        assertEquals(listOf("s1", "s2", "b1", "b2"), result.map { it.id })
    }

    @Test
    fun lineColor_matchesBadgeColors() {
        assertEquals(LineColor.RED, lineColor("8", isBus = false))
        assertEquals(LineColor.BLUE, lineColor("13", isBus = false))
        assertEquals(LineColor.YELLOW, lineColor("11", isBus = false))
        assertEquals(LineColor.GREEN, lineColor("17", isBus = false))
        assertEquals(LineColor.OTHER, lineColor("S5", isBus = false))
        assertEquals(LineColor.BUS, lineColor("121", isBus = true))
        assertEquals(LineColor.SPRINT, lineColor("500", isBus = true))
    }

    @Test
    fun platformChipsFit_kroepckeNeedsWideWidgetOrTab() {
        val all = listOf("#1", "#2", "#3", "#4", "#5", "#6", "#17", "#18", "#19")
        val tram = all.take(6)
        // ~3 Zellen (250 dp) minus Padding und Bus/Bahn-Buttons ≈ 160 dp frei
        assertFalse(platformChipsFit(all, 160))
        assertTrue(platformChipsFit(tram, 160))
        assertTrue(platformChipsFit(all, 260))
        // Ein einzelner Steig braucht keinen Filter
        assertFalse(platformChipsFit(listOf("#1"), 500))
    }

    @Test
    fun cleanDestination_stripsHannoverPrefixLikeStopDisplay() {
        assertEquals("August-Holweg-Platz", cleanDestination("Hannover August-Holweg-Platz"))
        assertEquals("Wettbergen", cleanDestination("Hannover/Wettbergen"))
        assertEquals("Altwarmbüchen", cleanDestination("Altwarmbüchen"))
        assertEquals("Hannover", cleanDestination("Hannover"))
        assertEquals("Mehrum, Ortsmitte", cleanDestination("Mehrum, Ortsmitte"))
        assertNull(cleanDestination(null))
    }

    @Test
    fun effectivePlatform_fallsBackToAllWhenNotAvailable() {
        val available = listOf(PlatformOption("p1", "#1", LineColor.BUS), PlatformOption("p2", "#2", LineColor.BUS))
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

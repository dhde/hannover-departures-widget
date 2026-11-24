package de.dhde.hannover.departures.widget

import com.google.gson.Gson
import de.dhde.hannover.departures.widget.api.DepartureItem
import de.dhde.hannover.departures.widget.api.toFlatRows
import de.dhde.hannover.departures.widget.data.withPlatformNames
import org.junit.Test
import org.junit.Assert.assertEquals

class GsonTest {
    @Test
    fun testIsCancelledMapsToApiCancelled() {
        // Das API-Feld heißt "isCancelled" und wird auf DepartureItem.apiCancelled gemappt.
        val json = """{"line": "Stadtbahn 3", "isCancelled": true}"""
        val item = Gson().fromJson(json, DepartureItem::class.java)
        assertEquals(true, item.apiCancelled)
    }

    @Test
    fun bonMapsToPlatformAndSurvivesCacheRoundTrip() {
        // Echte Form aus der Lister-Platz-Antwort: "bon" = Steig-ID.
        val json = """{"line": "Bus 121", "number": "121", "lineId": "gvh%3A03121%3AH",
            "bon": "de:03241:2021:3:2023",
            "events": [{"plannedTime": "2099-01-01T10:00:00.000Z"}]}"""
        val item = Gson().fromJson(json, DepartureItem::class.java)
        assertEquals("de:03241:2021:3:2023", item.platformId)

        val enriched = listOf(item).withPlatformNames(mapOf("de:03241:2021:3:2023" to "Bus 1"))
        // Widget liest aus dem JSON-Cache → Name muss Serialisierung überleben.
        val cached = Gson().fromJson(Gson().toJson(enriched.first()), DepartureItem::class.java)
        val row = cached.toFlatRows().single()
        assertEquals("de:03241:2021:3:2023", row.platformId)
        assertEquals("Bus 1", row.platformLabel)
    }
}

package de.dhde.hannover.departures.widget

import com.google.gson.Gson
import de.dhde.hannover.departures.widget.api.DepartureItem
import org.junit.Test
import org.junit.Assert.assertEquals

class GsonTest {
    @Test
    fun testIsCancelled() {
        val json = """{"line": "Stadtbahn 3", "isCancelled": true}"""
        val item = Gson().fromJson(json, DepartureItem::class.java)
        assertEquals(true, item.isCancelled)
    }
}

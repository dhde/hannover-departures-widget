package de.dhde.hannover.departures.widget

import de.dhde.hannover.departures.widget.api.stripHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StripHtmlTest {

    @Test
    fun stripHtml_removesTagsDecodesEntitiesDropsLinkUrl() {
        val raw = "<div class=\"ems-activity ems-content\">\n" +
            "Aufgrund von Gleisbauarbeiten &amp; Sperrung.<br>" +
            "Details <a href=\"https://download.transdev.de/x.pdf\" target=\"_blank\" rel=\"noopener\">hier</a> abrufen.\n" +
            "</div>"
        val out = stripHtml(raw)
        assertFalse("keine spitzen Klammern mehr", out.contains("<") || out.contains(">"))
        assertFalse("keine Tag-Attribute mehr", out.contains("href") || out.contains("ems-activity"))
        assertFalse("Link-URL entfällt", out.contains("https://download.transdev.de"))
        assertTrue("Entity dekodiert", out.contains("Gleisbauarbeiten & Sperrung."))
        assertTrue("Linktext bleibt", out.contains("Details"))
        assertTrue("Linktext bleibt", out.contains("hier abrufen."))
    }

    @Test
    fun stripHtml_plainTextUnchanged() {
        assertEquals("Fahrt entfällt heute", stripHtml("Fahrt entfällt heute"))
    }

    @Test
    fun stripHtml_collapsesExcessBlankLines() {
        assertEquals("A\n\nB", stripHtml("A<br><br><br><br>B"))
    }
}

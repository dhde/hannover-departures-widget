package de.dhde.hannover.departures.widget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TimeFormatTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    // 2026-10-28 14:07:30 Europe/Berlin (MEZ, UTC+1)
    private val dep = Instant.parse("2026-10-28T13:07:30Z")

    // ── Countdown ─────────────────────────────────────────────────────────

    @Test
    fun countdown_default_dropsZeroParts() {
        val p = TimeFormat.DEFAULT_COUNTDOWN
        assertEquals("7 Min", TimeFormat.formatCountdown(p, 7 * 60 + 42))   // > 120 s: Sekunden = 0
        assertEquals("1 Min 42s", TimeFormat.formatCountdown(p, 102))
        assertEquals("1 Min", TimeFormat.formatCountdown(p, 60))
        assertEquals("35s", TimeFormat.formatCountdown(p, 35))
    }

    @Test
    fun countdown_secondsCappedAbove120() {
        assertEquals("2 Min", TimeFormat.formatCountdown(TimeFormat.DEFAULT_COUNTDOWN, 121))
        assertEquals("2 Min", TimeFormat.formatCountdown(TimeFormat.DEFAULT_COUNTDOWN, 120))
    }

    @Test
    fun countdown_twoDigitSeconds() {
        assertEquals("1:05", TimeFormat.formatCountdown("{m}:{ss}", 65))
        assertEquals("0:35", TimeFormat.formatCountdown("{m}:{ss}", 35))
    }

    @Test
    fun countdown_presets() {
        assertEquals("7 Min", TimeFormat.formatCountdown("{m} Min", 7 * 60 + 42))
        assertEquals("7'", TimeFormat.formatCountdown("{m}'", 7 * 60 + 42))
    }

    @Test
    fun countdown_bracketWithoutPlaceholderIsKept() {
        assertEquals("noch 35s", TimeFormat.formatCountdown("[noch ][{m} Min ]{s}s", 35))
    }

    @Test
    fun countdown_invalidPatternFallsBackToDefault() {
        assertEquals("35s", TimeFormat.formatCountdown("ohne Platzhalter", 35))
    }

    @Test
    fun countdown_validation() {
        assertTrue(TimeFormat.isValidCountdownPattern(TimeFormat.DEFAULT_COUNTDOWN))
        assertTrue(TimeFormat.isValidCountdownPattern("{m}'"))
        assertFalse("kein Platzhalter", TimeFormat.isValidCountdownPattern("Min"))
        assertFalse("offene Klammer", TimeFormat.isValidCountdownPattern("[{m} Min"))
        assertFalse("schließende Klammer ohne öffnende", TimeFormat.isValidCountdownPattern("{m}] Min"))
        assertFalse("verschachtelt", TimeFormat.isValidCountdownPattern("[[{m}]]"))
        assertFalse("unbekannter Platzhalter", TimeFormat.isValidCountdownPattern("{h}:{m}"))
    }

    @Test
    fun countdown_blankResultFallsBackToDefault() {
        assertEquals("35s", TimeFormat.formatCountdown("[{m} Min ]", 35))
        assertEquals("3 Min", TimeFormat.formatCountdown("[{s}s]", 180))
    }

    @Test
    fun countdown_secondsPrecisionBoundary() {
        assertEquals("1 Min 59s", TimeFormat.formatCountdown(TimeFormat.DEFAULT_COUNTDOWN, 119))
        assertEquals("2 Min", TimeFormat.formatCountdown(TimeFormat.DEFAULT_COUNTDOWN, 121))
        assertEquals("2:00", TimeFormat.formatCountdown("{m}:{ss}", 130))
    }

    @Test
    fun showsSeconds() {
        assertTrue(TimeFormat.showsSeconds("MIN", TimeFormat.DEFAULT_COUNTDOWN))
        assertTrue(TimeFormat.showsSeconds("MIN", "{m}:{ss}"))
        assertFalse(TimeFormat.showsSeconds("MIN", "{m} Min"))
        assertFalse(TimeFormat.showsSeconds("MIN", "{m}'"))
        assertFalse("Uhrzeit-Modus", TimeFormat.showsSeconds("CLOCK", TimeFormat.DEFAULT_COUNTDOWN))
        assertFalse("Legacy CLOCK_SEC", TimeFormat.showsSeconds("CLOCK_SEC", "{m}:{ss}"))
        assertTrue("ungültig → Default mit Sekunden", TimeFormat.showsSeconds("MIN", "ohne Platzhalter"))
    }

    @Test
    fun isStillVisible() {
        val iso = dep.toString()
        assertTrue(TimeFormat.isStillVisible(iso, dep.minusSeconds(60)))
        assertTrue(TimeFormat.isStillVisible(iso, dep.plusSeconds(9)))
        assertFalse(TimeFormat.isStillVisible(iso, dep.plusSeconds(10)))
        assertFalse(TimeFormat.isStillVisible(iso, dep.plusSeconds(60)))
        assertTrue("unparsebar bleibt sichtbar", TimeFormat.isStillVisible("kaputt", dep))
        assertTrue("null bleibt sichtbar", TimeFormat.isStillVisible(null, dep))
    }

    // ── Uhrzeit ───────────────────────────────────────────────────────────

    @Test
    fun clock_presets() {
        assertEquals("14:07", TimeFormat.formatClock("HH:mm", dep, berlin))
        assertEquals("14:07:30", TimeFormat.formatClock("HH:mm:ss", dep, berlin))
        assertEquals("14.07", TimeFormat.formatClock("HH.mm", dep, berlin))
        assertTrue(TimeFormat.formatClock("h:mm a", dep, berlin).startsWith("2:07"))
        assertEquals("14:07 Uhr", TimeFormat.formatClock("HH:mm 'Uhr'", dep, berlin))
    }

    @Test
    fun clock_validationAndFallback() {
        assertTrue(TimeFormat.isValidClockPattern("HH:mm"))
        assertFalse("leer", TimeFormat.isValidClockPattern(""))
        assertFalse("ungültiger Buchstabe", TimeFormat.isValidClockPattern("HH:mm Uhr"))
        assertEquals("14:07", TimeFormat.formatClock("HH:mm Uhr", dep, berlin))
    }

    // ── Abfahrtszeit gesamt ───────────────────────────────────────────────

    @Test
    fun departureTime_modes() {
        val iso = dep.toString()
        val now = dep.minusSeconds(102)
        assertEquals("1 Min 42s", TimeFormat.formatDepartureTime("MIN", iso, TimeFormat.DEFAULT_COUNTDOWN, "HH:mm", false, now, berlin))
        assertEquals("14:07:30", TimeFormat.formatDepartureTime("CLOCK", iso, TimeFormat.DEFAULT_COUNTDOWN, "HH:mm:ss", false, now, berlin))
    }

    @Test
    fun departureTime_departedOrInvalid() {
        val iso = dep.toString()
        assertEquals("jetzt", TimeFormat.formatDepartureTime("MIN", iso, TimeFormat.DEFAULT_COUNTDOWN, "HH:mm", false, dep, berlin))
        assertEquals("evtl. weg?", TimeFormat.formatDepartureTime("MIN", iso, TimeFormat.DEFAULT_COUNTDOWN, "HH:mm", true, dep.plusSeconds(5), berlin))
        assertEquals("?", TimeFormat.formatDepartureTime("MIN", "kaputt", TimeFormat.DEFAULT_COUNTDOWN, "HH:mm", false, dep, berlin))
        assertEquals("?", TimeFormat.formatDepartureTime("MIN", null, TimeFormat.DEFAULT_COUNTDOWN, "HH:mm", false, dep, berlin))
    }

    @Test
    fun shortTime() {
        val iso = dep.toString()
        assertEquals("5m", TimeFormat.formatShortTime("MIN", iso, dep.minusSeconds(5 * 60 + 30), berlin))
        assertEquals("0m", TimeFormat.formatShortTime("MIN", iso, dep.minusSeconds(30), berlin))
        assertEquals("14:07", TimeFormat.formatShortTime("CLOCK", iso, dep, berlin))
        assertEquals("?", TimeFormat.formatShortTime("MIN", null, dep, berlin))
    }

    @Test
    fun normalizeMode_mapsLegacyClockSec() {
        assertEquals("CLOCK", TimeFormat.normalizeMode("CLOCK_SEC"))
        assertEquals("CLOCK", TimeFormat.normalizeMode("CLOCK"))
        assertEquals("MIN", TimeFormat.normalizeMode("MIN"))
        assertEquals("MIN", TimeFormat.normalizeMode(null))
    }
}

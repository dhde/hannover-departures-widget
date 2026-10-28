package de.dhde.hannover.departures.widget.data

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Konfigurierbare Zeitformate für die Abfahrtszeit (Optionen → Zeitformat).
 *
 * Countdown-Muster: {m} Minuten, {s} Sekunden, {ss} Sekunden zweistellig.
 * Ein Teil in [ … ] entfällt, wenn alle Platzhalter darin 0 sind.
 * Uhrzeit-Muster: java.time.format.DateTimeFormatter.
 */
object TimeFormat {
    const val MODE_MIN = "MIN"
    const val MODE_CLOCK = "CLOCK"

    const val DEFAULT_COUNTDOWN = "[{m} Min ][{s}s]"
    const val DEFAULT_CLOCK = "HH:mm"
    private const val SHORT_CLOCK = "HH:mm"

    /** Muster to Anzeige-Label (Beispiel statt Muster). */
    val COUNTDOWN_PRESETS: List<Pair<String, String>> = listOf(
        "{m} Min" to "7 Min",
        DEFAULT_COUNTDOWN to "7 Min 42s",
        "{m}'" to "7'",
    )
    val CLOCK_PRESETS: List<Pair<String, String>> = listOf(
        DEFAULT_CLOCK to "14:07",
        "HH:mm:ss" to "14:07:30",
        "HH.mm" to "14.07",
        "h:mm a" to "2:07 PM",
    )

    /** Darüber tickt das Widget nur minütlich – Sekunden zählen dann als 0. */
    private const val SECONDS_PRECISION_LIMIT = 120L

    private val PLACEHOLDER = Regex("\\{(m|s|ss)\\}")
    private val ANY_PLACEHOLDER = Regex("\\{[^}]*\\}")
    private val BRACKET_PART = Regex("\\[([^\\]]*)\\]")

    /** Früherer Modus "CLOCK_SEC" wird zu "CLOCK"; alles andere zu "MIN". */
    fun normalizeMode(mode: String?): String =
        if (mode == MODE_CLOCK || mode == "CLOCK_SEC") MODE_CLOCK else MODE_MIN

    fun isValidCountdownPattern(pattern: String): Boolean {
        if (ANY_PLACEHOLDER.findAll(pattern).any { !PLACEHOLDER.matches(it.value) }) return false
        if (!PLACEHOLDER.containsMatchIn(pattern)) return false
        var open = false
        for (c in pattern) {
            when (c) {
                '[' -> if (open) return false else open = true
                ']' -> if (!open) return false else open = false
            }
        }
        return !open
    }

    fun isValidClockPattern(pattern: String): Boolean {
        if (pattern.isBlank()) return false
        return try {
            DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.of("UTC")).format(Instant.EPOCH)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Formatiert einen Countdown; nur für [secs] > 0 gedacht. */
    fun formatCountdown(pattern: String, secs: Long): String {
        val p = if (isValidCountdownPattern(pattern)) pattern else DEFAULT_COUNTDOWN
        val m = secs / 60
        val s = if (secs > SECONDS_PRECISION_LIMIT) 0L else secs % 60

        fun fill(text: String): String = PLACEHOLDER.replace(text) { match ->
            when (match.groupValues[1]) {
                "m" -> m.toString()
                "s" -> s.toString()
                else -> s.toString().padStart(2, '0')
            }
        }

        val withoutBrackets = BRACKET_PART.replace(p) { match ->
            val inner = match.groupValues[1]
            val values = PLACEHOLDER.findAll(inner).map { if (it.groupValues[1] == "m") m else s }.toList()
            if (values.isNotEmpty() && values.all { it == 0L }) "" else fill(inner)
        }
        // Ein gültiges Muster kann leer ergeben (z. B. "[{m} Min ]" bei 35 s) – dann Default.
        // Leeres Ergebnis (z. B. "[{m} Min ]" bei 35 s) → Standardmuster; beim Standard selbst nur Sekunden
        return fill(withoutBrackets).trim().ifBlank {
            if (p != DEFAULT_COUNTDOWN) formatCountdown(DEFAULT_COUNTDOWN, secs) else "${secs}s"
        }
    }

    /** Zeigt die Hauptzeit Sekunden an (dann braucht das Widget den 10-s-Takt)? */
    fun showsSeconds(mode: String, countdownPattern: String): Boolean {
        if (normalizeMode(mode) != MODE_MIN) return false
        val p = if (isValidCountdownPattern(countdownPattern)) countdownPattern else DEFAULT_COUNTDOWN
        return PLACEHOLDER.findAll(p).any { it.groupValues[1] != "m" }
    }

    /** Abfahrten bleiben bis 10 s nach Abfahrt sichtbar; unparsebare Zeiten bleiben stehen. */
    fun isStillVisible(isoTime: String?, now: Instant = Instant.now()): Boolean {
        val dep = parse(isoTime) ?: return true
        return Duration.between(now, dep).seconds > -10
    }

    fun formatClock(pattern: String, instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val p = if (isValidClockPattern(pattern)) pattern else DEFAULT_CLOCK
        return DateTimeFormatter.ofPattern(p).withZone(zone).format(instant)
    }

    /** Hauptzeit einer Abfahrtszeile (Widget und App-Vorschau). */
    fun formatDepartureTime(
        mode: String,
        isoTime: String?,
        countdownPattern: String,
        clockPattern: String,
        isWarning: Boolean,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val dep = parse(isoTime) ?: return "?"
        if (normalizeMode(mode) == MODE_CLOCK) return formatClock(clockPattern, dep, zone)
        val secs = Duration.between(now, dep).seconds
        return if (secs <= 0) (if (isWarning) "evtl. weg?" else "jetzt") else formatCountdown(countdownPattern, secs)
    }

    /** Kurzzeile der gruppierten Folgeabfahrten – bewusst fest und kompakt. */
    fun formatShortTime(
        mode: String,
        isoTime: String?,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val dep = parse(isoTime) ?: return "?"
        if (normalizeMode(mode) == MODE_CLOCK) return formatClock(SHORT_CLOCK, dep, zone)
        val m = Duration.between(now, dep).toMinutes()
        return if (m <= 0) "0m" else "${m}m"
    }

    private fun parse(isoTime: String?): Instant? =
        try { isoTime?.let(Instant::parse) } catch (_: DateTimeParseException) { null }
}

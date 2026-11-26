package de.dhde.hannover.departures.widget.data

/**
 * Normalisiert für die Haltestellensuche: Kleinschreibung und Umlaute in Umschrift
 * (ä→ae, ö→oe, ü→ue, ß→ss). So finden „Kroepcke“ und „Kröpcke“ dasselbe – wichtig,
 * wenn die Tastatur keine Umlaute hat (oder per adb getippt wird).
 */
fun normalizeForSearch(text: String): String =
    text.lowercase()
        .replace("ä", "ae")
        .replace("ö", "oe")
        .replace("ü", "ue")
        .replace("ß", "ss")

/** Enthält der Haltestellenname die Suchanfrage (umlaut- und großschreibungs-tolerant)? */
fun stopNameMatches(name: String, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return normalizeForSearch(name).contains(normalizeForSearch(q))
}

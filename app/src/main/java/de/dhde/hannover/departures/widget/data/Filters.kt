package de.dhde.hannover.departures.widget.data

/** Verkehrsmittel-Filter (Tab). Kapselt die Legacy-Speicherform; "TRAIN" = Tram. */
enum class TransportFilter(val storageValue: String) {
    ALL("ALL"),
    BUS("BUS"),
    TRAM("TRAIN");   // "TRAIN" = Legacy-Speicherwert für Tram (nicht migriert)

    companion object {
        fun fromStorage(value: String?): TransportFilter =
            entries.firstOrNull { it.storageValue == value } ?: ALL
    }
}

/**
 * Steig-Filter statt Fahrtrichtung.
 *
 * Hintergrund: Die EFA-lineId-Endung „H"/„R" heißt nur Hin-/Rückfahrt der jeweiligen Linie
 * und ist pro Linie willkürlich festgelegt. Bei Bussen fahren dadurch Linien mit gleicher
 * Endung in entgegengesetzte Richtungen (z.B. Lister Platz: 100 „H" an Bus 2, 121 „H" an Bus 1).
 * Zuverlässig ist nur der Steig (`bon` der Abfahrt = `platforms[].id` aus /stops):
 * alles, was vom selben Steig abfährt, fährt in dieselbe Richtung.
 */
data class PlatformOption(val id: String, val label: String)

/** Anzeigename eines Steigs; Fallback „Steig <letztes ID-Segment>", wenn /stops den Steig nicht kennt. */
fun platformLabel(platformId: String?, names: Map<String, String>): String? {
    if (platformId.isNullOrBlank()) return null
    return names[platformId]?.takeIf { it.isNotBlank() } ?: "Steig ${platformId.substringAfterLast(':')}"
}

private val NUMBER_REGEX = Regex("\\d+")

/** Sortierschlüssel mit natürlicher Zahlensortierung („Bus 2" vor „Bus 10"). */
private fun naturalKey(label: String): String =
    label.lowercase().replace(NUMBER_REGEX) { it.value.padStart(6, '0') }

/** Anwählbare Steige aus (platformId, label)-Paaren: ohne Duplikate/Unbekannte, natürlich nach Label sortiert. */
fun availablePlatforms(platforms: List<Pair<String?, String?>>): List<PlatformOption> =
    platforms
        .mapNotNull { (id, label) -> if (id.isNullOrBlank() || label.isNullOrBlank()) null else PlatformOption(id, label) }
        .distinctBy { it.id }
        .sortedBy { naturalKey(it.label) }

/**
 * Tatsächlich anzuwendender Steig-Filter: der gespeicherte Steig, sofern er unter den
 * aktuell anwählbaren ist – sonst null (= alle). Verhindert eine leere Liste, wenn der
 * gemerkte Steig gerade nicht bedient wird oder per Tab ausgeblendet ist.
 */
fun effectivePlatform(selected: String?, available: List<PlatformOption>): String? =
    selected?.takeIf { sel -> sel.isNotBlank() && available.any { it.id == sel } }

/** Passt eine Abfahrt (Steig [platformId]) zum Filter [selected]? null-Filter = alle. */
fun platformMatches(platformId: String?, selected: String?): Boolean =
    selected == null || platformId == selected

package de.dhde.hannover.departures.widget.data

import de.dhde.hannover.departures.widget.api.FlatDeparture

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
data class PlatformOption(
    val id: String,
    val label: String,
    val color: LineColor,
    /** Linien an diesem Steig, numerisch sortiert (für die Steig-Auswahl). */
    val lines: List<String> = emptyList(),
    /** Ziele an diesem Steig, häufigstes zuerst (für die Steig-Auswahl). */
    val destinations: List<String> = emptyList()
)

/** GVH-Linienfarbe (Stadtbahn-Strecken, Bus). UI-Farbwerte: UestraColors.lineColors(). */
enum class LineColor { BLUE, RED, YELLOW, GREEN, BUS, SPRINT, OTHER }

/** Einzige Quelle der Linienfarbe – für Linien-Badges und Steig-Chips. */
fun lineColor(line: String, isBus: Boolean): LineColor =
    if (isBus) {
        // SprintH-Linien: 300, 400, … 900
        if (line.length == 3 && line[0] in '3'..'9' && line.substring(1) == "00") LineColor.SPRINT else LineColor.BUS
    } else when (line) {
        "1", "2", "8" -> LineColor.RED          // B-Strecke
        "3", "7", "9", "13" -> LineColor.BLUE   // A-Strecke
        "4", "5", "6", "11" -> LineColor.YELLOW // C-Strecke
        "10", "17" -> LineColor.GREEN           // D-Strecke
        else -> LineColor.OTHER
    }

private val NUMBER_REGEX = Regex("\\d+")

/**
 * /stops-Name eines Steigs. Die Abfahrten liefern die Steig-ID teils mit anderem Bereichs-Segment
 * als /stops (Noltemeyerbrücke: Abfahrt „…:2171:11:2174", /stops „…:2171:2:2174" = „Bus 4").
 * Das letzte Segment ist je Haltestelle eindeutig → Fallback-Zuordnung darüber.
 */
fun platformName(platformId: String, names: Map<String, String>): String? {
    names[platformId]?.takeIf { it.isNotBlank() }?.let { return it }
    val last = platformId.substringAfterLast(':')
    return names.entries.firstOrNull { it.key.substringAfterLast(':') == last }?.value?.takeIf { it.isNotBlank() }
}

/**
 * Chip-Beschriftung eines Steigs: „#<Nummer>" aus dem /stops-Namen („#1/SB", „SB6", „Bus17", „Bus 1").
 * Das „#" grenzt den Steig von Liniennummern ab. Name ohne Zahl → Name; unbekannter Steig → null
 * (wird in [platformLabels] fortlaufend nummeriert).
 */
fun platformLabel(platformId: String?, names: Map<String, String>): String? {
    if (platformId.isNullOrBlank()) return null
    val name = platformName(platformId, names) ?: return null
    return NUMBER_REGEX.find(name)?.let { "#${it.value}" } ?: name
}

/**
 * Labels für alle Steige einer Antwort. Unbekannte Steige (nicht in /stops, z.B. offline) bekommen
 * keine interne ID mehr („#2174"), sondern fortlaufende Nummern hinter der höchsten bekannten –
 * sortiert nach Steig-ID, damit sie auf jedem Gerät gleich ausfallen.
 */
fun platformLabels(platformIds: Collection<String?>, names: Map<String, String>): Map<String, String> {
    val ids = platformIds.filterNotNull().filter { it.isNotBlank() }.distinct()
    val labels = ids.mapNotNull { id -> platformLabel(id, names)?.let { id to it } }.toMap().toMutableMap()
    val maxKnown = (labels.values + names.values)
        .mapNotNull { NUMBER_REGEX.find(it)?.value?.toIntOrNull() }
        .filter { it < 1000 } // keine ID-artigen Nummern hochzählen
        .maxOrNull() ?: 0
    ids.filter { it !in labels }.sorted().forEachIndexed { i, id -> labels[id] = "#${maxKnown + i + 1}" }
    return labels
}

/** Sortierschlüssel mit natürlicher Zahlensortierung („#2" vor „#10"). */
private fun naturalKey(label: String): String =
    label.lowercase().replace(NUMBER_REGEX) { it.value.padStart(6, '0') }

/**
 * Anwählbare Steige aus den Abfahrten. Farbe = Linienfarbe der meisten dort fahrenden Linien.
 * Sortierung: Bahn-Steige vor Bus-Steigen (Lister Platz hat #1/#2 für beides), dann nach Nummer.
 */
fun availablePlatforms(departures: List<FlatDeparture>): List<PlatformOption> =
    departures
        .filter { !it.platformId.isNullOrBlank() && !it.platformLabel.isNullOrBlank() }
        .groupBy { it.platformId!! }
        .map { (id, deps) ->
            val color = deps.distinctBy { it.lineShort }
                .groupingBy { lineColor(it.lineShort, it.isBus) }.eachCount()
                .maxByOrNull { it.value }!!.key
            val lines = deps.map { it.lineShort }.distinct()
                .sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))
            val destinations = deps.mapNotNull { it.destination?.takeIf(String::isNotBlank) }
                .groupingBy { it }.eachCount().entries
                .sortedByDescending { it.value }.map { it.key }
            PlatformOption(id, deps.first().platformLabel!!, color, lines, destinations)
        }
        .sortedWith(compareBy(
            { if (it.color == LineColor.BUS || it.color == LineColor.SPRINT) 1 else 0 },
            { naturalKey(it.label) }
        ))

/** Horizontales Innen-Padding eines Steig-Chips (dp). */
const val PLATFORM_CHIP_PADDING_DP = 4
/** Abstand zwischen Steig-Chips (dp). */
const val PLATFORM_CHIP_GAP_DP = 2
/** Konservative Zeichenbreite für 10sp fett (dp) – lieber Chips ausblenden als abschneiden. */
private const val CHIP_CHAR_DP = 7

/** Geschätzte Breite der Chip-Reihe in dp; jeder Chip mindestens [minChipDp] breit (= Bus/Bahn-Button). */
fun platformChipsWidthDp(labels: List<String>, minChipDp: Int = 0): Int =
    labels.sumOf { maxOf(minChipDp, 2 * PLATFORM_CHIP_PADDING_DP + it.length * CHIP_CHAR_DP) } +
        (labels.size - 1).coerceAtLeast(0) * PLATFORM_CHIP_GAP_DP

/**
 * Steig-Chips nur zeigen, wenn es mindestens zwei gibt und ALLE in [availableDp] passen.
 * Glance kann nicht horizontal scrollen; passen sie nicht, zeigt das Widget stattdessen
 * einen „Steig“-Button mit vertikal scrollbarer Auswahl.
 */
fun platformChipsFit(labels: List<String>, availableDp: Int, minChipDp: Int = 0): Boolean =
    labels.size in 2..MAX_PLATFORM_CHIPS && platformChipsWidthDp(labels, minChipDp) <= availableDp

/**
 * Glance-Row/Column erlauben max. 10 Kinder; mehr werden STILL abgeschnitten
 * („Truncated Row container from 11 to 10 elements“). Chips daher ohne Spacer-Kinder
 * rendern (Abstand als Padding) und nie mehr als 10 Chips.
 */
const val MAX_PLATFORM_CHIPS = 10

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

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
data class PlatformOption(val id: String, val label: String, val color: LineColor)

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
 * Chip-Beschriftung eines Steigs: „#<Nummer>" aus dem /stops-Namen („#1/SB", „SB6", „Bus17", „Bus 1").
 * Das „#" grenzt den Steig von Liniennummern ab. Name ohne Zahl → Name; unbekannter Steig → letztes ID-Segment.
 */
fun platformLabel(platformId: String?, names: Map<String, String>): String? {
    if (platformId.isNullOrBlank()) return null
    val name = names[platformId]?.takeIf { it.isNotBlank() }
        ?: return "#${platformId.substringAfterLast(':')}"
    return NUMBER_REGEX.find(name)?.let { "#${it.value}" } ?: name
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
            PlatformOption(id, deps.first().platformLabel!!, color)
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

/** Geschätzte Breite der Chip-Reihe in dp. */
fun platformChipsWidthDp(labels: List<String>): Int =
    labels.sumOf { 2 * PLATFORM_CHIP_PADDING_DP + it.length * CHIP_CHAR_DP } +
        (labels.size - 1).coerceAtLeast(0) * PLATFORM_CHIP_GAP_DP

/**
 * Steig-Chips nur zeigen, wenn es mindestens zwei gibt und ALLE in [availableDp] passen.
 * Glance kann nicht scrollen; eine abgeschnittene Reihe würde Steige verschlucken.
 */
fun platformChipsFit(labels: List<String>, availableDp: Int): Boolean =
    labels.size >= 2 && platformChipsWidthDp(labels) <= availableDp

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

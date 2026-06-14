package de.dhde.hannover.departures.widget.api

import com.google.gson.annotations.SerializedName
import java.time.Instant
import java.time.Duration

/** Verkehrsmittel-Kategorien für Filter und Badges. */
enum class TransportType { TRAM, BUS, SBAHN, DB, FERNBUS }

/**
 * Ersetzt das alte EFA-Modell durch das vom ÜSTRA-Proxy gelieferte Custom-JSON.
 */
data class UestraDepartureResponse(
    @SerializedName("stop") val stop: String?,
    @SerializedName("departures") val departures: List<DepartureItem>? = null
)

data class DepartureItem(
    @SerializedName("line") val line: String?,
    @SerializedName("lineId") val lineId: String?,
    @SerializedName("destination") val destination: String?,
    @SerializedName("number") val number: String?,
    @SerializedName("events") val events: List<DepartureEvent>? = null,
    @SerializedName("isCancelled") val apiCancelled: Boolean? = false,
    @SerializedName("infos") val infos: List<DepartureInfo>? = null,
    @SerializedName("hints") val hints: List<DepartureHint>? = null
) {
    /**
     * Einzige Quelle der Verkehrsmittel-Klassifikation. Bewusst computed get()
     * statt by lazy: Gson alloziert DepartureItem via Unsafe ohne Konstruktor,
     * ein lazy-Delegate-Feld wäre null → NPE.
     */
    val transportTypes: Set<TransportType> get() {
        val s = lineShort.uppercase()
        val num = s.toIntOrNull()

        val fernbus = s.startsWith("FLX") ||
            line?.contains("Fernbus", ignoreCase = true) == true ||
            lineId?.contains("flx%3A", ignoreCase = true) == true ||
            lineId?.contains("flx:", ignoreCase = true) == true

        val db = run {
            if (s.startsWith("S") && s.length <= 2 && num == null) return@run false
            if (line?.contains("S-Bahn", ignoreCase = true) == true) return@run false
            val idMatch = lineId?.contains("ddb%3A", ignoreCase = true) == true ||
                lineId?.contains("ddb:", ignoreCase = true) == true ||
                lineId?.contains("db%3A", ignoreCase = true) == true ||
                lineId?.contains("db:", ignoreCase = true) == true ||
                lineId?.contains("met%3A", ignoreCase = true) == true ||
                lineId?.contains("erx%3A", ignoreCase = true) == true
            if (idMatch) return@run true
            s.startsWith("RE") || s.startsWith("RB") ||
                s.startsWith("IC") || s.startsWith("EC") || s.startsWith("EN") ||
                s.startsWith("TGV") ||
                line?.contains("Regionalbahn", ignoreCase = true) == true
        }

        val tram = line?.contains("Stadtbahn", ignoreCase = true) == true ||
            (num != null && num in 1..17) ||
            s == "E"

        val bus = !fernbus && !db && (
            line?.contains("Bus", ignoreCase = true) == true ||
            (num != null && num >= 100) ||
            s.startsWith("N")
        )

        val sbahn = !db && !fernbus && (
            line?.contains("S-Bahn", ignoreCase = true) == true ||
            (s.startsWith("S") && s.length <= 2)
        )

        return buildSet {
            if (tram) add(TransportType.TRAM)
            if (bus) add(TransportType.BUS)
            if (sbahn) add(TransportType.SBAHN)
            if (db) add(TransportType.DB)
            if (fernbus) add(TransportType.FERNBUS)
        }
    }

    // Abgeleitete Hilfsabfragen für das UI-Filtering (Namen/Verhalten unverändert)
    val isFernbus: Boolean get() = TransportType.FERNBUS in transportTypes
    val isDB: Boolean get() = TransportType.DB in transportTypes
    val isBus: Boolean get() = TransportType.BUS in transportTypes
    val isTram: Boolean get() = TransportType.TRAM in transportTypes
    val isSBahn: Boolean get() = TransportType.SBAHN in transportTypes

    // Die nächste verfügbare Abfahrtszeit (Echtzeit bevorzugt, abgelaufene Events überspringen)
    val nextDepartureTime: String?
        get() {
            val now = java.time.Instant.now().minusSeconds(60)
            val nextEvent = events?.firstOrNull { event ->
                val t = event.estimatedTime ?: event.plannedTime
                t?.let { runCatching { java.time.Instant.parse(it).isAfter(now) }.getOrDefault(true) } == true
            } ?: events?.firstOrNull()
            return nextEvent?.estimatedTime ?: nextEvent?.plannedTime
        }

    // Die Liniennummer (z.B. "3", "300")
    val lineShort: String get() = number ?: line?.substringAfter(" ") ?: "??"

    // Verspätung in Minuten
    val delayMinutes: Long? get() {
        val event = events?.firstOrNull() ?: return null
        val planned = event.plannedTime ?: return null
        val estimated = event.estimatedTime ?: return null
        // Defensive: malformte Timestamps aus der API dürfen nicht crashen (vgl. nextDepartureTime/toFlatRows)
        return runCatching {
            val diff = Duration.between(Instant.parse(planned), Instant.parse(estimated)).toMinutes()
            if (diff > 0) diff else null
        }.getOrNull()
    }

    /**
     * Anzeigbare Meldungen mit stabiler ID und Startdatum.
     * infos tragen i.d.R. eine API-ID ("ems-…") und ein incidentStart; hints (Fahrzeug-Attribute)
     * haben beides nicht und bekommen eine aus dem Inhalt abgeleitete ID.
     */
    val messageItems: List<MsgItem> get() = buildList {
        infos?.forEach { info ->
            val c = info.content?.let { stripHtml(it) }?.takeIf { it.isNotBlank() } ?: return@forEach
            val id = info.id?.takeIf { it.isNotBlank() } ?: "c:${c.hashCode()}"
            val start = info.incidentStart
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
            add(MsgItem(id = id, content = c, startMillis = start))
        }
        hints?.forEach { hint ->
            val c = hint.content?.let { stripHtml(it) }?.takeIf { it.isNotBlank() } ?: return@forEach
            add(MsgItem(id = "h:${c.hashCode()}", content = c, startMillis = 0L))
        }
    }
}

data class DepartureEvent(
    @SerializedName("plannedTime") val plannedTime: String?,
    // Die API liefert "estimated_time" (snake_case), NICHT "estimatedTime" (camelCase)!
    @SerializedName("estimated_time") val estimatedTime: String?,
    @SerializedName("isCancelled") val apiCancelled: Boolean? = false,
    @SerializedName("realtimeState") val realtimeState: String? = null
)

/**
 * Eine einzelne, flach aufgelöste Abfahrt (Linie + Richtung + konkreter Abfahrtszeitpunkt).
 * Erzeugt aus DepartureItem.toFlatRows() – jedes Event wird zu einer eigenen Zeile.
 */
data class FlatDeparture(
    val line: String?,
    val lineId: String?,
    val destination: String?,
    val number: String?,
    val transportTypes: Set<TransportType>,
    val departureTime: String,   // Echtzeit, falls vorhanden, sonst Planzeit
    val plannedTime: String?,
    val estimatedTime: String?,
    val delayMinutes: Long?,
    val lineShort: String,
    val isCancelled: Boolean = false,
    val messages: List<MsgItem> = emptyList()
) {
    val isBus: Boolean get() = TransportType.BUS in transportTypes
    val isTram: Boolean get() = TransportType.TRAM in transportTypes
    val isSBahn: Boolean get() = TransportType.SBAHN in transportTypes   // S-Bahn; DB-Fernverkehr ist isDB
    val isDB: Boolean get() = TransportType.DB in transportTypes
    val isFernbus: Boolean get() = TransportType.FERNBUS in transportTypes
}

/** Klappt alle zukünftigen Events eines DepartureItem zu einzelnen FlatDeparture-Zeilen auf. */
fun DepartureItem.toFlatRows(cutoffSeconds: Long = 60): List<FlatDeparture> {
    val now = Instant.now().minusSeconds(cutoffSeconds)
    val types = transportTypes
    return events.orEmpty()
        .filter { event ->
            val t = event.estimatedTime ?: event.plannedTime
            t?.let { runCatching { Instant.parse(it).isAfter(now) }.getOrDefault(true) } == true
        }
        .map { event ->
            val dept = event.estimatedTime ?: event.plannedTime ?: return@map null
            // Defensive: malformte Timestamps dürfen die Verspätungsberechnung nicht crashen lassen.
            val delay = if (event.plannedTime != null && event.estimatedTime != null) {
                runCatching {
                    val d = Duration.between(Instant.parse(event.plannedTime), Instant.parse(event.estimatedTime)).toMinutes()
                    if (d > 0) d else null
                }.getOrNull()
            } else null
            FlatDeparture(
                line = line, lineId = lineId, destination = destination?.removePrefix("Hannover/")?.trim(), number = number,
                transportTypes = types,
                departureTime = dept,
                plannedTime = event.plannedTime,
                estimatedTime = event.estimatedTime,
                delayMinutes = delay,
                lineShort = lineShort,
                isCancelled = (this@toFlatRows.apiCancelled == true) || (event.apiCancelled == true) || (event.realtimeState == "CANCELED"),
                messages = messageItems
            )
        }
        .filterNotNull()
}


/**
 * Modell für die neue Haltestellensuche via /stops
 */
data class StationSearchResult(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("gid") val gid: String? = null,
    @SerializedName("lat") val lat: Double? = null,
    @SerializedName("lon") val lon: Double? = null,
    @SerializedName("platforms") val platforms: List<Platform>? = null
)

data class Platform(
    @SerializedName("productClasses") val productClasses: List<Int>? = null
) {
    // GVH Produktklassen: 0=Zug, 1=S-Bahn, 2=U-Bahn, 3=Stadtbahn, 4=Straßenbahn, 5/6=Bus
    val isBus: Boolean get() = productClasses?.any { it in 5..11 } == true
    val isTram: Boolean get() = productClasses?.any { it in 2..4 } == true
}

/**
 * Bereinigt Meldungstexte von HTML: <br>/<p>/<div>/<li> → Zeilenumbruch, übrige Tags raus,
 * gängige HTML-Entities dekodiert, Whitespace normalisiert. Link-URLs (in <a href>) entfallen,
 * der sichtbare Linktext bleibt. Reiner Text-Helfer (JVM-testbar, kein Android-API).
 */
private val HTML_BR_REGEX = Regex("(?i)<\\s*br\\s*/?>")
private val HTML_BLOCK_CLOSE_REGEX = Regex("(?i)<\\s*/\\s*(p|div|li)\\s*>")
private val HTML_TAG_REGEX = Regex("<[^>]+>")
private val EXCESS_BLANK_LINES_REGEX = Regex("\n{3,}")

fun stripHtml(raw: String): String {
    var s = raw
    s = s.replace(HTML_BR_REGEX, "\n")
    s = s.replace(HTML_BLOCK_CLOSE_REGEX, "\n")
    s = s.replace(HTML_TAG_REGEX, "")
    s = s.replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
    s = s.lines().joinToString("\n") { it.trim() }
    s = s.replace(EXCESS_BLANK_LINES_REGEX, "\n\n")
    return s.trim()
}

data class DepartureInfo(
    @SerializedName("id") val id: String? = null,
    @SerializedName("priority") val priority: String? = null,
    @SerializedName("incidentStart") val incidentStart: String? = null,
    @SerializedName("incidentEnd") val incidentEnd: String? = null,
    @SerializedName("titel") val titel: String?,
    @SerializedName("content") val content: String?
)

data class DepartureHint(
    @SerializedName("content") val content: String?
)

/**
 * Eine einzelne, anzeigbare Meldung mit stabiler Identität.
 * - [id]: stabile ID der API (z.B. "ems-11956"); für ID-lose Quellen (hints) synthetisch
 *   aus dem Inhalt ("h:<hash>"), für infos ohne ID "c:<hash>".
 * - [content]: HTML-bereinigter Anzeigetext.
 * - [startMillis]: incidentStart als Epoch-Millis (0, falls unbekannt – z.B. bei hints).
 */
data class MsgItem(
    val id: String,
    val content: String,
    val startMillis: Long = 0L
)

/** Meldungen einer Linie – Übergabeformat für den (i)-Meldungsdialog im App-UI. */
data class LineMessages(
    val line: String,
    val messages: List<MsgItem> = emptyList()
)

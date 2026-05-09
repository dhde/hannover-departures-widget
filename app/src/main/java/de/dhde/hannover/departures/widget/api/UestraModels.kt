package de.dhde.hannover.departures.widget.api

import com.google.gson.annotations.SerializedName
import java.time.Instant
import java.time.Duration

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
    @SerializedName("events") val events: List<DepartureEvent>? = null
) {
    // Hilfsabfragen für das UI-Filtering
    val isBus: Boolean get() {
        val s = lineShort.uppercase()
        return line?.contains("Bus", ignoreCase = true) == true || 
               (s.toIntOrNull() != null && s.toInt() >= 100) ||
               s.startsWith("N")
    }
    val isTram: Boolean get() {
        val s = lineShort.uppercase()
        return line?.contains("Stadtbahn", ignoreCase = true) == true || 
               (s.toIntOrNull() != null && s.toInt() <= 17) ||
               s == "E"
    }
    
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
        val planned = event.plannedTime?.let { Instant.parse(it) } ?: return null
        val estimated = event.estimatedTime?.let { Instant.parse(it) } ?: return null
        val diff = Duration.between(planned, estimated).toMinutes()
        return if (diff > 0) diff else null
    }
}

data class DepartureEvent(
    @SerializedName("plannedTime") val plannedTime: String?,
    // Die API liefert "estimated_time" (snake_case), NICHT "estimatedTime" (camelCase)!
    @SerializedName("estimated_time") val estimatedTime: String?
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
    val isBus: Boolean,
    val isTram: Boolean,
    val departureTime: String,   // Echtzeit, falls vorhanden, sonst Planzeit
    val plannedTime: String?,
    val estimatedTime: String?,
    val delayMinutes: Long?,
    val lineShort: String
)

/** Klappt alle zukünftigen Events eines DepartureItem zu einzelnen FlatDeparture-Zeilen auf. */
fun DepartureItem.toFlatRows(cutoffSeconds: Long = 60): List<FlatDeparture> {
    val now = Instant.now().minusSeconds(cutoffSeconds)
    return events.orEmpty()
        .filter { event ->
            val t = event.estimatedTime ?: event.plannedTime
            t?.let { runCatching { Instant.parse(it).isAfter(now) }.getOrDefault(true) } == true
        }
        .map { event ->
            val dept = event.estimatedTime ?: event.plannedTime ?: return@map null
            val delay = if (event.plannedTime != null && event.estimatedTime != null) {
                val d = Duration.between(Instant.parse(event.plannedTime), Instant.parse(event.estimatedTime)).toMinutes()
                if (d > 0) d else null
            } else null
            FlatDeparture(
                line = line, lineId = lineId, destination = destination, number = number,
                isBus = isBus, isTram = isTram,
                departureTime = dept,
                plannedTime = event.plannedTime,
                estimatedTime = event.estimatedTime,
                delayMinutes = delay,
                lineShort = lineShort
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

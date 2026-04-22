package com.uestra.widgetapp.api

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
    val isBus: Boolean get() = line?.contains("Bus", ignoreCase = true) == true
    val isTram: Boolean get() = line?.contains("Stadtbahn", ignoreCase = true) == true || (lineShort.toIntOrNull() != null && lineShort.length <= 2)
    
    // Die nächste verfügbare Abfahrtszeit (Echtzeit bevorzugt)
    val nextDepartureTime: String? 
        get() = events?.firstOrNull()?.estimatedTime ?: events?.firstOrNull()?.plannedTime

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
    @SerializedName("estimatedTime") val estimatedTime: String?
)


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

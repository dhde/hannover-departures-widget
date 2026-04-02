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
    val isTram: Boolean get() = line?.contains("Stadtbahn", ignoreCase = true) == true
    
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
    @SerializedName("estimated_time") val estimatedTime: String?
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
    val isBus: Boolean get() = productClasses?.contains(6) == true
    val isTram: Boolean get() = productClasses?.contains(0) == true
}

package de.dhde.hannover.departures.widget.data

data class StopCandidate(
    val stopId: String,
    val name: String,
    val distanceM: Int,
    val transportTypes: List<String>   // "BUS", "TRAM"
)

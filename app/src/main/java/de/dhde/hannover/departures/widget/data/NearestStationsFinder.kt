package de.dhde.hannover.departures.widget.data

import android.location.Location
import de.dhde.hannover.departures.widget.api.StationSearchResult

/** (lat1, lon1, lat2, lon2) → Distanz in Metern. */
typealias DistanceCalculator = (Double, Double, Double, Double) -> Float

object NearestStationsFinder {

    /** Prod-Implementierung. Verwendet Location.distanceBetween — Android-only, deshalb via Function-Type gekapselt. */
    val androidDistance: DistanceCalculator = { lat1, lon1, lat2, lon2 ->
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        results[0]
    }

    /**
     * Top-N nächste Stopps nach Distanz.
     * Stopps ohne Koordinaten werden verworfen.
     * Bei transportFilter != null werden nur Stopps zurückgegeben, deren Platforms den Filter erfüllen
     * (analog findAndSetActiveNearestStation: leere/null Platforms zählen als "passt" — konservativ).
     */
    fun findNearestStops(
        stops: List<StationSearchResult>,
        userLat: Double,
        userLon: Double,
        count: Int,
        transportFilter: TransportFilter?,
        distance: DistanceCalculator = androidDistance
    ): List<StopCandidate> {
        val filtered = stops.asSequence().filter { s ->
            if (s.lat == null || s.lon == null) return@filter false
            when (transportFilter) {
                TransportFilter.BUS -> s.platforms.isNullOrEmpty() || s.platforms!!.any { it.isBus }
                TransportFilter.TRAM -> s.platforms.isNullOrEmpty() || s.platforms!!.any { it.isTram }
                else -> true
            }
        }

        return filtered.map { s ->
            val d = distance(userLat, userLon, s.lat!!, s.lon!!)
            val types = buildList {
                if (s.platforms?.any { it.isBus } == true) add("BUS")
                if (s.platforms?.any { it.isTram } == true) add("TRAM")
            }
            StopCandidate(stopId = s.id, name = s.name, distanceM = d.toInt(), transportTypes = types)
        }
        .sortedBy { it.distanceM }
        .take(count)
        .toList()
    }
}

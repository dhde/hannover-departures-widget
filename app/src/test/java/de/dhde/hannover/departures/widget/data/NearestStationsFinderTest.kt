package de.dhde.hannover.departures.widget.data

import de.dhde.hannover.departures.widget.api.Platform
import de.dhde.hannover.departures.widget.api.StationSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class NearestStationsFinderTest {

    // Fake-Distanz: Manhattan-Distanz in Grad, mit Faktor 1000 (Grad ~ km-Region)
    private val fakeDist: DistanceCalculator =
        { lat1, lon1, lat2, lon2 -> ((abs(lat1 - lat2) + abs(lon1 - lon2)) * 1000.0).toFloat() }

    private fun stop(id: String, lat: Double, lon: Double, bus: Boolean = false, tram: Boolean = false): StationSearchResult {
        val platforms = if (!bus && !tram) null else listOf(
            Platform(productClasses = buildList {
                if (bus) add(6)         // Bus (5..11)
                if (tram) add(4)        // Straßenbahn (2..4)
            })
        )
        return StationSearchResult(id = id, name = "Stop-$id", lat = lat, lon = lon, platforms = platforms)
    }

    @Test
    fun sortiertNachDistanz() {
        val stops = listOf(
            stop("A", 0.0, 0.5),
            stop("B", 0.0, 0.1),
            stop("C", 0.0, 0.3)
        )
        val result = NearestStationsFinder.findNearestStops(
            stops = stops, userLat = 0.0, userLon = 0.0,
            count = 3, transportFilter = null, distance = fakeDist
        )
        assertEquals(listOf("B", "C", "A"), result.map { it.stopId })
    }

    @Test
    fun kapptBeiCount() {
        val stops = (1..10).map { stop("s$it", 0.0, it * 0.1) }
        val result = NearestStationsFinder.findNearestStops(
            stops = stops, userLat = 0.0, userLon = 0.0,
            count = 3, transportFilter = null, distance = fakeDist
        )
        assertEquals(3, result.size)
    }

    @Test
    fun filtertBusVsTram() {
        val stops = listOf(
            stop("bus1", 0.0, 0.1, bus = true),
            stop("tram1", 0.0, 0.2, tram = true),
            stop("bus2", 0.0, 0.3, bus = true)
        )
        val onlyTram = NearestStationsFinder.findNearestStops(
            stops = stops, userLat = 0.0, userLon = 0.0,
            count = 5, transportFilter = TransportFilter.TRAM, distance = fakeDist
        )
        assertEquals(listOf("tram1"), onlyTram.map { it.stopId })

        val onlyBus = NearestStationsFinder.findNearestStops(
            stops = stops, userLat = 0.0, userLon = 0.0,
            count = 5, transportFilter = TransportFilter.BUS, distance = fakeDist
        )
        assertEquals(listOf("bus1", "bus2"), onlyBus.map { it.stopId })
    }

    @Test
    fun filterNullLiefertAlle() {
        val stops = listOf(
            stop("bus1", 0.0, 0.1, bus = true),
            stop("tram1", 0.0, 0.2, tram = true)
        )
        val result = NearestStationsFinder.findNearestStops(
            stops = stops, userLat = 0.0, userLon = 0.0,
            count = 5, transportFilter = null, distance = fakeDist
        )
        assertEquals(2, result.size)
    }

    @Test
    fun ignoriertStopsOhneKoordinaten() {
        val withCoords = stop("A", 0.0, 0.1)
        val withoutCoords = StationSearchResult(id = "B", name = "no-coords", lat = null, lon = null)
        val result = NearestStationsFinder.findNearestStops(
            stops = listOf(withCoords, withoutCoords),
            userLat = 0.0, userLon = 0.0, count = 5, transportFilter = null, distance = fakeDist
        )
        assertEquals(listOf("A"), result.map { it.stopId })
    }

    @Test
    fun leereStops() {
        val result = NearestStationsFinder.findNearestStops(
            stops = emptyList(), userLat = 0.0, userLon = 0.0,
            count = 3, transportFilter = null, distance = fakeDist
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun transportTypesGesetzt() {
        val stops = listOf(stop("mix", 0.0, 0.1, bus = true, tram = true))
        val result = NearestStationsFinder.findNearestStops(
            stops = stops, userLat = 0.0, userLon = 0.0,
            count = 1, transportFilter = null, distance = fakeDist
        )
        assertEquals(setOf("BUS", "TRAM"), result.first().transportTypes.toSet())
    }
}

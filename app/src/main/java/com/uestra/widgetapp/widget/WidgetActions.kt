package com.uestra.widgetapp.widget

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import androidx.glance.appwidget.cornerRadius
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.google.gson.Gson
import com.uestra.widgetapp.api.DepartureItem
import com.uestra.widgetapp.api.UestraApi
import com.uestra.widgetapp.api.StationSearchResult
import com.uestra.widgetapp.data.DeparturesCache
import com.uestra.widgetapp.data.FavoritesRepository
import com.uestra.widgetapp.data.StopsRepository

class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        try {
            val repo = FavoritesRepository(context)
            val cache = DeparturesCache(context)
            
            // Wenn GPS-Automatik an ist, erst Standort prüfen
            if (cache.isGpsModeActive()) {
                findAndSetActiveNearestStation(context)
            }
            
            val stationId = repo.getActiveStationIdNow()
            val api = UestraApi.create()
            val response = api.getDepartures(stationId = stationId)
            val json = Gson().toJson(response.departures ?: emptyList<DepartureItem>())

            // Speichere im Cache (Bypass für Glance-State-Bug)
            cache.saveDepartures(stationId, json)
            
            // Widget-Update-Signal
            DeparturesWidget().updateAll(context)
        } catch (e: Exception) {
            // Im Fehlerfall loggen oder Cache leeren/Status setzen
            // Hier verzichten wir auf updateAppWidgetState, um Build-Fehler zu vermeiden
        }
    }
}

class ChangeTabAction : ActionCallback {
    companion object {
        val KEY_TAB = ActionParameters.Key<String>("tab")
    }
    
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val targetTab = parameters[KEY_TAB] ?: "ALL"
        val cache = DeparturesCache(context)
        val stationId = cache.getStationId()
        cache.setTabState(stationId, targetTab)
        
        // Wenn GPS-Automatik an ist, sofort neue Haltestelle suchen
        if (cache.isGpsModeActive()) {
            findAndSetActiveNearestStation(context)
        }
        
        DeparturesWidget().updateAll(context)
    }
}

class ChangeStationAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val repo = FavoritesRepository(context)
        val currentId = repo.getActiveStationIdNow()
        val favorites = repo.getFavoritesNow()
        
        if (favorites.isNotEmpty()) {
            val currentIndex = favorites.indexOfFirst { it.first == currentId }
            val nextIndex = if (currentIndex == -1 || currentIndex >= favorites.size - 1) 0 else currentIndex + 1
            val (nextId, nextName) = favorites[nextIndex]
            
            repo.setActiveStation(nextId, nextName)
            
            // Manueller Wechsel schaltet GPS-Automatik aus
            DeparturesCache(context).setGpsMode(false)
            
            // Load departures for newly selected station
            RefreshAction().onAction(context, glanceId, parameters)
        }
    }
}

class ChangeDirectionAction : ActionCallback {
    companion object {
        val KEY_DIRECTION = ActionParameters.Key<String>("direction")
    }
    
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val targetDirection = parameters[KEY_DIRECTION] ?: "ALL"
        val cache = DeparturesCache(context)
        val stationId = cache.getStationId()
        cache.setDirectionState(stationId, targetDirection)
        DeparturesWidget().updateAll(context)
    }
}

class ToggleTimeDisplayAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val cache = DeparturesCache(context)
        val current = cache.getTimeDisplayMode()
        val next = if (current == "MIN") "CLOCK" else "MIN"
        cache.setTimeDisplayMode(next)
        DeparturesWidget().updateAll(context)
    }
}

class LocateNearestStationAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val cache = DeparturesCache(context)
        val current = cache.isGpsModeActive()
        val next = !current
        cache.setGpsMode(next)
        
        if (next) {
            findAndSetActiveNearestStation(context)
        }
        
        DeparturesWidget().updateAll(context)
    }
}

/**
 * Kern-Logik für die GPS-Suche, ausgelagert zur Wiederverwendung.
 */
suspend fun findAndSetActiveNearestStation(context: Context) {
    // 1. Check permissions
    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!hasCoarse && !hasFine) return

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    
    // Alle aktiven Provider durchprobieren
    val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    )
    
    var bestLoc: android.location.Location? = null
    for (provider in providers) {
        val loc = try {
            if (locationManager.isProviderEnabled(provider))
                locationManager.getLastKnownLocation(provider)
            else null
        } catch (e: SecurityException) { null }
        
        if (loc != null && (bestLoc == null || loc.time > bestLoc.time)) {
            bestLoc = loc
        }
    }

    if (bestLoc == null) return

    // 2. Alle Stationen aus dem lokalen Cache laden
    val stopsRepo = StopsRepository(context)
    val allStops = stopsRepo.getAllStops()
    if (allStops.isEmpty()) return

    // 3. Aktuellen Tab-Filter berücksichtigen
    val cache = DeparturesCache(context)
    val currentId = cache.getStationId()
    val tabState = cache.getTabState(currentId)
    
    val stopsWithCoords = allStops.filter { stop ->
        if (stop.lat == null || stop.lon == null) return@filter false
        when (tabState) {
            "BUS"   -> stop.platforms?.any { it.isBus } == true
            "TRAIN" -> stop.platforms?.any { it.isTram } == true
            else    -> true
        }
    }

    // 4. Nächste Station finden
    var nearestStop: StationSearchResult? = null
    var minDistance = Float.MAX_VALUE
    val results = FloatArray(1)
    
    for (stop in stopsWithCoords) {
        Location.distanceBetween(bestLoc.latitude, bestLoc.longitude, stop.lat!!, stop.lon!!, results)
        if (results[0] < minDistance) {
            minDistance = results[0]
            nearestStop = stop
        }
    }

    // 5. Station als aktiv setzen
    if (nearestStop != null) {
        // Tab-State auf die neue Station kopieren, damit der Filter erhalten bleibt
        cache.setTabState(nearestStop.id, tabState)
        FavoritesRepository(context).setActiveStation(nearestStop.id, nearestStop.name)
    }
}

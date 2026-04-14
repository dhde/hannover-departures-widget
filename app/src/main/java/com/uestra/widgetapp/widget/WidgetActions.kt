package com.uestra.widgetapp.widget

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import com.google.gson.Gson
import com.uestra.widgetapp.api.*
import com.uestra.widgetapp.data.DeparturesCache
import com.uestra.widgetapp.data.FavoritesRepository
import com.uestra.widgetapp.data.StopsRepository
import java.time.Instant
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.collect
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch

class RefreshAction : ActionCallback {
    companion object {
        val KEY_FORCE = ActionParameters.Key<Boolean>("force")
    }
    
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        try {
            val repo = FavoritesRepository(context)
            val cache = DeparturesCache(context)
            val isForce = parameters[KEY_FORCE] ?: false

            // Momentanen Status prüfen
            var alreadyRefreshing = false
            cache.isRefreshingFlow().take(1).collect { alreadyRefreshing = it }

            // 1. UI-Dinge tun, die immer gehen (z.B. GPS-Suche oder UI-Refresh)
            if (cache.isGpsModeActive()) {
                findAndSetActiveNearestStation(context)
            }
            
            // 2. Nur wenn nicht bereits ein Fetch läuft, starten wir einen neuen
            if (!alreadyRefreshing) {
                // Widget auf "lädt" setzen und sofort onAction() zurückgeben lassen!
                cache.setRefreshing(true)
                DeparturesWidget().updateAll(context)

                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()).launch {
                    try {
                        val stationId = repo.getActiveStationIdNow()
                        val lastUpdatedStr = cache.getLastUpdated(stationId)
                        val secondsOld = if (lastUpdatedStr.isNotEmpty()) {
                            val lastTime = Instant.ofEpochMilli(lastUpdatedStr.toLong())
                            Duration.between(lastTime, Instant.now()).getSeconds()
                        } else 999L

                        // Drosselung: Maximal alle 30 Sekunden eine Anfrage pro Haltestelle,
                        // es sei denn, es ist ein wirklich erzwungener Refresh vom User.
                        if (isForce || secondsOld >= 30) {
                            cache.updateRefreshTime(stationId)
                            var departures: List<DepartureItem>? = null
                            var errorOccurred = false
                            
                            // --- ÜSTRA Web Proxy (Original-Quelle) ---
                            try {
                                val api = UestraApi.create()
                                val response = api.getDepartures(stationId)
                                departures = response.departures
                            } catch (e: Exception) {
                                e.printStackTrace()
                                errorOccurred = true
                            }
                            
                            if (departures != null) {
                                cache.saveDepartures(stationId, Gson().toJson(departures))
                            }
                        }
                    } finally {
                        cache.setRefreshing(false)
                        DeparturesWidget().updateAll(context)
                    }
                }
            } else {
                DeparturesWidget().updateAll(context)
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
        if (cache.isGpsModeActive()) findAndSetActiveNearestStation(context)
        
        // WICHTIG: Beim Tab-Wechsel KEIN Force-Refresh, nur normales onAction.
        // Das sorgt dafür, dass nur neue Daten geholt werden, wenn die 30s/5min abgelaufen sind.
        RefreshAction().onAction(context, glanceId, actionParametersOf())
    }
}

class ChangeStationAction : ActionCallback {
    companion object {
        val KEY_TARGET_INDEX = ActionParameters.Key<Int>("targetIndex")
        val KEY_CYCLE_REMAINING = ActionParameters.Key<Boolean>("cycleRemaining")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val repo = FavoritesRepository(context)
        val currentId = repo.getActiveStationIdNow()
        val favorites = repo.getFavoritesNow()
        
        val targetIndex = parameters[KEY_TARGET_INDEX]
        val cycleRemaining = parameters[KEY_CYCLE_REMAINING] ?: false
        
        if (favorites.isNotEmpty()) {
            if (targetIndex != null && targetIndex in favorites.indices) {
                val fav = favorites[targetIndex]
                repo.setActiveStation(fav.id, fav.name)
            } else if (cycleRemaining) {
                if (favorites.size > 3) {
                    var currentIndex = favorites.indexOfFirst { it.id == currentId }
                    if (currentIndex < 3) {
                        currentIndex = 3
                    } else {
                        currentIndex++
                        if (currentIndex >= favorites.size) currentIndex = 3
                    }
                    val fav = favorites[currentIndex]
                    repo.setActiveStation(fav.id, fav.name)
                }
            } else {
                val currentIndex = favorites.indexOfFirst { it.id == currentId }
                val nextIndex = if (currentIndex == -1 || currentIndex >= favorites.size - 1) 0 else currentIndex + 1
                val fav = favorites[nextIndex]
                repo.setActiveStation(fav.id, fav.name)
            }
            
            DeparturesCache(context).setGpsMode(false)
            DeparturesWidget().updateAll(context)
            RefreshAction().onAction(context, glanceId, actionParametersOf())
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
        RefreshAction().onAction(context, glanceId, actionParametersOf())
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
        cache.setTimeDisplayMode(if (current == "MIN") "CLOCK" else "MIN")
        DeparturesWidget().updateAll(context)
        RefreshAction().onAction(context, glanceId, actionParametersOf())
    }
}

class LocateNearestStationAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val cache = DeparturesCache(context)
        val next = !cache.isGpsModeActive()
        cache.setGpsMode(next)
        if (next) findAndSetActiveNearestStation(context)
        DeparturesWidget().updateAll(context)
        RefreshAction().onAction(context, glanceId, actionParametersOf())
    }
}

suspend fun findAndSetActiveNearestStation(context: Context) {
    val hasL = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!hasL) return

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val lastLoc = try { locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) } catch (e: SecurityException) { null } ?: return

    val stopsRepo = StopsRepository(context)
    val allStops = stopsRepo.getAllStops()
    if (allStops.isEmpty()) return
    
    val stopsWithCoords = allStops.filter { it.lat != null && it.lon != null }

    var nearestStop: StationSearchResult? = null
    var minDistance = Float.MAX_VALUE
    val results = FloatArray(1)
    
    for (stop in stopsWithCoords) {
        Location.distanceBetween(lastLoc.latitude, lastLoc.longitude, stop.lat!!, stop.lon!!, results)
        if (results[0] < minDistance) {
            minDistance = results[0]
            nearestStop = stop
        }
    }

    if (nearestStop != null) {
        FavoritesRepository(context).setActiveStation(nearestStop.id, nearestStop.name)
    }
}

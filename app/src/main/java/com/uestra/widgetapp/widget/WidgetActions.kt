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
            
            if (cache.isGpsModeActive()) {
                findAndSetActiveNearestStation(context)
            }
            
            val stationId = repo.getActiveStationIdNow()
            val lastUpdatedStr = cache.getLastUpdated(stationId)
            val minutesOld = if (lastUpdatedStr.isNotEmpty()) {
                val lastTime = Instant.ofEpochMilli(lastUpdatedStr.toLong())
                Duration.between(lastTime, Instant.now()).toMinutes()
            } else 999L
            
            if (isForce || minutesOld >= 5) {
                val api = UestraApi.createWeb()
                val response = api.getDepartures(stationId)
                val departures = response.departures ?: emptyList()
                
                cache.saveDepartures(stationId, Gson().toJson(departures))
            }
            
            DeparturesWidget().update(context, glanceId)
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
        DeparturesWidget().update(context, glanceId)
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
            DeparturesCache(context).setGpsMode(false)
            DeparturesWidget().update(context, glanceId)
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
        DeparturesWidget().update(context, glanceId)
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
        DeparturesWidget().update(context, glanceId)
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
        DeparturesWidget().update(context, glanceId)
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

    val cache = DeparturesCache(context)
    
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

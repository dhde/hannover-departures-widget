package de.dhde.hannover.departures.widget.widget

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
import de.dhde.hannover.departures.widget.api.*
import de.dhde.hannover.departures.widget.data.DeparturesCache
import de.dhde.hannover.departures.widget.data.FavoritesRepository
import de.dhde.hannover.departures.widget.data.StopsRepository
import java.time.Instant
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.collect
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class RefreshAction : ActionCallback {
    companion object {
        val KEY_FORCE = ActionParameters.Key<Boolean>("force")
        
        /** Zentraler Auslöser für Updates, um Kaskaden zu vermeiden */
        suspend fun triggerUpdate(context: Context, isForce: Boolean = false) {
            val repo = FavoritesRepository(context)
            val cache = DeparturesCache(context)
            
            // 1. WICHTIG: UI sofort updaten (z.B. für Tab-Wechsel)
            // Auch wenn wir danach den Netzwerkabruf überspringen, 
            // ist die Klick-Aktion (wie Tab-Wechsel) dann schon sichtbar.
            DeparturesWidget().updateAll(context)
            
            // Falls bereits ein Refresh läuft, brechen wir hier ab, 
            // um den DataStore nicht zu überlasten.
            if (cache.isRefreshing()) return
            
            if (cache.isGpsModeActive()) {
                findAndSetActiveNearestStation(context)
            }
            
            val stationId = repo.getActiveStationIdNow()
            val lastUpdatedStr = cache.getLastUpdated(stationId)
            val secondsOld = if (lastUpdatedStr.isNotEmpty()) {
                val lastTime = java.time.Instant.ofEpochMilli(lastUpdatedStr.toLong())
                java.time.Duration.between(lastTime, java.time.Instant.now()).seconds
            } else 999L

            // Drosselung: Nur alle 60s anfragen, außer bei manuellem Force
            if (isForce || secondsOld >= 60) {
                cache.setRefreshing(true)
                cache.updateRefreshTime(stationId)
                DeparturesWidget().updateAll(context)
                
                try {
                    // Wir führen den Netzwerk-Check in einem begrenzten Zeitfenster aus
                    val success = kotlinx.coroutines.withTimeoutOrNull(10000) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val api = UestraApi.create()
                            val response = api.getDepartures(stationId)
                            val departures = response.departures
                            if (departures != null) {
                                cache.saveDepartures(stationId, Gson().toJson(departures))
                                
                                // Extract and track messages
                                val msgsWithTypes = mutableMapOf<String, MutableSet<String>>()
                                departures.forEach { dep ->
                                    val types = mutableSetOf<String>()
                                    if (dep.isBus) types.add("Bus")
                                    if (dep.isTram) types.add("Stadtbahn")
                                    if (dep.isTrain) types.add("S-Bahn")
                                    if (dep.isDB) types.add("DB")
                                    if (dep.isFernbus) types.add("Fernbus")
                                    
                                    dep.infos?.forEach { it.content?.let { c -> msgsWithTypes.getOrPut(c) { mutableSetOf() }.addAll(types) } }
                                    dep.hints?.forEach { it.content?.let { c -> msgsWithTypes.getOrPut(c) { mutableSetOf() }.addAll(types) } }
                                }
                                if (msgsWithTypes.isNotEmpty()) {
                                    repo.trackMessages(msgsWithTypes)
                                }
                                
                                true // Signal success
                            } else {
                                false
                            }
                        }
                    }
                    if (success == null) {
                        cache.setErrorState("Zeitüberschreitung") // Timeout
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    cache.setErrorState("Verbindung fehlgeschlagen")
                } finally {
                    cache.setRefreshing(false)
                    DeparturesWidget().updateAll(context)
                }
            }
        }
    }
    
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val isForce = parameters[KEY_FORCE] ?: false
        triggerUpdate(context, isForce)
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
        val repo = FavoritesRepository(context)
        val stationId = repo.getActiveStationIdNow()
        val uniqueId = repo.getActiveFavoriteUniqueIdNow()
        
        val currentTab = cache.getTabState(stationId)
        val newTab = if (currentTab == targetTab) "ALL" else targetTab
        
        cache.setTabState(stationId, newTab)
        if (uniqueId != null) {
            repo.setFavoriteTransportFilter(uniqueId, newTab)
        }
        
        // Sofortiges UI-Feedback für den Tab-Wechsel
        DeparturesWidget().updateAll(context)
        
        if (cache.isGpsModeActive()) findAndSetActiveNearestStation(context)
        RefreshAction.triggerUpdate(context)
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
        val currentId = repo.getActiveFavoriteUniqueIdNow() ?: repo.getActiveStationIdNow()
        val favorites = repo.getFavoritesNow()
        
        val targetIndex = parameters[KEY_TARGET_INDEX]
        val cycleRemaining = parameters[KEY_CYCLE_REMAINING] ?: false
        
        if (favorites.isNotEmpty()) {
            if (targetIndex != null && targetIndex in favorites.indices) {
                val fav = favorites[targetIndex]
                repo.setActiveStation(fav.safeUniqueId, fav.name)
            } else if (cycleRemaining) {
                val maxFavorites = repo.maxFavoritesFlow.first()
                val maxFavRows = repo.maxFavRowsFlow.first()
                val maxVisible = maxFavorites * maxFavRows
                if (favorites.size > maxVisible) {
                    var currentIndex = favorites.indexOfFirst { it.safeUniqueId == currentId }
                    if (currentIndex < maxVisible) {
                        currentIndex = maxVisible
                    } else {
                        currentIndex++
                        if (currentIndex >= favorites.size) currentIndex = maxVisible
                    }
                    val fav = favorites[currentIndex]
                    repo.setActiveStation(fav.safeUniqueId, fav.name)
                }
            } else {
                val currentIndex = favorites.indexOfFirst { it.safeUniqueId == currentId }
                val nextIndex = (currentIndex + 1) % favorites.size
                val nextFav = favorites[nextIndex]
                repo.setActiveStation(nextFav.safeUniqueId, nextFav.name)
            }
            
            DeparturesCache(context).setGpsMode(false)
            RefreshAction.triggerUpdate(context)
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
        val repo = FavoritesRepository(context)
        val stationId = repo.getActiveStationIdNow()
        val uniqueId = repo.getActiveFavoriteUniqueIdNow()
        
        val currentDirection = cache.getDirectionState(stationId)
        val newDirection = if (currentDirection == targetDirection) "ALL" else targetDirection
        
        cache.setDirectionState(stationId, newDirection)
        if (uniqueId != null) {
            repo.setFavoriteDirectionFilter(uniqueId, newDirection)
        }
        
        // Sofortiges UI-Feedback für Richtungswechsel
        DeparturesWidget().updateAll(context)
        
        RefreshAction.triggerUpdate(context)
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
        // Nur API-Refresh auslösen, wenn der Nutzer es aktiviert hat (Default: AUS)
        val autoRefresh = FavoritesRepository(context).getAutoRefreshOnInteractionNow()
        if (autoRefresh) {
            RefreshAction.triggerUpdate(context)
        } else {
            // Nur Widget-Redraw ohne API-Aufruf
            DeparturesWidget().updateAll(context)
        }
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
        RefreshAction.triggerUpdate(context)
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
    
    val repo = FavoritesRepository(context)
    val currentStationId = repo.getActiveStationIdNow()
    val cache = DeparturesCache(context)
    val currentTabState = cache.getTabState(currentStationId)

    val stopsWithCoords = allStops.filter { stop ->
        if (stop.lat == null || stop.lon == null) return@filter false
        val platforms = stop.platforms
        when (currentTabState) {
            "BUS" -> platforms.isNullOrEmpty() || platforms.any { it.isBus }
            "TRAIN" -> platforms.isNullOrEmpty() || platforms.any { it.isTram }
            else -> true
        }
    }

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
        repo.setActiveStation(nearestStop.id, nearestStop.name)
        // Den Filter (Bus/Bahn) für die neue Haltestelle übernehmen, damit das Erlebnis konsistent bleibt
        cache.setTabState(nearestStop.id, currentTabState)
    }
}

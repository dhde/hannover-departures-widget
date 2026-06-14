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
import de.dhde.hannover.departures.widget.data.DirectionFilter
import de.dhde.hannover.departures.widget.data.FavoritesRepository
import de.dhde.hannover.departures.widget.data.FilterStateStore
import de.dhde.hannover.departures.widget.data.StopsRepository
import de.dhde.hannover.departures.widget.data.TrackedMessage
import de.dhde.hannover.departures.widget.data.TransportFilter
import de.dhde.hannover.departures.widget.data.WidgetSessionStore
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
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class RefreshAction : ActionCallback {
    companion object {
        val KEY_FORCE = ActionParameters.Key<Boolean>("force")

        /** Zentraler Auslöser für Updates, um Kaskaden zu vermeiden */
        suspend fun triggerUpdate(context: Context, isForce: Boolean = false) {
            val repo = FavoritesRepository(context)
            val cache = DeparturesCache(context)
            val session = WidgetSessionStore(context)

            // 1. WICHTIG: UI sofort updaten (z.B. für Tab-Wechsel)
            // Auch wenn wir danach den Netzwerkabruf überspringen,
            // ist die Klick-Aktion (wie Tab-Wechsel) dann schon sichtbar.
            DeparturesWidget().updateAll(context)

            // Falls bereits ein Refresh läuft, brechen wir hier ab,
            // um den DataStore nicht zu überlasten.
            if (session.isRefreshing()) return

            if (session.isGpsModeActive()) {
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
                session.setRefreshing(true)
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
                                session.setErrorState("")

                                // Extract and track messages (keyed by stable id)
                                val msgMap = mutableMapOf<String, TrackedMessage>()
                                departures.forEach { dep ->
                                    val types = mutableSetOf<String>()
                                    if (dep.isBus) types.add("Bus")
                                    if (dep.isTram) types.add("Stadtbahn")
                                    if (dep.isSBahn) types.add("S-Bahn")
                                    if (dep.isDB) types.add("DB")
                                    if (dep.isFernbus) types.add("Fernbus")

                                    dep.messageItems.forEach { mi ->
                                        val existing = msgMap[mi.id]
                                        msgMap[mi.id] = TrackedMessage(
                                            id = mi.id,
                                            content = mi.content,
                                            startMillis = mi.startMillis,
                                            transportTypes = (existing?.transportTypes ?: emptySet()) + types
                                        )
                                    }
                                }
                                if (msgMap.isNotEmpty()) {
                                    repo.trackMessages(msgMap.values.toList())
                                }

                                true // Signal success
                            } else {
                                false
                            }
                        }
                    }
                    if (success == null) {
                        session.setErrorState("Zeitüberschreitung") // Timeout
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    session.setErrorState("Verbindung fehlgeschlagen")
                } finally {
                    session.setRefreshing(false)
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
        val targetTab = TransportFilter.fromStorage(parameters[KEY_TAB])
        val filters = FilterStateStore(context)
        val session = WidgetSessionStore(context)
        val repo = FavoritesRepository(context)
        val stationId = repo.getActiveStationIdNow()
        val uniqueId = repo.getActiveFavoriteUniqueIdNow()

        val currentTab = filters.getTabState(stationId)
        val newTab = if (currentTab == targetTab) TransportFilter.ALL else targetTab

        filters.setTabState(stationId, newTab)
        if (uniqueId != null) {
            repo.setFavoriteTransportFilter(uniqueId, newTab)
        }

        // Sofortiges UI-Feedback für den Tab-Wechsel
        DeparturesWidget().updateAll(context)

        if (session.isGpsModeActive()) findAndSetActiveNearestStation(context)
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

            WidgetSessionStore(context).setGpsMode(false)
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
        val targetDirection = DirectionFilter.fromStorage(parameters[KEY_DIRECTION])
        val filters = FilterStateStore(context)
        val repo = FavoritesRepository(context)
        val stationId = repo.getActiveStationIdNow()
        val uniqueId = repo.getActiveFavoriteUniqueIdNow()

        val currentDirection = filters.getDirectionState(stationId)
        val newDirection = if (currentDirection == targetDirection) DirectionFilter.ALL else targetDirection

        filters.setDirectionState(stationId, newDirection)
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
        val session = WidgetSessionStore(context)
        val current = session.getTimeDisplayMode()
        session.setTimeDisplayMode(if (current == "MIN") "CLOCK" else "MIN")
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
        val session = WidgetSessionStore(context)
        val next = !session.isGpsModeActive()
        session.setGpsMode(next)
        if (next) {
            findAndSetActiveNearestStation(context)
            // GPS-Aktivierung = aktuellen Halt ungefiltert zeigen (alle Linien/Richtungen),
            // auch wenn die nächste Haltestelle bereits die aktive war (kein Wechsel in findAndSet).
            val repo = FavoritesRepository(context)
            val filters = FilterStateStore(context)
            val sid = repo.getActiveStationIdNow()
            filters.setTabState(sid, TransportFilter.ALL)
            filters.setDirectionState(sid, DirectionFilter.ALL)
        }
        RefreshAction.triggerUpdate(context)
    }
}

/**
 * Bestmögliche Position: fordert AKTIV einen frischen Fix via FusedLocationProviderClient an
 * (mit Timeout), fällt sonst auf den zuletzt bekannten Standort über mehrere Provider zurück.
 * Der bisherige getLastKnownLocation(PASSIVE_PROVIDER) lieferte oft null (PASSIVE ortet nie selbst).
 */
suspend fun getBestLocation(context: Context): Location? {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) return null

    // 1. Aktiver Fix via Fused (Play Services). BALANCED reicht für "nächste Haltestelle"
    // im Stadtgebiet und schont den Akku (kein GPS-Chip-Hochfahren wie bei HIGH_ACCURACY).
    val priority = if (fine) Priority.PRIORITY_BALANCED_POWER_ACCURACY else Priority.PRIORITY_LOW_POWER
    val active = try {
        withTimeoutOrNull(8000) {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            suspendCancellableCoroutine<Location?> { cont ->
                fused.getCurrentLocation(priority, cts.token)
                    .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                cont.invokeOnCancellation { cts.cancel() }
            }
        }
    } catch (e: SecurityException) {
        null
    } catch (e: Exception) {
        null
    }
    if (active != null) return active

    // 2. Fallback: zuletzt bekannter Standort, frischester über alle Provider.
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
    return providers.mapNotNull { p ->
        try { lm.getLastKnownLocation(p) } catch (e: SecurityException) { null }
    }.maxByOrNull { it.time }
}

suspend fun findAndSetActiveNearestStation(context: Context) {
    val lastLoc = getBestLocation(context) ?: return

    val stopsRepo = StopsRepository(context)
    val allStops = stopsRepo.getAllStops()
    if (allStops.isEmpty()) return

    val repo = FavoritesRepository(context)
    val currentStationId = repo.getActiveStationIdNow()
    val filters = FilterStateStore(context)
    val currentTabState = filters.getTabState(currentStationId)

    val stopsWithCoords = allStops.filter { stop ->
        if (stop.lat == null || stop.lon == null) return@filter false
        val platforms = stop.platforms
        when (currentTabState) {
            TransportFilter.BUS -> platforms.isNullOrEmpty() || platforms.any { it.isBus }
            TransportFilter.TRAM -> platforms.isNullOrEmpty() || platforms.any { it.isTram }
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

    // Nur bei echtem Stationswechsel umschalten (sonst setzt setActiveStation die
    // Filter bei jedem Refresh zurück). Im GPS-Modus wird der nächste Halt bewusst
    // UNGEFILTERT gezeigt – alle Linien, alle Richtungen –, auch wenn der Halt zufällig
    // als Favorit (mit Filtern) gespeichert ist. Den Favoriten-Linienfilter blendet
    // die Anzeige im GPS-Modus zusätzlich aus (siehe gpsModeActive im Render).
    if (nearestStop != null && nearestStop.id != currentStationId) {
        repo.setActiveStation(nearestStop.id, nearestStop.name)
        filters.setTabState(nearestStop.id, TransportFilter.ALL)
        filters.setDirectionState(nearestStop.id, DirectionFilter.ALL)
    }
}

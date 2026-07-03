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
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import android.os.Looper
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
                            val response = api.getDepartures(stationId, UestraApi.DEFAULT_DEP_SEQUENCE)
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
            // Im GPS-Modus steuert der Verkehrsmittel-Filter, welcher Halt gewählt wird:
            // Filter Bahn → nächste Bahn-Haltestelle, Filter Bus → nächster Bus-Halt,
            // Filter ALL → nächster Halt insgesamt. findAndSet übernimmt den aktiven
            // Filter auch auf den neuen Halt, damit er nicht bei jedem Refresh rausspringt.
            // Beim Einschalten daher KEIN forciertes Reset auf ALL mehr (siehe Bug-Video
            // Claudiusstraße: Tram-Filter sprang sonst beim GPS-Toggle automatisch raus).
            findAndSetActiveNearestStation(context)
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

    // Wir wollen eine WIRKLICH aktuelle Position. getCurrentLocation/BALANCED lieferte auf echten
    // Geräten teils minutenalte, grobe (100m) Cache-Fixes → die "nächste Station" wurde aus einer
    // Position von vorhin berechnet ("hängt auf letzter Station"). Daher:
    //  - HIGH_ACCURACY (bei FINE) für einen präzisen GPS-Fix,
    //  - requestLocationUpdates mit kleinem maxUpdateAgeMillis (verwirft alte Cache-Fixes),
    //  - einen gelieferten Fix nur verwenden, wenn er nicht älter als maxAgeMs ist,
    //  - sonst den frischesten verfügbaren (aktiv vs. zuletzt bekannt) zurückgeben.
    val maxAgeMs = 60_000L
    val priority = if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
    val active = try {
        withTimeoutOrNull(7000) {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            suspendCancellableCoroutine<Location?> { cont ->
                val req = LocationRequest.Builder(priority, 1000L)
                    .setMaxUpdates(1)
                    .setMaxUpdateAgeMillis(10_000L)
                    .setDurationMillis(6000L)
                    .build()
                val cb = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        fused.removeLocationUpdates(this)
                        if (cont.isActive) cont.resume(result.lastLocation)
                    }
                }
                try {
                    fused.requestLocationUpdates(req, cb, Looper.getMainLooper())
                } catch (e: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                }
                cont.invokeOnCancellation { fused.removeLocationUpdates(cb) }
            }
        }
    } catch (e: SecurityException) {
        null
    } catch (e: Exception) {
        null
    }
    val now = System.currentTimeMillis()
    // Frischen aktiven Fix sofort verwenden.
    if (active != null && now - active.time <= maxAgeMs) return active

    // Sonst Fallback: frischester zuletzt bekannter Standort über alle Provider;
    // den (älteren) aktiven Fix nur nehmen, wenn er neuer ist als alle Last-Known.
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
    val lastKnown = providers.mapNotNull { p ->
        try { lm.getLastKnownLocation(p) } catch (e: SecurityException) { null }
    }.maxByOrNull { it.time }
    return listOfNotNull(active, lastKnown).maxByOrNull { it.time }
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
    // Filter bei jedem Refresh zurück). Den aktiven Verkehrsmittel-Filter aus dem
    // GPS-Modus auf den neuen Halt übernehmen: er hat ja gerade bestimmt, welcher
    // Halt überhaupt gewählt wurde (z.B. „Bahn" → nächste Bahn-Haltestelle).
    // setActiveStation hätte den Filter sonst auf den Favoriten-Default (meist ALL)
    // zurückgesetzt → Filter wäre bei jedem Refresh wieder rausgesprungen.
    // Richtung wird beim Stationswechsel zurückgesetzt (linien-/halt-spezifisch).
    // Den Favoriten-Linienfilter blendet die Anzeige im GPS-Modus aus (siehe
    // gpsModeActive im Render).
    if (nearestStop != null && nearestStop.id != currentStationId) {
        repo.setActiveStation(nearestStop.id, nearestStop.name)
        filters.setTabState(nearestStop.id, currentTabState)
        filters.setDirectionState(nearestStop.id, DirectionFilter.ALL)
    }
}

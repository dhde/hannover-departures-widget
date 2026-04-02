package com.uestra.widgetapp.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*

val Context.cacheDataStore by preferencesDataStore(name = "departures_cache")

/** Caches departures and widget UI states locally per station. */
class DeparturesCache(private val context: Context) {

    companion object {
        private val STATION_ID = stringPreferencesKey("station_id")
        private val TIME_DISPLAY_MODE = stringPreferencesKey("time_display_mode")
        private val GPS_MODE = booleanPreferencesKey("gps_mode")

        private fun departuresKey(id: String) = stringPreferencesKey("deps_$id")
        private fun updatedKey(id: String) = stringPreferencesKey("upd_$id")

        private fun tabKey(stationId: String) = stringPreferencesKey("tab_state_$stationId")
        private fun directionKey(stationId: String) = stringPreferencesKey("direction_state_$stationId")
    }

    suspend fun saveDepartures(stationId: String, json: String) {
        context.cacheDataStore.edit { prefs ->
            prefs[STATION_ID] = stationId
            prefs[departuresKey(stationId)] = json
            prefs[updatedKey(stationId)] = System.currentTimeMillis().toString()
        }
    }

    suspend fun getStationId(): String {
        var id = "25000031"
        context.cacheDataStore.data.map { prefs -> prefs[STATION_ID] }.take(1).collect { saved ->
            if (saved != null) id = saved
        }
        return id
    }

    suspend fun getDeparturesJson(stationId: String): String {
        var json = "[]"
        context.cacheDataStore.data.map { prefs -> prefs[departuresKey(stationId)] }.take(1).collect { saved ->
            if (saved != null) json = saved
        }
        return json
    }

    suspend fun getLastUpdated(stationId: String): String {
        var updated = ""
        context.cacheDataStore.data.map { prefs -> prefs[updatedKey(stationId)] }.take(1).collect { saved ->
            if (saved != null) updated = saved
        }
        return updated
    }

    suspend fun getTabState(stationId: String): String {
        var state = "ALL"
        context.cacheDataStore.data.map { prefs -> prefs[tabKey(stationId)] }.take(1).collect { saved ->
            if (saved != null) state = saved
        }
        return state
    }

    suspend fun getDirectionState(stationId: String): String {
        var state = "ALL"
        context.cacheDataStore.data.map { prefs -> prefs[directionKey(stationId)] }.take(1).collect { saved ->
            if (saved != null) state = saved
        }
        return state
    }

    fun getTabStateFlow(stationId: String): Flow<String> = 
        context.cacheDataStore.data.map { it[tabKey(stationId)] ?: "ALL" }

    fun getDirectionStateFlow(stationId: String): Flow<String> = 
        context.cacheDataStore.data.map { it[directionKey(stationId)] ?: "ALL" }

    fun getDeparturesJsonFlow(stationId: String): Flow<String> = 
        context.cacheDataStore.data.map { it[departuresKey(stationId)] ?: "[]" }
        
    fun getStationIdFlow(): Flow<String> = 
        context.cacheDataStore.data.map { it[STATION_ID] ?: "25000031" }

    fun getLastUpdatedFlow(stationId: String): Flow<String> = 
        context.cacheDataStore.data.map { it[updatedKey(stationId)] ?: "" }

    suspend fun setTabState(stationId: String, state: String) {
        context.cacheDataStore.edit { prefs ->
            prefs[tabKey(stationId)] = state
        }
    }

    suspend fun setDirectionState(stationId: String, state: String) {
        context.cacheDataStore.edit { prefs ->
            prefs[directionKey(stationId)] = state
        }
    }

    fun getTimeDisplayModeFlow(): Flow<String> = 
        context.cacheDataStore.data.map { it[TIME_DISPLAY_MODE] ?: "MIN" }

    suspend fun getTimeDisplayMode(): String {
        var mode = "MIN"
        context.cacheDataStore.data.map { prefs -> prefs[TIME_DISPLAY_MODE] }.take(1).collect { saved ->
            if (saved != null) mode = saved
        }
        return mode
    }

    suspend fun setTimeDisplayMode(mode: String) {
        context.cacheDataStore.edit { prefs ->
            prefs[TIME_DISPLAY_MODE] = mode
        }
    }

    fun getGpsModeFlow(): Flow<Boolean> = 
        context.cacheDataStore.data.map { it[GPS_MODE] ?: false }

    suspend fun isGpsModeActive(): Boolean {
        var active = false
        context.cacheDataStore.data.map { prefs -> prefs[GPS_MODE] }.take(1).collect { saved ->
            if (saved != null) active = saved
        }
        return active
    }

    suspend fun setGpsMode(active: Boolean) {
        context.cacheDataStore.edit { prefs ->
            prefs[GPS_MODE] = active
        }
    }
}

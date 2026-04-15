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
        private val IS_REFRESHING = booleanPreferencesKey("is_refreshing")

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

    suspend fun updateRefreshTime(stationId: String) {
        context.cacheDataStore.edit { prefs ->
            prefs[updatedKey(stationId)] = System.currentTimeMillis().toString()
        }
    }

    suspend fun getStationId(): String = 
        context.cacheDataStore.data.map { it[STATION_ID] }.first() ?: "25000031"

    suspend fun getDeparturesJson(stationId: String): String = 
        context.cacheDataStore.data.map { it[departuresKey(stationId)] }.first() ?: "[]"

    suspend fun getLastUpdated(stationId: String): String = 
        context.cacheDataStore.data.map { it[updatedKey(stationId)] }.first() ?: ""

    suspend fun getTabState(stationId: String): String = 
        context.cacheDataStore.data.map { it[tabKey(stationId)] }.first() ?: "ALL"

    suspend fun getDirectionState(stationId: String): String = 
        context.cacheDataStore.data.map { it[directionKey(stationId)] }.first() ?: "ALL"

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

    suspend fun getTimeDisplayMode(): String = 
        context.cacheDataStore.data.map { it[TIME_DISPLAY_MODE] }.first() ?: "MIN"

    suspend fun setTimeDisplayMode(mode: String) {
        context.cacheDataStore.edit { prefs ->
            prefs[TIME_DISPLAY_MODE] = mode
        }
    }

    fun getGpsModeFlow(): Flow<Boolean> = 
        context.cacheDataStore.data.map { it[GPS_MODE] ?: false }

    suspend fun isGpsModeActive(): Boolean = 
        context.cacheDataStore.data.map { it[GPS_MODE] }.first() ?: false

    suspend fun setGpsMode(active: Boolean) {
        context.cacheDataStore.edit { prefs ->
            prefs[GPS_MODE] = active
        }
    }

    fun isRefreshingFlow(): Flow<Boolean> = 
        context.cacheDataStore.data.map { it[IS_REFRESHING] ?: false }

    suspend fun isRefreshing(): Boolean = 
        context.cacheDataStore.data.map { it[IS_REFRESHING] }.first() ?: false

    suspend fun setRefreshing(refreshing: Boolean) {
        context.cacheDataStore.edit { prefs ->
            prefs[IS_REFRESHING] = refreshing
        }
    }
}

package com.uestra.widgetapp.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cacheDataStore by preferencesDataStore(name = "departures_cache")

/**
 * Ein Cache für die Abfahrtszeiten, der den Glance-Status-Bug umgeht.
 */
class DeparturesCache(private val context: Context) {

    companion object {
        private val DEPARTURES_JSON = stringPreferencesKey("departures_json")
        private val LAST_UPDATED = stringPreferencesKey("last_updated")
        private val STATION_ID = stringPreferencesKey("station_id")
        private val TIME_DISPLAY_MODE = stringPreferencesKey("time_display_mode") // "MIN" | "CLOCK"
        private val GPS_MODE = booleanPreferencesKey("gps_mode")

        private fun tabKey(stationId: String) = stringPreferencesKey("tab_state_$stationId")
        private fun directionKey(stationId: String) = stringPreferencesKey("direction_state_$stationId")
    }

    suspend fun saveDepartures(stationId: String, json: String) {
        context.cacheDataStore.edit { prefs ->
            prefs[STATION_ID] = stationId
            prefs[DEPARTURES_JSON] = json
            prefs[LAST_UPDATED] = System.currentTimeMillis().toString()
        }
    }

    suspend fun getDeparturesJson(): String = 
        context.cacheDataStore.data.map { it[DEPARTURES_JSON] ?: "[]" }.first()

    suspend fun getStationId(): String = 
        context.cacheDataStore.data.map { it[STATION_ID] ?: "Unbekannt" }.first()

    suspend fun getLastUpdated(): String = 
        context.cacheDataStore.data.map { it[LAST_UPDATED] ?: "" }.first()

    suspend fun getTabState(stationId: String): String = 
        context.cacheDataStore.data.map { it[tabKey(stationId)] ?: "ALL" }.first()

    suspend fun getDirectionState(stationId: String): String = 
        context.cacheDataStore.data.map { it[directionKey(stationId)] ?: "ALL" }.first()

    fun getTabStateFlow(stationId: String): Flow<String> = 
        context.cacheDataStore.data.map { it[tabKey(stationId)] ?: "ALL" }

    fun getDirectionStateFlow(stationId: String): Flow<String> = 
        context.cacheDataStore.data.map { it[directionKey(stationId)] ?: "ALL" }

    fun getDeparturesJsonFlow(): Flow<String> = 
        context.cacheDataStore.data.map { it[DEPARTURES_JSON] ?: "[]" }
        
    fun getStationIdFlow(): Flow<String> = 
        context.cacheDataStore.data.map { it[STATION_ID] ?: "Unbekannt" }

    fun getLastUpdatedFlow(): Flow<String> = 
        context.cacheDataStore.data.map { it[LAST_UPDATED] ?: "" }

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
        context.cacheDataStore.data.map { it[TIME_DISPLAY_MODE] ?: "MIN" }.first()

    suspend fun setTimeDisplayMode(mode: String) {
        context.cacheDataStore.edit { prefs ->
            prefs[TIME_DISPLAY_MODE] = mode
        }
    }

    suspend fun toggleTimeDisplayMode() {
        val current = getTimeDisplayMode()
        setTimeDisplayMode(if (current == "MIN") "CLOCK" else "MIN")
    }

    suspend fun isGpsModeActive(): Boolean = 
        context.cacheDataStore.data.map { it[GPS_MODE] ?: false }.first()

    fun getGpsModeFlow(): Flow<Boolean> = 
        context.cacheDataStore.data.map { it[GPS_MODE] ?: false }

    suspend fun setGpsMode(active: Boolean) {
        context.cacheDataStore.edit { prefs ->
            prefs[GPS_MODE] = active
        }
    }
}

package de.dhde.hannover.departures.widget.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*

val Context.cacheDataStore by preferencesDataStore(name = "departures_cache")

/** Fallback-Haltestelle (Hannover Hauptbahnhof), wenn noch keine Station gewählt wurde. */
const val DEFAULT_STATION_ID = "25000031"

/** Caches departures and widget UI states locally per station. */
class DeparturesCache(private val context: Context) {

    companion object {
        private val STATION_ID = stringPreferencesKey("station_id")
        private val TIME_DISPLAY_MODE = stringPreferencesKey("time_display_mode")
        private val GPS_MODE = booleanPreferencesKey("gps_mode")
        private val IS_REFRESHING = booleanPreferencesKey("is_refreshing")
        private val REFRESH_TS = longPreferencesKey("refresh_ts")
        private val ERROR_STATE = stringPreferencesKey("error_state")

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
            prefs[ERROR_STATE] = "" // Reset error on success
        }
    }

    suspend fun updateRefreshTime(stationId: String) {
        context.cacheDataStore.edit { prefs ->
            prefs[updatedKey(stationId)] = System.currentTimeMillis().toString()
        }
    }

    suspend fun getStationId(): String = 
        context.cacheDataStore.data.map { it[STATION_ID] }.first() ?: DEFAULT_STATION_ID

    suspend fun getDeparturesJson(stationId: String): String = 
        context.cacheDataStore.data.map { it[departuresKey(stationId)] }.first() ?: "[]"

    suspend fun getLastUpdated(stationId: String): String = 
        context.cacheDataStore.data.map { it[updatedKey(stationId)] }.first() ?: ""

    suspend fun getTabState(stationId: String): TransportFilter =
        TransportFilter.fromStorage(context.cacheDataStore.data.map { it[tabKey(stationId)] }.first())

    suspend fun getDirectionState(stationId: String): DirectionFilter =
        DirectionFilter.fromStorage(context.cacheDataStore.data.map { it[directionKey(stationId)] }.first())

    fun getTabStateFlow(stationId: String): Flow<TransportFilter> =
        context.cacheDataStore.data.map { TransportFilter.fromStorage(it[tabKey(stationId)]) }

    fun getDirectionStateFlow(stationId: String): Flow<DirectionFilter> =
        context.cacheDataStore.data.map { DirectionFilter.fromStorage(it[directionKey(stationId)]) }

    fun getDeparturesJsonFlow(stationId: String): Flow<String> = 
        context.cacheDataStore.data.map { it[departuresKey(stationId)] ?: "[]" }
        
    fun getStationIdFlow(): Flow<String> = 
        context.cacheDataStore.data.map { it[STATION_ID] ?: DEFAULT_STATION_ID }

    fun getLastUpdatedFlow(stationId: String): Flow<String> = 
        context.cacheDataStore.data.map { it[updatedKey(stationId)] ?: "" }

    suspend fun setTabState(stationId: String, state: TransportFilter) {
        context.cacheDataStore.edit { prefs ->
            prefs[tabKey(stationId)] = state.storageValue
        }
    }

    suspend fun setDirectionState(stationId: String, state: DirectionFilter) {
        context.cacheDataStore.edit { prefs ->
            prefs[directionKey(stationId)] = state.storageValue
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
        context.cacheDataStore.data.map { 
            val isR = it[IS_REFRESHING] ?: false
            val ts = it[REFRESH_TS] ?: 0L
            val now = System.currentTimeMillis()
            isR && (now - ts < 15000)
        }

    suspend fun isRefreshing(): Boolean {
        val prefs = context.cacheDataStore.data.first()
        val isR = prefs[IS_REFRESHING] ?: false
        val ts = prefs[REFRESH_TS] ?: 0L
        val now = System.currentTimeMillis()
        return isR && (now - ts < 15000)
    }

    suspend fun setRefreshing(refreshing: Boolean) {
        context.cacheDataStore.edit { prefs ->
            prefs[IS_REFRESHING] = refreshing
            if (refreshing) prefs[REFRESH_TS] = System.currentTimeMillis()
        }
    }

    suspend fun setErrorState(error: String) {
        context.cacheDataStore.edit { prefs ->
            prefs[ERROR_STATE] = error
        }
    }

    suspend fun getErrorState(): String = 
        context.cacheDataStore.data.map { it[ERROR_STATE] }.first() ?: ""
    
    fun getErrorStateFlow(): Flow<String> = 
        context.cacheDataStore.data.map { it[ERROR_STATE] ?: "" }
}

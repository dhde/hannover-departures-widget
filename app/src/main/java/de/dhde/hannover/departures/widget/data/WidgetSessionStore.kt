package de.dhde.hannover.departures.widget.data

import android.content.Context
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.*

/** Persistiert Anzeige-Optionen (Zeitformat, GPS) und transienten Session-State (Refresh, Fehler). */
class WidgetSessionStore(private val context: Context) {

    companion object {
        private val TIME_DISPLAY_MODE = stringPreferencesKey("time_display_mode")
        private val GPS_MODE = booleanPreferencesKey("gps_mode")
        private val IS_REFRESHING = booleanPreferencesKey("is_refreshing")
        private val REFRESH_TS = longPreferencesKey("refresh_ts")
        private val ERROR_STATE = stringPreferencesKey("error_state")
        private val DEBUG_MODE = booleanPreferencesKey("debug_mode")
    }

    fun debugModeFlow(): Flow<Boolean> =
        context.cacheDataStore.data.map { it[DEBUG_MODE] ?: false }

    suspend fun isDebugMode(): Boolean =
        context.cacheDataStore.data.map { it[DEBUG_MODE] }.first() ?: false

    suspend fun setDebugMode(enabled: Boolean) {
        context.cacheDataStore.edit { it[DEBUG_MODE] = enabled }
    }

    fun getTimeDisplayModeFlow(): Flow<String> =
        context.cacheDataStore.data.map { it[TIME_DISPLAY_MODE] ?: "MIN" }

    suspend fun getTimeDisplayMode(): String =
        context.cacheDataStore.data.map { it[TIME_DISPLAY_MODE] }.first() ?: "MIN"

    suspend fun setTimeDisplayMode(mode: String) {
        context.cacheDataStore.edit { it[TIME_DISPLAY_MODE] = mode }
    }

    fun getGpsModeFlow(): Flow<Boolean> =
        context.cacheDataStore.data.map { it[GPS_MODE] ?: false }

    suspend fun isGpsModeActive(): Boolean =
        context.cacheDataStore.data.map { it[GPS_MODE] }.first() ?: false

    suspend fun setGpsMode(active: Boolean) {
        context.cacheDataStore.edit { it[GPS_MODE] = active }
    }

    fun isRefreshingFlow(): Flow<Boolean> =
        context.cacheDataStore.data.map {
            isRefreshFresh(it[IS_REFRESHING] ?: false, it[REFRESH_TS] ?: 0L, System.currentTimeMillis())
        }

    suspend fun isRefreshing(): Boolean {
        val prefs = context.cacheDataStore.data.first()
        return isRefreshFresh(prefs[IS_REFRESHING] ?: false, prefs[REFRESH_TS] ?: 0L, System.currentTimeMillis())
    }

    suspend fun setRefreshing(refreshing: Boolean) {
        context.cacheDataStore.edit { prefs ->
            prefs[IS_REFRESHING] = refreshing
            if (refreshing) prefs[REFRESH_TS] = System.currentTimeMillis()
        }
    }

    suspend fun setErrorState(error: String) {
        context.cacheDataStore.edit { it[ERROR_STATE] = error }
    }

    suspend fun getErrorState(): String =
        context.cacheDataStore.data.map { it[ERROR_STATE] }.first() ?: ""

    fun getErrorStateFlow(): Flow<String> =
        context.cacheDataStore.data.map { it[ERROR_STATE] ?: "" }
}

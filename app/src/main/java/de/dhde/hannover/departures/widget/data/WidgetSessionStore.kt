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
        private val PICKER_MODE = booleanPreferencesKey("picker_mode")
        private val PICKER_CANDIDATES = stringPreferencesKey("picker_candidates_json")
        private val PICKER_OPENED_AT = longPreferencesKey("picker_opened_at")
        private val PICKER_FILTER_OVERRIDE = stringPreferencesKey("picker_filter_override")
        private val PICKER_LOC_LAT = doublePreferencesKey("picker_loc_lat")
        private val PICKER_LOC_LON = doublePreferencesKey("picker_loc_lon")
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

    // --- Picker-State ---

    fun pickerModeFlow(): Flow<Boolean> =
        context.cacheDataStore.data.map { it[PICKER_MODE] ?: false }

    suspend fun isPickerModeActive(): Boolean =
        context.cacheDataStore.data.map { it[PICKER_MODE] }.first() ?: false

    suspend fun setPickerMode(active: Boolean) {
        context.cacheDataStore.edit { it[PICKER_MODE] = active }
    }

    fun pickerCandidatesFlow(): Flow<String?> =
        context.cacheDataStore.data.map { it[PICKER_CANDIDATES] }

    suspend fun getPickerCandidatesJson(): String? =
        context.cacheDataStore.data.map { it[PICKER_CANDIDATES] }.first()

    suspend fun setPickerCandidatesJson(json: String?) {
        context.cacheDataStore.edit { prefs ->
            if (json == null) prefs.remove(PICKER_CANDIDATES) else prefs[PICKER_CANDIDATES] = json
        }
    }

    suspend fun getPickerOpenedAt(): Long =
        context.cacheDataStore.data.map { it[PICKER_OPENED_AT] }.first() ?: 0L

    suspend fun setPickerOpenedAt(ts: Long) {
        context.cacheDataStore.edit { it[PICKER_OPENED_AT] = ts }
    }

    fun pickerFilterOverrideFlow(): Flow<String?> =
        context.cacheDataStore.data.map { it[PICKER_FILTER_OVERRIDE] }

    suspend fun getPickerFilterOverride(): String? =
        context.cacheDataStore.data.map { it[PICKER_FILTER_OVERRIDE] }.first()

    suspend fun setPickerFilterOverride(value: String?) {
        context.cacheDataStore.edit { prefs ->
            if (value == null) prefs.remove(PICKER_FILTER_OVERRIDE) else prefs[PICKER_FILTER_OVERRIDE] = value
        }
    }

    suspend fun setPickerLocation(lat: Double?, lon: Double?) {
        context.cacheDataStore.edit { prefs ->
            if (lat == null || lon == null) {
                prefs.remove(PICKER_LOC_LAT); prefs.remove(PICKER_LOC_LON)
            } else {
                prefs[PICKER_LOC_LAT] = lat; prefs[PICKER_LOC_LON] = lon
            }
        }
    }

    suspend fun getPickerLocation(): Pair<Double, Double>? {
        val prefs = context.cacheDataStore.data.first()
        val lat = prefs[PICKER_LOC_LAT]; val lon = prefs[PICKER_LOC_LON]
        return if (lat != null && lon != null) lat to lon else null
    }

    suspend fun clearPickerState() {
        context.cacheDataStore.edit { prefs ->
            prefs[PICKER_MODE] = false
            prefs.remove(PICKER_CANDIDATES)
            prefs.remove(PICKER_FILTER_OVERRIDE)
            prefs.remove(PICKER_LOC_LAT); prefs.remove(PICKER_LOC_LON)
            // PICKER_OPENED_AT bleibt drin, aber picker_mode=false verhindert Wirkung
        }
    }
}

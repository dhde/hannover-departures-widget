package de.dhde.hannover.departures.widget.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*

val Context.cacheDataStore by preferencesDataStore(name = "departures_cache")

/** Fallback-Haltestelle (Hannover Hauptbahnhof), wenn noch keine Station gewählt wurde. */
const val DEFAULT_STATION_ID = "25000031"

/** Persistiert gecachte Abfahrtsdaten pro Station. */
class DeparturesCache(private val context: Context) {

    companion object {
        private val STATION_ID = stringPreferencesKey("station_id")
        private fun departuresKey(id: String) = stringPreferencesKey("deps_$id")
        private fun updatedKey(id: String) = stringPreferencesKey("upd_$id")
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
        context.cacheDataStore.data.map { it[STATION_ID] }.first() ?: DEFAULT_STATION_ID

    fun getStationIdFlow(): Flow<String> =
        context.cacheDataStore.data.map { it[STATION_ID] ?: DEFAULT_STATION_ID }

    suspend fun getDeparturesJson(stationId: String): String =
        context.cacheDataStore.data.map { it[departuresKey(stationId)] }.first() ?: "[]"

    fun getDeparturesJsonFlow(stationId: String): Flow<String> =
        context.cacheDataStore.data.map { it[departuresKey(stationId)] ?: "[]" }

    suspend fun getLastUpdated(stationId: String): String =
        context.cacheDataStore.data.map { it[updatedKey(stationId)] }.first() ?: ""

    fun getLastUpdatedFlow(stationId: String): Flow<String> =
        context.cacheDataStore.data.map { it[updatedKey(stationId)] ?: "" }
}

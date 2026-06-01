package de.dhde.hannover.departures.widget.data

import android.content.Context
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.*

/** Persistiert die per-Station Filter-Auswahl (Verkehrsmittel + Richtung). */
class FilterStateStore(private val context: Context) {

    companion object {
        private fun tabKey(stationId: String) = stringPreferencesKey("tab_state_$stationId")
        private fun directionKey(stationId: String) = stringPreferencesKey("direction_state_$stationId")
    }

    suspend fun getTabState(stationId: String): TransportFilter =
        TransportFilter.fromStorage(context.cacheDataStore.data.map { it[tabKey(stationId)] }.first())

    suspend fun getDirectionState(stationId: String): DirectionFilter =
        DirectionFilter.fromStorage(context.cacheDataStore.data.map { it[directionKey(stationId)] }.first())

    fun getTabStateFlow(stationId: String): Flow<TransportFilter> =
        context.cacheDataStore.data.map { TransportFilter.fromStorage(it[tabKey(stationId)]) }

    fun getDirectionStateFlow(stationId: String): Flow<DirectionFilter> =
        context.cacheDataStore.data.map { DirectionFilter.fromStorage(it[directionKey(stationId)]) }

    suspend fun setTabState(stationId: String, state: TransportFilter) {
        context.cacheDataStore.edit { it[tabKey(stationId)] = state.storageValue }
    }

    suspend fun setDirectionState(stationId: String, state: DirectionFilter) {
        context.cacheDataStore.edit { it[directionKey(stationId)] = state.storageValue }
    }
}

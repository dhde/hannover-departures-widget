package de.dhde.hannover.departures.widget.data

import android.content.Context
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.*

/** Persistiert die per-Station Filter-Auswahl (Verkehrsmittel + Steig). */
class FilterStateStore(private val context: Context) {

    companion object {
        private fun tabKey(stationId: String) = stringPreferencesKey("tab_state_$stationId")
        // Neuer Key statt "direction_state_…": alte H/R-Werte werden so automatisch zu „alle".
        private fun platformKey(stationId: String) = stringPreferencesKey("platform_state_$stationId")
    }

    suspend fun getTabState(stationId: String): TransportFilter =
        TransportFilter.fromStorage(context.cacheDataStore.data.map { it[tabKey(stationId)] }.first())

    /** Gewählter Steig (platformId) oder null = alle. */
    suspend fun getPlatformState(stationId: String): String? =
        context.cacheDataStore.data.map { it[platformKey(stationId)]?.takeIf(String::isNotBlank) }.first()

    fun getTabStateFlow(stationId: String): Flow<TransportFilter> =
        context.cacheDataStore.data.map { TransportFilter.fromStorage(it[tabKey(stationId)]) }

    fun getPlatformStateFlow(stationId: String): Flow<String?> =
        context.cacheDataStore.data.map { it[platformKey(stationId)]?.takeIf(String::isNotBlank) }

    suspend fun setTabState(stationId: String, state: TransportFilter) {
        context.cacheDataStore.edit { it[tabKey(stationId)] = state.storageValue }
    }

    suspend fun setPlatformState(stationId: String, platformId: String?) {
        context.cacheDataStore.edit { it[platformKey(stationId)] = platformId.orEmpty() }
    }
}

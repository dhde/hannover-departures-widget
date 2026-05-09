package de.dhde.hannover.departures.widget.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.*

val Context.dataStore by preferencesDataStore(name = "favorites_prefs")

data class FavoriteStation(
    val id: String,
    val name: String,
    val alias: String? = null
)

/** Persistiert Favoriten-Haltestellen und die aktive Widget-Station. */
class FavoritesRepository(private val context: Context) {
    private val gson = Gson()

    companion object {
        private val OLD_FAVORITES_KEY = stringSetPreferencesKey("saved_favorites")
        private val FAVORITES_JSON_KEY = stringPreferencesKey("favorites_json")
        val WIDGET_MODE_KEY      = stringPreferencesKey("widget_mode")            // "gps" | "station"
        val ACTIVE_STATION_ID    = stringPreferencesKey("active_station_id")
        val ACTIVE_STATION_NAME  = stringPreferencesKey("active_station_name")
        val MAX_FAV_KEY          = intPreferencesKey("max_favorites_widget")
        val MAX_FAV_ROWS_KEY     = intPreferencesKey("max_fav_rows_widget")
        val MAX_ROWS_KEY         = intPreferencesKey("max_rows_widget")
    }

    // ── Aktive Station ───────────────────────────────────────────────────────

    val activeStationId: Flow<String?> = context.dataStore.data
        .map { it[ACTIVE_STATION_ID] }

    val activeStationName: Flow<String?> = context.dataStore.data
        .map { it[ACTIVE_STATION_NAME] }

    /** Gibt Favoriten als geordnete Liste zurück. Migriert automatisch vom alten Format. */
    val favoritesFlow: Flow<List<FavoriteStation>> = context.dataStore.data.map { prefs ->
        val json = prefs[FAVORITES_JSON_KEY]
        if (json != null) {
            val type = object : TypeToken<List<FavoriteStation>>() {}.type
            gson.fromJson(json, type)
        } else {
            // Migration vom alten StringSet
            val oldSet = prefs[OLD_FAVORITES_KEY] ?: emptySet()
            oldSet.map { entry ->
                val parts = entry.split("|", limit = 2)
                FavoriteStation(parts.getOrElse(0) { "" }, parts.getOrElse(1) { entry })
            }
        }
    }

    /** Findet den Alias oder Namen für die aktuell aktive Station. */
    val effectiveStationName: Flow<String> = combine(activeStationId, activeStationName, favoritesFlow) { id, official, favs ->
        favs.find { it.id == id }?.alias ?: official ?: "Unbekannt"
    }

    suspend fun setActiveStation(id: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[ACTIVE_STATION_ID]   = id
            prefs[ACTIVE_STATION_NAME] = name
            prefs[WIDGET_MODE_KEY]     = "station"
        }
    }

    suspend fun getActiveStationIdNow(): String {
        var id = "25000031"
        context.dataStore.data.map { prefs -> prefs[ACTIVE_STATION_ID] }.take(1).collect { saved ->
            if (saved != null) id = saved
        }
        return id
    }

    // ── Favoriten ────────────────────────────────────────────────────────────

    suspend fun getFavoritesNow(): List<FavoriteStation> {
        var list = emptyList<FavoriteStation>()
        favoritesFlow.take(1).collect { list = it }
        return list
    }

    suspend fun addFavorite(id: String, name: String) {
        val current = getFavoritesNow().toMutableList()
        if (current.none { it.id == id }) {
            current.add(FavoriteStation(id, name))
            saveFavorites(current)
        }
    }

    suspend fun removeFavorite(id: String) {
        val updated = getFavoritesNow().filter { it.id != id }
        saveFavorites(updated)
    }

    suspend fun moveFavorite(id: String, up: Boolean) {
        val list = getFavoritesNow().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index == -1) return
        
        val target = if (up) index - 1 else index + 1
        if (target in 0 until list.size) {
            val item = list.removeAt(index)
            list.add(target, item)
            saveFavorites(list)
        }
    }

    suspend fun setFavoriteAlias(id: String, alias: String?) {
        val list = getFavoritesNow().map { fav ->
            if (fav.id == id) fav.copy(alias = alias?.takeIf { it.isNotBlank() }) else fav
        }
        saveFavorites(list)
    }

    suspend fun updateFavoritesOrder(list: List<FavoriteStation>) {
        saveFavorites(list)
    }

    private suspend fun saveFavorites(list: List<FavoriteStation>) {
        context.dataStore.edit { prefs ->
            prefs[FAVORITES_JSON_KEY] = gson.toJson(list)
            // Altes Set löschen, um Migration abzuschließen
            prefs.remove(OLD_FAVORITES_KEY)
        }
    }

    suspend fun isFavorite(id: String): Boolean =
        getFavoritesNow().any { fav -> fav.id == id }

    // ── Widget-Modus ─────────────────────────────────────────────────────────

    val widgetModeFlow: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[WIDGET_MODE_KEY] ?: "station" }

    suspend fun setWidgetMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[WIDGET_MODE_KEY] = mode
        }
    }

    // ── UI Einstellungen ─────────────────────────────────────────────────────

    val maxFavoritesFlow: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[MAX_FAV_KEY] ?: 3 }

    val maxFavRowsFlow: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[MAX_FAV_ROWS_KEY] ?: 1 }

    val maxRowsFlow: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[MAX_ROWS_KEY] ?: 10 }

    suspend fun setMaxFavorites(max: Int) {
        context.dataStore.edit { prefs -> prefs[MAX_FAV_KEY] = max }
    }

    suspend fun setMaxFavRows(max: Int) {
        context.dataStore.edit { prefs -> prefs[MAX_FAV_ROWS_KEY] = max }
    }

    suspend fun setMaxRows(max: Int) {
        context.dataStore.edit { prefs -> prefs[MAX_ROWS_KEY] = max }
    }
}

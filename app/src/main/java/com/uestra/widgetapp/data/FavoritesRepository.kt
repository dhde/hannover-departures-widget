package com.uestra.widgetapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "favorites_prefs")

/** Persistiert Favoriten-Haltestellen und die aktive Widget-Station. */
class FavoritesRepository(private val context: Context) {

    companion object {
        val FAVORITES_KEY        = stringSetPreferencesKey("saved_favorites")     // Set<"id|name">
        val WIDGET_MODE_KEY      = stringPreferencesKey("widget_mode")            // "gps" | "station"
        val ACTIVE_STATION_ID    = stringPreferencesKey("active_station_id")
        val ACTIVE_STATION_NAME  = stringPreferencesKey("active_station_name")
    }

    // ── Aktive Station ───────────────────────────────────────────────────────

    val activeStationId: Flow<String?> = context.dataStore.data
        .map { it[ACTIVE_STATION_ID] }

    val activeStationName: Flow<String?> = context.dataStore.data
        .map { it[ACTIVE_STATION_NAME] }

    suspend fun setActiveStation(id: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[ACTIVE_STATION_ID]   = id
            prefs[ACTIVE_STATION_NAME] = name
            prefs[WIDGET_MODE_KEY]     = "station"
        }
    }

    suspend fun getActiveStationIdNow(): String =
        context.dataStore.data.first()[ACTIVE_STATION_ID] ?: "25000031" // Fallback: Kröpcke

    // ── Favoriten ────────────────────────────────────────────────────────────

    /** Gibt Favoriten als Liste von Pair(id, name) zurück. */
    val favoritesFlow: Flow<List<Pair<String, String>>> =
        context.dataStore.data.map { prefs ->
            prefs[FAVORITES_KEY]
                ?.map { entry ->
                    val parts = entry.split("|", limit = 2)
                    Pair(parts.getOrElse(0) { "" }, parts.getOrElse(1) { entry })
                } ?: emptyList()
        }

    suspend fun getFavoritesNow(): List<Pair<String, String>> = favoritesFlow.first()

    suspend fun addFavorite(id: String, name: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[FAVORITES_KEY] ?: emptySet()
            prefs[FAVORITES_KEY] = current + "$id|$name"
        }
    }

    suspend fun removeFavorite(id: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[FAVORITES_KEY] ?: emptySet()
            prefs[FAVORITES_KEY] = current.filter { !it.startsWith("$id|") }.toSet()
        }
    }

    suspend fun isFavorite(id: String): Boolean =
        context.dataStore.data.first()[FAVORITES_KEY]
            ?.any { it.startsWith("$id|") } ?: false

    // ── Widget-Modus ─────────────────────────────────────────────────────────

    val widgetModeFlow: Flow<String> = context.dataStore.data
        .map { it[WIDGET_MODE_KEY] ?: "station" }

    suspend fun setWidgetMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[WIDGET_MODE_KEY] = mode
        }
    }
}

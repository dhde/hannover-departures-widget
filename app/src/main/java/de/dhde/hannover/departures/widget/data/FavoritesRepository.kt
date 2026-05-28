package de.dhde.hannover.departures.widget.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    val alias: String? = null,
    val filteredLines: Set<String>? = null
)

data class SeenMessageEntry(
    val count: Int,
    val lastSeenMillis: Long
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
        val TRANSPORT_TYPES_KEY  = stringSetPreferencesKey("transport_types_filter")
        val IGNORED_MESSAGES_KEY = stringSetPreferencesKey("ignored_messages_filter")
        val FAVORITES_HEIGHT_KEY = stringPreferencesKey("favorites_buttons_height")
        val FILTER_HEIGHT_KEY    = stringPreferencesKey("filter_buttons_height")
        val AUTO_REFRESH_ON_INTERACTION_KEY = booleanPreferencesKey("auto_refresh_on_interaction")
        val GROUP_DEPARTURES_KEY = booleanPreferencesKey("group_departures")
        val GROUP_DEPARTURES_MAX_KEY = intPreferencesKey("group_departures_max")
        val SEEN_MESSAGES_KEY    = stringPreferencesKey("seen_messages_json")
        val GROUP_FONT_SIZE_KEY  = stringPreferencesKey("group_font_size")  // "KLEIN" | "STANDARD" | "GROSS"
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

    suspend fun setFavoriteLineFilter(id: String, lines: Set<String>?) {
        val list = getFavoritesNow().map { fav ->
            if (fav.id == id) fav.copy(filteredLines = lines) else fav
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

    // ── Verkehrsmittel Filter ────────────────────────────────────────────────

    val transportTypesFlow: Flow<Set<String>> = context.dataStore.data
        .map { prefs -> prefs[TRANSPORT_TYPES_KEY] ?: setOf("Stadtbahn", "Bus", "S-Bahn") }

    suspend fun setTransportTypes(types: Set<String>) {
        context.dataStore.edit { prefs -> prefs[TRANSPORT_TYPES_KEY] = types }
    }

    // ── Meldungs-Filter ──────────────────────────────────────────────────────

    val ignoredMessagesFlow: Flow<Set<String>> = context.dataStore.data
        .map { prefs -> prefs[IGNORED_MESSAGES_KEY] ?: emptySet() }

    suspend fun setIgnoredMessages(ignored: Set<String>) {
        context.dataStore.edit { prefs -> prefs[IGNORED_MESSAGES_KEY] = ignored }
    }

    // ── Button-Höhen Einstellungen ───────────────────────────────────────────

    val favoritesHeightFlow: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[FAVORITES_HEIGHT_KEY] ?: "STANDARD" }

    suspend fun setFavoritesHeight(height: String) {
        context.dataStore.edit { prefs -> prefs[FAVORITES_HEIGHT_KEY] = height }
    }

    val filterHeightFlow: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[FILTER_HEIGHT_KEY] ?: "STANDARD" }

    suspend fun setFilterHeight(height: String) {
        context.dataStore.edit { prefs -> prefs[FILTER_HEIGHT_KEY] = height }
    }

    // ── Auto-Refresh bei Interaktion ─────────────────────────────────────────

    val autoRefreshOnInteractionFlow: Flow<Boolean> = context.dataStore.data.map { it[AUTO_REFRESH_ON_INTERACTION_KEY] ?: false }
    suspend fun setAutoRefreshOnInteraction(autoRefresh: Boolean) { context.dataStore.edit { it[AUTO_REFRESH_ON_INTERACTION_KEY] = autoRefresh } }

    val groupDeparturesFlow: Flow<Boolean> = context.dataStore.data.map { it[GROUP_DEPARTURES_KEY] ?: true }
    suspend fun setGroupDepartures(group: Boolean) { context.dataStore.edit { it[GROUP_DEPARTURES_KEY] = group } }

    val maxGroupedDeparturesFlow: Flow<Int> = context.dataStore.data.map { it[GROUP_DEPARTURES_MAX_KEY] ?: 2 }
    suspend fun setMaxGroupedDepartures(max: Int) { context.dataStore.edit { it[GROUP_DEPARTURES_MAX_KEY] = max } }

    // ── Dynamic Message Tracking ─────────────────────────────────────────────

    val seenMessagesFlow: Flow<Map<String, SeenMessageEntry>> = context.dataStore.data.map { prefs ->
        val json = prefs[SEEN_MESSAGES_KEY]
        if (json != null) {
            val type = object : TypeToken<Map<String, SeenMessageEntry>>() {}.type
            try {
                gson.fromJson(json, type) ?: emptyMap()
            } catch (e: Exception) {
                emptyMap() // Graceful reset if old format (Map<String, Int>) is encountered
            }
        } else {
            emptyMap()
        }
    }

    suspend fun trackMessages(messages: List<String>) {
        val now = System.currentTimeMillis()
        val twoDaysMillis = 2 * 24 * 60 * 60 * 1000L

        context.dataStore.edit { prefs ->
            val currentJson = prefs[SEEN_MESSAGES_KEY]
            val type = object : TypeToken<MutableMap<String, SeenMessageEntry>>() {}.type
            val entries: MutableMap<String, SeenMessageEntry> = if (currentJson != null) {
                try {
                    gson.fromJson(currentJson, type) ?: mutableMapOf()
                } catch (e: Exception) {
                    mutableMapOf()
                }
            } else {
                mutableMapOf()
            }

            var changed = false
            
            // Prune old messages
            val iterator = entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value.lastSeenMillis > twoDaysMillis) {
                    iterator.remove()
                    changed = true
                    
                    // Also cleanup from ignored messages list if it expired
                    val currentIgnored = prefs[IGNORED_MESSAGES_KEY] ?: emptySet()
                    if (entry.key in currentIgnored) {
                        prefs[IGNORED_MESSAGES_KEY] = currentIgnored - entry.key
                    }
                }
            }

            // Add new messages
            for (msg in messages) {
                val cleanMsg = msg.trim()
                if (cleanMsg.isNotEmpty()) {
                    val currentCount = entries[cleanMsg]?.count ?: 0
                    // Cap the count at 20
                    entries[cleanMsg] = SeenMessageEntry(
                        count = minOf(currentCount + 1, 20),
                        lastSeenMillis = now
                    )
                    changed = true
                }
            }

            if (changed) {
                // Keep only top 50 messages to prevent unbounded growth
                val sortedEntries = entries.entries.sortedByDescending { it.value.count }.take(50).associate { it.key to it.value }
                prefs[SEEN_MESSAGES_KEY] = gson.toJson(sortedEntries)
            }
        }
    }

    suspend fun removeSeenMessage(message: String) {
        context.dataStore.edit { prefs ->
            val currentJson = prefs[SEEN_MESSAGES_KEY]
            val type = object : TypeToken<MutableMap<String, SeenMessageEntry>>() {}.type
            val entries: MutableMap<String, SeenMessageEntry> = if (currentJson != null) {
                try {
                    gson.fromJson(currentJson, type) ?: mutableMapOf()
                } catch (e: Exception) {
                    mutableMapOf()
                }
            } else return@edit
            entries.remove(message.trim())
            prefs[SEEN_MESSAGES_KEY] = gson.toJson(entries)
        }
    }

    suspend fun getAutoRefreshOnInteractionNow(): Boolean {
        var value = false
        autoRefreshOnInteractionFlow.take(1).collect { value = it }
        return value
    }

    // ── Grouped Departure Font Size ──────────────────────────────────────────

    val groupedFontSizeFlow: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[GROUP_FONT_SIZE_KEY] ?: "STANDARD" }

    suspend fun setGroupedFontSize(size: String) {
        context.dataStore.edit { prefs -> prefs[GROUP_FONT_SIZE_KEY] = size }
    }
}

/**
 * Filtert Meldungen anhand der vom Benutzer ausgewählten ignorierten Texte.
 * Direkter Textvergleich (case-insensitive contains) – keine hardcodierten Keywords.
 */
fun filterMessages(messages: List<String>, ignoredCategories: Set<String>): List<String> {
    if (ignoredCategories.isEmpty()) return messages
    return messages.filter { msg ->
        val lowerMsg = msg.trim().lowercase()
        ignoredCategories.none { ignored -> lowerMsg.contains(ignored.trim().lowercase()) }
    }
}

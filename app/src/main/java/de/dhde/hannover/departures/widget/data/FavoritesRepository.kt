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
import de.dhde.hannover.departures.widget.api.MsgItem
import kotlinx.coroutines.flow.*

val Context.dataStore by preferencesDataStore(name = "favorites_prefs")

data class FavoriteStation(
    val id: String,
    val name: String,
    val alias: String? = null,
    val filteredLines: Set<String>? = null,
    val uniqueId: String? = null,
    val transportFilter: String? = null,
    val directionFilter: String? = null
) {
    val safeUniqueId: String
        get() = uniqueId ?: id
}

/**
 * Katalog-Eintrag einer gesehenen Meldung, abgelegt unter ihrer stabilen ID.
 * - [content]: Anzeigetext (für die Ausblendliste).
 * - [startMillis]: incidentStart der API (0, falls unbekannt – z.B. bei hints).
 * - [firstSeenMillis]: erstes Auftreten in der App – Fallback fürs Alter, wenn kein startMillis vorliegt.
 */
data class SeenMessageEntry(
    val count: Int,
    val lastSeenMillis: Long,
    val transportTypes: Set<String> = emptySet(),
    val content: String = "",
    val startMillis: Long = 0L,
    val firstSeenMillis: Long = 0L
) {
    /** Maßgeblicher Zeitpunkt fürs Alter: echtes Startdatum, sonst erstes Sehen. */
    val effectiveStartMillis: Long get() = if (startMillis > 0) startMillis else firstSeenMillis
}

/** Zu trackende Meldung (aus den API-Daten gebildet). */
data class TrackedMessage(
    val id: String,
    val content: String,
    val startMillis: Long,
    val transportTypes: Set<String>
)

/** Persistiert Favoriten-Haltestellen und die aktive Widget-Station. */
class FavoritesRepository(private val context: Context) {
    private val gson = Gson()

    companion object {
        private val OLD_FAVORITES_KEY = stringSetPreferencesKey("saved_favorites")
        private val FAVORITES_JSON_KEY = stringPreferencesKey("favorites_json")
        val WIDGET_MODE_KEY      = stringPreferencesKey("widget_mode")            // "gps" | "station"
        val ACTIVE_STATION_ID    = stringPreferencesKey("active_station_id")
        val ACTIVE_FAVORITE_UNIQUE_ID = stringPreferencesKey("active_fav_unique_id")
        val ACTIVE_STATION_NAME  = stringPreferencesKey("active_station_name")
        val MAX_FAV_KEY          = intPreferencesKey("max_favorites_widget")
        val MAX_FAV_ROWS_KEY     = intPreferencesKey("max_fav_rows_widget")
        val MAX_ROWS_KEY         = intPreferencesKey("max_rows_widget")
        val TRANSPORT_TYPES_KEY  = stringSetPreferencesKey("transport_types_filter_v2")
        val IGNORED_MESSAGES_KEY = stringSetPreferencesKey("ignored_messages_filter")
        val FAVORITES_HEIGHT_KEY = stringPreferencesKey("favorites_buttons_height")
        val FILTER_HEIGHT_KEY    = stringPreferencesKey("filter_buttons_height")
        val AUTO_REFRESH_ON_INTERACTION_KEY = booleanPreferencesKey("auto_refresh_on_interaction")
        val GROUP_DEPARTURES_KEY = booleanPreferencesKey("group_departures")
        val GROUP_DEPARTURES_MAX_KEY = intPreferencesKey("group_departures_max")
        val SEEN_MESSAGES_KEY    = stringPreferencesKey("seen_messages_json")
        val GROUP_FONT_SIZE_KEY  = stringPreferencesKey("group_font_size")  // "KLEIN" | "STANDARD" | "GROSS"
        val ALLOW_DUPLICATES_KEY = booleanPreferencesKey("allow_duplicates")
    }

    // ── Aktive Station ───────────────────────────────────────────────────────

    val allowDuplicatesFlow: Flow<Boolean> = context.dataStore.data
        .map { it[ALLOW_DUPLICATES_KEY] ?: false }

    val activeStationId: Flow<String?> = context.dataStore.data
        .map { it[ACTIVE_STATION_ID] }

    val activeFavoriteUniqueId: Flow<String?> = context.dataStore.data
        .map { it[ACTIVE_FAVORITE_UNIQUE_ID] ?: it[ACTIVE_STATION_ID] }

    val activeStationName: Flow<String?> = context.dataStore.data
        .map { it[ACTIVE_STATION_NAME] }

    /** Gibt Favoriten als geordnete Liste zurück. Migriert automatisch vom alten Format. */
    val favoritesFlow: Flow<List<FavoriteStation>> = context.dataStore.data.map { prefs ->
        val json = prefs[FAVORITES_JSON_KEY]
        val list = if (json != null) {
            val type = object : TypeToken<List<FavoriteStation>>() {}.type
            gson.fromJson<List<FavoriteStation>>(json, type) ?: emptyList()
        } else {
            // Migration vom alten StringSet
            val oldSet = prefs[OLD_FAVORITES_KEY] ?: emptySet()
            oldSet.map { entry ->
                val parts = entry.split("|", limit = 2)
                FavoriteStation(parts.getOrElse(0) { "" }, parts.getOrElse(1) { entry })
            }
        }
        
        // Deterministisch deduplizieren: ein zufälliges UUID je Emission würde dazu führen,
        // dass die aktive Auswahl (ACTIVE_FAVORITE_UNIQUE_ID) nach dem nächsten Read nicht mehr
        // matcht. Stattdessen positionsstabiler Suffix → identischer Output bei gleicher Liste.
        val seen = mutableSetOf<String>()
        list.mapIndexed { index, fav ->
            if (!seen.add(fav.safeUniqueId)) {
                val deduped = "${fav.safeUniqueId}#$index"
                seen.add(deduped)
                fav.copy(uniqueId = deduped)
            } else fav
        }
    }

    /** Findet den Alias oder Namen für die aktuell aktive Station. */
    val effectiveStationName: Flow<String> = combine(activeFavoriteUniqueId, activeStationName, favoritesFlow) { uniqueId, official, favs ->
        favs.find { it.safeUniqueId == uniqueId }?.alias ?: official ?: "Unbekannt"
    }

    suspend fun setActiveStation(uniqueId: String, name: String) {
        val fav = getFavoritesNow().find { it.safeUniqueId == uniqueId }
        val realId = fav?.id ?: uniqueId
        
        context.dataStore.edit { prefs ->
            prefs[ACTIVE_FAVORITE_UNIQUE_ID] = uniqueId
            prefs[ACTIVE_STATION_ID]   = realId
            prefs[ACTIVE_STATION_NAME] = name
            prefs[WIDGET_MODE_KEY]     = "station"
        }
        
        // Filter des gewählten Favoriten in den Cache übernehmen
        val filters = FilterStateStore(context)
        filters.setTabState(realId, TransportFilter.fromStorage(fav?.transportFilter))
        filters.setDirectionState(realId, DirectionFilter.fromStorage(fav?.directionFilter))
    }

    suspend fun getActiveStationIdNow(): String =
        context.dataStore.data.first()[ACTIVE_STATION_ID] ?: DEFAULT_STATION_ID

    suspend fun getActiveFavoriteUniqueIdNow(): String? =
        context.dataStore.data.first()[ACTIVE_FAVORITE_UNIQUE_ID]

    // ── Favoriten ────────────────────────────────────────────────────────────

    suspend fun getFavoritesNow(): List<FavoriteStation> = favoritesFlow.first()

    suspend fun addFavorite(id: String, name: String) {
        val current = getFavoritesNow().toMutableList()
        if (current.none { it.id == id }) {
            current.add(FavoriteStation(id, name))
            saveFavorites(current)
        }
    }

    suspend fun removeFavoriteByUniqueId(uniqueId: String) {
        val updated = getFavoritesNow().filter { it.safeUniqueId != uniqueId }
        saveFavorites(updated)
    }

    suspend fun removeAllFavoritesByStationId(stationId: String) {
        val updated = getFavoritesNow().filter { it.id != stationId }
        saveFavorites(updated)
    }

    suspend fun duplicateFavorite(uniqueId: String) {
        val list = getFavoritesNow().toMutableList()
        val index = list.indexOfFirst { it.safeUniqueId == uniqueId }
        if (index != -1) {
            val original = list[index]
            val duplicate = original.copy(uniqueId = java.util.UUID.randomUUID().toString())
            list.add(index + 1, duplicate)
            saveFavorites(list)
        }
    }

    suspend fun moveFavorite(uniqueId: String, up: Boolean) {
        val list = getFavoritesNow().toMutableList()
        val index = list.indexOfFirst { it.safeUniqueId == uniqueId }
        if (index == -1) return
        
        val target = if (up) index - 1 else index + 1
        if (target in 0 until list.size) {
            val item = list.removeAt(index)
            list.add(target, item)
            saveFavorites(list)
        }
    }

    suspend fun setFavoriteAlias(uniqueId: String, alias: String?) {
        val list = getFavoritesNow().map { fav ->
            if (fav.safeUniqueId == uniqueId) fav.copy(alias = alias?.takeIf { it.isNotBlank() }) else fav
        }
        saveFavorites(list)
    }

    suspend fun setFavoriteLineFilter(uniqueId: String, lines: Set<String>?) {
        val list = getFavoritesNow().map { fav ->
            if (fav.safeUniqueId == uniqueId) fav.copy(filteredLines = lines) else fav
        }
        saveFavorites(list)
    }

    suspend fun setFavoriteTransportFilter(uniqueId: String, filter: TransportFilter?) {
        val list = getFavoritesNow().map { fav ->
            if (fav.safeUniqueId == uniqueId) fav.copy(transportFilter = filter?.storageValue) else fav
        }
        saveFavorites(list)
    }

    suspend fun setFavoriteDirectionFilter(uniqueId: String, filter: DirectionFilter?) {
        val list = getFavoritesNow().map { fav ->
            if (fav.safeUniqueId == uniqueId) fav.copy(directionFilter = filter?.storageValue) else fav
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

    suspend fun setAllowDuplicates(allowed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[ALLOW_DUPLICATES_KEY] = allowed
        }
        if (!allowed) {
            val list = getFavoritesNow()
            val seen = mutableSetOf<String>()
            val cleaned = list.filter { seen.add(it.id) }
            if (cleaned.size != list.size) {
                saveFavorites(cleaned)
            }
        }
    }

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
        .map { prefs -> prefs[TRANSPORT_TYPES_KEY] ?: setOf("Stadtbahn", "Bus", "S-Bahn", "DB") }

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

    suspend fun trackMessages(messages: List<TrackedMessage>) {
        if (messages.isEmpty()) return
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

            // Add new messages (keyed by stable id)
            for (m in messages) {
                if (m.id.isBlank() || m.content.isBlank()) continue
                val currentEntry = entries[m.id]
                val firstSeen = currentEntry?.firstSeenMillis?.takeIf { it > 0 } ?: now

                // Cap the count at 10000
                entries[m.id] = SeenMessageEntry(
                    count = minOf((currentEntry?.count ?: 0) + 1, 10000),
                    lastSeenMillis = now,
                    transportTypes = (currentEntry?.transportTypes ?: emptySet()) + m.transportTypes,
                    content = m.content,
                    startMillis = if (m.startMillis > 0) m.startMillis else (currentEntry?.startMillis ?: 0L),
                    firstSeenMillis = firstSeen
                )
                changed = true
            }

            if (changed) {
                // Keep only the 50 most recently seen messages to prevent unbounded growth
                val sortedEntries = entries.entries.sortedByDescending { it.value.lastSeenMillis }.take(50).associate { it.key to it.value }
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

    suspend fun getAutoRefreshOnInteractionNow(): Boolean = autoRefreshOnInteractionFlow.first()

    // ── Grouped Departure Font Size ──────────────────────────────────────────

    val groupedFontSizeFlow: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[GROUP_FONT_SIZE_KEY] ?: "STANDARD" }

    suspend fun setGroupedFontSize(size: String) {
        context.dataStore.edit { prefs -> prefs[GROUP_FONT_SIZE_KEY] = size }
    }
}

/** Präfixe der von uns vergebenen Meldungs-IDs (API-ID "ems-…" bzw. synthetisch "c:"/"h:"). */
private fun isMessageId(s: String): Boolean =
    s.startsWith("ems-") || s.startsWith("c:") || s.startsWith("h:")

/**
 * Blendet ausgeblendete Meldungen aus. [ignored] enthält primär stabile IDs;
 * aus Abwärtskompatibilität können noch alte text-basierte Einträge enthalten sein,
 * die weiterhin per (case-insensitive) Teilstring-Vergleich greifen.
 */
fun filterMessages(messages: List<MsgItem>, ignored: Set<String>): List<MsgItem> {
    if (ignored.isEmpty()) return messages
    val legacyTexts = ignored.filterNot { isMessageId(it) }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    return messages.filter { msg ->
        if (msg.id in ignored) return@filter false
        val lc = msg.content.trim().lowercase()
        legacyTexts.none { lc.contains(it) }
    }
}

package de.dhde.hannover.departures.widget.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.dhde.hannover.departures.widget.api.StationSearchResult
import de.dhde.hannover.departures.widget.api.UestraApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class StopsRepository(context: Context) {
    private val fileName = "uestra_stops_v2.json"
    private val file = File(context.cacheDir, fileName)
    private val CACHE_DURATION_MS = 7L * 24 * 60 * 60 * 1000 // 7 Tage

    suspend fun getAllStops(): List<StationSearchResult> = withContext(Dispatchers.IO) {
        // Prüfen, ob Cache existiert und noch gütlig ist
        if (file.exists() && (System.currentTimeMillis() - file.lastModified() < CACHE_DURATION_MS)) {
            try {
                val json = file.readText()
                val type = object : TypeToken<List<StationSearchResult>>() {}.type
                val stops: List<StationSearchResult> = Gson().fromJson(json, type)
                if (stops.isNotEmpty()) {
                    return@withContext stops
                }
            } catch (e: Exception) {
                // Bei Fehler im Cache (z.B. defekte Datei) weiter zum Network-Fallback
            }
        }

        // Von der API abrufen
        try {
            val api = UestraApi.create()
            val stops = api.getAllStops()
            if (stops.isNotEmpty()) {
                val json = Gson().toJson(stops)
                file.writeText(json)
            }
            stops
        } catch (e: Exception) {
            emptyList()
        }
    }
}

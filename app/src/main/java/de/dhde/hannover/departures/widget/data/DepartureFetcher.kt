package de.dhde.hannover.departures.widget.data

import android.content.Context
import de.dhde.hannover.departures.widget.api.DepartureItem
import de.dhde.hannover.departures.widget.api.UestraApi

/**
 * Die EFA liefert pro Abfrage max. 40 Abfahrten. An großen Halten (Kröpcke: 9 Steige) bleiben
 * pro Steig nur wenige übrig. Fragt man mit der Steig-ID (`bon`, z.B. "de:03241:11:1:11") statt
 * der Haltestellen-ID ab, kommen 40 Abfahrten NUR dieses Steigs.
 *
 * Ist ein Steig gewählt, werden daher beide Abfragen kombiniert: die Haltestelle (damit Chips/Liste
 * alle Steige kennen) und der Steig (für die vollständige Abfahrtsliste), siehe [mergePlatformDepartures].
 */
suspend fun fetchDepartures(context: Context, stationId: String, platformId: String?): List<DepartureItem>? {
    val api = UestraApi.create()
    val station = api.getDepartures(stationId, UestraApi.DEFAULT_DEP_SEQUENCE).departures ?: return null
    val merged = if (platformId.isNullOrBlank()) station else {
        // Fehler bei der Zusatzabfrage: auf die Haltestellen-Daten zurückfallen statt gar nichts zu zeigen.
        val platform = runCatching {
            api.getDepartures(platformId, UestraApi.DEFAULT_DEP_SEQUENCE).departures
        }.getOrNull().orEmpty()
        mergePlatformDepartures(station, platform, platformId)
    }
    return merged.withPlatformNames(StopsRepository(context).platformNames(stationId))
}

/**
 * Ersetzt die Abfahrten von [platformId] aus der Haltestellen-Abfrage durch die der Steig-Abfrage.
 * Andere Steige bleiben erhalten. Liefert die Steig-Abfrage nichts, bleiben die Haltestellen-Daten.
 */
fun mergePlatformDepartures(
    station: List<DepartureItem>,
    platform: List<DepartureItem>,
    platformId: String
): List<DepartureItem> {
    val own = platform.filter { it.platformId == platformId }
    if (own.isEmpty()) return station
    return station.filter { it.platformId != platformId } + own
}

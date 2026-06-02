package de.dhde.hannover.departures.widget.data

/** Verkehrsmittel-Filter (Tab). Kapselt die Legacy-Speicherform; "TRAIN" = Tram. */
enum class TransportFilter(val storageValue: String) {
    ALL("ALL"),
    BUS("BUS"),
    TRAM("TRAIN");   // "TRAIN" = Legacy-Speicherwert für Tram (nicht migriert)

    companion object {
        fun fromStorage(value: String?): TransportFilter =
            entries.firstOrNull { it.storageValue == value } ?: ALL
    }
}

/** Fahrtrichtungs-Filter. Kapselt die Legacy-Speicherform (H = einwärts, R = auswärts). */
enum class DirectionFilter(val storageValue: String) {
    ALL("ALL"),
    INBOUND("H"),
    OUTBOUND("R");

    companion object {
        fun fromStorage(value: String?): DirectionFilter =
            entries.firstOrNull { it.storageValue == value } ?: ALL
    }
}

/**
 * Leitet die Fahrtrichtung aus dem EFA-lineId-Suffix ab:
 * Endung „H" = stadteinwärts (INBOUND), „R" = stadtauswärts (OUTBOUND), sonst null.
 */
fun lineDirection(lineId: String?): DirectionFilter? = when {
    lineId == null -> null
    lineId.endsWith("H", ignoreCase = true) -> DirectionFilter.INBOUND
    lineId.endsWith("R", ignoreCase = true) -> DirectionFilter.OUTBOUND
    else -> null
}

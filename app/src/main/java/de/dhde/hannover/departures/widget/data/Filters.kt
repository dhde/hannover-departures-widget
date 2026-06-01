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

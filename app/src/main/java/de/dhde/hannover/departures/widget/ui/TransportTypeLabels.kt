package de.dhde.hannover.departures.widget.ui

import androidx.annotation.StringRes
import de.dhde.hannover.departures.widget.R

/**
 * Anzeigetext für einen Verkehrsmittel-SPEICHERSCHLÜSSEL ("Stadtbahn", "Bus", "S-Bahn", "DB", "Fernbus").
 * Die Schlüssel selbst bleiben unübersetzt (persistiert in DataStore, Filterlogik), nur die Anzeige wird lokalisiert.
 * Unbekannter Schlüssel → null (Aufrufer zeigt dann den Schlüssel selbst).
 */
@StringRes
fun transportTypeLabel(key: String): Int? = when (key) {
    "Stadtbahn" -> R.string.common_type_tram
    "Bus" -> R.string.common_type_bus
    "S-Bahn" -> R.string.common_type_sbahn
    "DB" -> R.string.common_type_db
    "Fernbus" -> R.string.common_type_fernbus
    else -> null
}

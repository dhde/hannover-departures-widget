# Üstra Hannover - Abfahrten Widget

Ein modernes, natives Android-Widget für Live-Abfahrtszeiten des GVH (Großraum-Verkehr Hannover). Behalte Busse und Bahnen direkt auf deinem Homescreen im Blick.

![Üstra Widget](docs/images/widget_hbf.png)

## Features

- **Live-Abfahrten**: Echtzeitdaten für alle Stationen der ÜSTRA / GVH.
- **Intelligenter Filter**: Schnelles Umschalten zwischen Bus- und Stadtbahn-Ansicht.
- **GPS-Modus**: Findet automatisch die nächstgelegene Haltestelle in deiner Umgebung.
- **Flexible Zeitanzeige**: Wechsel per Klick zwischen Minuten-Countdown (z.B. "5 Min") und Uhrzeit (z.B. "14:30").
- **Schnellnavigation**: Durch Klick auf den Haltestellennamen zyklisch durch deine Favoriten blättern.
- **Batterieschonend & Schnell**: Integrierte Caching-Logik (5-Minuten-Intervall) und gezielte UI-Updates.

## Screenshots

| Widget (Hauptbahnhof) | Widget (Klingerstraße) | App-Konfiguration |
| --- | --- | --- |
| ![Widget HBF](docs/images/widget_hbf.png) | ![Widget Klinger](docs/images/widget.png) | ![App Configuration](docs/images/app.png) |

## Bedienung & Tipps

- **Zielkreuz-Icon 🎯**: Aktiviert/Deaktiviert die GPS-Suche. Ist sie aktiv, wird automatisch die nächste Station gewählt.
- **Refresh-Icon 🔄**: Erzwingt ein sofortiges Update der Daten.
- **Klick auf die Zeit**: Wechselt zwischen relativer Angabe ("4 Min") und absoluter Uhrzeit.
- **Klick auf den Stationsnamen**: Springt zur nächsten Favoritenstation.

## Technische Details

- **Sprache**: Kotlin
- **Framework**: Jetpack Compose mit Glance (für Widgets)
- **API**: ÜSTRA Web Proxy (EFA XML_DM_REQUEST)
- **Speicherung**: DataStore für schnelles Caching und Favoritenverwaltung

---
*Hinweis: Dies ist ein inoffizielles Widget und steht in keiner direkten Verbindung zur ÜSTRA oder dem GVH.*

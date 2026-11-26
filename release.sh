#!/bin/bash
set -e

# Konfiguration
TAG="v1.11.2"                   # Release-Tag (z. B. v1.0.0)
ASSET_FILE="./app/build/outputs/apk/release/app-release.apk"  # Pfad zur Asset-Datei
RELEASE_TITLE="Üstra Widget 1.11.2" # Optional: Release-Titel
RELEASE_NOTES=$(cat <<'NOTES'
## Üstra Widget 1.11.2

### 🚏 Steige

- Am Kröpcke fehlte im Bahn-Filter ein Steig, obwohl genug Platz war. Jetzt sind alle Steige zu sehen.
- Hinter den Steig-Chips gibt es einen **„▾“**-Button. Er öffnet die Steig-Liste mit Linien und Zielen, auch wenn alle Chips passen.
- Ist ein Steig gewählt, lädt das Widget die Abfahrten nur für diesen Steig. So erscheinen alle kommenden Abfahrten, nicht nur wenige.
- Steige haben keine internen vierstelligen Nummern mehr (z.B. Noltemeyerbrücke „#2174“ → „#4“). Die Nummern sind auf jedem Gerät gleich.

### ℹ️ Meldungen

- Jede Meldung lässt sich sofort ausblenden, das Schloss ist weg.
- Ohne Meldungen zeigt das Widget ein graues (i). Ein Tipp darauf öffnet den Meldungsfilter, dort kann man ausgeblendete Meldungen nachlesen und wieder einblenden.
- Der Haltestellenname springt nicht mehr, wenn das (i) erscheint oder verschwindet.

### Kleinigkeiten

- Suche: „oe“ findet „ö“ (ebenso ae/ä, ue/ü, ss/ß).
- Sprachauswahl als Dropdown mit Flaggen.
NOTES
)  # Optional: Release-Notizen

# Sicherstellen, dass wir auf dem main Branch sind
CURRENT_BRANCH=$(git branch --show-current)
if [ "$CURRENT_BRANCH" != "main" ]; then
    echo "Fehler: Nicht auf dem main Branch (aktuell: $CURRENT_BRANCH)"
    exit 1
fi

# Prüfen, ob der Tag existiert, und ggf. erstellen
if ! git rev-parse "$TAG" >/dev/null 2>&1; then
    echo "Tag $TAG existiert nicht. Erstelle annotated tag..."
    git tag -a "$TAG" -m "Release $TAG"
    git push origin "$TAG"
else
    echo "Tag $TAG existiert bereits"
fi

# Release erstellen und Asset hochladen
echo "Erstelle Release $TAG und lade Asset hoch..."
gh release create "$TAG" \
    --title "$RELEASE_TITLE" \
    --notes "$RELEASE_NOTES" \
    "$ASSET_FILE"

echo "Release $TAG erfolgreich erstellt mit Asset: $ASSET_FILE"

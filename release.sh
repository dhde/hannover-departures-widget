#!/bin/bash
set -e

# Konfiguration
TAG="v1.10.2"                   # Release-Tag (z. B. v1.0.0)
ASSET_FILE="./app/build/outputs/apk/release/app-release.apk"  # Pfad zur Asset-Datei
RELEASE_TITLE="Üstra Widget 1.10.2" # Optional: Release-Titel
RELEASE_NOTES=$(cat <<'NOTES'
## Üstra Widget 1.10.2

### 🚏 Steig-Chips für große Haltestellen

In 1.10.1 fehlten an großen Haltestellen wie dem Kröpcke (9 Steige) die Steig-Chips, weil höchstens 4 vorgesehen waren.

- Die Chips zeigen jetzt die Steignummer mit „#“ davor (z.B. „#1“, „#17“), damit man sie nicht mit Liniennummern verwechselt.
- Jeder Chip hat die Farbe der Linien, die dort abfahren: blau (3/7/9/13), rot (1/2/8 und Busse), gelb (4/5/6/11), grün (10/17), magenta (SprintH).
- Bahn-Steige stehen vor den Bus-Steigen.
- Das Widget zeigt die Chips, sobald alle in seine Breite passen. Ist das Widget zu schmal, mit dem Bus- oder Bahn-Tab eingrenzen oder das Widget breiter ziehen.

### 🪧 Fahrtziele wie auf der Anzeigetafel

Bei Bussen stand „Hannover “ vor dem Ziel (z.B. „Hannover August-Holweg-Platz“). Das entfällt jetzt, wie schon bei der Stadtbahn.
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

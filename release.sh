#!/bin/bash
set -e

# Konfiguration
TAG="v1.11.1"                   # Release-Tag (z. B. v1.0.0)
ASSET_FILE="./app/build/outputs/apk/release/app-release.apk"  # Pfad zur Asset-Datei
RELEASE_TITLE="Üstra Widget 1.11.1" # Optional: Release-Titel
RELEASE_NOTES=$(cat <<'NOTES'
## Üstra Widget 1.11.1

### 🚏 Größere Steig-Chips und Steig-Auswahl

Die Steig-Chips aus 1.11.0 waren zu klein zum Antippen.

- Jeder Steig-Chip ist jetzt mindestens so groß wie die Bus/Bahn-Buttons. Es werden so viele angezeigt, wie in die Breite des Widgets passen.
- Passen nicht alle (z.B. am Kröpcke), erscheint ein **„Steig ▾“**-Button. Er öffnet eine Auswahl, in der man hoch und runter scrollen kann: Jeder Steig steht dort mit Farbe, Linien und Zielen.
- In der App-Vorschau gibt es dieselbe Auswahl als Menü.

### Kleinigkeiten

- Unten im Widget öffnet jetzt auch ein Tipp auf „Inoffiziell“ die App, nicht nur der kleine Pfeil.
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

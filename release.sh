#!/bin/bash
set -e

# Konfiguration
TAG="v1.10.1"                   # Release-Tag (z. B. v1.0.0)
ASSET_FILE="./app/build/outputs/apk/release/app-release.apk"  # Pfad zur Asset-Datei
RELEASE_TITLE="Üstra Widget 1.10.1" # Optional: Release-Titel
RELEASE_NOTES=$(cat <<'NOTES'
## Üstra Widget 1.10.1

### 🚏 Steig-Filter statt City/Home

Der Richtungsfilter (City/Home) war bei Bussen unzuverlässig: Linien, die in entgegengesetzte Richtungen fahren, landeten unter demselben Symbol (z.B. am Lister Platz die 100 und die 121). Die Richtungsangabe der Fahrplandaten gilt nämlich nur pro Linie, nicht für die Stadt.

- Gefiltert wird jetzt nach **Steig** (z.B. „SB1“, „Bus 2“). Alles, was vom selben Steig abfährt, fährt in dieselbe Richtung.
- Die Steig-Chips erscheinen in Widget und App, sobald ein Halt 2–4 Steige hat. An großen Halten erst per Bus/Bahn-Tab eingrenzen.
- Der gewählte Steig wird pro Favorit gemerkt. Nochmal tippen zeigt wieder alle Abfahrten.
- Wird der gemerkte Steig gerade nicht bedient, zeigt das Widget alle Abfahrten statt einer leeren Liste.

Hinweis: Bisher gespeicherte City/Home-Einstellungen werden auf „alle“ zurückgesetzt. Nach dem Update lädt die App die Haltestellenliste einmal neu.
NOTES
)  # Optional: Release-Notizen

# Sicherstellen, dass wir auf dem main Branch sind
CURRENT_BRANCH=$(git branch --show-current)
if [ "$CURRENT_BRANCH" != "main" ]; then
    echo "Fehler: Nicht auf dem main Branch (aktuell: $CURRENT_BRANCH)"
    exit 1
fi

# Tag的存在性 prüfen und ggf. erstellen
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

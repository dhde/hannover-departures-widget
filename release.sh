#!/bin/bash
set -e

# Konfiguration
TAG="v1.7.1"                    # Release-Tag (z. B. v1.0.0)
ASSET_FILE="./app/build/outputs/apk/release/app-release.apk"  # Pfad zur Asset-Datei
RELEASE_TITLE="Üstra Widget 1.7.1" # Optional: Release-Titel
RELEASE_NOTES=$(cat <<'NOTES'
## Üstra Widget 1.7.1

🛰️ GPS-Modus: Filter steuert Haltestellenwahl
- Mit aktivem Bahn-Filter springt das Widget auf die nächste Bahn-Haltestelle, mit Bus-Filter auf den nächsten Bushalt – der Filter bleibt erhalten und wandert beim Halt-Wechsel mit.
- Behoben: Der gewählte Verkehrsmittel-Filter sprang im GPS-Modus an reinen Bus- oder Bahn-Haltestellen automatisch wieder raus.

🔄 „Keine Abfahrten"-Hinweis aufgeräumt
- Großes Refresh-Icon mit „Tippen zum Neuladen" statt unscheinbarer grauer Text – ein Tap lädt direkt neu.
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

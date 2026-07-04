#!/bin/bash
set -e

# Konfiguration
TAG="v1.8.2"                    # Release-Tag (z. B. v1.0.0)
ASSET_FILE="./app/build/outputs/apk/release/app-release.apk"  # Pfad zur Asset-Datei
RELEASE_TITLE="Üstra Widget 1.8.2" # Optional: Release-Titel
RELEASE_NOTES=$(cat <<'NOTES'
## Üstra Widget 1.8.2

🧰 Interne Aufräumarbeiten
- AndroidX-Core-Bibliothek aktualisiert (1.12.0 → 1.15.0). Adressiert einen Hinweis aus dem Play-Store-Pre-Launch-Report zum Speicherverbrauch beim Bitmap-Laden im Framework-Code. Keine Auswirkung auf die Bedienung – reine Wartung.
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

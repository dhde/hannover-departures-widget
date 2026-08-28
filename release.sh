#!/bin/bash
set -e

# Konfiguration
TAG="v1.9.2"                    # Release-Tag (z. B. v1.0.0)
ASSET_FILE="./app/build/outputs/apk/release/app-release.apk"  # Pfad zur Asset-Datei
RELEASE_TITLE="Üstra Widget 1.9.2" # Optional: Release-Titel
RELEASE_NOTES=$(cat <<'NOTES'
## Üstra Widget 1.9.2

🐛 Fix: „Verbindung fehlgeschlagen" hing im Widget fest
- Wenn das Android-System den Refresh-Coroutine-Scope während eines Widget-Rerenders abgebrochen hat, wurde die Cancellation fälschlicherweise als Netzwerkfehler angezeigt. Ab jetzt wird sie sauber durchgereicht.
- Der Fehler-Zustand wird beim Start eines neuen Refresh-Versuchs sofort geleert, damit keine stale „Verbindung fehlgeschlagen"-Meldung mehr neben frischen Daten stehen bleibt.

_Inhaltlich identisch zu v1.9.1 — nur neuer versionCode (30), damit die AAB im Play Store re-uploadbar ist._
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

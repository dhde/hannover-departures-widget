#!/bin/bash
set -e

# Konfiguration
TAG="v1.9.5"                    # Release-Tag (z. B. v1.0.0)
ASSET_FILE="./app/build/outputs/apk/release/app-release.apk"  # Pfad zur Asset-Datei
RELEASE_TITLE="Üstra Widget 1.9.5" # Optional: Release-Titel
RELEASE_NOTES=$(cat <<'NOTES'
## Üstra Widget 1.9.5

Wartungs- und Feinschliff-Release ohne funktionale Änderungen für Endnutzer.

### Unter der Haube

📦 Dependency-Update
- Alle AndroidX-, Compose-, Glance-, Play-Services- und Coroutines-Bibliotheken auf aktuelle Stable-Versionen gehoben (Compose BOM 2026.08.00, Lifecycle 2.11, Core-KTX 1.19 usw.).
- `compileSdk` auf 37 angehoben. `targetSdk`/`minSdk` unverändert.
- Retrofit bewusst auf 2.9.0 belassen (2→3 mit OkHttp-4.12-Sprung folgt separat).

📍 GPS-Fast-Path im Widget
- Fused-Location-Cache wird zuerst geprüft (< 30 s alt → Millisekunden-Response), aktiver `getCurrentLocation(HIGH_ACCURACY)`-Fix nur als Fallback (Timeout 4 s statt 7 s).
- GPS-Toggle löst nur noch einen API-Call aus, wenn sich die nächste Haltestelle wirklich geändert hat — sonst reines UI-Feedback ohne Netzwerk-Roundtrip.

🐛 Debug-Screen (nur bei aktiviertem Debug-Modus)
- JSON-Blöcke in Log-Zeilen werden jetzt pretty-printed und farbcodiert (Keys blau, Strings grün, Zahlen orange, `null` rot, Booleans lila).
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

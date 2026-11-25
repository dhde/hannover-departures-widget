#!/bin/bash
set -e

# Konfiguration
TAG="v1.11.0"                   # Release-Tag (z. B. v1.0.0)
ASSET_FILE="./app/build/outputs/apk/release/app-release.apk"  # Pfad zur Asset-Datei
RELEASE_TITLE="Üstra Widget 1.11.0" # Optional: Release-Titel
RELEASE_NOTES=$(cat <<'NOTES'
## Üstra Widget 1.11.0

### 🌍 Mehrsprachig

Die App und das Widget gibt es jetzt neben Deutsch auch auf **Englisch, Türkisch, Polnisch, Ukrainisch und Russisch**.

- Standardmäßig folgt die App der Sprache des Handys.
- In den Optionen unter „Darstellung“ lässt sich die Sprache auch direkt wählen, unabhängig von der Handysprache.
- Ab Android 13 geht das auch über Einstellungen → Apps → Bus & Bahn Hannover Widget → Sprache.

Die Übersetzungen sind neu. Wer einen holprigen Satz findet, kann gern ein Issue aufmachen.

### Kleinigkeiten

- „1 Zeile“ statt „1 Zeilen“ und ähnliche Einzahl/Mehrzahl-Formen in den Optionen korrigiert.
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

#!/bin/bash
set -e

# Konfiguration
TAG="v1.8.0"                    # Release-Tag (z. B. v1.0.0)
ASSET_FILE="./app/build/outputs/apk/release/app-release.apk"  # Pfad zur Asset-Datei
RELEASE_TITLE="Üstra Widget 1.8.0" # Optional: Release-Titel
RELEASE_NOTES=$(cat <<'NOTES'
## Üstra Widget 1.8.0

🔄 Automatischer Refresh beim Entsperren
- Neuer Toggle in den Einstellungen: Sobald du das Handy entsperrst, holt sich das Widget frische Abfahrtsdaten (gedrosselt: max. alle 60 Sek.). Standard: AUS.

🛠️ Fix: Widget hakt nach Verbindungsfehler
- Wenn unterwegs ein Verbindungsfehler auftrat, war der Refresh-Button teilweise nicht mehr erreichbar. Der Ladeindikator ist jetzt selbst klickbar und der Minuten-Ticker läuft auch im Fehlerzustand weiter – das Widget kann sich selbst wieder befreien.

🛠️ Fix: Fixe Event-Grenze bei stark frequentierten Haltestellen
- Bei Peak-Haltestellen (z.B. Hauptbahnhof) mit vielen Linien wird jetzt konsistent das API-Maximum abgefragt. Mehr ist server-seitig strukturell nicht möglich – die Anzeige zusätzlicher Abfahrten pro Linie ist bei dicht befahrenen Haltestellen dadurch begrenzt.
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

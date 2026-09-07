# GPS-Picker: Auswahl der nächstgelegenen Stationen

**Datum:** 2026-09-07
**Status:** Entwurf zur Umsetzung
**Scope:** GPS-Feature erweitern — Klick auf GPS-Icon öffnet einen Kandidaten-Picker (X nächste Stationen) statt sofort auf die einzelne nächste Station zu springen. Gilt für Widget und App-Dashboard.

---

## 1. Ziel und Motivation

Aktuell aktiviert ein Klick auf das GPS-Icon den "Nearest-Mode" und setzt automatisch die geografisch nächste Station als aktiv. Das ist zu rigide: der User kann eine leicht weiter entfernte, aber besser passende Station (z.B. auf der richtigen Straßenseite oder mit passendem Verkehrsmittel) nicht in einem Schritt wählen.

**Ziel:** Klick auf GPS-Icon öffnet eine kompakte Auswahl der X nächstgelegenen Stationen (X konfigurierbar, 2–5, Default 3). Der User wählt aus. Bleibt so nah wie möglich am bestehenden "sofort im Widget"-Charakter.

---

## 2. UX-Verhalten

### 2.1 Interaktion (Widget und App identisch)

- **Klick auf GPS-Icon** — immer, unabhängig davon ob GPS gerade an oder aus ist — öffnet den Kandidaten-Picker.
- Rationale: das Icon fungiert nicht als An/Aus-Toggle, sondern als "öffne Standort-Menü". Konsistent, egal welcher Zustand vorher aktiv war. Der häufigste Grund für einen Klick bei aktivem GPS ist ohnehin "andere Station wählen", nicht "einfach nur aus".
- **Ausschalten** erfolgt über den `✕`-Button im Picker-Header.

### 2.2 Kandidatenauswahl

- Datenbasis: der lokal gecachte Stopps-Datensatz.
- Sortierung: aufsteigend nach Distanz zum aktuellen Standort (`Location.distanceBetween`).
- Anzahl: `nearest_count` aus DataStore, Wertebereich 2–5, Default 3.
- **Verkehrsmittel-Filter respektieren:** wenn der globale Filter auf "Bahn" steht, kommen nur Bahn-Stopps in die Liste. Analog "Bus". Bei "ALL" alle Stopps.

### 2.3 Filter-Toggle im Picker

- **Nur sichtbar, wenn der globale Verkehrsmittel-Filter aktiv ist** (nicht ALL). Bei globalem Filter=ALL gibt es nichts zu togglen — Button ausblenden.
- Klick auf Filter-Toggle im Picker schaltet die **Anzeige** zwischen "gefiltert" und "ALL" um. **Kein neuer GPS-Request nötig** — die Kandidatenliste wird komplett gehalten und lokal umgefiltert.
- **Bei Auswahl einer Station** wird der globale Filter auf den aktuell im Picker sichtbaren Zustand gesetzt. Beispiel: globaler Filter war "Bahn", User schaltet im Picker auf ALL, wählt einen Bus-Stopp → globaler Filter wird ALL. Sonst würde das Widget nach der Auswahl "keine Bahn-Abfahrten" für den Bus-Stopp zeigen.

### 2.4 Zeileninhalt pro Kandidat

Pro Zeile: **Stopp-Name · Distanz · Verkehrsmittel-Icons** (z.B. Bus/Bahn-Symbole für die dort verkehrenden Modi).

### 2.5 Verlassen ohne Auswahl

- **Widget:** `✕`-Button (immer sichtbar im Picker-Header) ODER automatischer Timeout nach 60 Sekunden über den Widget-Ticker.
- **App:** `ModalBottomSheet` — Dismiss via Swipe-Down oder Backdrop-Klick. Kein Timeout.

### 2.6 Fehlerfälle

- **Kein Location-Fix möglich** (`getBestLocation()` liefert `null`): Picker öffnet trotzdem, zeigt "Standort nicht verfügbar" + `✕`. GPS-Modus bleibt unverändert.
- **Location-Permission fehlt:** analoge Meldung "Standort-Berechtigung fehlt". In der App zusätzlich Button "Berechtigung anfordern" (nutzt bestehenden `requestPermissionLauncher`).
- **Keine Stopps im Cache** (Cold-Start): Meldung "Stationen werden geladen…" + `✕`. Der reguläre Widget-Refresh-Ticker holt sie im nächsten Cycle nach.
- **Prozess-Kill während Picker offen:** Widget-Rendering prüft `now - picker_opened_at > 60_000` → wenn ja, `picker_mode=false` automatisch. Sonst rendert es den Picker mit den persistierten Kandidaten (Distanzen können minimal veraltet sein, aber ausreichend genau für Auswahl).

---

## 3. Architektur

### 3.1 Persistenz-Keys

**In `FavoritesRepository`** (persistente User-Setting, wie andere Optionen):

```kotlin
val NEAREST_COUNT = intPreferencesKey("nearest_count")   // 2..5, Default 3
```
Flow + Setter analog zu `maxFavoritesFlow` / `setMaxFavorites`.

**In `WidgetSessionStore`** (transienter Session-State, wie GPS-Modus):

```kotlin
val PICKER_MODE         = booleanPreferencesKey("picker_mode")          // Default false
val PICKER_CANDIDATES   = stringPreferencesKey("picker_candidates_json")// JSON von List<StopCandidate>, nullable
val PICKER_OPENED_AT    = longPreferencesKey("picker_opened_at")        // Millis, für 60s-Timeout
val PICKER_FILTER_OVERRIDE = stringPreferencesKey("picker_filter_override") // null | "ALL"
```

Getter/Setter/Flows analog zu bestehendem Muster (`getGpsModeFlow`, `setGpsMode` etc.).

### 3.2 Neues Datenmodell

Datei: `app/src/main/java/de/dhde/hannover/departures/widget/data/StopCandidate.kt`

```kotlin
data class StopCandidate(
    val stopId: String,
    val name: String,
    val distanceM: Int,
    val transportTypes: List<String>   // z.B. ["BUS", "TRAM"]
)
```

Serialisierung: Gson (bereits im Projekt).

### 3.3 Neuer Helper `NearestStationsFinder`

Datei: `app/src/main/java/de/dhde/hannover/departures/widget/data/NearestStationsFinder.kt`

```kotlin
object NearestStationsFinder {
    /**
     * Liefert bis zu [count] nächstgelegene Stopps zu [location].
     * Wenn [transportFilter] gesetzt ist, werden nur Stopps zurückgegeben,
     * die diesen Modus bedienen. Bei null: alle Stopps.
     * Return: absteigende Priorität nach Distanz (nächster zuerst).
     */
    suspend fun findNearestStops(
        context: Context,
        location: Location,
        count: Int,
        transportFilter: TransportFilter?
    ): List<StopCandidate>
}
```

Wird sowohl vom Widget (`OpenPickerAction`) als auch von der App (MainActivity-Coroutine) aufgerufen — **eine einzige Auswahlregel für beide UIs**.

`getBestLocation()` bleibt in `WidgetActions.kt`, wird aber öffentlich sichtbar (aktuell privat) und ebenfalls von der App genutzt.

### 3.4 Neue Glance-Actions in `WidgetActions.kt`

- **`OpenPickerAction`** — ersetzt die bisherige direkte Toggle-Logik am GPS-Icon.
  1. Lädt `nearest_count`, globalen Filter aus Store.
  2. Ruft `getBestLocation()`.
  3. Bei Erfolg: `findNearestStops(location, count, filter)` → speichert Ergebnis als JSON in `PICKER_CANDIDATES`.
  4. Setzt `PICKER_MODE=true`, `PICKER_OPENED_AT=now`, `PICKER_FILTER_OVERRIDE=null`.
  5. Bei Fehler (Fix null / leere Liste): setzt `PICKER_CANDIDATES` auf leer/null, `PICKER_MODE=true` trotzdem — Renderer zeigt Fehler-State.
  6. `DeparturesWidget().updateAll(context)`.
  7. Debug-Log siehe §4.

- **`PickCandidateAction(stopId)`**
  1. Lädt `PICKER_FILTER_OVERRIDE`.
  2. Setzt aktive Station auf `stopId` (bestehende Setter).
  3. Wenn `PICKER_FILTER_OVERRIDE == "ALL"` → globalen Filter auf ALL setzen.
  4. `setGpsMode(true)`.
  5. `PICKER_MODE=false`, `PICKER_CANDIDATES=null`, `PICKER_FILTER_OVERRIDE=null`.
  6. Triggert Refresh (`triggerUpdate` wie heute nach `ChangeStationAction`).

- **`TogglePickerFilterAction`**
  1. `PICKER_FILTER_OVERRIDE = if (current == "ALL") null else "ALL"`.
  2. `updateAll()`. Kein neuer GPS-Fix, kein neuer Netzwerk-Call.

- **`ClosePickerAction`**
  1. `PICKER_MODE=false`, `PICKER_CANDIDATES=null`, `PICKER_FILTER_OVERRIDE=null`.
  2. `updateAll()`.

### 3.5 Widget-Rendering (`DeparturesWidget.kt`)

- Ganz oben im `provideContent`-Block (bzw. äquivalentem Composable): prüfe `picker_mode`. Wenn `true`, rendere `PickerLayout(candidates, filterOverride, globalFilter)` statt der normalen `DeparturesLayout`.
- **`PickerLayout`** Composable:
  - Header-Row: Titel "Station wählen" links, Filter-Toggle-Icon in der Mitte (nur wenn `globalFilter != ALL`), `✕`-Icon rechts (→ `ClosePickerAction`).
  - Content:
    - Wenn `candidates` leer und `PICKER_OPENED_AT` gesetzt → "Standort nicht verfügbar" / "Keine Stationen in der Nähe" (je nach Fehlermodus, unterschieden über zusätzliches Flag oder über leere Liste + Timestamp).
    - Sonst: `LazyColumn` mit einer Row pro Kandidat, geclickt → `PickCandidateAction(stopId)`. Zeile: Name links, Distanz + Verkehrsmittel-Icons rechts.
- Zeilen-Filter im Renderer: wenn `PICKER_FILTER_OVERRIDE == "ALL"` → alle Kandidaten anzeigen; sonst nur die, die den globalen Verkehrsmittel-Filter erfüllen.

### 3.6 Timeout im `DeparturesWidgetReceiver.kt`

Im `onReceive(TICK)`-Handler vor der bestehenden GPS-Refresh-Logik:

```kotlin
if (store.isPickerModeActive() &&
    System.currentTimeMillis() - store.getPickerOpenedAt() > 60_000L) {
    store.setPickerMode(false)
    store.setPickerCandidates(null)
    store.setPickerFilterOverride(null)
    DebugLog.log("[picker] timeout after 60s, closing")
    DeparturesWidget().updateAll(context)
    return   // dieser Tick war für Timeout-Cleanup — normale Refresh-Logik überspringen
}
```

### 3.7 App-Seite (`MainActivity.kt`)

Neuer State im Compose-Scope:

```kotlin
var showPickerSheet by remember { mutableStateOf(false) }
var pickerCandidates by remember { mutableStateOf<List<StopCandidate>>(emptyList()) }
var pickerLoading by remember { mutableStateOf(false) }
var pickerFilterOverride by remember { mutableStateOf(false) }  // false = folgt globalem Filter; true = ALL
var pickerError by remember { mutableStateOf<String?>(null) }
```

Bestehender `onGpsClick`-Handler im `WidgetHeader`-Preview wird ersetzt durch:

```kotlin
onGpsClick = {
    scope.launch {
        showPickerSheet = true
        pickerLoading = true
        pickerError = null
        pickerCandidates = emptyList()
        pickerFilterOverride = false

        val loc = getBestLocation(context)
        if (loc == null) {
            pickerError = if (!hasLocationPermission()) "Standort-Berechtigung fehlt"
                          else "Standort nicht verfügbar"
        } else {
            val count = store.getNearestCount()
            val filter = store.getTransportFilter()
            pickerCandidates = NearestStationsFinder.findNearestStops(context, loc, count, filter)
            if (pickerCandidates.isEmpty()) pickerError = "Keine Stationen in der Nähe"
        }
        pickerLoading = false
        DebugLog.log("[picker] app open: err=$pickerError count=${pickerCandidates.size}")
    }
}
```

`ModalBottomSheet` mit äquivalentem Layout zur Widget-Ansicht (aber echte Compose-Komponenten). Klick auf Kandidat → dieselbe State-Änderung wie `PickCandidateAction`, dann `showPickerSheet = false`.

Kein Kandidaten-Cache in DataStore für die App — Sheet-State ist ephemer.

### 3.8 Options-UI

Neue Einstellung "Anzahl Kandidaten (2–5)" landet im existierenden `OptionsScreen.kt`. Dort sitzen alle anderen User-Settings (Max-Favoriten, Zeilenanzahl etc.) im gleichen `LazyColumn`-Muster mit `OptionsGroupHeader`-Sektionen. Vorschlag: neue Sektion "Standort" oder passend zu bestehender GPS-Nähe. Widget: `Slider` (2..5, step 1) analog `MaxFavorites`-Slider oder ein kompakter `SegmentedButton`.

---

## 4. Debug-Logging

Alle neuen Log-Zeilen verwenden das existierende `DebugLog.log(...)` und tragen ein konsistentes Präfix, damit sie im Logcat und im DebugScreen sofort erkennbar sind:

- `[picker]` — alles rund um den Kandidaten-Picker
- `[gps]` — bestehende / erweiterte GPS-bezogene Zeilen

**Konkrete Log-Punkte:**

| Ort | Log-Zeile |
|-----|-----------|
| `OpenPickerAction` start | `[picker] open: nearestCount=X filter=Y gpsActiveBefore=Z` |
| `OpenPickerAction` nach Fix | `[picker] fix: age=Nms acc=Mm` bzw. `[picker] fix: null (timeout/error)` |
| `OpenPickerAction` fertig | `[picker] candidates: n=K first=<name>@<dist>m` |
| `PickCandidateAction` | `[picker] pick: stopId=X overrideFilter=Y` |
| `TogglePickerFilterAction` | `[picker] toggleFilter: A→B` |
| `ClosePickerAction` | `[picker] close (user)` |
| Timeout im Receiver | `[picker] timeout after 60s, closing` |
| App-Sheet open | `[picker] app open: err=<x> count=K` |
| App-Sheet pick | `[picker] app pick: stopId=X overrideFilter=Y` |
| `NearestStationsFinder` | `[picker] finder: stopsInCache=N afterFilter=M returned=K` |

**Filter-Suchfeld im DebugScreen** ist **explizit nicht Teil dieses Changes** — als separater kleiner PR nachgeliefert (ein `TextField` oben im DebugScreen mit Substring-Filter auf `LazyColumn`-Items).

---

## 5. Migration / Kompatibilität

- Neue DataStore-Keys — bestehende Installationen bekommen beim ersten Lesen die Defaults (`nearest_count=3`, `picker_mode=false`, Rest null). Keine explizite Migration nötig.
- Bestehendes Verhalten der `LocateNearestStationAction` wird **durch `OpenPickerAction` ersetzt**. Die alte Action kann entfernt werden — sie ist nur intern verdrahtet.
- Bestehende `ChangeStationAction` behält den Auto-Disable-Effekt (setGpsMode=false), damit manuelles Wechseln über die Favoritenliste weiterhin GPS ausschaltet.

---

## 6. Testing

### 6.1 Unit-Tests

- `NearestStationsFinderTest`:
  - Sortiert korrekt nach Distanz
  - Respektiert `count` (kappt bei count auch wenn mehr da sind)
  - Respektiert `transportFilter=null` (alle) vs. gesetzt (nur passende)
  - Liefert leere Liste bei leerer Stopp-Cache
  - Liefert leere Liste, wenn Filter zu strikt (kein Match)

- `PickerTimeoutTest`: reine Delta-Prüfung (`now - openedAt > 60_000`).

- `PickCandidateFilterOverrideTest`: verifiziert die Regel "override=ALL → globaler Filter wird ALL bei Auswahl".

### 6.2 Manuelle Verifikation

- Widget: GPS-Klick öffnet Picker → wähle Kandidat → Station wechselt + GPS an + korrekter Filter aktiv?
- Widget: Klick auf `✕` schließt Picker ohne Änderung?
- Widget: 60s warten → Picker verschwindet automatisch?
- Widget: Filter aktiv → Filter-Toggle-Icon sichtbar? Filter=ALL → Icon unsichtbar?
- Widget: Filter-Toggle klicken → Liste ändert sich sofort ohne neuen GPS-Fix (Debug-Log prüfen)?
- App-Dashboard: analog alle Punkte oben.
- Fehlerpfad: Location-Permission entzogen → Picker zeigt Fehlermeldung, GPS-Modus unverändert?
- Debug-Modus einschalten (7× Version im Help), Flow durchlaufen, `[picker]`-Zeilen im DebugScreen sichtbar?

---

## 7. Offene Punkte / Nicht in Scope

- **Filter-Suchfeld im DebugScreen** — separater PR.
- **Umbenennung / Reorganisation der GPS-Konstanten in `WidgetActions.kt`** — nicht Teil, nur nötige Ergänzungen.
- **Weitere Konfig-Optionen (Refresh-Intervall etc.)** — Trigger für dedizierten SettingsScreen, aber nicht jetzt.
- **Long-Press auf GPS-Icon als Shortcut zum "sofort nächste"** — bewusst nicht dazugenommen. Falls User später missen: kann als Add-on ergänzt werden.

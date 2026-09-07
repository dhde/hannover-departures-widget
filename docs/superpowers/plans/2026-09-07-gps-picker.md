# GPS-Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** GPS-Icon-Klick öffnet einen Kandidaten-Picker (2–5 nächste Stationen, konfigurierbar) statt sofort auf die einzelne nächste Station zu springen — sowohl im Widget als auch im App-Dashboard.

**Architecture:** DataStore-basierter State im `WidgetSessionStore` (Picker-Modus, Kandidaten-JSON, Timeout-Timestamp, Filter-Override). Neue Glance-Actions steuern Öffnen/Wählen/Filter-Toggle/Schließen. Ein gemeinsamer `NearestStationsFinder` liefert die Kandidatenliste — vom Widget und der App aufgerufen. App nutzt `ModalBottomSheet`, Widget rendert einen alternativen Picker-Layout-Zweig.

**Tech Stack:** Kotlin, Jetpack Glance 1.1.1 (Widget), Compose Material3 (App), Gson (Serialisierung), DataStore Preferences 1.2.1, Play Services Location 21.4.0, JUnit 4.13.2 (Unit-Tests).

## Global Constraints

- Min SDK 26, Target SDK 36, JavaVersion 17. Verbatim aus `app/build.gradle.kts`.
- Deutsche UI-Texte durchgängig (existierende Konvention).
- Debug-Logs: alle neuen Zeilen über `de.dhde.hannover.departures.widget.debug.DebugLog.log(...)` mit Präfix `[picker]` bzw. `[gps]` (siehe Spec §4).
- Persistente User-Settings → `FavoritesRepository` (nutzt `context.dataStore`).
- Session-/Transient-State → `WidgetSessionStore` (nutzt `context.cacheDataStore`).
- Keine neuen Third-Party-Dependencies. JUnit 4.13.2 ist bereits als `testImplementation` gesetzt.
- Test-Setup: Unit-Tests für pure Kotlin-Logik in `app/src/test/java/…` — Android-Deps (wie `Location.distanceBetween`) werden durch injizierbare Function-Types umgangen, kein Robolectric.
- BCPR-Workflow: alles auf Feature-Branch `feat/gps-picker`, am Ende Max-Check vor Push, dann PR.

---

## File Structure

| Datei | Aktion | Verantwortlich für |
|-------|--------|---------------------|
| `app/src/main/java/de/dhde/hannover/departures/widget/data/StopCandidate.kt` | CREATE | Datenklasse Kandidat (id, name, distanceM, transportTypes) |
| `app/src/main/java/de/dhde/hannover/departures/widget/data/NearestStationsFinder.kt` | CREATE | Berechnet Top-N nächste Stopps, injizierbare Distance-Funktion |
| `app/src/test/java/de/dhde/hannover/departures/widget/data/NearestStationsFinderTest.kt` | CREATE | JVM-Unit-Tests für Sortierung, Filter, count-Kappung |
| `app/src/main/java/de/dhde/hannover/departures/widget/data/WidgetSessionStore.kt` | MODIFY | 4 neue Session-Keys (picker_mode, picker_candidates_json, picker_opened_at, picker_filter_override) + Getter/Setter/Flows |
| `app/src/main/java/de/dhde/hannover/departures/widget/data/FavoritesRepository.kt` | MODIFY | 1 neuer Key `nearest_count` (Int, 2..5, Default 3) + Flow + Setter |
| `app/src/main/java/de/dhde/hannover/departures/widget/widget/WidgetActions.kt` | MODIFY | 4 neue Actions: `OpenPickerAction`, `PickCandidateAction`, `TogglePickerFilterAction`, `ClosePickerAction`. Alte `LocateNearestStationAction` entfernen |
| `app/src/main/java/de/dhde/hannover/departures/widget/widget/DeparturesWidget.kt` | MODIFY | Picker-Layout-Zweig, GPS-Icon-Onclick auf `OpenPickerAction` umbiegen |
| `app/src/main/java/de/dhde/hannover/departures/widget/widget/DeparturesWidgetReceiver.kt` | MODIFY | Timeout-Check im TICK-Handler |
| `app/src/main/java/de/dhde/hannover/departures/widget/MainActivity.kt` | MODIFY | `ModalBottomSheet`-Picker in der App, `onGpsClick`-Handler ersetzen |
| `app/src/main/java/de/dhde/hannover/departures/widget/OptionsScreen.kt` | MODIFY | Neue Option "Anzahl Kandidaten" (Slider 2..5) |

---

## Task 1: Feature-Branch + Spec committen

**Files:**
- Create: (keine)
- Modify: `docs/superpowers/specs/2026-09-07-gps-picker-design.md` (bereits geschrieben)

**Interfaces:**
- Produces: (nichts konsumiert von späteren Tasks)

- [ ] **Step 1: Aktuellen Stand von main pullen**

Run:
```bash
cd /home/didi/git/uestra
git checkout main
git pull --ff-only
```
Expected: "Already up to date." oder Fast-Forward.

- [ ] **Step 2: Feature-Branch anlegen**

Run:
```bash
git checkout -b feat/gps-picker
```
Expected: "Switched to a new branch 'feat/gps-picker'"

- [ ] **Step 3: Spec committen**

Run:
```bash
git add docs/superpowers/specs/2026-09-07-gps-picker-design.md docs/superpowers/plans/2026-09-07-gps-picker.md
git commit -m "docs: Spec und Plan für GPS-Picker-Feature"
```
Expected: 2 files changed.

---

## Task 2: `StopCandidate` + `NearestStationsFinder` + Unit-Tests

**Files:**
- Create: `app/src/main/java/de/dhde/hannover/departures/widget/data/StopCandidate.kt`
- Create: `app/src/main/java/de/dhde/hannover/departures/widget/data/NearestStationsFinder.kt`
- Create: `app/src/test/java/de/dhde/hannover/departures/widget/data/NearestStationsFinderTest.kt`

**Interfaces:**
- Produces:
  - `data class StopCandidate(val stopId: String, val name: String, val distanceM: Int, val transportTypes: List<String>)` — `transportTypes` enthält Elemente aus `{"BUS", "TRAM"}`.
  - `object NearestStationsFinder`:
    - `typealias DistanceCalculator = (Double, Double, Double, Double) -> Float`
    - `val androidDistance: DistanceCalculator` (nutzt `Location.distanceBetween`)
    - `fun findNearestStops(stops: List<StationSearchResult>, userLat: Double, userLon: Double, count: Int, transportFilter: TransportFilter?, distance: DistanceCalculator = androidDistance): List<StopCandidate>`

- [ ] **Step 1: `StopCandidate.kt` anlegen**

```kotlin
package de.dhde.hannover.departures.widget.data

data class StopCandidate(
    val stopId: String,
    val name: String,
    val distanceM: Int,
    val transportTypes: List<String>   // "BUS", "TRAM"
)
```

- [ ] **Step 2: Failing Test schreiben**

Datei: `app/src/test/java/de/dhde/hannover/departures/widget/data/NearestStationsFinderTest.kt`

```kotlin
package de.dhde.hannover.departures.widget.data

import de.dhde.hannover.departures.widget.api.Platform
import de.dhde.hannover.departures.widget.api.StationSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class NearestStationsFinderTest {

    // Fake-Distanz: Manhattan-Distanz in Grad, mit Faktor 1000 (Grad ~ km-Region)
    private val fakeDist: NearestStationsFinder.DistanceCalculator =
        { lat1, lon1, lat2, lon2 -> ((abs(lat1 - lat2) + abs(lon1 - lon2)) * 1000f) }

    private fun stop(id: String, lat: Double, lon: Double, bus: Boolean = false, tram: Boolean = false): StationSearchResult {
        val platforms = if (!bus && !tram) null else listOf(
            Platform(productClasses = buildList {
                if (bus) add(6)         // Bus (5..11)
                if (tram) add(4)        // Straßenbahn (2..4)
            })
        )
        return StationSearchResult(id = id, name = "Stop-$id", lat = lat, lon = lon, platforms = platforms)
    }

    @Test
    fun sortiertNachDistanz() {
        val stops = listOf(
            stop("A", 0.0, 0.5),
            stop("B", 0.0, 0.1),
            stop("C", 0.0, 0.3)
        )
        val result = NearestStationsFinder.findNearestStops(
            stops = stops, userLat = 0.0, userLon = 0.0,
            count = 3, transportFilter = null, distance = fakeDist
        )
        assertEquals(listOf("B", "C", "A"), result.map { it.stopId })
    }

    @Test
    fun kapptBeiCount() {
        val stops = (1..10).map { stop("s$it", 0.0, it * 0.1) }
        val result = NearestStationsFinder.findNearestStops(
            stops = stops, userLat = 0.0, userLon = 0.0,
            count = 3, transportFilter = null, distance = fakeDist
        )
        assertEquals(3, result.size)
    }

    @Test
    fun filtertBusVsTram() {
        val stops = listOf(
            stop("bus1", 0.0, 0.1, bus = true),
            stop("tram1", 0.0, 0.2, tram = true),
            stop("bus2", 0.0, 0.3, bus = true)
        )
        val onlyTram = NearestStationsFinder.findNearestStops(
            stops = stops, userLat = 0.0, userLon = 0.0,
            count = 5, transportFilter = TransportFilter.TRAM, distance = fakeDist
        )
        assertEquals(listOf("tram1"), onlyTram.map { it.stopId })

        val onlyBus = NearestStationsFinder.findNearestStops(
            stops = stops, userLat = 0.0, userLon = 0.0,
            count = 5, transportFilter = TransportFilter.BUS, distance = fakeDist
        )
        assertEquals(listOf("bus1", "bus2"), onlyBus.map { it.stopId })
    }

    @Test
    fun filterNullLiefertAlle() {
        val stops = listOf(
            stop("bus1", 0.0, 0.1, bus = true),
            stop("tram1", 0.0, 0.2, tram = true)
        )
        val result = NearestStationsFinder.findNearestStops(
            stops = stops, userLat = 0.0, userLon = 0.0,
            count = 5, transportFilter = null, distance = fakeDist
        )
        assertEquals(2, result.size)
    }

    @Test
    fun ignoriertStopsOhneKoordinaten() {
        val withCoords = stop("A", 0.0, 0.1)
        val withoutCoords = StationSearchResult(id = "B", name = "no-coords", lat = null, lon = null)
        val result = NearestStationsFinder.findNearestStops(
            stops = listOf(withCoords, withoutCoords),
            userLat = 0.0, userLon = 0.0, count = 5, transportFilter = null, distance = fakeDist
        )
        assertEquals(listOf("A"), result.map { it.stopId })
    }

    @Test
    fun leereStops() {
        val result = NearestStationsFinder.findNearestStops(
            stops = emptyList(), userLat = 0.0, userLon = 0.0,
            count = 3, transportFilter = null, distance = fakeDist
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun transportTypesGesetzt() {
        val stops = listOf(stop("mix", 0.0, 0.1, bus = true, tram = true))
        val result = NearestStationsFinder.findNearestStops(
            stops = stops, userLat = 0.0, userLon = 0.0,
            count = 1, transportFilter = null, distance = fakeDist
        )
        assertEquals(setOf("BUS", "TRAM"), result.first().transportTypes.toSet())
    }
}
```

- [ ] **Step 3: Test laufen lassen (soll fehlschlagen)**

Run:
```bash
cd /home/didi/git/uestra && ./gradlew :app:testDebugUnitTest --tests "de.dhde.hannover.departures.widget.data.NearestStationsFinderTest"
```
Expected: FAIL — `NearestStationsFinder` existiert noch nicht.

- [ ] **Step 4: `NearestStationsFinder.kt` implementieren**

```kotlin
package de.dhde.hannover.departures.widget.data

import android.location.Location
import de.dhde.hannover.departures.widget.api.StationSearchResult

object NearestStationsFinder {

    /** (lat1, lon1, lat2, lon2) → Distanz in Metern. */
    typealias DistanceCalculator = (Double, Double, Double, Double) -> Float

    /** Prod-Implementierung. Verwendet Location.distanceBetween — Android-only, deshalb via Function-Type kapselt. */
    val androidDistance: DistanceCalculator = { lat1, lon1, lat2, lon2 ->
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        results[0]
    }

    /**
     * Top-N nächste Stopps nach Distanz.
     * Stopps ohne Koordinaten werden verworfen.
     * Bei transportFilter != null werden nur Stopps zurückgegeben, deren Platforms den Filter erfüllen
     * (analog findAndSetActiveNearestStation: leere/null Platforms zählen als "passt" — konservativ).
     */
    fun findNearestStops(
        stops: List<StationSearchResult>,
        userLat: Double,
        userLon: Double,
        count: Int,
        transportFilter: TransportFilter?,
        distance: DistanceCalculator = androidDistance
    ): List<StopCandidate> {
        val filtered = stops.asSequence().filter { s ->
            if (s.lat == null || s.lon == null) return@filter false
            when (transportFilter) {
                TransportFilter.BUS -> s.platforms.isNullOrEmpty() || s.platforms!!.any { it.isBus }
                TransportFilter.TRAM -> s.platforms.isNullOrEmpty() || s.platforms!!.any { it.isTram }
                else -> true
            }
        }

        return filtered.map { s ->
            val d = distance(userLat, userLon, s.lat!!, s.lon!!)
            val types = buildList {
                if (s.platforms?.any { it.isBus } == true) add("BUS")
                if (s.platforms?.any { it.isTram } == true) add("TRAM")
            }
            StopCandidate(stopId = s.id, name = s.name, distanceM = d.toInt(), transportTypes = types)
        }
        .sortedBy { it.distanceM }
        .take(count)
        .toList()
    }
}
```

- [ ] **Step 5: Test laufen lassen (soll passen)**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "de.dhde.hannover.departures.widget.data.NearestStationsFinderTest"
```
Expected: `BUILD SUCCESSFUL`, 7 tests pass.

- [ ] **Step 6: Committen**

```bash
git add app/src/main/java/de/dhde/hannover/departures/widget/data/StopCandidate.kt \
        app/src/main/java/de/dhde/hannover/departures/widget/data/NearestStationsFinder.kt \
        app/src/test/java/de/dhde/hannover/departures/widget/data/NearestStationsFinderTest.kt
git commit -m "feat: StopCandidate + NearestStationsFinder mit Unit-Tests"
```

---

## Task 3: `WidgetSessionStore` — Picker-Session-Keys

**Files:**
- Modify: `app/src/main/java/de/dhde/hannover/departures/widget/data/WidgetSessionStore.kt`

**Interfaces:**
- Consumes: (nichts)
- Produces:
  - `suspend fun isPickerModeActive(): Boolean`
  - `suspend fun setPickerMode(active: Boolean)`
  - `fun pickerModeFlow(): Flow<Boolean>`
  - `suspend fun getPickerCandidatesJson(): String?`
  - `suspend fun setPickerCandidatesJson(json: String?)`
  - `fun pickerCandidatesFlow(): Flow<String?>`
  - `suspend fun getPickerOpenedAt(): Long`
  - `suspend fun setPickerOpenedAt(ts: Long)`
  - `suspend fun getPickerFilterOverride(): String?`
  - `suspend fun setPickerFilterOverride(value: String?)`
  - `fun pickerFilterOverrideFlow(): Flow<String?>`
  - `suspend fun clearPickerState()` — Sammel-Setter der alles resettet

- [ ] **Step 1: Companion-Object erweitern**

In `WidgetSessionStore.kt` innerhalb des `companion object` (nach `DEBUG_MODE`) ergänzen:

```kotlin
        private val PICKER_MODE = booleanPreferencesKey("picker_mode")
        private val PICKER_CANDIDATES = stringPreferencesKey("picker_candidates_json")
        private val PICKER_OPENED_AT = longPreferencesKey("picker_opened_at")
        private val PICKER_FILTER_OVERRIDE = stringPreferencesKey("picker_filter_override")
```

- [ ] **Step 2: Getter/Setter/Flows ergänzen**

Ans Ende der Klasse (vor der schließenden `}`) einfügen:

```kotlin
    // --- Picker-State ---

    fun pickerModeFlow(): Flow<Boolean> =
        context.cacheDataStore.data.map { it[PICKER_MODE] ?: false }

    suspend fun isPickerModeActive(): Boolean =
        context.cacheDataStore.data.map { it[PICKER_MODE] }.first() ?: false

    suspend fun setPickerMode(active: Boolean) {
        context.cacheDataStore.edit { it[PICKER_MODE] = active }
    }

    fun pickerCandidatesFlow(): Flow<String?> =
        context.cacheDataStore.data.map { it[PICKER_CANDIDATES] }

    suspend fun getPickerCandidatesJson(): String? =
        context.cacheDataStore.data.map { it[PICKER_CANDIDATES] }.first()

    suspend fun setPickerCandidatesJson(json: String?) {
        context.cacheDataStore.edit { prefs ->
            if (json == null) prefs.remove(PICKER_CANDIDATES) else prefs[PICKER_CANDIDATES] = json
        }
    }

    suspend fun getPickerOpenedAt(): Long =
        context.cacheDataStore.data.map { it[PICKER_OPENED_AT] }.first() ?: 0L

    suspend fun setPickerOpenedAt(ts: Long) {
        context.cacheDataStore.edit { it[PICKER_OPENED_AT] = ts }
    }

    fun pickerFilterOverrideFlow(): Flow<String?> =
        context.cacheDataStore.data.map { it[PICKER_FILTER_OVERRIDE] }

    suspend fun getPickerFilterOverride(): String? =
        context.cacheDataStore.data.map { it[PICKER_FILTER_OVERRIDE] }.first()

    suspend fun setPickerFilterOverride(value: String?) {
        context.cacheDataStore.edit { prefs ->
            if (value == null) prefs.remove(PICKER_FILTER_OVERRIDE) else prefs[PICKER_FILTER_OVERRIDE] = value
        }
    }

    suspend fun clearPickerState() {
        context.cacheDataStore.edit { prefs ->
            prefs[PICKER_MODE] = false
            prefs.remove(PICKER_CANDIDATES)
            prefs.remove(PICKER_FILTER_OVERRIDE)
            // PICKER_OPENED_AT bleibt drin, aber picker_mode=false verhindert Wirkung
        }
    }
```

- [ ] **Step 3: Compile-Check**

Run:
```bash
./gradlew :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Committen**

```bash
git add app/src/main/java/de/dhde/hannover/departures/widget/data/WidgetSessionStore.kt
git commit -m "feat: Picker-Session-Keys im WidgetSessionStore"
```

---

## Task 4: `FavoritesRepository` — `nearest_count`

**Files:**
- Modify: `app/src/main/java/de/dhde/hannover/departures/widget/data/FavoritesRepository.kt`

**Interfaces:**
- Produces:
  - `val nearestCountFlow: Flow<Int>` — Default 3
  - `suspend fun setNearestCount(count: Int)` — clampt auf 2..5
  - `suspend fun getNearestCountNow(): Int`

- [ ] **Step 1: Schlüssel + Default ergänzen**

Finde die Zeile mit `MAX_FAVORITES` bzw. dem PreferencesKey-Block (in etwa Zeile 260–270). Direkt daneben ergänzen:

```kotlin
        private val NEAREST_COUNT = intPreferencesKey("nearest_count")
        private const val DEFAULT_NEAREST_COUNT = 3
        private const val MIN_NEAREST_COUNT = 2
        private const val MAX_NEAREST_COUNT = 5
```
(Exakte Position an Konvention der bestehenden Datei ausrichten — dort wo die anderen Keys sitzen.)

- [ ] **Step 2: Flow + Getter + Setter ergänzen**

Analog zu `maxFavoritesFlow` / `setMaxFavorites` (siehe Zeile 269–279 der bestehenden Datei) ergänzen:

```kotlin
    val nearestCountFlow: Flow<Int> = context.dataStore.data
        .map { (it[NEAREST_COUNT] ?: DEFAULT_NEAREST_COUNT).coerceIn(MIN_NEAREST_COUNT, MAX_NEAREST_COUNT) }

    suspend fun getNearestCountNow(): Int =
        context.dataStore.data.map { it[NEAREST_COUNT] ?: DEFAULT_NEAREST_COUNT }.first()
            .coerceIn(MIN_NEAREST_COUNT, MAX_NEAREST_COUNT)

    suspend fun setNearestCount(count: Int) {
        context.dataStore.edit { it[NEAREST_COUNT] = count.coerceIn(MIN_NEAREST_COUNT, MAX_NEAREST_COUNT) }
    }
```

- [ ] **Step 3: Compile-Check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Committen**

```bash
git add app/src/main/java/de/dhde/hannover/departures/widget/data/FavoritesRepository.kt
git commit -m "feat: nearest_count Setting im FavoritesRepository"
```

---

## Task 5: `OpenPickerAction`

**Files:**
- Modify: `app/src/main/java/de/dhde/hannover/departures/widget/widget/WidgetActions.kt`

**Interfaces:**
- Consumes:
  - `NearestStationsFinder.findNearestStops(...)`
  - `WidgetSessionStore.setPickerCandidatesJson`, `setPickerMode`, `setPickerOpenedAt`, `setPickerFilterOverride`
  - `FavoritesRepository.getNearestCountNow()`
  - existierende `getBestLocation(context)` (bereits public in derselben Datei)
- Produces:
  - `class OpenPickerAction : ActionCallback`

- [ ] **Step 1: Neue Action-Klasse hinzufügen**

Am Ende von `WidgetActions.kt` (vor der finalen `}` der letzten Klasse — oder ganz unten als Top-Level-Klasse; die anderen Action-Klassen sind Top-Level) einfügen:

```kotlin
class OpenPickerAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val session = WidgetSessionStore(context)
        val favRepo = FavoritesRepository(context)
        val stopsRepo = StopsRepository(context)
        val filters = FilterStateStore(context)
        val currentStationId = favRepo.getActiveStationIdNow()
        val globalFilter = filters.getTabState(currentStationId)

        de.dhde.hannover.departures.widget.debug.DebugLog.log(
            "[picker] open: nearestCount=? filter=$globalFilter gpsActiveBefore=${session.isGpsModeActive()}"
        )

        val loc = getBestLocation(context)
        val count = favRepo.getNearestCountNow()
        de.dhde.hannover.departures.widget.debug.DebugLog.log(
            "[picker] fix: ${loc?.let { "age=${System.currentTimeMillis() - it.time}ms acc=${it.accuracy}m" } ?: "null (timeout/error)"}"
        )

        val candidates: List<StopCandidate> = if (loc != null) {
            val allStops = stopsRepo.getAllStops()
            de.dhde.hannover.departures.widget.debug.DebugLog.log(
                "[picker] finder: stopsInCache=${allStops.size} count=$count filter=$globalFilter"
            )
            NearestStationsFinder.findNearestStops(
                stops = allStops,
                userLat = loc.latitude,
                userLon = loc.longitude,
                count = count,
                transportFilter = globalFilter
            )
        } else emptyList()

        de.dhde.hannover.departures.widget.debug.DebugLog.log(
            "[picker] candidates: n=${candidates.size} first=${candidates.firstOrNull()?.let { "${it.name}@${it.distanceM}m" } ?: "-"}"
        )

        val json = if (candidates.isEmpty()) null else Gson().toJson(candidates)
        session.setPickerCandidatesJson(json)
        session.setPickerFilterOverride(null)
        session.setPickerOpenedAt(System.currentTimeMillis())
        session.setPickerMode(true)

        DeparturesWidget().updateAll(context)
    }
}
```

- [ ] **Step 2: Fehlenden Import ergänzen**

Am Kopf der Datei, bei den anderen `import de.dhde.…data.*`-Zeilen:

```kotlin
import de.dhde.hannover.departures.widget.data.NearestStationsFinder
import de.dhde.hannover.departures.widget.data.StopCandidate
```

- [ ] **Step 3: Compile-Check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Committen**

```bash
git add app/src/main/java/de/dhde/hannover/departures/widget/widget/WidgetActions.kt
git commit -m "feat: OpenPickerAction (Kandidaten-Fetch + State)"
```

---

## Task 6: `PickCandidateAction` + `TogglePickerFilterAction` + `ClosePickerAction`

**Files:**
- Modify: `app/src/main/java/de/dhde/hannover/departures/widget/widget/WidgetActions.kt`

**Interfaces:**
- Consumes:
  - `FavoritesRepository.setActiveStation`, `FilterStateStore.setTabState`, `WidgetSessionStore.setGpsMode`, `clearPickerState`, `RefreshAction.triggerUpdate`
- Produces:
  - `class PickCandidateAction : ActionCallback` — Companion mit `KEY_STOP_ID = ActionParameters.Key<String>("stopId")` und `KEY_STOP_NAME = ActionParameters.Key<String>("stopName")`
  - `class TogglePickerFilterAction : ActionCallback`
  - `class ClosePickerAction : ActionCallback`

- [ ] **Step 1: `PickCandidateAction` hinzufügen**

Ans Ende von `WidgetActions.kt`:

```kotlin
class PickCandidateAction : ActionCallback {
    companion object {
        val KEY_STOP_ID = ActionParameters.Key<String>("stopId")
        val KEY_STOP_NAME = ActionParameters.Key<String>("stopName")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val session = WidgetSessionStore(context)
        val favRepo = FavoritesRepository(context)
        val filters = FilterStateStore(context)

        val stopId = parameters[KEY_STOP_ID] ?: return
        val stopName = parameters[KEY_STOP_NAME] ?: stopId
        val override = session.getPickerFilterOverride()

        de.dhde.hannover.departures.widget.debug.DebugLog.log(
            "[picker] pick: stopId=$stopId overrideFilter=$override"
        )

        favRepo.setActiveStation(stopId, stopName)
        if (override == "ALL") {
            filters.setTabState(stopId, TransportFilter.ALL)
        }
        filters.setDirectionState(stopId, DirectionFilter.ALL)
        session.setGpsMode(true)
        session.clearPickerState()

        RefreshAction.triggerUpdate(context)
    }
}
```

- [ ] **Step 2: `TogglePickerFilterAction` hinzufügen**

```kotlin
class TogglePickerFilterAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val session = WidgetSessionStore(context)
        val current = session.getPickerFilterOverride()
        val next = if (current == "ALL") null else "ALL"
        de.dhde.hannover.departures.widget.debug.DebugLog.log(
            "[picker] toggleFilter: ${current ?: "null"} -> ${next ?: "null"}"
        )
        session.setPickerFilterOverride(next)
        DeparturesWidget().updateAll(context)
    }
}
```

- [ ] **Step 3: `ClosePickerAction` hinzufügen**

```kotlin
class ClosePickerAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        de.dhde.hannover.departures.widget.debug.DebugLog.log("[picker] close (user)")
        WidgetSessionStore(context).clearPickerState()
        DeparturesWidget().updateAll(context)
    }
}
```

- [ ] **Step 4: Compile-Check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Committen**

```bash
git add app/src/main/java/de/dhde/hannover/departures/widget/widget/WidgetActions.kt
git commit -m "feat: PickCandidate/TogglePickerFilter/ClosePicker Actions"
```

---

## Task 7: `DeparturesWidget` — Picker-Layout + Icon-Rebind

**Files:**
- Modify: `app/src/main/java/de/dhde/hannover/departures/widget/widget/DeparturesWidget.kt`

**Interfaces:**
- Consumes: alle Actions aus Task 5 und 6, `WidgetSessionStore.pickerModeFlow`, `pickerCandidatesFlow`, `pickerFilterOverrideFlow`.

- [ ] **Step 1: Existierendes Rendering und GPS-Icon-Onclick finden**

Run:
```bash
grep -n "LocateNearestStationAction\|gpsModeActive\|WidgetHeader\|provideContent" /home/didi/git/uestra/app/src/main/java/de/dhde/hannover/departures/widget/widget/DeparturesWidget.kt
```
Notiere Zeilen mit `actionRunCallback<LocateNearestStationAction>()` — dort das Icon-Binding.

- [ ] **Step 2: GPS-Icon-Onclick auf `OpenPickerAction` umbiegen**

Ersetze im GPS-Icon-Composable (Header):
```kotlin
.clickable(actionRunCallback<LocateNearestStationAction>())
```
mit:
```kotlin
.clickable(actionRunCallback<OpenPickerAction>())
```

- [ ] **Step 3: Picker-State aus DataStore lesen im provideContent**

Im `provideContent`-Block (ganz oben, wo bereits andere Flow-`collectAsState` sind) ergänzen:

```kotlin
val session = WidgetSessionStore(context)
val pickerMode by session.pickerModeFlow().collectAsState(initial = false)
val pickerCandidatesJson by session.pickerCandidatesFlow().collectAsState(initial = null)
val pickerFilterOverride by session.pickerFilterOverrideFlow().collectAsState(initial = null)
```

(Wenn kein `collectAsState` vorhanden ist, entsprechend Glance-idiomatisch mit `currentState<Preferences>` — Muster in der Datei nachschlagen und angleichen.)

- [ ] **Step 4: Picker-Layout einbinden**

Im Rendering-Root (dort wo `DeparturesLayout()` aufgerufen wird), davor einen Conditional-Zweig:

```kotlin
if (pickerMode) {
    PickerLayout(
        candidatesJson = pickerCandidatesJson,
        filterOverride = pickerFilterOverride,
        globalFilter = currentTabState   // bereits im Scope
    )
} else {
    // bestehende Rendering-Kaskade
    DeparturesLayout(...)
}
```

- [ ] **Step 5: `PickerLayout`-Composable schreiben**

Als neues `@Composable` in derselben Datei (nach dem existierenden `WidgetHeader` bzw. am Ende):

```kotlin
@Composable
private fun PickerLayout(
    candidatesJson: String?,
    filterOverride: String?,
    globalFilter: TransportFilter
) {
    val candidates: List<StopCandidate> = remember(candidatesJson) {
        if (candidatesJson.isNullOrBlank()) emptyList()
        else runCatching {
            val type = com.google.gson.reflect.TypeToken.getParameterized(
                List::class.java, StopCandidate::class.java
            ).type
            Gson().fromJson<List<StopCandidate>>(candidatesJson, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    val effectiveFilter = if (filterOverride == "ALL") TransportFilter.ALL else globalFilter
    val visible = candidates.filter { c ->
        when (effectiveFilter) {
            TransportFilter.BUS -> c.transportTypes.contains("BUS") || c.transportTypes.isEmpty()
            TransportFilter.TRAM -> c.transportTypes.contains("TRAM") || c.transportTypes.isEmpty()
            else -> true
        }
    }

    Column(modifier = GlanceModifier.fillMaxSize().padding(8.dp)) {
        // Header
        Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Station wählen",
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = ColorProvider(UestraColors.TextMain), fontSize = 14.sp)
            )
            if (globalFilter != TransportFilter.ALL) {
                // Filter-Toggle: sichtbar wenn globaler Filter aktiv
                Image(
                    provider = ImageProvider(R.drawable.ic_filter_off),   // oder passendes Icon aus res
                    contentDescription = "Alle anzeigen",
                    modifier = GlanceModifier.size(24.dp)
                        .clickable(actionRunCallback<TogglePickerFilterAction>())
                        .padding(end = 6.dp),
                    colorFilter = ColorFilter.tint(
                        ColorProvider(if (filterOverride == "ALL") UestraColors.GpsBlue else UestraColors.TextSub)
                    )
                )
            }
            Image(
                provider = ImageProvider(R.drawable.ic_close),   // vorhandenes Close-Icon, sonst neu anlegen
                contentDescription = "Schließen",
                modifier = GlanceModifier.size(24.dp)
                    .clickable(actionRunCallback<ClosePickerAction>()),
                colorFilter = ColorFilter.tint(ColorProvider(UestraColors.TextSub))
            )
        }

        // Content
        when {
            candidates.isEmpty() -> {
                Text("Standort nicht verfügbar",
                    style = TextStyle(color = ColorProvider(UestraColors.TextSub), fontSize = 13.sp))
            }
            visible.isEmpty() -> {
                Text("Keine Stationen für diesen Filter",
                    style = TextStyle(color = ColorProvider(UestraColors.TextSub), fontSize = 13.sp))
            }
            else -> {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(visible.size) { idx ->
                        val c = visible[idx]
                        Row(
                            modifier = GlanceModifier.fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable(actionRunCallback<PickCandidateAction>(
                                    actionParametersOf(
                                        PickCandidateAction.KEY_STOP_ID to c.stopId,
                                        PickCandidateAction.KEY_STOP_NAME to c.name
                                    )
                                )),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = c.name,
                                modifier = GlanceModifier.defaultWeight(),
                                style = TextStyle(color = ColorProvider(UestraColors.TextMain), fontSize = 14.sp)
                            )
                            Text(
                                text = "${c.distanceM} m",
                                modifier = GlanceModifier.padding(end = 6.dp),
                                style = TextStyle(color = ColorProvider(UestraColors.TextSub), fontSize = 12.sp)
                            )
                            if (c.transportTypes.contains("TRAM")) {
                                Image(provider = ImageProvider(R.drawable.ic_tram),
                                    contentDescription = null,
                                    modifier = GlanceModifier.size(16.dp).padding(end = 2.dp))
                            }
                            if (c.transportTypes.contains("BUS")) {
                                Image(provider = ImageProvider(R.drawable.ic_bus),
                                    contentDescription = null,
                                    modifier = GlanceModifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
```

**Hinweis zu Icons:** `ic_close`, `ic_filter_off`, `ic_tram`, `ic_bus` müssen existieren. Prüfe mit
```bash
ls /home/didi/git/uestra/app/src/main/res/drawable/ | grep -E "close|filter|tram|bus"
```
Falls fehlend, entsprechende Vector-Drawables anlegen (Material-Icons als XML in `drawable/`). Bei Zweifelsfall vorhandene Icons wiederverwenden (z.B. das GPS-Icon als temporären Fallback) und in einer eigenen Notiz für später aufnehmen.

- [ ] **Step 6: Compile-Check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. Bei fehlenden Icons: R.drawable-Referenzen anpassen.

- [ ] **Step 7: Committen**

```bash
git add app/src/main/java/de/dhde/hannover/departures/widget/widget/DeparturesWidget.kt \
        app/src/main/res/drawable/   # falls neue Icons dazugekommen
git commit -m "feat: PickerLayout im Widget + GPS-Icon-Rebind"
```

---

## Task 8: `DeparturesWidgetReceiver` — Timeout-Check

**Files:**
- Modify: `app/src/main/java/de/dhde/hannover/departures/widget/widget/DeparturesWidgetReceiver.kt`

**Interfaces:**
- Consumes: `WidgetSessionStore.isPickerModeActive`, `getPickerOpenedAt`, `clearPickerState`.

- [ ] **Step 1: TICK-Handler erweitern**

In `onReceive`, im `if (intent.action == "de.dhde.hannover.departures.widget.TICK")`-Block direkt in `scope.launch { ... }`, VOR der bestehenden GPS-Refresh-Logik:

```kotlin
val session = WidgetSessionStore(context)
if (session.isPickerModeActive() &&
    System.currentTimeMillis() - session.getPickerOpenedAt() > 60_000L) {
    de.dhde.hannover.departures.widget.debug.DebugLog.log("[picker] timeout after 60s, closing")
    session.clearPickerState()
    DeparturesWidget().updateAll(context)
    return@launch
}
```

Anschließend die bestehende `WidgetSessionStore(context).isGpsModeActive()`-Zeile durch Wiederverwendung von `session` ersetzen (`session.isGpsModeActive()`).

- [ ] **Step 2: Compile-Check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Committen**

```bash
git add app/src/main/java/de/dhde/hannover/departures/widget/widget/DeparturesWidgetReceiver.kt
git commit -m "feat: 60s-Timeout für Picker im Widget-Ticker"
```

---

## Task 9: `OptionsScreen` — `nearest_count`-Setting

**Files:**
- Modify: `app/src/main/java/de/dhde/hannover/departures/widget/OptionsScreen.kt`

**Interfaces:**
- Consumes: `FavoritesRepository.nearestCountFlow`, `setNearestCount`.

- [ ] **Step 1: Flow-Collect im Composable ergänzen**

In `OptionsScreen(...)` bei den anderen `collectAsState`-Zeilen (Zeile ~36–52) ergänzen:

```kotlin
val nearestCount by repo.nearestCountFlow.collectAsState(initial = 3)
```

Und den lokalen Slider-State (bei den anderen `localMax*` in Zeile ~125–128) ergänzen:

```kotlin
var localNearestCount by remember(nearestCount) { mutableStateOf(nearestCount.toFloat()) }
```

- [ ] **Step 2: Neue Options-Zeile in der `LazyColumn` einfügen**

Direkt nach dem bestehenden `item { OptionsGroupHeader("Anzeige") }`-Block eine neue Sektion — oder in einer passenden bestehenden Gruppe (nach Muster der `MaxFavorites`-Zeile). Muster analog zu vorhandenen Slidern:

```kotlin
item { OptionsGroupHeader("Standort") }
item {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = UestraColors.CardBg)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Anzahl Kandidaten beim GPS-Klick: ${localNearestCount.roundToInt()}",
                color = UestraColors.TextMain,
                fontSize = 14.sp
            )
            Slider(
                value = localNearestCount,
                onValueChange = { localNearestCount = it },
                onValueChangeFinished = {
                    scope.launch { repo.setNearestCount(localNearestCount.roundToInt()) }
                },
                valueRange = 2f..5f,
                steps = 2,   // 2 steps zwischen 2 und 5 → Werte 2, 3, 4, 5
                colors = SliderDefaults.colors(
                    thumbColor = UestraColors.Teal,
                    activeTrackColor = UestraColors.Teal
                )
            )
            Text(
                text = "Beim Klick auf das GPS-Icon erscheinen die X nächsten Haltestellen zur Auswahl.",
                color = UestraColors.TextSub,
                fontSize = 12.sp
            )
        }
    }
}
```

- [ ] **Step 3: Reset-Dialog erweitern**

Im `showResetConfirm`-Dialog (Zeile ~76–103), im `scope.launch { ... }`-Block eine Zeile ergänzen:

```kotlin
repo.setNearestCount(3)
```

- [ ] **Step 4: Compile-Check + Preview**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Committen**

```bash
git add app/src/main/java/de/dhde/hannover/departures/widget/OptionsScreen.kt
git commit -m "feat: OptionsScreen-Slider für Anzahl GPS-Kandidaten"
```

---

## Task 10: `MainActivity` — ModalBottomSheet + onGpsClick

**Files:**
- Modify: `app/src/main/java/de/dhde/hannover/departures/widget/MainActivity.kt`

**Interfaces:**
- Consumes: `getBestLocation(context)` (public in WidgetActions.kt), `NearestStationsFinder`, `StopsRepository`, `FavoritesRepository`, `FilterStateStore`, `WidgetSessionStore`.

- [ ] **Step 1: State für den Sheet neben den anderen `remember { mutableStateOf(...) }` einfügen**

Im Composable-Scope, wo der `WidgetHeader`-Preview lebt (der `onGpsClick` empfängt — siehe MainActivity.kt Zeile 501–513 aus der Explore-Recherche):

```kotlin
var showPickerSheet by remember { mutableStateOf(false) }
var pickerCandidates by remember { mutableStateOf<List<StopCandidate>>(emptyList()) }
var pickerLoading by remember { mutableStateOf(false) }
var pickerFilterOverride by remember { mutableStateOf(false) }
var pickerError by remember { mutableStateOf<String?>(null) }
val pickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
```

- [ ] **Step 2: `onGpsClick`-Handler ersetzen**

Der bisherige Handler toggelt GPS direkt. Ersetze mit:

```kotlin
onGpsClick = {
    scope.launch {
        showPickerSheet = true
        pickerLoading = true
        pickerError = null
        pickerCandidates = emptyList()
        pickerFilterOverride = false

        val fine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            pickerError = "Standort-Berechtigung fehlt"
            pickerLoading = false
        } else {
            val loc = de.dhde.hannover.departures.widget.widget.getBestLocation(context)
            if (loc == null) {
                pickerError = "Standort nicht verfügbar"
            } else {
                val count = favRepo.getNearestCountNow()
                val stationId = favRepo.getActiveStationIdNow()
                val filter = FilterStateStore(context).getTabState(stationId)
                val stops = StopsRepository(context).getAllStops()
                pickerCandidates = NearestStationsFinder.findNearestStops(
                    stops = stops, userLat = loc.latitude, userLon = loc.longitude,
                    count = count, transportFilter = filter
                )
                if (pickerCandidates.isEmpty()) pickerError = "Keine Stationen in der Nähe"
            }
            pickerLoading = false
        }
        de.dhde.hannover.departures.widget.debug.DebugLog.log(
            "[picker] app open: err=$pickerError count=${pickerCandidates.size}"
        )
    }
}
```

**Hinweis:** `favRepo` und `scope` müssen im Scope verfügbar sein — sie werden in `MainActivity` bereits definiert (recherchieren, wenn Name abweicht).

- [ ] **Step 3: `ModalBottomSheet` einbauen**

Direkt nach dem `WidgetHeader`-Aufruf (oder außerhalb, im Compose-Root der Activity):

```kotlin
if (showPickerSheet) {
    ModalBottomSheet(
        onDismissRequest = { showPickerSheet = false },
        sheetState = pickerSheetState,
        containerColor = UestraColors.CardBg
    ) {
        val filterState = FilterStateStore(context)
        val currentGlobalFilter = remember { mutableStateOf(TransportFilter.ALL) }
        LaunchedEffect(Unit) {
            currentGlobalFilter.value = filterState.getTabState(favRepo.getActiveStationIdNow())
        }
        val effectiveFilter = if (pickerFilterOverride) TransportFilter.ALL else currentGlobalFilter.value
        val visible = pickerCandidates.filter { c ->
            when (effectiveFilter) {
                TransportFilter.BUS -> c.transportTypes.contains("BUS") || c.transportTypes.isEmpty()
                TransportFilter.TRAM -> c.transportTypes.contains("TRAM") || c.transportTypes.isEmpty()
                else -> true
            }
        }
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Station wählen", color = UestraColors.TextMain, fontSize = 16.sp,
                    modifier = Modifier.weight(1f))
                if (currentGlobalFilter.value != TransportFilter.ALL) {
                    TextButton(onClick = { pickerFilterOverride = !pickerFilterOverride }) {
                        Text(if (pickerFilterOverride) "Filter aktiv" else "Alle anzeigen",
                            color = UestraColors.Teal)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                pickerLoading -> CircularProgressIndicator(color = UestraColors.Teal)
                pickerError != null -> Text(pickerError!!, color = UestraColors.TextSub)
                visible.isEmpty() -> Text("Keine Stationen für diesen Filter", color = UestraColors.TextSub)
                else -> LazyColumn {
                    items(visible) { c ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                scope.launch {
                                    favRepo.setActiveStation(c.stopId, c.name)
                                    if (pickerFilterOverride) filterState.setTabState(c.stopId, TransportFilter.ALL)
                                    WidgetSessionStore(context).setGpsMode(true)
                                    de.dhde.hannover.departures.widget.debug.DebugLog.log(
                                        "[picker] app pick: stopId=${c.stopId} overrideFilter=$pickerFilterOverride"
                                    )
                                    de.dhde.hannover.departures.widget.widget.DeparturesWidget().updateAll(context)
                                    showPickerSheet = false
                                }
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(c.name, color = UestraColors.TextMain, modifier = Modifier.weight(1f))
                            Text("${c.distanceM} m", color = UestraColors.TextSub, fontSize = 12.sp,
                                modifier = Modifier.padding(end = 8.dp))
                            if (c.transportTypes.contains("TRAM"))
                                Icon(painterResource(R.drawable.ic_tram), null,
                                    tint = UestraColors.TextSub, modifier = Modifier.size(16.dp))
                            if (c.transportTypes.contains("BUS"))
                                Icon(painterResource(R.drawable.ic_bus), null,
                                    tint = UestraColors.TextSub, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
```

**Hinweis:** Existierende Icon-Ressourcen wiederverwenden. Falls die Compose-Version `painterResource(...)` mit `android.R.drawable.…` gecrasht ist (bekannter Fallstrick der Projektnotiz), stattdessen `Image(ImageVector)` verwenden oder Material-Icons via `Icons.Filled.*`.

- [ ] **Step 4: Fehlende Imports ergänzen**

```kotlin
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.clickable
import de.dhde.hannover.departures.widget.data.NearestStationsFinder
import de.dhde.hannover.departures.widget.data.StopCandidate
import de.dhde.hannover.departures.widget.data.StopsRepository
import de.dhde.hannover.departures.widget.data.FilterStateStore
import de.dhde.hannover.departures.widget.data.TransportFilter
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
```

Am `MainActivity`-Composable ggf. `@OptIn(ExperimentalMaterial3Api::class)` ergänzen (falls nicht schon vorhanden).

- [ ] **Step 5: Compile-Check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Committen**

```bash
git add app/src/main/java/de/dhde/hannover/departures/widget/MainActivity.kt
git commit -m "feat: ModalBottomSheet-Picker in MainActivity"
```

---

## Task 11: Cleanup — `LocateNearestStationAction` entfernen

**Files:**
- Modify: `app/src/main/java/de/dhde/hannover/departures/widget/widget/WidgetActions.kt` (Klasse `LocateNearestStationAction` löschen)
- Modify: MainActivity.kt / DeparturesWidget.kt — jegliche verbleibenden Referenzen entfernen.

**Interfaces:** (nur Cleanup)

- [ ] **Step 1: Verwendungen suchen**

Run:
```bash
grep -rn "LocateNearestStationAction" /home/didi/git/uestra/app/src/
```
Expected: außer der Definition (`class LocateNearestStationAction`) sollten keine Referenzen mehr existieren (durch Task 7 wurde das Icon umgebogen).

- [ ] **Step 2: Klasse löschen**

Aus `WidgetActions.kt` den kompletten Block `class LocateNearestStationAction : ActionCallback { ... }` (Zeilen 290–319 aktuell) entfernen.

- [ ] **Step 3: `findAndSetActiveNearestStation` — Nutzung prüfen**

Run:
```bash
grep -rn "findAndSetActiveNearestStation" /home/didi/git/uestra/app/src/
```
Diese Funktion wird noch aus `RefreshAction.triggerUpdate` und `ChangeTabAction` verwendet (im GPS-Auto-Refresh-Pfad, wenn `isGpsModeActive()` true ist). **Nicht löschen** — sie bleibt für den GPS-Auto-Follow-Modus relevant (Ticker aktualisiert nächste Station im laufenden GPS-Mode).

- [ ] **Step 4: Compile-Check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Committen**

```bash
git add app/src/main/java/de/dhde/hannover/departures/widget/widget/WidgetActions.kt
git commit -m "chore: entferne unbenutzte LocateNearestStationAction"
```

---

## Task 12: Manuelle Verifikation + Debug-Log-Review

**Files:** (keine — Verifikations-Checkliste)

**Interfaces:** Deliverable ist ein grüner Manual-Test-Report.

- [ ] **Step 1: Debug-APK bauen und aufs Test-Device deployen**

Run:
```bash
./gradlew :app:installDebug
adb shell am start -n de.dhde.hannover.departures.widget/.MainActivity
```

- [ ] **Step 2: Debug-Modus aktivieren**

Im Help-Screen der App die Version 7× tippen. Dann in den Debug-Screen wechseln (Log-Ausgabe sichtbar).

- [ ] **Step 3: Optionen prüfen**

Öffne OptionsScreen — neue Sektion "Standort" sichtbar? Slider 2..5 funktioniert? Werteänderung persistiert (App neu starten, Wert bleibt)?

- [ ] **Step 4: Widget-Flow — Happy Path**

1. Widget auf Homescreen platzieren.
2. GPS-Icon tippen → Picker öffnet sich mit erwarteter Anzahl Kandidaten.
3. Auf Kandidat tippen → aktive Station wechselt, GPS-Icon blau, Abfahrten für neue Station laden.
4. Debug-Screen prüfen: `[picker] open`, `[picker] fix`, `[picker] candidates`, `[picker] pick` mit sinnvollen Werten.

- [ ] **Step 5: Widget-Flow — Filter-Toggle**

1. Verkehrsmittel-Filter (Bus/Bahn) im Widget aktivieren.
2. GPS-Icon tippen → Picker öffnet, Filter-Toggle-Icon sichtbar.
3. Kandidaten sind gefiltert (nur Bus- oder nur Bahn-Stopps).
4. Filter-Toggle tippen → Liste wächst um andere Stopps, **kein neuer Debug-Log für `[picker] fix`** (kein neuer GPS-Request!).
5. Kandidat wählen → globaler Filter im Widget wird ALL.

- [ ] **Step 6: Widget-Flow — Schließen und Timeout**

1. Picker öffnen, `✕` tippen → Widget zeigt wieder Abfahrten.
2. Picker öffnen, 65 Sekunden warten (Ticker läuft minütlich) → Picker verschwindet automatisch, Debug-Log `[picker] timeout after 60s`.

- [ ] **Step 7: Widget-Flow — Fehler**

1. Location-Berechtigung entziehen (Settings → App → Permissions → Standort).
2. GPS-Icon tippen → Picker öffnet mit Meldung "Standort nicht verfügbar".
3. `✕` tippen → zurück zur normalen Ansicht.

- [ ] **Step 8: App-Flow analog**

Alle obigen Schritte in der App-MainActivity nachfahren mit dem BottomSheet.

- [ ] **Step 9: Regression**

- Bestehender GPS-Auto-Follow-Modus im laufenden GPS-Mode: einmal auswählen, warten 2 Minuten. Ticker aktualisiert nächste Station (siehe `findAndSetActiveNearestStation`)?
- Favoriten-Wechsel per Klick auf Favoritenzeile schaltet GPS aus (bestehendes `ChangeStationAction`-Verhalten)?

- [ ] **Step 10: Commit "no-op" für Test-Report — falls Findings**

Falls Bugs gefunden: neuer Fix-Commit, `verification-before-completion` beachten. Sonst weiter zum Push.

---

## Push-Vorbereitung

- [ ] **Max-Check (Pflicht vor Push)**

Ruf Max via Agent-Tool auf und lass ihn die staged/committed Änderungen prüfen. Bei Urteil OK → weiter, sonst Findings adressieren.

- [ ] **Push + PR**

Run:
```bash
git push -u origin feat/gps-picker
gh pr create --title "feat: GPS-Picker für nächste Stationen" --body "$(cat <<'EOF'
## Summary
- GPS-Icon-Klick öffnet Kandidaten-Picker (2–5 nächste Stationen, konfigurierbar) statt sofort einzelne nächste Station
- Widget: Picker als Widget-interne Ansicht mit Timeout 60s
- App: ModalBottomSheet mit gleicher Logik
- Filter-Toggle im Picker (nur wenn globaler Filter aktiv) — Filter lokal umschaltbar ohne neuen GPS-Request
- Neue Option "Anzahl Kandidaten" im OptionsScreen (Slider 2..5, Default 3)
- Debug-Logs mit Präfix `[picker]` an allen Schlüsselstellen
- Unit-Tests für NearestStationsFinder (JVM, keine Robolectric-Deps)

## Test plan
- [ ] Widget: GPS-Klick → Picker → Kandidat wählen → Station wechselt, GPS blau
- [ ] Widget: Filter aktiv → Filter-Toggle sichtbar → Toggle-Klick ändert Liste ohne neuen GPS-Fix
- [ ] Widget: 60s-Timeout schließt Picker automatisch
- [ ] Widget: ✕ schließt Picker sofort
- [ ] Widget: fehlende Location-Permission → Meldung im Picker
- [ ] App-Dashboard: gleiche Punkte via BottomSheet
- [ ] OptionsScreen: Slider ändert nearest_count, persistiert
- [ ] Reset-Button setzt nearest_count auf 3 zurück
- [ ] Bestehender GPS-Auto-Follow (Ticker) funktioniert weiter
- [ ] Favoriten-Wechsel schaltet GPS aus (Regression)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Spec-Coverage-Check (Self-Review)

| Spec-Sektion | Task |
|--------------|------|
| §2.1 Interaktion (immer Picker öffnen) | Task 5, 7 |
| §2.2 Kandidatenauswahl (Sort, Count, Filter) | Task 2, 5 |
| §2.3 Filter-Toggle (nur wenn Filter aktiv, kein neuer Request) | Task 6, 7 |
| §2.4 Zeileninhalt (Name + Distanz + Icons) | Task 7, 10 |
| §2.5 Verlassen (✕, Timeout, Sheet-Dismiss) | Task 6, 8, 10 |
| §2.6 Fehlerfälle (kein Fix, Permission, keine Stopps, Prozess-Kill) | Task 5, 7, 8, 10 |
| §3.1 Persistenz-Keys | Task 3, 4 |
| §3.2 StopCandidate | Task 2 |
| §3.3 NearestStationsFinder | Task 2 |
| §3.4 Glance-Actions | Task 5, 6 |
| §3.5 Widget-Rendering | Task 7 |
| §3.6 Timeout im Receiver | Task 8 |
| §3.7 App-Seite | Task 10 |
| §3.8 Options-UI (OptionsScreen) | Task 9 |
| §4 Debug-Logging (mit `[picker]`-Präfix) | Task 5, 6, 8, 10 |
| §5 Migration (Defaults on first read) | implizit durch Elvis-Operatoren in Task 3, 4 |
| §6 Testing (Unit + manuell) | Task 2, 12 |

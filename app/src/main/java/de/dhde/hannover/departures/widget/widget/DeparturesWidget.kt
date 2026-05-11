package de.dhde.hannover.departures.widget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.ColorFilter
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import androidx.glance.appwidget.cornerRadius
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.appWidgetBackground
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.dhde.hannover.departures.widget.api.DepartureItem
import de.dhde.hannover.departures.widget.api.FlatDeparture
import de.dhde.hannover.departures.widget.api.toFlatRows
import de.dhde.hannover.departures.widget.api.UestraApi
import de.dhde.hannover.departures.widget.data.FavoriteStation
import de.dhde.hannover.departures.widget.data.FavoritesRepository
import de.dhde.hannover.departures.widget.data.DeparturesCache
import de.dhde.hannover.departures.widget.widget.RefreshAction
import de.dhde.hannover.departures.widget.widget.ChangeTabAction
import de.dhde.hannover.departures.widget.widget.ChangeStationAction
import de.dhde.hannover.departures.widget.widget.ChangeDirectionAction
import de.dhde.hannover.departures.widget.widget.LocateNearestStationAction
import de.dhde.hannover.departures.widget.widget.ToggleTimeDisplayAction
import java.time.Instant
import java.time.format.DateTimeParseException
import java.time.Duration
import de.dhde.hannover.departures.widget.R
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import androidx.compose.runtime.SideEffect
import androidx.glance.LocalContext
import androidx.glance.appwidget.CircularProgressIndicator

object WidgetTicker {
    fun scheduleNextTick(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DeparturesWidgetReceiver::class.java).apply {
            action = "de.dhde.hannover.departures.widget.TICK"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val now = System.currentTimeMillis()
        val nextMinute = now + (60000L - (now % 60000L))
        alarmManager.setWindow(AlarmManager.RTC, nextMinute, 1000L, pendingIntent)
    }
    fun cancelTick(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DeparturesWidgetReceiver::class.java).apply {
            action = "de.dhde.hannover.departures.widget.TICK"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, 
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) alarmManager.cancel(pendingIntent)
    }
}

class DeparturesWidget : GlanceAppWidget() {
    companion object {
        private val gson = Gson()
    }

    // Wir verzichten komplett auf GlanceStateDefinition, um den 
    // Bug-behafteten updateAppWidgetState-Mechanismus zu umgehen.

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val cache = DeparturesCache(context)
        val repo = FavoritesRepository(context)

        provideContent {
            val stationIdState by repo.activeStationId.collectAsState(initial = "25000031")
            val stationId = stationIdState ?: "25000031"
            
            val maxFavorites by repo.maxFavoritesFlow.collectAsState(initial = 3)
            val maxFavRows by repo.maxFavRowsFlow.collectAsState(initial = 1)
            val maxRows by repo.maxRowsFlow.collectAsState(initial = 10)
            
            val favorites by repo.favoritesFlow.collectAsState(initial = emptyList())
            
            val stationNameState by repo.effectiveStationName.collectAsState(initial = "Laden...")
            val stationName = stationNameState

            val tabState by cache.getTabStateFlow(stationId).collectAsState(initial = "ALL")
            val directionState by cache.getDirectionStateFlow(stationId).collectAsState(initial = "ALL")
            val gpsModeActive by cache.getGpsModeFlow().collectAsState(initial = false)
            val timeDisplayMode by cache.getTimeDisplayModeFlow().collectAsState(initial = "MIN")
            
            val cachedJson by cache.getDeparturesJsonFlow(stationId).collectAsState(initial = "[]")
            val lastUpdated by cache.getLastUpdatedFlow(stationId).collectAsState(initial = "")
            
            val errorState by cache.getErrorStateFlow().collectAsState(initial = "")
            val transportFilters by repo.transportTypesFlow.collectAsState(initial = setOf("Stadtbahn", "Bus", "S-Bahn"))

            val departures: List<DepartureItem> = try {
                val list: List<DepartureItem> = gson.fromJson(cachedJson, object : TypeToken<List<DepartureItem>>() {}.type)
                list
            } catch (e: Exception) {
                emptyList()
            }

            // Alle Events aller Linien zu einzelnen Abfahrtszeilen aufkläppen und nach Echtzeit sortieren
            val flatDepartures: List<FlatDeparture> = departures
                .flatMap { it.toFlatRows() }
                .sortedBy { it.departureTime }

            val hasFutureDepartures = flatDepartures.isNotEmpty()

            val context = LocalContext.current
            SideEffect {
                if (hasFutureDepartures) {
                    WidgetTicker.scheduleNextTick(context)
                } else {
                    WidgetTicker.cancelTick(context)
                }
            }

            val isRefreshing by cache.isRefreshingFlow().collectAsState(initial = false)

            WidgetContent(
                stationName     = stationName,
                stationId       = stationId,
                lastUpdated     = lastUpdated,
                departures      = departures,
                flatDepartures  = flatDepartures,
                favorites       = favorites,
                tabState        = tabState,
                directionState  = directionState,
                gpsModeActive   = gpsModeActive,
                timeDisplayMode = timeDisplayMode,
                status          = if (errorState.isNotEmpty()) "error" else "ok",
                errorMsg        = errorState,
                isRefreshing    = isRefreshing,
                maxFavorites    = maxFavorites,
                maxFavRows      = maxFavRows,
                maxRows         = maxRows,
                transportFilters = transportFilters
            )
        }
    }

    @Composable
    private fun WidgetContent(
        stationName: String,
        stationId: String,
        lastUpdated: String,
        departures: List<DepartureItem>,
        flatDepartures: List<FlatDeparture>,
        favorites: List<FavoriteStation>,
        tabState: String,
        directionState: String,
        gpsModeActive: Boolean,
        timeDisplayMode: String,
        status: String,
        errorMsg: String,
        isRefreshing: Boolean,
        maxFavorites: Int,
        maxFavRows: Int,
        maxRows: Int,
        transportFilters: Set<String>
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(ColorProvider(Color(0xFF121212)))
        ) {
            // --- Hintergrund-Icon (Subtil) ---
            BackgroundIcon(tabState)

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
            Header(stationName, gpsModeActive, isRefreshing)
            
            FilterSegmentedRow(departures, tabState, directionState)

            if (status == "error" && errorMsg.isNotEmpty()) {
                Text("⚠️ $errorMsg", style = TextStyle(color = ColorProvider(Color(0xFFFF9800)), fontSize = 12.sp, fontWeight = FontWeight.Medium), modifier = GlanceModifier.padding(vertical = 4.dp))
            }

            val minutesSinceUpdate = if (lastUpdated.isNotEmpty()) {
                val lastTime = Instant.ofEpochMilli(lastUpdated.toLong())
                Duration.between(lastTime, Instant.now()).toMinutes()
            } else 0L

            val isStale = minutesSinceUpdate >= 5
            val isWarning = minutesSinceUpdate >= 10


            val filtered = flatDepartures.filter {
                // Globaler Transport-Filter
                val globalTypeMatch = when {
                    it.isBus -> "Bus" in transportFilters
                    it.isTram -> "Stadtbahn" in transportFilters
                    it.isTrain -> "S-Bahn" in transportFilters
                    else -> true
                }
                if (!globalTypeMatch) return@filter false

                val typeMatch = when (tabState) {
                    "BUS" -> it.isBus
                    "TRAIN" -> it.isTram
                    else -> true
                }
                val dirMatch = when (directionState) {
                    "H" -> it.lineId?.endsWith("H", ignoreCase = true) == true
                    "R" -> it.lineId?.endsWith("R", ignoreCase = true) == true
                    else -> true
                }
                typeMatch && dirMatch
            }

            Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.CenterEnd) {
                val limitedFiltered = if (maxRows >= 15) filtered else filtered.take(maxRows)
                if (limitedFiltered.isEmpty()) {
                    Text("Keine Abfahrten", style = TextStyle(color = ColorProvider(Color.Gray)))
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(limitedFiltered) { departure ->
                            FlatDepartureRow(departure, timeDisplayMode, isWarning)
                        }
                    }
                    
                    // Unsichtbare Box (ca. 1/4 der Breite) über den Abfahrtszeiten, 
                    // fängt alle Klicks ab und ändert das Zeit-Format
                    Box(
                        modifier = GlanceModifier
                            .fillMaxHeight()
                            .width(80.dp)
                            .clickable(actionRunCallback<ToggleTimeDisplayAction>())
                    ) {}
                }
            }

            FavoritesRow(favorites, stationId, maxFavorites, maxFavRows)
            Footer(lastUpdated, isStale)
        }
    }
}

    @Composable
    private fun BackgroundIcon(tabState: String) {
        if (tabState == "ALL") return // Nur bei spezifischer Wahl anzeigen

        val iconRes = when (tabState) {
            "BUS" -> R.drawable.ic_widget_bus
            "TRAIN" -> R.drawable.ic_widget_tram
            else -> return
        }

        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                modifier = GlanceModifier.size(130.dp), 
                colorFilter = ColorFilter.tint(ColorProvider(Color(0xFF141F14))) // Ganz dezentes Dunkelgrün
            )
        }
    }

    @Composable
    private fun Header(stationName: String, gpsModeActive: Boolean, isRefreshing: Boolean) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val cleanName = stationName
                .replace("Hannover", "", ignoreCase = true)
                .replace("Landeshauptstadt", "", ignoreCase = true)
                .replace(Regex("[,/()]+"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")
                
            val displayName = if (gpsModeActive) "📍 $cleanName" else cleanName
            
            Text(
                text = displayName,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight().clickable(actionRunCallback<ChangeStationAction>())
            )
            
            val gpsIconColor = if (gpsModeActive) Color(0xFF4285F4) else Color.Gray

            Image(
                provider = ImageProvider(android.R.drawable.ic_menu_mylocation),
                contentDescription = "GPS Nearest Station",
                modifier = GlanceModifier.padding(end = 8.dp).clickable(actionRunCallback<LocateNearestStationAction>()),
                colorFilter = ColorFilter.tint(ColorProvider(gpsIconColor))
            )

            if (isRefreshing) {
                CircularProgressIndicator(
                    color = ColorProvider(Color(0xFF4285F4)),
                    modifier = GlanceModifier.size(24.dp)
                )
            } else {
                Image(
                    provider = ImageProvider(android.R.drawable.ic_popup_sync),
                    contentDescription = "Refresh",
                    modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>(
                        actionParametersOf(RefreshAction.KEY_FORCE to true)
                    )),
                    colorFilter = ColorFilter.tint(ColorProvider(Color.Gray))
                )
            }
        }
    }

    @Composable
    private fun FavoritesRow(favorites: List<FavoriteStation>, currentStationId: String, maxFavorites: Int, maxFavRows: Int) {
        if (favorites.isEmpty() || maxFavorites <= 0 || maxFavRows <= 0) return

        Column(modifier = GlanceModifier.fillMaxWidth()) {
            val maxVisible = maxFavorites * maxFavRows
            val needsCycle = favorites.size > maxVisible
            val totalNormalButtons = if (needsCycle) maxVisible else favorites.size

            for (rowIndex in 0 until maxFavRows) {
                val startIndex = rowIndex * maxFavorites
                if (startIndex >= totalNormalButtons) break

                val endIndex = minOf(startIndex + maxFavorites, totalNormalButtons)

                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in startIndex until endIndex) {
                        val fav = favorites[i]
                        val label = fav.alias
                            ?: fav.name
                                .replace("Hannover", "", ignoreCase = true)
                                .replace(Regex("[,/()]+"), " ")
                                .trim()
                                .split(" ")
                                .firstOrNull() ?: fav.name
                        val shortLabel = if (label.length > 8) label.take(7) + "." else label
                        val isActive = fav.id == currentStationId
                        val bgColor = if (isActive) Color(0xFF005A9B) else Color(0xFF2A2A2A)

                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .padding(horizontal = 2.dp)
                                .cornerRadius(6.dp)
                                .background(ColorProvider(bgColor))
                                .clickable(actionRunCallback<ChangeStationAction>(
                                    actionParametersOf(ChangeStationAction.KEY_TARGET_INDEX to i)
                                )),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = shortLabel,
                                style = TextStyle(
                                    color = ColorProvider(Color.White),
                                    fontSize = 11.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                ),
                                maxLines = 1,
                                modifier = GlanceModifier.padding(vertical = 3.dp)
                            )
                        }
                    }

                    val isLastRow = rowIndex == maxFavRows - 1
                    if (isLastRow && needsCycle) {
                        val isRemainingActive = favorites.drop(maxVisible).any { it.id == currentStationId }
                        val bgColor = if (isRemainingActive) Color(0xFF005A9B) else Color(0xFF2A2A2A)
                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .padding(horizontal = 2.dp)
                                .cornerRadius(6.dp)
                                .background(ColorProvider(bgColor))
                                .clickable(actionRunCallback<ChangeStationAction>(
                                    actionParametersOf(ChangeStationAction.KEY_CYCLE_REMAINING to true)
                                )),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "▶",
                                style = TextStyle(color = ColorProvider(Color.White), fontSize = 11.sp),
                                modifier = GlanceModifier.padding(vertical = 3.dp)
                            )
                        }
                    } else if (endIndex - startIndex < maxFavorites) {
                        val missing = maxFavorites - (endIndex - startIndex)
                        for (m in 0 until missing) {
                            Box(modifier = GlanceModifier.defaultWeight().padding(horizontal = 2.dp)) {}
                        }
                    }
                }
            }
        }
    }


    @Composable
    private fun FilterToggleButton(tabState: String) {
        val (label, bgColor) = when (tabState) {
            "BUS" -> "Nur Bus" to Color(0xFFE94560)
            "TRAIN" -> "Nur Bahn" to Color(0xFF0F7173)
            else -> "Alle Typen" to Color(0xFF333333)
        }
        
        Box(
            modifier = GlanceModifier
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .background(ColorProvider(bgColor))
                .clickable(actionRunCallback<ChangeTabAction>()),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }

    @Composable
    private fun FlatDepartureRow(departure: FlatDeparture, timeDisplayMode: String, isWarning: Boolean) {
        val rowBgColor = when {
            departure.lineId?.endsWith("H", ignoreCase = true) == true -> Color(0x14FFFFFF)
            departure.lineId?.endsWith("R", ignoreCase = true) == true -> Color(0x4D000000)
            else -> Color.Transparent
        }

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
                .cornerRadius(6.dp)
                .background(ColorProvider(rowBgColor))
                .padding(vertical = 2.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LineBadge(departure.lineShort, departure.isBus)
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = departure.destination ?: "Unbekannt",
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp),
                modifier = GlanceModifier.defaultWeight()
            )
            val timeText = if (timeDisplayMode == "CLOCK") {
                clockTime(departure.departureTime)
            } else {
                minutesUntil(departure.departureTime, isWarning)
            }

            val hasDelay = (departure.delayMinutes ?: 0) > 0
            val delayText = if (hasDelay) " (+${departure.delayMinutes})" else ""

            val timeStyle = when {
                isWarning -> TextStyle(color = ColorProvider(Color.Gray), fontSize = 14.sp, fontStyle = FontStyle.Italic)
                hasDelay -> TextStyle(color = ColorProvider(Color(0xFFFF9800)), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                else -> TextStyle(color = ColorProvider(Color(0xFF4CAF50)), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Text(
                text = timeText + delayText,
                style = timeStyle
            )
        }
    }

    @Composable
    private fun DepartureRow(departure: DepartureItem, timeDisplayMode: String, isWarning: Boolean) {
        val rowBgColor = when {
            // "City" (H) aufhellen: Sehr sanftes, transluzentes Weiß (ca. 8% Deckkraft)
            departure.lineId?.endsWith("H", ignoreCase = true) == true -> Color(0x14FFFFFF) 
            // "Home" (R) abdunkeln: Zartes, dunkles Schwarz (ca. 30% Deckkraft macht das Dunkelgrau zu Tiefschwarz)
            departure.lineId?.endsWith("R", ignoreCase = true) == true -> Color(0x4D000000) 
            else -> Color.Transparent
        }

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
                .cornerRadius(6.dp)
                .background(ColorProvider(rowBgColor))
                .padding(vertical = 2.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LineBadge(departure.lineShort, departure.isBus)
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = departure.destination ?: "Unbekannt",
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp),
                modifier = GlanceModifier.defaultWeight()
            )
            val timeText = if (timeDisplayMode == "CLOCK") {
                clockTime(departure.nextDepartureTime)
            } else {
                minutesUntil(departure.nextDepartureTime, isWarning)
            }
            
            val hasDelay = (departure.delayMinutes ?: 0) > 0
            val delayText = if (hasDelay) " (+${departure.delayMinutes})" else ""
            
            // Wenn ab 2 Min veraltet: Grau und Kursiv. Wenn Verspätung: Orange. Sonst Gruen und Fett
            val timeStyle = when {
                isWarning -> TextStyle(color = ColorProvider(Color.Gray), fontSize = 14.sp, fontStyle = FontStyle.Italic)
                hasDelay -> TextStyle(color = ColorProvider(Color(0xFFFF9800)), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                else -> TextStyle(color = ColorProvider(Color(0xFF4CAF50)), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Text(
                text = timeText + delayText,
                style = timeStyle
            )
        }
    }

    @Composable
    private fun LineBadge(line: String, isBus: Boolean) {
        val (bgColor, textColor) = if (isBus) {
            val isSprintH = line.length == 3 && line[0] in '3'..'9' && line.substring(1) == "00"
            // sprintH Linien (300, 400, 500, 600, 700, 800, 900) sind Magenta
            if (isSprintH) {
                Color(0xFFB42082) to Color.White
            } else {
                Color(0xFFE3001B) to Color.White // Standard ÜSTRA-Rot für andere Busse
            }
        } else {
            when (line) {
                "1", "2", "8" -> Color(0xFFE3001B) to Color.White     // B-Strecke (Rot)
                "3", "7", "9", "13" -> Color(0xFF005A9B) to Color.White // A-Strecke (Blau)
                "4", "5", "6", "11" -> Color(0xFFFFCC00) to Color.Black // C-Strecke (Gelb)
                "10", "17" -> Color(0xFF009A44) to Color.White    // D-Strecke (Grün)
                else -> Color.Gray to Color.White
            }
        }

        Box(
            modifier = GlanceModifier
                .width(34.dp).height(24.dp)
                .background(ColorProvider(bgColor)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = line,
                style = TextStyle(
                    color = ColorProvider(textColor),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.padding(horizontal = 2.dp)
            )
        }
    }

    @Composable
    private fun FilterSegmentedRow(departures: List<DepartureItem>, tabState: String, directionState: String) {
        val hasH = departures.any { it.lineId?.endsWith("H") == true }
        val hasR = departures.any { it.lineId?.endsWith("R") == true }
        val showDirectionGroup = hasH || hasR
        val showAllToggle = hasH && hasR

        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- Gruppe VEHICLE (Links-bündig) ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                SegmentButton(R.drawable.ic_widget_bus, null, tabState == "BUS", Color(0xFFE94560)) { 
                    actionRunCallback<ChangeTabAction>(actionParametersOf(ChangeTabAction.KEY_TAB to "BUS")) 
                }
                Spacer(modifier = GlanceModifier.width(2.dp))
                SegmentButton(R.drawable.ic_widget_tram, null, tabState == "TRAIN", Color(0xFF005A9B)) { 
                    actionRunCallback<ChangeTabAction>(actionParametersOf(ChangeTabAction.KEY_TAB to "TRAIN")) 
                }
                Spacer(modifier = GlanceModifier.width(2.dp))
                SegmentButton(R.drawable.ic_widget_all, null, tabState == "ALL", Color(0xFF555555)) { 
                    actionRunCallback<ChangeTabAction>(actionParametersOf(ChangeTabAction.KEY_TAB to "ALL")) 
                }
            }

            // --- PLATZHALTER (drückt die nächste Gruppe nach rechts) ---
            Spacer(modifier = GlanceModifier.defaultWeight())

            // --- Gruppe DIRECTION (Rechts-bündig, dynamisch) ---
            if (showDirectionGroup) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasH) {
                        SegmentButton(R.drawable.ic_widget_city, null, directionState == "H", Color(0xFF0F7173)) { 
                            actionRunCallback<ChangeDirectionAction>(actionParametersOf(ChangeDirectionAction.KEY_DIRECTION to "H")) 
                        }
                        Spacer(modifier = GlanceModifier.width(2.dp))
                    }
                    if (hasR) {
                        SegmentButton(R.drawable.ic_widget_home, null, directionState == "R", Color(0xFFE94560)) { 
                            actionRunCallback<ChangeDirectionAction>(actionParametersOf(ChangeDirectionAction.KEY_DIRECTION to "R")) 
                        }
                        Spacer(modifier = GlanceModifier.width(2.dp))
                    }
                    if (showAllToggle) {
                        SegmentButton(R.drawable.ic_widget_swap, null, directionState == "ALL", Color(0xFF555555)) { 
                            actionRunCallback<ChangeDirectionAction>(actionParametersOf(ChangeDirectionAction.KEY_DIRECTION to "ALL")) 
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SegmentButton(
        iconRes: Int?, 
        text: String?, 
        isActive: Boolean, 
        activeColor: Color,
        onClick: () -> androidx.glance.action.Action
    ) {
        val bgColor = if (isActive) activeColor else Color(0xFF252525)
        val contentColor = if (isActive) Color.White else Color.Gray

        Box(
            modifier = GlanceModifier
                .cornerRadius(6.dp)
                .background(ColorProvider(bgColor))
                .clickable(onClick())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (iconRes != null) {
                Image(
                    provider = ImageProvider(iconRes),
                    contentDescription = text ?: "",
                    modifier = GlanceModifier.size(20.dp),
                    colorFilter = ColorFilter.tint(ColorProvider(contentColor))
                )
            } else if (text != null) {
                Text(
                    text = text,
                    style = TextStyle(color = ColorProvider(contentColor), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }

    @Composable
    private fun Footer(lastUpdated: String, isStale: Boolean) {
        val timeStr = if (lastUpdated.isNotEmpty()) {
            val instant = Instant.ofEpochMilli(lastUpdated.toLong())
            java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault()).format(instant)
        } else "--:--"

        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val footerText = if (isStale) "Stand: $timeStr (Veraltet ⚠️)" else "Stand: $timeStr"
            val footerColor = if (isStale) Color(0xFFFF9800) else Color.Gray

            Text(
                text = footerText,
                style = TextStyle(color = ColorProvider(footerColor), fontSize = 10.sp),
                modifier = GlanceModifier.defaultWeight()
            )
            
            Text(
                text = "Inoffiziell",
                style = TextStyle(color = ColorProvider(Color.Gray), fontSize = 10.sp),
                modifier = GlanceModifier.padding(start = 4.dp)
            )
        }
    }

    private fun minutesUntil(isoTime: String?, isWarning: Boolean): String {
        if (isoTime == null) return "?"
        return try {
            val eventTime = Instant.parse(isoTime)
            val now = Instant.now()
            val diffMinutes = Duration.between(now, eventTime).toMinutes()
            when {
                diffMinutes <= 0 -> if (isWarning) "evtl. weg?" else "jetzt"
                diffMinutes == 1L -> "1 Min"
                else -> "$diffMinutes Min"
            }
        } catch (e: DateTimeParseException) { "?" }
    }

    private fun clockTime(isoTime: String?): String {
        if (isoTime == null) return "?"
        return try {
            val instant = Instant.parse(isoTime)
            java.time.format.DateTimeFormatter
                .ofPattern("HH:mm")
                .withZone(java.time.ZoneId.systemDefault())
                .format(instant)
        } catch (e: DateTimeParseException) { "?" }
    }
}

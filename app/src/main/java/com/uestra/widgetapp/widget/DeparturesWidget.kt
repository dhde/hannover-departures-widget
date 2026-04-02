package com.uestra.widgetapp.widget

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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uestra.widgetapp.api.DepartureItem
import com.uestra.widgetapp.api.UestraApi
import com.uestra.widgetapp.data.FavoritesRepository
import com.uestra.widgetapp.data.DeparturesCache
import com.uestra.widgetapp.widget.RefreshAction
import com.uestra.widgetapp.widget.ChangeTabAction
import com.uestra.widgetapp.widget.ChangeStationAction
import com.uestra.widgetapp.widget.ChangeDirectionAction
import com.uestra.widgetapp.widget.LocateNearestStationAction
import com.uestra.widgetapp.widget.ToggleTimeDisplayAction
import java.time.Instant
import java.time.format.DateTimeParseException
import java.time.Duration

class DeparturesWidget : GlanceAppWidget() {

    // Wir verzichten komplett auf GlanceStateDefinition, um den 
    // Bug-behafteten updateAppWidgetState-Mechanismus zu umgehen.

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val cache = DeparturesCache(context)

        provideContent {
            val stationId by cache.getStationIdFlow().collectAsState(initial = "")
            val tabState by cache.getTabStateFlow(stationId).collectAsState(initial = "ALL")
            val directionState by cache.getDirectionStateFlow(stationId).collectAsState(initial = "ALL")
            val gpsModeActive by cache.getGpsModeFlow().collectAsState(initial = false)
            val timeDisplayMode by cache.getTimeDisplayModeFlow().collectAsState(initial = "MIN")
            val departuresJson by cache.getDeparturesJsonFlow().collectAsState(initial = "[]")
            val lastUpdated by cache.getLastUpdatedFlow().collectAsState(initial = "")
            
            val repo = FavoritesRepository(context)
            val stationName by repo.activeStationName.collectAsState(initial = stationId)
            
            val status = "ok"    // In Zukunft via Cache

            val departures: List<DepartureItem> = try {
                Gson().fromJson(departuresJson, object : TypeToken<List<DepartureItem>>() {}.type)
            } catch (e: Exception) {
                emptyList()
            }

            WidgetContent(
                stationName     = stationName ?: stationId,
                lastUpdated     = lastUpdated,
                departures      = departures,
                tabState        = tabState,
                directionState  = directionState,
                gpsModeActive   = gpsModeActive,
                timeDisplayMode = timeDisplayMode,
                status          = status
            )
        }
    }

    @Composable
    private fun WidgetContent(
        stationName: String,
        lastUpdated: String,
        departures: List<DepartureItem>,
        tabState: String,
        directionState: String,
        gpsModeActive: Boolean,
        timeDisplayMode: String,
        status: String
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFF121212)))
                .padding(8.dp)
        ) {
            Header(stationName, gpsModeActive)
            
            FilterSegmentedRow(departures, tabState, directionState)

            if (status.startsWith("error")) {
                Text("Fehler: $status", style = TextStyle(color = ColorProvider(Color.Red), fontSize = 12.sp))
            }

            val minutesSinceUpdate = if (lastUpdated.isNotEmpty()) {
                val lastTime = Instant.ofEpochMilli(lastUpdated.toLong())
                Duration.between(lastTime, Instant.now()).toMinutes()
            } else 0L

            val isStale = minutesSinceUpdate >= 5
            val isWarning = minutesSinceUpdate >= 2


            val filtered = departures.filter { 
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

            Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                if (filtered.isEmpty()) {
                    Text("Keine Abfahrten", style = TextStyle(color = ColorProvider(Color.Gray)))
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(filtered) { departure ->
                            DepartureRow(departure, timeDisplayMode, isWarning)
                        }
                    }
                }
            }

            Footer(lastUpdated, isStale)
        }
    }

    @Composable
    private fun Header(stationName: String, gpsModeActive: Boolean) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
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
                modifier = GlanceModifier.defaultWeight().clickable(actionRunCallback<ChangeStationAction>())
            )
            
            val gpsIconColor = if (gpsModeActive) Color(0xFF4285F4) else Color.Gray

            Image(
                provider = ImageProvider(android.R.drawable.ic_menu_mylocation),
                contentDescription = "GPS Nearest Station",
                modifier = GlanceModifier.padding(horizontal = 8.dp).clickable(actionRunCallback<LocateNearestStationAction>()),
                colorFilter = ColorFilter.tint(ColorProvider(gpsIconColor))
            )

            Image(
                provider = ImageProvider(android.R.drawable.ic_popup_sync),
                contentDescription = "Refresh",
                modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>()),
                colorFilter = ColorFilter.tint(ColorProvider(Color.Gray))
            )
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
    private fun DepartureRow(departure: DepartureItem, timeDisplayMode: String, isWarning: Boolean) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
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
                style = timeStyle,
                modifier = GlanceModifier.clickable(actionRunCallback<ToggleTimeDisplayAction>())
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
                SegmentButton("🚌", null, tabState == "BUS", Color(0xFFE94560)) { 
                    actionRunCallback<ChangeTabAction>(actionParametersOf(ChangeTabAction.KEY_TAB to "BUS")) 
                }
                Spacer(modifier = GlanceModifier.width(2.dp))
                SegmentButton("🚋", null, tabState == "TRAIN", Color(0xFF005A9B)) { 
                    actionRunCallback<ChangeTabAction>(actionParametersOf(ChangeTabAction.KEY_TAB to "TRAIN")) 
                }
                Spacer(modifier = GlanceModifier.width(2.dp))
                SegmentButton(null, "ALLE", tabState == "ALL", Color(0xFF555555)) { 
                    actionRunCallback<ChangeTabAction>(actionParametersOf(ChangeTabAction.KEY_TAB to "ALL")) 
                }
            }

            // --- PLATZHALTER (drückt die nächste Gruppe nach rechts) ---
            Spacer(modifier = GlanceModifier.defaultWeight())

            // --- Gruppe DIRECTION (Rechts-bündig, dynamisch) ---
            if (showDirectionGroup) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasH) {
                        SegmentButton("🏙️", null, directionState == "H", Color(0xFF0F7173)) { 
                            actionRunCallback<ChangeDirectionAction>(actionParametersOf(ChangeDirectionAction.KEY_DIRECTION to "H")) 
                        }
                        Spacer(modifier = GlanceModifier.width(2.dp))
                    }
                    if (hasR) {
                        SegmentButton("🏡", null, directionState == "R", Color(0xFFE94560)) { 
                            actionRunCallback<ChangeDirectionAction>(actionParametersOf(ChangeDirectionAction.KEY_DIRECTION to "R")) 
                        }
                        Spacer(modifier = GlanceModifier.width(2.dp))
                    }
                    if (showAllToggle) {
                        SegmentButton("↔", null, directionState == "ALL", Color(0xFF555555)) { 
                            actionRunCallback<ChangeDirectionAction>(actionParametersOf(ChangeDirectionAction.KEY_DIRECTION to "ALL")) 
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SegmentButton(
        icon: String?, 
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
                .clickable(onClick()),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Text(
                    text = icon,
                    style = TextStyle(fontSize = 14.sp),
                    modifier = GlanceModifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            } else if (text != null) {
                Text(
                    text = text,
                    style = TextStyle(color = ColorProvider(contentColor), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.padding(horizontal = 6.dp, vertical = 4.dp)
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
                text = "Inoffizielles Widget",
                style = TextStyle(color = ColorProvider(Color.Gray.copy(alpha = 0.5f)), fontSize = 8.sp),
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

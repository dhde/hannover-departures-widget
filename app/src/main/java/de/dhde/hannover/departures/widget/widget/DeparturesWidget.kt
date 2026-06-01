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
import androidx.glance.appwidget.action.actionStartActivity
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
import de.dhde.hannover.departures.widget.data.DirectionFilter
import de.dhde.hannover.departures.widget.data.FavoriteStation
import de.dhde.hannover.departures.widget.data.FavoritesRepository
import de.dhde.hannover.departures.widget.data.DeparturesCache
import de.dhde.hannover.departures.widget.data.FilterStateStore
import de.dhde.hannover.departures.widget.data.WidgetSessionStore
import de.dhde.hannover.departures.widget.data.TransportFilter
import de.dhde.hannover.departures.widget.data.filterMessages
import de.dhde.hannover.departures.widget.data.lineDirection
import de.dhde.hannover.departures.widget.ui.UestraColors
import de.dhde.hannover.departures.widget.data.DEFAULT_STATION_ID
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
        val filters = FilterStateStore(context)
        val session = WidgetSessionStore(context)
        val repo = FavoritesRepository(context)

        provideContent {
            val stationIdState by repo.activeStationId.collectAsState(initial = DEFAULT_STATION_ID)
            val stationId = stationIdState ?: DEFAULT_STATION_ID

            val maxFavorites by repo.maxFavoritesFlow.collectAsState(initial = 3)
            val maxFavRows by repo.maxFavRowsFlow.collectAsState(initial = 1)
            val maxRows by repo.maxRowsFlow.collectAsState(initial = 10)

            val favorites by repo.favoritesFlow.collectAsState(initial = emptyList())

            val stationNameState by repo.effectiveStationName.collectAsState(initial = "Laden...")
            val stationName = stationNameState
            val activeFavUniqueId by repo.activeFavoriteUniqueId.collectAsState(initial = null)

            val tabState by filters.getTabStateFlow(stationId).collectAsState(initial = TransportFilter.ALL)
            val directionState by filters.getDirectionStateFlow(stationId).collectAsState(initial = DirectionFilter.ALL)
            val gpsModeActive by session.getGpsModeFlow().collectAsState(initial = false)
            val timeDisplayMode by session.getTimeDisplayModeFlow().collectAsState(initial = "MIN")

            val cachedJson by cache.getDeparturesJsonFlow(stationId).collectAsState(initial = "[]")
            val lastUpdated by cache.getLastUpdatedFlow(stationId).collectAsState(initial = "")

            val errorState by session.getErrorStateFlow().collectAsState(initial = "")
            val transportFilters by repo.transportTypesFlow.collectAsState(initial = setOf("Stadtbahn", "Bus", "S-Bahn"))
            val ignoredMessages by repo.ignoredMessagesFlow.collectAsState(initial = emptySet())
            val favoritesHeight by repo.favoritesHeightFlow.collectAsState(initial = "STANDARD")
            val filterHeight by repo.filterHeightFlow.collectAsState(initial = "STANDARD")
            val groupDepartures by repo.groupDeparturesFlow.collectAsState(initial = true)
            val maxGroupedDepartures by repo.maxGroupedDeparturesFlow.collectAsState(initial = 2)
            val groupedFontSize by repo.groupedFontSizeFlow.collectAsState(initial = "STANDARD")

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

            val isRefreshing by session.isRefreshingFlow().collectAsState(initial = false)

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
                transportFilters = transportFilters,
                ignoredMessages  = ignoredMessages,
                favoritesHeight  = favoritesHeight,
                filterHeight     = filterHeight,
                groupDepartures  = groupDepartures,
                maxGroupedDepartures = maxGroupedDepartures,
                groupedFontSize  = groupedFontSize,
                activeFavUniqueId = activeFavUniqueId
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
        tabState: TransportFilter,
        directionState: DirectionFilter,
        gpsModeActive: Boolean,
        timeDisplayMode: String,
        status: String,
        errorMsg: String,
        isRefreshing: Boolean,
        maxFavorites: Int,
        maxFavRows: Int,
        maxRows: Int,
        transportFilters: Set<String>,
        ignoredMessages: Set<String>,
        favoritesHeight: String,
        filterHeight: String,
        groupDepartures: Boolean,
        maxGroupedDepartures: Int,
        groupedFontSize: String = "STANDARD",
        activeFavUniqueId: String? = null
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(ColorProvider(UestraColors.WidgetBackground))
        ) {
            // --- Hintergrund-Icon (Subtil) ---
            BackgroundIcon(tabState)

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
            val filtered = flatDepartures.filter {
                val isAnyTypeExcluded = transportFilters.size < 5
                val globalTypeMatch = when {
                    it.isFernbus -> "Fernbus" in transportFilters
                    it.isDB -> "DB" in transportFilters
                    it.isBus -> "Bus" in transportFilters
                    it.isTram -> "Stadtbahn" in transportFilters
                    it.isSBahn -> "S-Bahn" in transportFilters
                    else -> !isAnyTypeExcluded
                }
                if (!globalTypeMatch) return@filter false

                // Stations-spezifischer Linien-Filter (nach uniqueId des aktiven Duplikats)
                val currentFav = favorites.find { fav -> fav.safeUniqueId == activeFavUniqueId }
                val linesFilter = currentFav?.filteredLines
                if (linesFilter != null && it.lineShort !in linesFilter) return@filter false

                val typeMatch = when (tabState) {
                    TransportFilter.BUS -> it.isBus
                    TransportFilter.TRAM -> it.isTram
                    else -> true
                }
                val dirMatch = when (directionState) {
                    DirectionFilter.INBOUND -> lineDirection(it.lineId) == DirectionFilter.INBOUND
                    DirectionFilter.OUTBOUND -> lineDirection(it.lineId) == DirectionFilter.OUTBOUND
                    else -> true
                }
                typeMatch && dirMatch
            }

            val messagesDeps = filtered.map { dep ->
                dep.copy(messages = filterMessages(dep.messages, ignoredMessages))
            }.filter { it.messages.isNotEmpty() }
            val hasMessages = messagesDeps.isNotEmpty()
            val groupedMessagesMap = if (hasMessages) {
                messagesDeps.groupBy { it.lineShort }
                    .map { (line, deps) -> listOf(line, deps.flatMap { it.messages }.distinct()) }
            } else emptyList()
            val groupedMessages = groupedMessagesMap
                .joinToString("\n\n") { item ->
                    "Linie ${item[0]}:\n" + (item[1] as List<*>).joinToString("\n")
                }
            val groupedMessagesJson = com.google.gson.Gson().toJson(groupedMessagesMap)

            Header(stationName, gpsModeActive, isRefreshing, hasMessages, groupedMessages, groupedMessagesJson)
            
            FilterSegmentedRow(departures, tabState, directionState, filterHeight)

            if (status == "error" && errorMsg.isNotEmpty()) {
                Text("⚠️ $errorMsg", style = TextStyle(color = ColorProvider(UestraColors.Warning), fontSize = 12.sp, fontWeight = FontWeight.Medium), modifier = GlanceModifier.padding(vertical = 4.dp))
            }

            val minutesSinceUpdate = if (lastUpdated.isNotEmpty()) {
                val lastTime = Instant.ofEpochMilli(lastUpdated.toLong())
                Duration.between(lastTime, Instant.now()).toMinutes()
            } else 0L

            val isStale = minutesSinceUpdate >= 5
            val isWarning = minutesSinceUpdate >= 10

            Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.CenterEnd) {
                // Gruppieren der Abfahrten
                val groupedList = mutableListOf<Pair<FlatDeparture, List<FlatDeparture>>>()
                if (groupDepartures) {
                    val groupedMap = mutableMapOf<String, MutableList<FlatDeparture>>()
                    for (dep in filtered) {
                        val key = "${dep.lineShort}|${dep.destination}"
                        groupedMap.getOrPut(key) { mutableListOf() }.add(dep)
                    }
                    for (group in groupedMap.values) {
                        val mainDep = group.first()
                        val subDeps = group.drop(1).take(maxGroupedDepartures)
                        groupedList.add(mainDep to subDeps)
                    }
                    // WICHTIG: Nach der Gruppierung wieder chronologisch sortieren!
                    groupedList.sortBy { it.first.departureTime }
                } else {
                    for (dep in filtered) {
                        groupedList.add(dep to emptyList())
                    }
                }

                val limitedFiltered = if (maxRows >= 15) groupedList else groupedList.take(maxRows)
                
                if (limitedFiltered.isEmpty()) {
                    Text("Keine Abfahrten", style = TextStyle(color = ColorProvider(Color.Gray)))
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(limitedFiltered) { (departure, subsequent) ->
                            FlatDepartureRow(departure, subsequent, timeDisplayMode, isWarning, groupedFontSize)
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

            FavoritesRow(favorites, activeFavUniqueId ?: stationId, maxFavorites, maxFavRows, favoritesHeight)
            Footer(lastUpdated, isStale)
        }
    }
}

    @Composable
    private fun BackgroundIcon(tabState: TransportFilter) {
        if (tabState == TransportFilter.ALL) return // Nur bei spezifischer Wahl anzeigen

        val iconRes = when (tabState) {
            TransportFilter.BUS -> R.drawable.ic_widget_bus
            TransportFilter.TRAM -> R.drawable.ic_widget_tram
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
                colorFilter = ColorFilter.tint(ColorProvider(UestraColors.DarkGreenTint)) // Ganz dezentes Dunkelgrün
            )
        }
    }

    @Composable
    private fun Header(stationName: String, gpsModeActive: Boolean, isRefreshing: Boolean, hasMessages: Boolean = false, groupedMessages: String = "", groupedMessagesJson: String = "") {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasMessages) {
                val intent = Intent(LocalContext.current, de.dhde.hannover.departures.widget.MainActivity::class.java).apply {
                    action = "de.dhde.hannover.departures.SHOW_INFO"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("info_title", "Meldungen: $stationName")
                    putExtra("info_msgs", groupedMessages)
                    putExtra("info_msgs_json", groupedMessagesJson)
                }
                Box(
                    modifier = GlanceModifier
                        .size(36.dp)
                        .clickable(actionStartActivity(intent)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Image(
                        provider = ImageProvider(android.R.drawable.ic_dialog_info),
                        contentDescription = "Meldungen",
                        modifier = GlanceModifier.size(20.dp),
                        colorFilter = ColorFilter.tint(ColorProvider(UestraColors.Amber))
                    )
                }
            }
            val cleanName = stationName
                .replace(Regex("\\b(Hannover|Landeshauptstadt)\\b", RegexOption.IGNORE_CASE), "")
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
            
            val gpsIconColor = if (gpsModeActive) UestraColors.GpsBlue else Color.Gray

            Image(
                provider = ImageProvider(android.R.drawable.ic_menu_mylocation),
                contentDescription = "GPS Nearest Station",
                modifier = GlanceModifier.padding(end = 8.dp).clickable(actionRunCallback<LocateNearestStationAction>()),
                colorFilter = ColorFilter.tint(ColorProvider(gpsIconColor))
            )

            if (isRefreshing) {
                CircularProgressIndicator(
                    color = ColorProvider(UestraColors.GpsBlue),
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
    private fun FavoritesRow(favorites: List<FavoriteStation>, currentStationId: String, maxFavorites: Int, maxFavRows: Int, favoritesHeight: String = "STANDARD") {
        if (favorites.isEmpty() || maxFavorites <= 0 || maxFavRows <= 0) return

        val (vertPadding, fontSize) = when (favoritesHeight) {
            "KOMPAKT" -> 1.dp to 10.sp
            "GROSS" -> 7.dp to 13.sp
            else -> 3.dp to 11.sp
        }

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
                                .replace(Regex("\\bHannover\\b", RegexOption.IGNORE_CASE), "")
                                .replace(Regex("[,/()]+"), " ")
                                .trim()
                                .split(" ")
                                .firstOrNull() ?: fav.name
                        val shortLabel = if (label.length > 8) label.take(7) + "." else label
                        val isActive = fav.safeUniqueId == currentStationId
                        val bgColor = if (isActive) UestraColors.LineBlue else UestraColors.ChipInactive

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
                                    fontSize = fontSize,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                ),
                                maxLines = 1,
                                modifier = GlanceModifier.padding(vertical = vertPadding)
                            )
                        }
                    }

                    val isLastRow = rowIndex == maxFavRows - 1
                    if (isLastRow && needsCycle) {
                        val isRemainingActive = favorites.drop(maxVisible).any { it.safeUniqueId == currentStationId }
                        val bgColor = if (isRemainingActive) UestraColors.LineBlue else UestraColors.ChipInactive
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
                                style = TextStyle(color = ColorProvider(Color.White), fontSize = fontSize),
                                modifier = GlanceModifier.padding(vertical = vertPadding)
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
    private fun FilterToggleButton(tabState: TransportFilter) {
        val (label, bgColor) = when (tabState) {
            TransportFilter.BUS -> "Nur Bus" to UestraColors.AccentRed
            TransportFilter.TRAM -> "Nur Bahn" to UestraColors.Teal
            else -> "Alle Typen" to UestraColors.ChipNeutral
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
    private fun FlatDepartureRow(departure: FlatDeparture, subsequentDepartures: List<FlatDeparture>, timeDisplayMode: String, isWarning: Boolean, groupedFontSize: String = "STANDARD") {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
                .cornerRadius(6.dp)
                .padding(vertical = 2.dp, horizontal = 6.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
            LineBadge(departure.lineShort, departure.isBus)
            Spacer(modifier = GlanceModifier.width(8.dp))
            val dir = lineDirection(departure.lineId)
            if (dir != null) {
                Image(
                    provider = ImageProvider(
                        if (dir == DirectionFilter.INBOUND) R.drawable.ic_widget_city else R.drawable.ic_widget_home
                    ),
                    contentDescription = null,
                    modifier = GlanceModifier.size(16.dp),
                    colorFilter = ColorFilter.tint(ColorProvider(
                        if (dir == DirectionFilter.INBOUND) UestraColors.Teal else UestraColors.AccentRed
                    ))
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
            }
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

            var finalTimeText = timeText + delayText
            if (departure.isCancelled) {
                finalTimeText = finalTimeText.map { it + "\u0336" }.joinToString("")
            }

            val timeStyle = when {
                isWarning -> TextStyle(color = ColorProvider(Color.Gray), fontSize = 14.sp, fontStyle = FontStyle.Italic)
                departure.isCancelled -> TextStyle(color = ColorProvider(UestraColors.AccentRed), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                hasDelay -> TextStyle(color = ColorProvider(UestraColors.Warning), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                else -> TextStyle(color = ColorProvider(UestraColors.OkGreen), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = finalTimeText,
                    style = timeStyle
                )
                if (subsequentDepartures.isNotEmpty()) {
                    val subFontSize = when (groupedFontSize) {
                        "KLEIN"  -> 9.sp
                        "GROSS"  -> 13.sp
                        else     -> 10.sp
                    }
                    val subText = subsequentDepartures.joinToString(", ") { dep ->
                        // Keine Verspätung in der Gruppen-Kurzansicht
                        val t = if (timeDisplayMode == "CLOCK") clockTime(dep.departureTime) else minutesUntil(dep.departureTime, isWarning).replace(" min", "m").replace("jetzt", "0m").replace("in ", "")
                        var res = t
                        if (dep.isCancelled) res = res.map { it + "\u0336" }.joinToString("")
                        res
                    }
                    Text(
                        text = subText,
                        style = TextStyle(color = ColorProvider(UestraColors.TextSub), fontSize = subFontSize, fontWeight = FontWeight.Normal)
                    )
                }
            }
        }
        if (departure.isCancelled) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(start = 42.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fahrt entfällt",
                    style = TextStyle(color = ColorProvider(UestraColors.AccentRed), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.padding(end = 8.dp)
                )
            }
        }
        }
    }

    @Composable
    private fun LineBadge(line: String, isBus: Boolean) {
        val (bgColor, textColor) = if (isBus) {
            val isSprintH = line.length == 3 && line[0] in '3'..'9' && line.substring(1) == "00"
            // sprintH Linien (300, 400, 500, 600, 700, 800, 900) sind Magenta
            if (isSprintH) {
                UestraColors.SprintMagenta to Color.White
            } else {
                UestraColors.LineRed to Color.White // Standard ÜSTRA-Rot für andere Busse
            }
        } else {
            when (line) {
                "1", "2", "8" -> UestraColors.LineRed to Color.White     // B-Strecke (Rot)
                "3", "7", "9", "13" -> UestraColors.LineBlue to Color.White // A-Strecke (Blau)
                "4", "5", "6", "11" -> UestraColors.LineYellow to Color.Black // C-Strecke (Gelb)
                "10", "17" -> UestraColors.LineGreen to Color.White    // D-Strecke (Grün)
                else -> Color.Gray to Color.White
            }
        }

        val width = if (line.length >= 4) 42.dp else 34.dp
        Box(
            modifier = GlanceModifier
                .width(width).height(24.dp)
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
                maxLines = 1,
                modifier = GlanceModifier.padding(horizontal = 2.dp)
            )
        }
    }

    @Composable
    private fun FilterSegmentedRow(departures: List<DepartureItem>, tabState: TransportFilter, directionState: DirectionFilter, filterHeight: String = "STANDARD") {
        val hasH = departures.any { lineDirection(it.lineId) == DirectionFilter.INBOUND }
        val hasR = departures.any { lineDirection(it.lineId) == DirectionFilter.OUTBOUND }
        val showDirectionGroup = hasH || hasR

        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- Gruppe VEHICLE (Links-bündig) ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                SegmentButton(R.drawable.ic_widget_bus, null, tabState == TransportFilter.BUS, UestraColors.AccentRed, filterHeight) {
                     actionRunCallback<ChangeTabAction>(actionParametersOf(ChangeTabAction.KEY_TAB to TransportFilter.BUS.storageValue))
                }
                Spacer(modifier = GlanceModifier.width(2.dp))
                SegmentButton(R.drawable.ic_widget_tram, null, tabState == TransportFilter.TRAM, UestraColors.LineBlue, filterHeight) {
                     actionRunCallback<ChangeTabAction>(actionParametersOf(ChangeTabAction.KEY_TAB to TransportFilter.TRAM.storageValue))
                }
            }

            // --- PLATZHALTER (drückt die nächste Gruppe nach rechts) ---
            Spacer(modifier = GlanceModifier.defaultWeight())

            // --- Gruppe DIRECTION (Rechts-bündig, dynamisch) ---
            if (showDirectionGroup) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasH) {
                        SegmentButton(R.drawable.ic_widget_city, null, directionState == DirectionFilter.INBOUND, UestraColors.Teal, filterHeight) {
                            actionRunCallback<ChangeDirectionAction>(actionParametersOf(ChangeDirectionAction.KEY_DIRECTION to DirectionFilter.INBOUND.storageValue))
                        }
                        if (hasR) Spacer(modifier = GlanceModifier.width(2.dp))
                    }
                    if (hasR) {
                        SegmentButton(R.drawable.ic_widget_home, null, directionState == DirectionFilter.OUTBOUND, UestraColors.AccentRed, filterHeight) {
                            actionRunCallback<ChangeDirectionAction>(actionParametersOf(ChangeDirectionAction.KEY_DIRECTION to DirectionFilter.OUTBOUND.storageValue))
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
        filterHeight: String = "STANDARD",
        onClick: () -> androidx.glance.action.Action
    ) {
        val bgColor = if (isActive) activeColor else UestraColors.SegmentInactive
        val contentColor = if (isActive) Color.White else Color.Gray

        val (vertPadding, iconSize) = when (filterHeight) {
            "KOMPAKT" -> 2.dp to 16.dp
            "GROSS" -> 7.dp to 24.dp
            else -> 4.dp to 20.dp
        }

        Box(
            modifier = GlanceModifier
                .cornerRadius(6.dp)
                .background(ColorProvider(bgColor))
                .clickable(onClick())
                .padding(horizontal = 6.dp, vertical = vertPadding),
            contentAlignment = Alignment.Center
        ) {
            if (iconRes != null) {
                Image(
                    provider = ImageProvider(iconRes),
                    contentDescription = text ?: "",
                    modifier = GlanceModifier.size(iconSize),
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
            java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").withZone(java.time.ZoneId.systemDefault()).format(instant)
        } else "--:--:--"

        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val footerText = if (isStale) "Stand: $timeStr (Veraltet ⚠️)" else "Stand: $timeStr"
            val footerColor = if (isStale) UestraColors.Warning else Color.Gray

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

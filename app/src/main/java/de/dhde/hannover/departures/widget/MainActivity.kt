package de.dhde.hannover.departures.widget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import de.dhde.hannover.departures.widget.api.StationSearchResult
import de.dhde.hannover.departures.widget.api.UestraApi
import de.dhde.hannover.departures.widget.api.FlatDeparture
import de.dhde.hannover.departures.widget.api.toFlatRows
import de.dhde.hannover.departures.widget.data.DirectionFilter
import de.dhde.hannover.departures.widget.data.isProtectedMessage
import de.dhde.hannover.departures.widget.data.lineDirection
import de.dhde.hannover.departures.widget.data.FavoritesRepository
import de.dhde.hannover.departures.widget.data.TransportFilter
import de.dhde.hannover.departures.widget.widget.WidgetTickerWorker
import de.dhde.hannover.departures.widget.ui.UestraColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.glance.appwidget.updateAll
import kotlin.math.roundToInt

import android.content.Intent

data class InfoDialogData(val title: String, val msgs: String? = null, val groupedMsgs: List<Pair<String, List<String>>>? = null)

class MainActivity : ComponentActivity() {

    private var infoDialogState by mutableStateOf<InfoDialogData?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permission result handled silently */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handleIntent(intent)

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        val repo = FavoritesRepository(this)

        // Periodischen Widget-Ticker starten (alle 15 Min, kein API-Call)
        WidgetTickerWorker.schedule(this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary        = UestraColors.Teal,
                    secondary      = UestraColors.AccentRed,
                    background     = UestraColors.DarkBg,
                    surface        = UestraColors.CardBg,
                    onBackground   = UestraColors.TextMain,
                    onSurface      = UestraColors.TextMain,
                )
            ) {
                val scope = rememberCoroutineScope()
                val ignoredMessages by repo.ignoredMessagesFlow.collectAsState(initial = emptySet())
                
                ConfigurationScreen(repo, onInfoClick = { infoDialogState = it })
                
                infoDialogState?.let { data ->
                    Dialog(
                        onDismissRequest = { infoDialogState = null },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .fillMaxHeight(0.8f)
                                .padding(16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = UestraColors.CardBg)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(24.dp)
                            ) {
                                Text(
                                    text = data.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                val scrollState = rememberScrollState()
                                Column(
                                    modifier = Modifier.weight(1f).verticalScroll(scrollState)
                                ) {
                                    if (data.msgs != null) {
                                        Text(
                                            text = data.msgs,
                                            color = UestraColors.TextMain,
                                            fontSize = 16.sp,
                                            lineHeight = 24.sp
                                        )
                                    } else if (data.groupedMsgs != null) {
                                        data.groupedMsgs.forEach { (line, lineMsgs) ->
                                            Text(
                                                text = "Linie $line:",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                            )
                                            lineMsgs.forEach { msg ->
                                                val isProtected = isProtectedMessage(msg)
                                                val isIgnored = msg in ignoredMessages
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    Text(
                                                        text = msg,
                                                        color = if (isIgnored && !isProtected) Color.Gray else UestraColors.TextMain,
                                                        fontSize = 14.sp,
                                                        lineHeight = 20.sp,
                                                        modifier = Modifier.weight(1f).padding(top = 10.dp)
                                                    )
                                                    if (isProtected) {
                                                        Icon(
                                                            Icons.Default.Lock,
                                                            contentDescription = "immer sichtbar",
                                                            tint = UestraColors.TextSub,
                                                            modifier = Modifier.padding(top = 10.dp, start = 4.dp).size(18.dp)
                                                        )
                                                    } else {
                                                        Checkbox(
                                                            checked = isIgnored,
                                                            onCheckedChange = { checked ->
                                                                scope.launch {
                                                                    val newSet = if (checked) ignoredMessages + msg else ignoredMessages - msg
                                                                    repo.setIgnoredMessages(newSet)
                                                                }
                                                            },
                                                            colors = CheckboxDefaults.colors(checkedColor = UestraColors.Teal, uncheckedColor = UestraColors.TextSub)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { infoDialogState = null }) {
                                        Text("Zur App", color = UestraColors.TextSub, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { 
                                            infoDialogState = null
                                            this@MainActivity.finishAffinity()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = UestraColors.AccentRed)
                                    ) {
                                        Text("Verlassen", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "de.dhde.hannover.departures.SHOW_INFO") {
            val title = intent.getStringExtra("info_title") ?: "Meldungen"
            val msgsJson = intent.getStringExtra("info_msgs_json")
            if (msgsJson != null) {
                // Structured JSON: List<List<Any>> = [[line, [msg1, msg2]], ...]
                try {
                    val type = object : com.google.gson.reflect.TypeToken<List<List<Any>>>() {}.type
                    val parsed: List<List<Any>> = com.google.gson.Gson().fromJson(msgsJson, type)
                    val grouped = parsed.map { item ->
                        val line = item[0] as? String ?: ""
                        @Suppress("UNCHECKED_CAST")
                        val msgs = (item[1] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                        line to msgs
                    }
                    infoDialogState = InfoDialogData(title = title, groupedMsgs = grouped)
                } catch (e: Exception) {
                    val fallback = intent.getStringExtra("info_msgs") ?: ""
                    infoDialogState = InfoDialogData(title = title, msgs = fallback)
                }
            } else {
                val msgs = intent.getStringExtra("info_msgs") ?: ""
                if (msgs.isNotEmpty()) {
                    infoDialogState = InfoDialogData(title = title, msgs = msgs)
                }
            }
        }
    }
}

// ── Screen ───────────────────────────────────────────────────────────────────

enum class AppScreen(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DASHBOARD("Abfahrten", Icons.Default.Dashboard),
    SEARCH("Suchen", Icons.Default.Search),
    FAVORITES("Favoriten", Icons.Default.Star),
    OPTIONS("Optionen", Icons.Default.Settings),
    HELP("Hilfe", Icons.Default.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationScreen(repo: FavoritesRepository, onInfoClick: (InfoDialogData) -> Unit = {}) {
    var currentScreen by remember { mutableStateOf(AppScreen.DASHBOARD) }
    val activeStationName by repo.effectiveStationName.collectAsState(initial = "Laden...")

    Scaffold(
        containerColor = UestraColors.DarkBg,
        topBar = {
            TopAppBar(
                title = {
                Column {
                    Text(
                        "Hannover Abfahrten Stadtbahnen",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp,
                        color      = UestraColors.TextMain
                    )
                    Text(
                        text = if (currentScreen == AppScreen.DASHBOARD) "Widget Vorschau · $activeStationName" else "Aktiv: $activeStationName",
                        fontSize = 12.sp,
                        color    = if (currentScreen == AppScreen.DASHBOARD) UestraColors.Amber else UestraColors.Teal,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = UestraColors.CardBg)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = UestraColors.CardBg) {
                AppScreen.values().forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = UestraColors.Teal,
                            selectedTextColor = UestraColors.Teal,
                            unselectedIconColor = UestraColors.TextSub,
                            unselectedTextColor = UestraColors.TextSub,
                            indicatorColor = UestraColors.Teal.copy(alpha = 0.2f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (currentScreen) {
                AppScreen.DASHBOARD -> DashboardScreen(repo, onInfoClick = onInfoClick)
                AppScreen.SEARCH -> SearchScreen(repo)
                AppScreen.OPTIONS -> OptionsScreen(repo)
                AppScreen.FAVORITES -> FavoritesScreen(repo)
                AppScreen.HELP -> HelpScreen()
            }
        }
    }
}

@Composable
fun DashboardScreen(repo: FavoritesRepository, onInfoClick: (InfoDialogData) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val session = remember { de.dhde.hannover.departures.widget.data.WidgetSessionStore(context) }
    val gpsModeActive by session.getGpsModeFlow().collectAsState(initial = false)

    val activeStationId by repo.activeStationId.collectAsState(initial = null)
    val activeFavoriteUniqueId by repo.activeFavoriteUniqueId.collectAsState(initial = null)
    val activeStationName by repo.effectiveStationName.collectAsState(initial = "Laden...")
    val favorites by repo.favoritesFlow.collectAsState(initial = emptyList())
    
    val maxFavorites by repo.maxFavoritesFlow.collectAsState(initial = 3)
    val maxFavRows by repo.maxFavRowsFlow.collectAsState(initial = 1)
    val maxRows by repo.maxRowsFlow.collectAsState(initial = 10)
    val transportFilters by repo.transportTypesFlow.collectAsState(initial = setOf("Stadtbahn", "Bus", "S-Bahn"))
    val ignoredMessages by repo.ignoredMessagesFlow.collectAsState(initial = emptySet())
    val favoritesHeight by repo.favoritesHeightFlow.collectAsState(initial = "STANDARD")
    val filterHeight by repo.filterHeightFlow.collectAsState(initial = "STANDARD")
    
    val groupDepartures by repo.groupDeparturesFlow.collectAsState(initial = true)
    val maxGroupedDepartures by repo.maxGroupedDeparturesFlow.collectAsState(initial = 2)
    val groupedFontSize by repo.groupedFontSizeFlow.collectAsState(initial = "STANDARD")
    
    var departures by remember { mutableStateOf<List<FlatDeparture>>(emptyList()) }
    var rawDepartures by remember { mutableStateOf<List<de.dhde.hannover.departures.widget.api.DepartureItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var lastUpdate by remember { mutableStateOf<java.time.Instant?>(null) }
    
    // States matching the widget
    var tabState by remember { mutableStateOf(TransportFilter.ALL) }
    var directionState by remember { mutableStateOf(DirectionFilter.ALL) }
    var timeDisplayMode by remember { mutableStateOf("MIN") }

    // Im GPS-Modus zählt der Halt nicht als Favorit → keine Favoriten-Filter (alle Linien/Richtungen).
    val currentFav = if (gpsModeActive) null else favorites.find { fav -> fav.safeUniqueId == activeFavoriteUniqueId }

    LaunchedEffect(currentFav) {
        if (currentFav != null) {
            tabState = TransportFilter.fromStorage(currentFav.transportFilter)
            directionState = DirectionFilter.fromStorage(currentFav.directionFilter)
        }
    }
    // GPS aktiviert → ungefiltert zeigen (alle Linien/Richtungen), nicht die Favoriten-Filter übernehmen.
    LaunchedEffect(gpsModeActive) {
        if (gpsModeActive) {
            tabState = TransportFilter.ALL
            directionState = DirectionFilter.ALL
        }
    }

    fun loadData(stationId: String) {
        if (isLoading) return
        scope.launch {
            isLoading = true
            try {
                val apiResponse = UestraApi.create().getDepartures(stationId)
                rawDepartures = apiResponse.departures ?: emptyList()
                departures = rawDepartures.flatMap { it.toFlatRows() }.sortedBy { it.departureTime }
                lastUpdate = java.time.Instant.now()
            } catch (e: Exception) {
                // Ignore for now, keep old data
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(activeStationId) {
        activeStationId?.let { loadData(it) }
    }

    LaunchedEffect(activeStationId, lastUpdate) {
        if (activeStationId != null && !isLoading) {
            delay(30000)
            loadData(activeStationId!!)
        }
    }

    // Filter logic
    val filtered = departures.filter {
        // Tab-Filter: wenn aktiv, übersteuert er den globalen Typ-Filter für den gewählten Typ
        val tabOverride = (tabState == TransportFilter.BUS && it.isBus) || (tabState == TransportFilter.TRAM && it.isTram)

        val isAnyTypeExcluded = transportFilters.size < 5 // 5 types total
        val globalTypeMatch = when {
            it.isFernbus -> "Fernbus" in transportFilters
            it.isDB -> "DB" in transportFilters
            it.isBus -> "Bus" in transportFilters
            it.isTram -> "Stadtbahn" in transportFilters
            it.isSBahn -> "S-Bahn" in transportFilters
            // Unbekannter Typ (z.B. unbekannte Züge): nur anzeigen wenn kein Filter aktiv
            else -> !isAnyTypeExcluded
        }
        if (!tabOverride && !globalTypeMatch) return@filter false

        // Favoriten-Linienfilter – im GPS-Modus ignorieren (ungefilterter nächster Halt).
        val currentFav = if (gpsModeActive) null else favorites.find { fav -> fav.safeUniqueId == activeFavoriteUniqueId }
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
        dep.copy(messages = de.dhde.hannover.departures.widget.data.filterMessages(dep.messages, ignoredMessages))
    }.filter { it.messages.isNotEmpty() }
    val hasMessages = messagesDeps.isNotEmpty()
    
    val groupedMessagesList = if (hasMessages) {
        messagesDeps.groupBy { it.lineShort }
            .map { (line, deps) -> line to deps.flatMap { it.messages }.distinct() }
    } else emptyList()

    Column(
        modifier = Modifier.fillMaxSize().background(UestraColors.DarkBg)
    ) {
        // "Widget Vorschau" label
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(UestraColors.CardBg)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Widget Vorschau",
                color = UestraColors.TextSub,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(UestraColors.WidgetBackground) // Widget Bg
                .padding(12.dp)
        ) {
            // Background Icon
            if (tabState != TransportFilter.ALL) {
                val iconRes = if (tabState == TransportFilter.BUS) R.drawable.ic_widget_bus else R.drawable.ic_widget_tram
                Icon(
                    androidx.compose.ui.res.painterResource(iconRes),
                    contentDescription = null,
                    tint = UestraColors.DarkGreenTint, // Subtle dark green
                    modifier = Modifier.fillMaxSize().padding(32.dp)
                )
            }

        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            WidgetHeader(
                stationName = activeStationName,
                isRefreshing = isLoading,
                hasMessages = hasMessages,
                gpsActive = gpsModeActive,
                onInfoClick = { onInfoClick(InfoDialogData(title = "Meldungen: $activeStationName", groupedMsgs = groupedMessagesList)) },
                onGps = {
                    scope.launch {
                        val newState = !gpsModeActive
                        session.setGpsMode(newState)
                        // Wie im Widget: bei Aktivierung sofort die nächste Haltestelle suchen.
                        // Der Wechsel von activeStationId löst über den Flow das Neuladen aus.
                        if (newState) {
                            de.dhde.hannover.departures.widget.widget.findAndSetActiveNearestStation(context)
                        }
                    }
                },
                onRefresh = { activeStationId?.let { loadData(it) } }
            )
            
            // Filter Segmented Row
            WidgetFilterRow(
                departures = rawDepartures,
                tabState = tabState,
                directionState = directionState,
                filterHeight = filterHeight,
                onTabChange = { newTab ->
                    tabState = newTab
                    activeFavoriteUniqueId?.let { id ->
                        scope.launch { repo.setFavoriteTransportFilter(id, newTab) }
                    }
                },
                onDirChange = { newDir ->
                    directionState = newDir
                    activeFavoriteUniqueId?.let { id ->
                        scope.launch { repo.setFavoriteDirectionFilter(id, newDir) }
                    }
                }
            )

            val minutesSinceUpdate = lastUpdate?.let { java.time.Duration.between(it, java.time.Instant.now()).toMinutes() } ?: 0
            val isWarning = minutesSinceUpdate >= 10

            // Gruppieren der Abfahrten (wie im Widget)
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
                groupedList.sortBy { it.first.departureTime }
            } else {
                for (dep in filtered) {
                    groupedList.add(dep to emptyList())
                }
            }

            val limitedFiltered = if (maxRows >= 15) groupedList else groupedList.take(maxRows)

            // List
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(limitedFiltered) { (dep, subDeps) ->
                        WidgetFlatDepartureRow(dep, subDeps, timeDisplayMode, isWarning, groupedFontSize, onInfoClick = onInfoClick)
                    }
                }
                // Invisible box to toggle time display mode
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(80.dp)
                        .align(Alignment.CenterEnd)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            timeDisplayMode = if (timeDisplayMode == "MIN") "CLOCK" else "MIN"
                        }
                )
            }

            // Favorites Row
            WidgetFavoritesRow(
                favorites = favorites,
                currentStationId = activeFavoriteUniqueId,
                maxFavorites = maxFavorites,
                maxFavRows = maxFavRows,
                favoritesHeight = favoritesHeight,
                onSelect = { scope.launch { repo.setActiveStation(it.safeUniqueId, it.name) } }
            )

            // Footer
            if (lastUpdate != null) {
                val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").withZone(java.time.ZoneId.systemDefault())
                Text(
                    text = "Letztes Update: ${formatter.format(lastUpdate)}",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp).align(Alignment.End)
                )
            }
        }
    }
}
}

// ---------------- UI COMPONENTS ----------------

@Composable
fun WidgetHeader(stationName: String, isRefreshing: Boolean, hasMessages: Boolean = false, gpsActive: Boolean = false, onInfoClick: () -> Unit = {}, onGps: () -> Unit = {}, onRefresh: () -> Unit) {
    val cleanName = stationName
        .replace(Regex("\\b(Hannover|Landeshauptstadt)\\b", RegexOption.IGNORE_CASE), "")
        .replace(Regex("[,/()]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasMessages) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onInfoClick() },
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    androidx.compose.ui.res.painterResource(android.R.drawable.ic_dialog_info),
                    contentDescription = "Info",
                    tint = UestraColors.Amber,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = cleanName,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // Framework-Drawables (wie im Widget) über Drawable->Bitmap laden:
        // Compose painterResource() unterstützt android.R.drawable.* NICHT.
        val headerCtx = LocalContext.current
        val gpsBitmap = remember {
            ContextCompat.getDrawable(headerCtx, android.R.drawable.ic_menu_mylocation)!!.toBitmap().asImageBitmap()
        }
        Icon(
            bitmap = gpsBitmap,
            contentDescription = "GPS – nächste Haltestelle",
            tint = if (gpsActive) UestraColors.GpsBlue else Color.Gray,
            modifier = Modifier.padding(end = 8.dp).size(20.dp).clickable { onGps() }
        )
        if (isRefreshing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = UestraColors.GpsBlue, strokeWidth = 2.dp)
        } else {
            val syncBitmap = remember {
                ContextCompat.getDrawable(headerCtx, android.R.drawable.ic_popup_sync)!!.toBitmap().asImageBitmap()
            }
            Icon(
                bitmap = syncBitmap,
                contentDescription = "Refresh",
                tint = Color.Gray,
                modifier = Modifier.size(20.dp).clickable { onRefresh() }
            )
        }
    }
}

@Composable
fun WidgetFilterRow(
    departures: List<de.dhde.hannover.departures.widget.api.DepartureItem>,
    tabState: TransportFilter,
    directionState: DirectionFilter,
    filterHeight: String = "STANDARD",
    onTabChange: (TransportFilter) -> Unit,
    onDirChange: (DirectionFilter) -> Unit
) {
    val hasH = departures.any { lineDirection(it.lineId) == DirectionFilter.INBOUND }
    val hasR = departures.any { lineDirection(it.lineId) == DirectionFilter.OUTBOUND }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vehicle Type Filters
        Row(verticalAlignment = Alignment.CenterVertically) {
            WidgetSegmentButton(R.drawable.ic_widget_bus, tabState == TransportFilter.BUS, UestraColors.AccentRed, filterHeight) { onTabChange(if (tabState == TransportFilter.BUS) TransportFilter.ALL else TransportFilter.BUS) }
            Spacer(modifier = Modifier.width(4.dp))
            WidgetSegmentButton(R.drawable.ic_widget_tram, tabState == TransportFilter.TRAM, UestraColors.LineBlue, filterHeight) { onTabChange(if (tabState == TransportFilter.TRAM) TransportFilter.ALL else TransportFilter.TRAM) }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Direction Filters
        if (hasH || hasR) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasH) {
                    WidgetSegmentButton(R.drawable.ic_widget_city, directionState == DirectionFilter.INBOUND, UestraColors.Teal, filterHeight) { onDirChange(if (directionState == DirectionFilter.INBOUND) DirectionFilter.ALL else DirectionFilter.INBOUND) }
                    if (hasR) Spacer(modifier = Modifier.width(4.dp))
                }
                if (hasR) {
                    WidgetSegmentButton(R.drawable.ic_widget_home, directionState == DirectionFilter.OUTBOUND, UestraColors.AccentRed, filterHeight) { onDirChange(if (directionState == DirectionFilter.OUTBOUND) DirectionFilter.ALL else DirectionFilter.OUTBOUND) }
                }
            }
        }
    }
}

fun filterSegmentSize(mode: String): Triple<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp> = when (mode) {
    "KOMPAKT" -> Triple(28.dp, 20.dp, 12.dp)
    "GROSS"   -> Triple(40.dp, 30.dp, 20.dp)
    else      -> Triple(34.dp, 24.dp, 16.dp)
}
fun favoritesButtonSize(mode: String): Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.TextUnit> = when (mode) {
    "KOMPAKT" -> 2.dp to 10.sp
    "GROSS"   -> 10.dp to 13.sp
    else      -> 6.dp to 11.sp
}
fun groupedSubFontSize(mode: String): androidx.compose.ui.unit.TextUnit = when (mode) {
    "KLEIN" -> 9.sp
    "GROSS" -> 13.sp
    else    -> 10.sp
}

@Composable
fun SegmentButtonVisual(iconRes: Int, isActive: Boolean, activeColor: Color, mode: String) {
    val bgColor = if (isActive) activeColor else UestraColors.ChipInactive
    val (width, height, iconSize) = filterSegmentSize(mode)
    Box(
        modifier = Modifier.size(width, height).clip(RoundedCornerShape(6.dp)).background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(androidx.compose.ui.res.painterResource(iconRes), contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun WidgetSegmentButton(iconRes: Int, isActive: Boolean, activeColor: Color, filterHeight: String = "STANDARD", onClick: () -> Unit) {
    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onClick() }) {
        SegmentButtonVisual(iconRes, isActive, activeColor, filterHeight)
    }
}

@Composable
fun WidgetFlatDepartureRow(
    dep: FlatDeparture, 
    subsequentDepartures: List<FlatDeparture> = emptyList(),
    timeDisplayMode: String, 
    isWarning: Boolean, 
    groupedFontSize: String = "STANDARD",
    onInfoClick: (InfoDialogData) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .padding(vertical = 4.dp, horizontal = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetLineBadge(dep.lineShort, dep.isBus)
            Spacer(modifier = Modifier.width(8.dp))
            val dir = lineDirection(dep.lineId)
            if (dir != null) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(
                        if (dir == DirectionFilter.INBOUND) R.drawable.ic_widget_city else R.drawable.ic_widget_home
                    ),
                    contentDescription = null,
                    tint = if (dir == DirectionFilter.INBOUND) UestraColors.Teal else UestraColors.AccentRed,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = dep.destination ?: "Unbekannt",
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            val minutes = try {
                val depTime = java.time.Instant.parse(dep.departureTime)
                java.time.Duration.between(java.time.Instant.now(), depTime).toMinutes()
            } catch (e: Exception) { -1L }

            val timeText = if (timeDisplayMode == "CLOCK") {
                try {
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                        .withZone(java.time.ZoneId.systemDefault())
                        .format(java.time.Instant.parse(dep.departureTime))
                } catch (e: Exception) { "--:--" }
            } else {
                if (minutes in 0..60) "$minutes min" else "-- min"
            }

            val hasDelay = (dep.delayMinutes ?: 0) > 0
            val delayText = if (hasDelay) " (+${dep.delayMinutes})" else ""

            var finalTimeText = timeText + delayText
            if (dep.isCancelled) {
                finalTimeText = finalTimeText.map { it + "\u0336" }.joinToString("")
            }

            val timeColor = when {
                isWarning -> Color.Gray
                dep.isCancelled -> UestraColors.AccentRed
                hasDelay -> UestraColors.Warning
                else -> UestraColors.OkGreen
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = finalTimeText,
                    color = timeColor,
                    fontSize = 14.sp,
                    fontWeight = if (isWarning) FontWeight.Normal else FontWeight.Bold,
                    fontStyle = if (isWarning) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                )

                if (subsequentDepartures.isNotEmpty()) {
                    val subFontSize = groupedSubFontSize(groupedFontSize)
                    val subText = subsequentDepartures.joinToString(", ") { subDep ->
                        var t = if (timeDisplayMode == "CLOCK") {
                            try {
                                java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                                    .withZone(java.time.ZoneId.systemDefault())
                                    .format(java.time.Instant.parse(subDep.departureTime))
                            } catch (e: Exception) { "--:--" }
                        } else {
                            try {
                                val dTime = java.time.Instant.parse(subDep.departureTime)
                                val m = java.time.Duration.between(java.time.Instant.now(), dTime).toMinutes()
                                if (m <= 0) "0m" else "${m}m"
                            } catch (e: Exception) { "?" }
                        }
                        if (subDep.isCancelled) t = t.map { it + "\u0336" }.joinToString("")
                        t
                    }
                    Text(
                        text = subText,
                        color = UestraColors.TextSub,
                        fontSize = subFontSize,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }

        if (dep.isCancelled) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 42.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fahrt entfällt",
                    color = UestraColors.AccentRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}

@Composable
fun DuplicateBlob(name: String, modifier: Modifier = Modifier) {
    val hash = name.hashCode()
    // Generate a vibrant color from the hash
    val color = Color(
        red = ((hash shr 16) and 0xFF) / 255f * 0.6f + 0.4f,
        green = ((hash shr 8) and 0xFF) / 255f * 0.6f + 0.4f,
        blue = (hash and 0xFF) / 255f * 0.6f + 0.4f
    )
    
    // Generate a pseudo-random shape based on the hash
    val random = java.util.Random(hash.toLong())
    
    Canvas(
        modifier = modifier
            .padding(end = 12.dp)
            .size(16.dp)
    ) {
        val width = size.width
        val height = size.height
        
        // Base radius is slightly smaller to allow for "blobbiness"
        val radius = minOf(width, height) / 2f * 0.8f
        val center = androidx.compose.ui.geometry.Offset(width / 2f, height / 2f)
        
        val numPoints = 6
        val points = mutableListOf<androidx.compose.ui.geometry.Offset>()
        
        for (i in 0 until numPoints) {
            val angle = (i.toFloat() / numPoints) * 2f * Math.PI
            // Vary the radius by up to +/- 20%
            val variance = (random.nextFloat() * 0.4f - 0.2f)
            val r = radius * (1f + variance)
            
            val x = center.x + r * kotlin.math.cos(angle).toFloat()
            val y = center.y + r * kotlin.math.sin(angle).toFloat()
            points.add(androidx.compose.ui.geometry.Offset(x, y))
        }
        
        val path = Path()
        path.moveTo(points[0].x, points[0].y)
        
        for (i in 0 until numPoints) {
            val p0 = points[(i - 1 + numPoints) % numPoints]
            val p1 = points[i]
            val p2 = points[(i + 1) % numPoints]
            val p3 = points[(i + 2) % numPoints]
            
            // Catmull-Rom to Bezier conversion for smooth curves
            val tension = 0.2f
            
            val cp1x = p1.x + (p2.x - p0.x) * tension
            val cp1y = p1.y + (p2.y - p0.y) * tension
            
            val cp2x = p2.x - (p3.x - p1.x) * tension
            val cp2y = p2.y - (p3.y - p1.y) * tension
            
            path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
        }
        
        path.close()
        
        drawPath(
            path = path,
            color = color
        )
        
        // Draw 2-4 small splatters around the blob
        val numSplatters = 2 + random.nextInt(3)
        for (i in 0 until numSplatters) {
            val splatterAngle = random.nextFloat() * 2f * Math.PI.toFloat()
            // Distance from center, outside the main blob (1.2 to 2.0 times the radius)
            val splatterDist = radius * (1.2f + random.nextFloat() * 0.8f)
            val sx = center.x + splatterDist * kotlin.math.cos(splatterAngle)
            val sy = center.y + splatterDist * kotlin.math.sin(splatterAngle)
            // Splatter radius (10% to 25% of the main radius)
            val sr = radius * (0.1f + random.nextFloat() * 0.15f)
            
            drawCircle(
                color = color,
                radius = sr,
                center = androidx.compose.ui.geometry.Offset(sx, sy)
            )
        }
    }
}

@Composable
fun WidgetLineBadge(line: String, isBus: Boolean) {
    val (bgColor, textColor) = if (isBus) {
        val isSprintH = line.length == 3 && line[0] in '3'..'9' && line.substring(1) == "00"
        if (isSprintH) UestraColors.SprintMagenta to Color.White
        else UestraColors.LineRed to Color.White
    } else {
        when (line) {
            "1", "2", "8" -> UestraColors.LineRed to Color.White
            "3", "7", "9", "13" -> UestraColors.LineBlue to Color.White
            "4", "5", "6", "11" -> UestraColors.LineYellow to Color.Black
            "10", "17" -> UestraColors.LineGreen to Color.White
            else -> Color.Gray to Color.White
        }
    }

    val width = if (line.length >= 4) 42.dp else 34.dp
    Box(
        modifier = Modifier
            .size(width, 24.dp)
            .background(bgColor, RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = line,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Visible
        )
    }
}

@Composable
fun FavoriteButtonVisual(label: String, isSelected: Boolean, mode: String, modifier: Modifier = Modifier) {
    val (vertPadding, fontSize) = favoritesButtonSize(mode)
    val bgColor = if (isSelected) UestraColors.LineBlue else UestraColors.ChipInactive
    Box(
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(bgColor).padding(vertical = vertPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = Color.White, fontSize = fontSize, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
fun WidgetFavoritesRow(
    favorites: List<de.dhde.hannover.departures.widget.data.FavoriteStation>,
    currentStationId: String?,
    maxFavorites: Int,
    maxFavRows: Int,
    favoritesHeight: String = "STANDARD",
    onSelect: (de.dhde.hannover.departures.widget.data.FavoriteStation) -> Unit
) {
    if (favorites.isEmpty() || maxFavorites <= 0 || maxFavRows <= 0) return

    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        val visibleFavs = favorites.take(maxFavorites * maxFavRows)
        visibleFavs.chunked(maxFavorites).forEach { chunk ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                chunk.forEach { fav ->
                    val label = fav.alias ?: fav.name.replace(Regex("\\bHannover\\b", RegexOption.IGNORE_CASE), "").replace(Regex("[,/()]+"), " ").trim().split(" ").firstOrNull() ?: fav.name
                    val shortLabel = if (label.length > 8) label.take(7) + "." else label
                    val isSelected = fav.safeUniqueId == currentStationId

                    Box(modifier = Modifier.weight(1f).clickable { onSelect(fav) }) {
                        FavoriteButtonVisual(shortLabel, isSelected, favoritesHeight, Modifier.fillMaxWidth())
                    }
                }

                // Fill remaining space if chunk is smaller than maxFavorites
                val missing = maxFavorites - chunk.size
                repeat(missing) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun SearchScreen(repo: FavoritesRepository) {
    val scope      = rememberCoroutineScope()
    var query      by remember { mutableStateOf("") }
    var results    by remember { mutableStateOf<List<Pair<StationSearchResult, Float?>>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    val favorites        by repo.favoritesFlow.collectAsState(initial = emptyList())
    val activeStationId  by repo.activeStationId.collectAsState(initial = null)

    val context = LocalContext.current
    val stopsRepo = remember { de.dhde.hannover.departures.widget.data.StopsRepository(context) }

    val lastLocation = remember {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
            try {
                locationManager.getLastKnownLocation(android.location.LocationManager.PASSIVE_PROVIDER)
                    ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            } catch (e: SecurityException) { null }
        } else null
    }

    // Debounced search
    LaunchedEffect(query) {
        delay(400)
        isSearching = true
        searchError = null
        try {
            val allStops = stopsRepo.getAllStops()
            
            val filteredStops = if (query.isBlank()) {
                allStops
            } else {
                allStops.filter { it.name.contains(query, ignoreCase = true) }
            }

            val stopsWithDistances = filteredStops.map { stop ->
                var dist: Float? = null
                if (lastLocation != null && stop.lat != null && stop.lon != null) {
                    val resultsArr = FloatArray(1)
                    android.location.Location.distanceBetween(
                        lastLocation.latitude, lastLocation.longitude,
                        stop.lat, stop.lon,
                        resultsArr
                    )
                    dist = resultsArr[0]
                }
                stop to dist
            }

            results = stopsWithDistances.sortedWith(compareBy({ it.second ?: Float.MAX_VALUE }, { it.first.name })).take(5)
        } catch (e: Exception) {
            searchError = "Suche fehlgeschlagen: ${e.message?.take(40)}"
        } finally {
            isSearching = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(UestraColors.DarkBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionLabel("Haltestelle suchen")
            OutlinedTextField(
                value         = query,
                onValueChange = { query = it },
                placeholder   = { Text("z.B. Kröpcke, Roderbruch…", color = UestraColors.TextSub) },
                leadingIcon   = {
                    if (isSearching)
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = UestraColors.Teal, strokeWidth = 2.dp)
                    else
                        Icon(Icons.Default.Search, null, tint = UestraColors.TextSub)
                },
                singleLine  = true,
                colors      = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = UestraColors.Teal,
                    unfocusedBorderColor = UestraColors.BorderSubtle,
                    focusedTextColor     = UestraColors.TextMain,
                    unfocusedTextColor   = UestraColors.TextMain,
                    cursorColor          = UestraColors.Teal
                ),
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp)
            )

            searchError?.let {
                Text(it, color = UestraColors.AccentRed, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        if (results.isNotEmpty()) {
            item { SectionLabel("Ergebnisse") }
            items(results) { item ->
                val location = item.first
                val dist = item.second
                val isFav   = favorites.any { it.id == location.id }
                val isActive = location.id == activeStationId

                val distText = dist?.let {
                    if (it >= 1000) {
                        String.format(java.util.Locale.getDefault(), "%.1f km", it / 1000f)
                    } else {
                        "${it.toInt()} m"
                    }
                }

                SearchResultRow(
                    location = location,
                    distanceText = distText,
                    isFav    = isFav,
                    isActive = isActive,
                    onToggleFav = {
                        scope.launch {
                            if (isFav) repo.removeAllFavoritesByStationId(location.id)
                            else       repo.addFavorite(location.id, location.name)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun FavoritesScreen(repo: FavoritesRepository) {
    val scope = rememberCoroutineScope()
    val favoritesFromRepo by repo.favoritesFlow.collectAsState(initial = emptyList())
    val activeFavoriteUniqueId by repo.activeFavoriteUniqueId.collectAsState(initial = null)
    val allowDuplicates by repo.allowDuplicatesFlow.collectAsState(initial = false)

    // Lokaler State für die Reihenfolge
    var listState by remember { mutableStateOf(favoritesFromRepo) }
    val itemHeights = remember { mutableStateMapOf<String, Float>() }
    var filterDialogFav by remember { mutableStateOf<de.dhde.hannover.departures.widget.data.FavoriteStation?>(null) }
    
    if (filterDialogFav != null) {
        LineFilterDialog(
            fav = filterDialogFav!!,
            repo = repo,
            onDismiss = { filterDialogFav = null }
        )
    }
    
    // Sync mit Repository
    LaunchedEffect(favoritesFromRepo) {
        val currentIds = listState.map { it.safeUniqueId }.toSet()
        val repoIds = favoritesFromRepo.map { it.safeUniqueId }.toSet()
        
        if (currentIds != repoIds) {
            // Elemente wurden hinzugefügt oder entfernt -> kompletter Reset
            listState = favoritesFromRepo
        } else {
            // Die Elemente sind gleich. Wir updaten die Eigenschaften (Alias, Filter), 
            // behalten aber die lokale Reihenfolge von listState, um Drag&Drop nicht zu stören.
            val repoMap = favoritesFromRepo.associateBy { it.safeUniqueId }
            listState = listState.mapNotNull { localFav ->
                repoMap[localFav.safeUniqueId]
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(UestraColors.DarkBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionLabel("Meine Favoriten") }

        if (listState.isEmpty()) {
            item {
                Text("Noch keine Favoriten. Suche eine Haltestelle und tippe auf ⭐.", color = UestraColors.TextSub, fontSize = 13.sp)
            }
        } else {
            itemsIndexed(listState, key = { _, it -> it.safeUniqueId }) { index, fav ->
                FavoriteRow(
                    fav      = fav,
                    isActive = fav.safeUniqueId == activeFavoriteUniqueId,
                    onSelect = { scope.launch { repo.setActiveStation(fav.safeUniqueId, fav.name) } },
                    onDelete = { scope.launch { repo.removeFavoriteByUniqueId(fav.safeUniqueId)         } },
                    onAlias  = { alias -> scope.launch { repo.setFavoriteAlias(fav.safeUniqueId, alias) } },
                    onFilter = { filterDialogFav = fav },
                    onDuplicate = { scope.launch { repo.duplicateFavorite(fav.safeUniqueId) } },
                    onMove   = { from, to ->
                        if (from != to && from in listState.indices && to in listState.indices) {
                            val newList = listState.toMutableList()
                            val item = newList.removeAt(from)
                            newList.add(to, item)
                            listState = newList
                            scope.launch { repo.updateFavoritesOrder(newList) }
                        }
                    },
                    getHeight = { targetIndex ->
                        if (targetIndex in listState.indices) {
                            itemHeights[listState[targetIndex].safeUniqueId] ?: 0f
                        } else 0f
                    },
                    onHeightMeasured = { height ->
                        itemHeights[fav.safeUniqueId] = height
                    },
                    allowDuplicates = allowDuplicates,
                    index = index,
                    totalCount = listState.size
                )
            }
        }
    }
}

@Composable
fun HelpScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(UestraColors.DarkBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionLabel("Unterstützen & Quellcode") }
        item {
            val context = LocalContext.current
            val intentHandler = { url: String ->
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                context.startActivity(intent)
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { intentHandler("https://www.buymeacoffee.com/dhde") },
                    colors = ButtonDefaults.buttonColors(containerColor = UestraColors.ButtonYellow),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("Kaffee spenden", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { intentHandler("https://github.com/dhde/hannover-departures-widget") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, UestraColors.TextSub)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = UestraColors.TextMain)
                    Spacer(Modifier.width(8.dp))
                    Text("Quellcode auf GitHub", color = UestraColors.TextMain)
                }
            }
        }
        
        item { SectionLabel("Widget-Funktionen") }
        item { WidgetFeaturesCard() }
        
        item { SectionLabel("Interaktion & Updates") }
        item { InteractionCard() }
        
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = UestraColors.CardBg.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Rechtlicher Hinweis",
                        color = UestraColors.AccentRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Dies ist eine inoffizielle App. Sie steht in keiner Verbindung zur ÜSTRA Hannoversche Verkehrsbetriebe AG oder dem GVH. Alle Daten werden über öffentliche Schnittstellen bezogen. Nutzung auf eigene Gefahr.",
                        color = UestraColors.TextSub,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun HelpItem(icon: @Composable () -> Unit, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.CenterStart) {
            icon()
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = UestraColors.TextMain, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(description, color = UestraColors.TextSub, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun WidgetFeaturesCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = UestraColors.CardBg),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            HelpItem(
                icon = {
                    Icon(androidx.compose.ui.res.painterResource(android.R.drawable.ic_dialog_info), null, tint = UestraColors.Amber, modifier = Modifier.size(20.dp))
                },
                title = "Info-Meldungen & Filter",
                description = "Tippe auf das goldene 'i' oben links im Widget, um aktuelle Meldungen und Störungen zu lesen. Dauerhafte Meldungen (z.B. Rollstuhl, WLAN) lassen sich in den Optionen ausblenden."
            )
            HorizontalDivider(color = UestraColors.Divider, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
            HelpItem(
                icon = {
                    Text("1", color = UestraColors.Teal, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                title = "Favoriten-Schnellwahl",
                description = "Nutze die Schnellwahl-Tasten ganz unten im Widget oder tippe oben auf den Stationsnamen, um durch die Haltestellen zu schalten."
            )
            HorizontalDivider(color = UestraColors.Divider, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
            HelpItem(
                icon = {
                    Row {
                        Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_widget_city), null, tint = UestraColors.Teal, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(2.dp))
                        Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_widget_home), null, tint = UestraColors.AccentRed, modifier = Modifier.size(18.dp))
                    }
                },
                title = "Richtungen & Filter",
                description = "Nutze die Symbole für City oder Home, um die Richtung zu filtern. Die Zeilen im Widget färben sich automatisch passend (hell/dunkel)."
            )
            HorizontalDivider(color = UestraColors.Divider, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
            HelpItem(
                icon = {
                    Icon(Icons.Default.Settings, null, tint = UestraColors.Teal, modifier = Modifier.size(20.dp))
                },
                title = "Größenanpassung",
                description = "Du kannst in den Optionen die visuelle Größe der Filter- und Favoriten-Buttons ('KOMPAKT', 'STANDARD', 'GROSS') einstellen, falls du größere Klickflächen bevorzugst."
            )
        }
    }
}

@Composable
private fun InteractionCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = UestraColors.CardBg),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            HelpItem(
                icon = {
                    Text("5", color = UestraColors.Teal, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                title = "Zeit-Ansicht",
                description = "Tippe im Widget auf eine Abfahrtszeit, um zwischen Countdown (Minuten) und genauer Uhrzeit hin- und herzuschalten. (Tipp: In den Optionen kannst du einstellen, ob dabei offline umgeschaltet wird oder die API aktualisiert werden soll)."
            )
            HorizontalDivider(color = UestraColors.Divider, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
            HelpItem(
                icon = {
                    Icon(Icons.Default.Refresh, null, tint = UestraColors.Teal, modifier = Modifier.size(20.dp))
                },
                title = "Aktualisierung",
                description = "Das Widget lädt aus Strom- und Datenspargründen keine Daten im Hintergrund. Um aktuelle Echtzeitdaten und Verspätungen der API abzurufen, tippe auf den kleinen Refresh-Pfeil oben rechts."
            )
            HorizontalDivider(color = UestraColors.Divider, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
            HelpItem(
                icon = {
                    Icon(Icons.Default.LocationOn, null, tint = UestraColors.Teal, modifier = Modifier.size(20.dp))
                },
                title = "GPS-Suche",
                description = "Tippe auf das Standort-Icon, damit das Widget automatisch Abfahrten der nächstgelegenen Haltestelle anzeigt."
            )
        }
    }
}

// ── Composable Helpers ───────────────────────────────────────────────────────

@Composable
fun SectionLabel(text: String) {
    Text(
        text     = text.uppercase(),
        color    = UestraColors.TextSub,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun SearchResultRow(
    location: StationSearchResult,
    distanceText: String?,
    isFav: Boolean,
    isActive: Boolean,
    onToggleFav: () -> Unit
) {
    Card(
        colors  = CardDefaults.cardColors(
            containerColor = if (isActive) UestraColors.TealSurface else UestraColors.CardBg
        ),
        shape   = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleFav() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                val displayName = location.name.substringAfter(", ").ifBlank { location.name }
                Text(
                    displayName,
                    color      = if (isActive) UestraColors.Teal else UestraColors.TextMain,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (displayName != location.name) {
                        Text(location.name, color = UestraColors.TextSub, fontSize = 11.sp)
                    }
                    if (distanceText != null) {
                        if (displayName != location.name) {
                            Text(" • ", color = UestraColors.TextSub, fontSize = 11.sp)
                        }
                        Text(distanceText, color = UestraColors.TextSub, fontSize = 11.sp)
                    }
                }
            }
            if (isActive) {
                Text("AKTIV", color = UestraColors.Teal, fontWeight = FontWeight.Bold, fontSize = 10.sp, 
                    modifier = Modifier.background(UestraColors.Teal.copy(0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp))
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = onToggleFav) {
                Icon(
                    if (isFav) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = if (isFav) "Favorit entfernen" else "Favorit hinzufügen",
                    tint = if (isFav) UestraColors.FavoriteGold else UestraColors.TextSub
                )
            }
        }
    }
}

@Composable
private fun FavoriteRow(
    fav: de.dhde.hannover.departures.widget.data.FavoriteStation,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onAlias: (String?) -> Unit,
    onFilter: () -> Unit,
    onDuplicate: () -> Unit,
    onMove: (Int, Int) -> Unit,
    getHeight: (Int) -> Float,
    onHeightMeasured: (Float) -> Unit,
    allowDuplicates: Boolean,
    index: Int,
    totalCount: Int
) {
    val currentIndex by rememberUpdatedState(index)
    var showAliasDialog by remember { mutableStateOf(false) }
    var aliasText by remember { mutableStateOf(fav.alias ?: "") }

    if (showAliasDialog) {
        AlertDialog(
            onDismissRequest = { showAliasDialog = false },
            title   = { Text("Alias für ${fav.name}") },
            text    = {
                OutlinedTextField(
                    value = aliasText,
                    onValueChange = { aliasText = it },
                    placeholder = { Text("z.B. Zuhause, Arbeit...") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onAlias(aliasText)
                    showAliasDialog = false
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { showAliasDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    val density = LocalDensity.current
    var dragOffset by remember { mutableStateOf(0f) }
    var rowHeightPx by remember { mutableStateOf(0) }

    Card(
        colors  = CardDefaults.cardColors(
            containerColor = if (isActive) UestraColors.TealSurface else UestraColors.CardBg
        ),
        shape   = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = dragOffset.dp)
            .zIndex(if (dragOffset != 0f) 1f else 0f)
            .onGloballyPositioned { 
                val h = it.size.height.toFloat() / density.density
                if (rowHeightPx != it.size.height) {
                    rowHeightPx = it.size.height
                    onHeightMeasured(h)
                }
            }
            .clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Real Drag Handle
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Verschieben",
                tint = if (dragOffset != 0f) UestraColors.Teal else UestraColors.TextSub,
                modifier = Modifier
                    .size(32.dp)
                    .padding(end = 8.dp)
                    .pointerInput(fav.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { /* optional feedback */ },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val deltaDp = with(density) { dragAmount.y.toDp() }.value
                                dragOffset += deltaDp
                                
                                val myHeight = if (rowHeightPx > 0) rowHeightPx / density.density else 50f
                                
                                if (dragOffset > 0 && currentIndex < totalCount - 1) {
                                    val targetHeight = getHeight(currentIndex + 1).takeIf { it > 0 } ?: 50f
                                    val distanceBetweenCenters = (myHeight / 2f) + 12f + (targetHeight / 2f)
                                    val threshold = distanceBetweenCenters / 2f
                                    
                                    if (dragOffset > threshold) {
                                        onMove(currentIndex, currentIndex + 1)
                                        dragOffset -= (targetHeight + 12f)
                                    }
                                } else if (dragOffset < 0 && currentIndex > 0) {
                                    val targetHeight = getHeight(currentIndex - 1).takeIf { it > 0 } ?: 50f
                                    val distanceBetweenCenters = (myHeight / 2f) + 12f + (targetHeight / 2f)
                                    val threshold = distanceBetweenCenters / 2f
                                    
                                    if (dragOffset < -threshold) {
                                        onMove(currentIndex, currentIndex - 1)
                                        dragOffset += (targetHeight + 12f)
                                    }
                                }
                            },
                            onDragEnd = { dragOffset = 0f },
                            onDragCancel = { dragOffset = 0f }
                        )
                    }
            )
            
            if (allowDuplicates) {
                DuplicateBlob(name = fav.name)
            }
            
            Column(Modifier.weight(1f)) {
                // Remove "Hannover " prefix if there is no alias, to save space
                val cleanName = fav.name.removePrefix("Hannover ").trim()
                val displayName = fav.alias ?: cleanName.substringAfter(", ").ifBlank { cleanName }
                
                Text(
                    text       = displayName,
                    color      = if (isActive) UestraColors.Teal else UestraColors.TextMain,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp,
                    maxLines   = 1,
                    overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                // Only show the official name as subtitle if an alias is actively used
                if (!fav.alias.isNullOrBlank()) {
                    Text(
                        text     = fav.name, 
                        color    = UestraColors.TextSub,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            if (allowDuplicates) {
                Column(horizontalAlignment = Alignment.End) {
                    Row {
                        IconButton(onClick = { showAliasDialog = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Edit, "Alias bearbeiten", tint = UestraColors.Teal, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onFilter, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.FilterList, "Linien filtern", tint = if (fav.filteredLines != null) UestraColors.FavoriteGold else UestraColors.Teal, modifier = Modifier.size(20.dp))
                        }
                    }
                    Row {
                        IconButton(onClick = onDuplicate, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Add, "Duplizieren", tint = UestraColors.Teal, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Delete, "Löschen", tint = UestraColors.AccentRed, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { showAliasDialog = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, "Alias bearbeiten", tint = UestraColors.Teal, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onFilter, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.FilterList, "Linien filtern", tint = if (fav.filteredLines != null) UestraColors.FavoriteGold else UestraColors.Teal, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, "Löschen", tint = UestraColors.AccentRed, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = UestraColors.CardBg),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            HelpItem(
                icon = { 
                    Text("1", color = UestraColors.Teal, fontWeight = FontWeight.Bold, fontSize = 18.sp) 
                },
                title = "Favoriten-Schnellwahl",
                description = "Nutze die Schnellwahl-Tasten ganz unten im Widget oder tippe oben auf den Stationsnamen, um durch die Haltestellen zu schalten."
            )
            HorizontalDivider(color = UestraColors.Divider, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
            HelpItem(
                icon = { 
                    Row {
                        Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_widget_city), null, tint = UestraColors.Teal, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(2.dp))
                        Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_widget_home), null, tint = UestraColors.AccentRed, modifier = Modifier.size(18.dp))
                    }
                },
                title = "Richtungen & Filter",
                description = "Nutze die Symbole für City oder Home, um die Richtung zu filtern. Die Zeilen im Widget färben sich automatisch passend (hell/dunkel)."
            )
            HorizontalDivider(color = UestraColors.Divider, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
            HelpItem(
                icon = { 
                    Text("5", color = UestraColors.Teal, fontWeight = FontWeight.Bold, fontSize = 18.sp) 
                },
                title = "Zeit-Ansicht",
                description = "Tippe im Widget auf eine Abfahrtszeit, um zwischen Countdown (Minuten) und genauer Uhrzeit hin- und herzuschalten."
            )
            HorizontalDivider(color = UestraColors.Divider, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
            HelpItem(
                icon = { 
                    Icon(Icons.Default.Refresh, null, tint = UestraColors.Teal, modifier = Modifier.size(20.dp))
                },
                title = "Aktualisierung",
                description = "Das Widget frischt sich ca. alle 15 Minuten selbst auf. Für sofortige Echtzeitdaten tippe auf den kleinen Refresh-Pfeil oben rechts."
            )
            HorizontalDivider(color = UestraColors.Divider, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
            HelpItem(
                icon = { 
                    Icon(Icons.Default.LocationOn, null, tint = UestraColors.Teal, modifier = Modifier.size(20.dp))
                },
                title = "GPS-Suche",
                description = "Tippe auf das Standort-Icon, damit das Widget automatisch Abfahrten der nächstgelegenen Haltestelle anzeigt."
            )
        }
    }
}


@Composable
fun LineFilterDialog(
    fav: de.dhde.hannover.departures.widget.data.FavoriteStation,
    repo: FavoritesRepository,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var availableLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedLines by remember { mutableStateOf<Set<String>>(fav.filteredLines ?: emptySet()) }
    var hasLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(fav.id) {
        isLoading = true
        try {
            val response = de.dhde.hannover.departures.widget.api.UestraApi.create().getDepartures(fav.id)
            val lines = response.departures?.mapNotNull { it.lineShort }?.distinct()?.sorted() ?: emptyList()
            if (fav.filteredLines == null) {
                availableLines = lines
                selectedLines = lines.toSet()
            } else {
                availableLines = (lines + fav.filteredLines).distinct().sorted()
                selectedLines = fav.filteredLines!!
            }
        } catch (e: Exception) {
            // Ignoriere Fehler, zeige leere Liste
        } finally {
            isLoading = false
            hasLoaded = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            val titleName = fav.alias ?: fav.name.substringBefore(",")
            Text("Linien filtern: $titleName", color = UestraColors.Teal, fontSize = 18.sp, fontWeight = FontWeight.Bold) 
        },
        text = {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = UestraColors.Teal)
                }
            } else if (availableLines.isEmpty()) {
                Text("Konnte aktuell keine Linien für diese Station finden.", color = UestraColors.TextSub)
            } else {
                LazyColumn {
                    items(availableLines) { line ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedLines = if (line in selectedLines) {
                                    selectedLines - line
                                } else {
                                    selectedLines + line
                                }
                            }.padding(vertical = 8.dp)
                        ) {
                            Checkbox(
                                checked = line in selectedLines,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = UestraColors.Teal, uncheckedColor = UestraColors.TextSub)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Linie $line", color = UestraColors.TextMain, fontSize = 16.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        // Speichere null, falls alle verfügbaren Linien markiert sind (kein Filter)
                        val filterToSave = if (selectedLines.containsAll(availableLines)) null else selectedLines
                        repo.setFavoriteLineFilter(fav.safeUniqueId, filterToSave)
                        onDismiss()
                    }
                },
                enabled = hasLoaded
            ) { Text("Speichern", color = UestraColors.Teal) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen", color = UestraColors.TextSub) }
        },
        containerColor = UestraColors.CardBg,
        titleContentColor = UestraColors.Teal,
        textContentColor = UestraColors.TextMain
    )
}


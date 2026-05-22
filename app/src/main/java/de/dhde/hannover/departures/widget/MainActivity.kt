package de.dhde.hannover.departures.widget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import de.dhde.hannover.departures.widget.api.StationSearchResult
import de.dhde.hannover.departures.widget.api.UestraApi
import de.dhde.hannover.departures.widget.api.FlatDeparture
import de.dhde.hannover.departures.widget.api.toFlatRows
import de.dhde.hannover.departures.widget.data.FavoritesRepository
import de.dhde.hannover.departures.widget.widget.WidgetTickerWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.glance.appwidget.updateAll
import kotlin.math.roundToInt

import android.content.Intent

data class InfoDialogData(val title: String, val msgs: String)

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
                    primary        = Color(0xFF0F7173),
                    secondary      = Color(0xFFE94560),
                    background     = Color(0xFF0D0D1A),
                    surface        = Color(0xFF1A1A2E),
                    onBackground   = Color(0xFFE0E0E0),
                    onSurface      = Color(0xFFE0E0E0),
                )
            ) {
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
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
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
                                Text(
                                    text = data.msgs,
                                    color = Color(0xFFE0E0E0),
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp,
                                    modifier = Modifier.weight(1f).verticalScroll(scrollState)
                                )
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { infoDialogState = null }) {
                                        Text("Zur App", color = Color(0xFF9090AA), fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { 
                                            infoDialogState = null
                                            this@MainActivity.finishAffinity()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560))
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
            val msgs = intent.getStringExtra("info_msgs")
            if (!msgs.isNullOrEmpty()) {
                val title = intent.getStringExtra("info_title") ?: "Meldungen"
                infoDialogState = InfoDialogData(title, msgs)
            }
        }
    }
}

// ── Farben und Design-Token ──────────────────────────────────────────────────

private val DarkBg     = Color(0xFF0D0D1A)
private val CardBg     = Color(0xFF1A1A2E)
private val Teal       = Color(0xFF0F7173)
private val Red        = Color(0xFFE94560)
private val TextMain   = Color(0xFFE0E0E0)
private val TextSub    = Color(0xFF9090AA)

// ── Screen ───────────────────────────────────────────────────────────────────

enum class AppScreen(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DASHBOARD("Abfahrten", Icons.Default.Dashboard),
    SEARCH("Suchen", Icons.Default.Search),
    FAVORITES("Favoriten", Icons.Default.Star),
    OPTIONS("Optionen", Icons.Default.Settings),
    HELP("Hilfe", Icons.Default.Info)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ConfigurationScreen(repo: FavoritesRepository, onInfoClick: (InfoDialogData) -> Unit = {}) {
    var currentScreen by remember { mutableStateOf(AppScreen.DASHBOARD) }
    val activeStationName by repo.effectiveStationName.collectAsState(initial = "Laden...")

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = {
                Column {
                    Text(
                        "Hannover Abfahrten Stadtbahnen",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp,
                        color      = TextMain
                    )
                    Text(
                        text = if (currentScreen == AppScreen.DASHBOARD) "Widget Vorschau · $activeStationName" else "Aktiv: $activeStationName",
                        fontSize = 12.sp,
                        color    = if (currentScreen == AppScreen.DASHBOARD) Color(0xFFFFB300) else Teal,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = CardBg) {
                AppScreen.values().forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Teal,
                            selectedTextColor = Teal,
                            unselectedIconColor = TextSub,
                            unselectedTextColor = TextSub,
                            indicatorColor = Teal.copy(alpha = 0.2f)
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
    
    val activeStationId by repo.activeStationId.collectAsState(initial = null)
    val activeStationName by repo.effectiveStationName.collectAsState(initial = "Laden...")
    val favorites by repo.favoritesFlow.collectAsState(initial = emptyList())
    
    val maxFavorites by repo.maxFavoritesFlow.collectAsState(initial = 3)
    val maxFavRows by repo.maxFavRowsFlow.collectAsState(initial = 1)
    val maxRows by repo.maxRowsFlow.collectAsState(initial = 10)
    val transportFilters by repo.transportTypesFlow.collectAsState(initial = setOf("Stadtbahn", "Bus", "S-Bahn"))
    
    var departures by remember { mutableStateOf<List<FlatDeparture>>(emptyList()) }
    var rawDepartures by remember { mutableStateOf<List<de.dhde.hannover.departures.widget.api.DepartureItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var lastUpdate by remember { mutableStateOf<java.time.Instant?>(null) }
    
    // States matching the widget
    var tabState by remember { mutableStateOf("ALL") }
    var directionState by remember { mutableStateOf("ALL") }
    var timeDisplayMode by remember { mutableStateOf("MIN") }

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
        // Globaler Transport-Filter aus den Optionen
        val globalTypeMatch = when {
            it.isBus -> "Bus" in transportFilters
            it.isTram -> "Stadtbahn" in transportFilters
            it.isTrain -> "S-Bahn" in transportFilters
            else -> true
        }
        if (!globalTypeMatch) return@filter false

        // Stations-spezifischer Linien-Filter
        val currentFav = favorites.find { fav -> fav.id == activeStationId }
        val linesFilter = currentFav?.filteredLines
        if (linesFilter != null && it.lineShort !in linesFilter) return@filter false

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

    val messagesDeps = rawDepartures.flatMap { it.toFlatRows() }.filter { it.messages.isNotEmpty() }
    val hasMessages = messagesDeps.isNotEmpty()
    val groupedMessages = if (hasMessages) {
        messagesDeps.groupBy { it.lineShort }
            .map { (line, deps) -> "Linie $line:\n" + deps.flatMap { it.messages }.distinct().joinToString("\n") }
            .joinToString("\n\n")
    } else ""

    Column(
        modifier = Modifier.fillMaxSize().background(DarkBg)
    ) {
        // "Widget Vorschau" label
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A2E))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Widget Vorschau",
                color = TextSub,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF121212)) // Widget Bg
                .padding(12.dp)
        ) {
            // Background Icon
            if (tabState != "ALL") {
                val iconRes = if (tabState == "BUS") R.drawable.ic_widget_bus else R.drawable.ic_widget_tram
                Icon(
                    androidx.compose.ui.res.painterResource(iconRes),
                    contentDescription = null,
                    tint = Color(0xFF141F14), // Subtle dark green
                    modifier = Modifier.fillMaxSize().padding(32.dp)
                )
            }

        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            WidgetHeader(
                stationName = activeStationName,
                isRefreshing = isLoading,
                hasMessages = hasMessages,
                onInfoClick = { onInfoClick(InfoDialogData("Meldungen: $activeStationName", groupedMessages)) },
                onRefresh = { activeStationId?.let { loadData(it) } }
            )
            
            // Filter Segmented Row
            WidgetFilterRow(
                departures = rawDepartures,
                tabState = tabState,
                directionState = directionState,
                onTabChange = { tabState = it },
                onDirChange = { directionState = it }
            )

            val minutesSinceUpdate = lastUpdate?.let { java.time.Duration.between(it, java.time.Instant.now()).toMinutes() } ?: 0
            val isWarning = minutesSinceUpdate >= 10

            // List
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filtered.take(maxRows)) { dep ->
                        WidgetFlatDepartureRow(dep, timeDisplayMode, isWarning, onInfoClick = onInfoClick)
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
                currentStationId = activeStationId,
                maxFavorites = maxFavorites,
                maxFavRows = maxFavRows,
                onSelect = { scope.launch { repo.setActiveStation(it.id, it.name) } }
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
fun WidgetHeader(stationName: String, isRefreshing: Boolean, hasMessages: Boolean = false, onInfoClick: () -> Unit = {}, onRefresh: () -> Unit) {
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
            Icon(
                androidx.compose.ui.res.painterResource(android.R.drawable.ic_dialog_info),
                contentDescription = "Info",
                tint = Color(0xFFFFB300),
                modifier = Modifier.size(20.dp).padding(end = 4.dp).clickable { onInfoClick() }
            )
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
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.padding(end = 8.dp).size(20.dp)
        )
        if (isRefreshing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF4285F4), strokeWidth = 2.dp)
        } else {
            Icon(
                Icons.Default.Refresh,
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
    tabState: String, 
    directionState: String,
    onTabChange: (String) -> Unit,
    onDirChange: (String) -> Unit
) {
    val hasH = departures.any { it.lineId?.endsWith("H") == true }
    val hasR = departures.any { it.lineId?.endsWith("R") == true }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vehicle Type Filters
        Row(verticalAlignment = Alignment.CenterVertically) {
            WidgetSegmentButton(R.drawable.ic_widget_bus, tabState == "BUS", Color(0xFFE94560)) { onTabChange(if (tabState == "BUS") "ALL" else "BUS") }
            Spacer(modifier = Modifier.width(4.dp))
            WidgetSegmentButton(R.drawable.ic_widget_tram, tabState == "TRAIN", Color(0xFF005A9B)) { onTabChange(if (tabState == "TRAIN") "ALL" else "TRAIN") }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Direction Filters
        if (hasH || hasR) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasH) {
                    WidgetSegmentButton(R.drawable.ic_widget_city, directionState == "H", Color(0xFF0F7173)) { onDirChange(if (directionState == "H") "ALL" else "H") }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (hasH && hasR) {
                    WidgetSegmentButton(R.drawable.ic_widget_all, directionState == "ALL", Color.Gray) { onDirChange("ALL") }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (hasR) {
                    WidgetSegmentButton(R.drawable.ic_widget_home, directionState == "R", Color(0xFFE94560)) { onDirChange(if (directionState == "R") "ALL" else "R") }
                }
            }
        }
    }
}

@Composable
fun WidgetSegmentButton(iconRes: Int, isActive: Boolean, activeColor: Color, onClick: () -> Unit) {
    val bgColor = if (isActive) activeColor else Color(0xFF2A2A2A)
    Box(
        modifier = Modifier
            .size(32.dp, 24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            androidx.compose.ui.res.painterResource(iconRes),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun WidgetFlatDepartureRow(dep: FlatDeparture, timeDisplayMode: String, isWarning: Boolean, onInfoClick: (InfoDialogData) -> Unit = {}) {
    val rowBgColor = when {
        dep.lineId?.endsWith("H", ignoreCase = true) == true -> Color(0x14FFFFFF)
        dep.lineId?.endsWith("R", ignoreCase = true) == true -> Color(0x4D000000)
        else -> Color.Transparent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(rowBgColor)
            .padding(vertical = 4.dp, horizontal = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetLineBadge(dep.lineShort, dep.isBus)
            Spacer(modifier = Modifier.width(8.dp))
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
                dep.isCancelled -> Color(0xFFE94560)
                hasDelay -> Color(0xFFFF9800)
                else -> Color(0xFF4CAF50)
            }
            
            val fontStyle = if (isWarning) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal

            Text(
                text = finalTimeText,
                color = timeColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontStyle = fontStyle
            )
        }

        if (dep.isCancelled) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 42.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fahrt entfällt",
                    color = Color(0xFFE94560),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}

@Composable
fun WidgetLineBadge(line: String, isBus: Boolean) {
    val (bgColor, textColor) = if (isBus) {
        val isSprintH = line.length == 3 && line[0] in '3'..'9' && line.substring(1) == "00"
        if (isSprintH) Color(0xFFB42082) to Color.White
        else Color(0xFFE3001B) to Color.White
    } else {
        when (line) {
            "1", "2", "8" -> Color(0xFFE3001B) to Color.White
            "3", "7", "9", "13" -> Color(0xFF005A9B) to Color.White
            "4", "5", "6", "11" -> Color(0xFFFFCC00) to Color.Black
            "10", "17" -> Color(0xFF009A44) to Color.White
            else -> Color.Gray to Color.White
        }
    }

    Box(
        modifier = Modifier
            .size(34.dp, 24.dp)
            .background(bgColor, RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = line,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun WidgetFavoritesRow(
    favorites: List<de.dhde.hannover.departures.widget.data.FavoriteStation>, 
    currentStationId: String?, 
    maxFavorites: Int,
    maxFavRows: Int,
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
                    val isActive = fav.id == currentStationId
                    val bgColor = if (isActive) Color(0xFF005A9B) else Color(0xFF2A2A2A)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(bgColor)
                            .clickable { onSelect(fav) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = shortLabel,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
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
        modifier = Modifier.fillMaxSize().background(DarkBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionLabel("Haltestelle suchen")
            OutlinedTextField(
                value         = query,
                onValueChange = { query = it },
                placeholder   = { Text("z.B. Kröpcke, Roderbruch…", color = TextSub) },
                leadingIcon   = {
                    if (isSearching)
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Teal, strokeWidth = 2.dp)
                    else
                        Icon(Icons.Default.Search, null, tint = TextSub)
                },
                singleLine  = true,
                colors      = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Teal,
                    unfocusedBorderColor = Color(0xFF3A3A5A),
                    focusedTextColor     = TextMain,
                    unfocusedTextColor   = TextMain,
                    cursorColor          = Teal
                ),
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp)
            )

            searchError?.let {
                Text(it, color = Red, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
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
                            if (isFav) repo.removeFavorite(location.id)
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
    val activeStationId by repo.activeStationId.collectAsState(initial = null)

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
        val currentIds = listState.map { it.id }.toSet()
        val repoIds = favoritesFromRepo.map { it.id }.toSet()
        
        if (currentIds != repoIds) {
            // Elemente wurden hinzugefügt oder entfernt -> kompletter Reset
            listState = favoritesFromRepo
        } else {
            // Die Elemente sind gleich. Wir updaten die Eigenschaften (Alias, Filter), 
            // behalten aber die lokale Reihenfolge von listState, um Drag&Drop nicht zu stören.
            val repoMap = favoritesFromRepo.associateBy { it.id }
            listState = listState.mapNotNull { localFav ->
                repoMap[localFav.id]
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DarkBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionLabel("Meine Favoriten") }

        if (listState.isEmpty()) {
            item {
                Text("Noch keine Favoriten. Suche eine Haltestelle und tippe auf ⭐.", color = TextSub, fontSize = 13.sp)
            }
        } else {
            itemsIndexed(listState, key = { _, it -> it.id }) { index, fav ->
                val isActive = fav.id == activeStationId
                FavoriteRow(
                    fav      = fav,
                    isActive = isActive,
                    onSelect = { scope.launch { repo.setActiveStation(fav.id, fav.name) } },
                    onDelete = { scope.launch { repo.removeFavorite(fav.id)         } },
                    onAlias  = { alias -> scope.launch { repo.setFavoriteAlias(fav.id, alias) } },
                    onFilter = { filterDialogFav = fav },
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
                            itemHeights[listState[targetIndex].id] ?: 0f
                        } else 0f
                    },
                    onHeightMeasured = { height ->
                        itemHeights[fav.id] = height
                    },
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
        modifier = Modifier.fillMaxSize().background(DarkBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionLabel("Hilfe & Tipps") }
        item {
            val context = LocalContext.current
            val intentHandler = { url: String ->
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                context.startActivity(intent)
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { intentHandler("https://www.buymeacoffee.com/dhde") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFDD00)),
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
                    border = androidx.compose.foundation.BorderStroke(1.dp, TextSub)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextMain)
                    Spacer(Modifier.width(8.dp))
                    Text("Quellcode auf GitHub", color = TextMain)
                }
            }

        }
        item { HelpCard() }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Rechtlicher Hinweis",
                        color = Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Dies ist eine inoffizielle App. Sie steht in keiner Verbindung zur ÜSTRA Hannoversche Verkehrsbetriebe AG oder dem GVH. Alle Daten werden über öffentliche Schnittstellen bezogen. Nutzung auf eigene Gefahr.",
                        color = TextSub,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

// ── Composable Helpers ───────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text     = text.uppercase(),
        color    = TextSub,
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
            containerColor = if (isActive) Color(0xFF0F2A2A) else CardBg
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
                    color      = if (isActive) Teal else TextMain,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (displayName != location.name) {
                        Text(location.name, color = TextSub, fontSize = 11.sp)
                    }
                    if (distanceText != null) {
                        if (displayName != location.name) {
                            Text(" • ", color = TextSub, fontSize = 11.sp)
                        }
                        Text(distanceText, color = TextSub, fontSize = 11.sp)
                    }
                }
            }
            if (isActive) {
                Text("AKTIV", color = Teal, fontWeight = FontWeight.Bold, fontSize = 10.sp, 
                    modifier = Modifier.background(Teal.copy(0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp))
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = onToggleFav) {
                Icon(
                    if (isFav) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = if (isFav) "Favorit entfernen" else "Favorit hinzufügen",
                    tint = if (isFav) Color(0xFFFFD700) else TextSub
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
    onMove: (Int, Int) -> Unit,
    getHeight: (Int) -> Float,
    onHeightMeasured: (Float) -> Unit,
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
            containerColor = if (isActive) Color(0xFF0F2A2A) else CardBg
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
                tint = if (dragOffset != 0f) Teal else TextSub,
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
            
            Column(Modifier.weight(1f)) {
                // Remove "Hannover " prefix if there is no alias, to save space
                val cleanName = fav.name.removePrefix("Hannover ").trim()
                val displayName = fav.alias ?: cleanName.substringAfter(", ").ifBlank { cleanName }
                
                Text(
                    text       = displayName,
                    color      = if (isActive) Teal else TextMain,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp,
                    maxLines   = 1,
                    overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                // Only show the official name as subtitle if an alias is actively used
                if (!fav.alias.isNullOrBlank()) {
                    Text(
                        text     = fav.name, 
                        color    = TextSub, 
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = { showAliasDialog = true }) {
                Icon(Icons.Default.Edit, null, tint = Teal, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onFilter) {
                Icon(Icons.Default.FilterList, "Linien filtern", tint = if (fav.filteredLines != null) Color(0xFFFFD700) else Teal, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = Red, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun HelpCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            HelpItem(
                icon = { 
                    Text("1", color = Teal, fontWeight = FontWeight.Bold, fontSize = 18.sp) 
                },
                title = "Favoriten-Schnellwahl",
                description = "Nutze die Schnellwahl-Tasten ganz unten im Widget oder tippe oben auf den Stationsnamen, um durch die Haltestellen zu schalten."
            )
            HorizontalDivider(color = Color(0xFF333344), thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
            HelpItem(
                icon = { 
                    Row {
                        Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_widget_city), null, tint = Teal, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(2.dp))
                        Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_widget_home), null, tint = Red, modifier = Modifier.size(18.dp))
                    }
                },
                title = "Richtungen & Filter",
                description = "Nutze die Symbole für City oder Home, um die Richtung zu filtern. Die Zeilen im Widget färben sich automatisch passend (hell/dunkel)."
            )
            HorizontalDivider(color = Color(0xFF333344), thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
            HelpItem(
                icon = { 
                    Text("5", color = Teal, fontWeight = FontWeight.Bold, fontSize = 18.sp) 
                },
                title = "Zeit-Ansicht",
                description = "Tippe im Widget auf eine Abfahrtszeit, um zwischen Countdown (Minuten) und genauer Uhrzeit hin- und herzuschalten."
            )
            HorizontalDivider(color = Color(0xFF333344), thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
            HelpItem(
                icon = { 
                    Icon(Icons.Default.Refresh, null, tint = Teal, modifier = Modifier.size(20.dp))
                },
                title = "Aktualisierung",
                description = "Das Widget frischt sich ca. alle 15 Minuten selbst auf. Für sofortige Echtzeitdaten tippe auf den kleinen Refresh-Pfeil oben rechts."
            )
            HorizontalDivider(color = Color(0xFF333344), thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
            HelpItem(
                icon = { 
                    Icon(Icons.Default.LocationOn, null, tint = Teal, modifier = Modifier.size(20.dp))
                },
                title = "GPS-Suche",
                description = "Tippe auf das Standort-Icon, damit das Widget automatisch Abfahrten der nächstgelegenen Haltestelle anzeigt."
            )
        }
    }
}

@Composable
private fun HelpItem(icon: @Composable () -> Unit, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.padding(top = 2.dp).size(24.dp), contentAlignment = Alignment.Center) {
            icon()
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(description, color = TextSub, fontSize = 12.sp)
        }
    }
}

@Composable
fun OptionsScreen(repo: de.dhde.hannover.departures.widget.data.FavoritesRepository) {
    val scope = rememberCoroutineScope()
    val maxFavsFlow by repo.maxFavoritesFlow.collectAsState(initial = 3)
    val maxFavRowsFlow by repo.maxFavRowsFlow.collectAsState(initial = 1)
    val maxRowsFlow by repo.maxRowsFlow.collectAsState(initial = 10)
    val transportTypes by repo.transportTypesFlow.collectAsState(initial = setOf("Stadtbahn", "Bus", "S-Bahn"))
    
    var localMaxFavs by remember(maxFavsFlow) { mutableStateOf(maxFavsFlow.toFloat()) }
    var localMaxFavRows by remember(maxFavRowsFlow) { mutableStateOf(maxFavRowsFlow.toFloat()) }
    var localMaxRows by remember(maxRowsFlow) { mutableStateOf(maxRowsFlow.toFloat()) }
    
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DarkBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                Button(
                    onClick = {
                        val myProvider = android.content.ComponentName(context, de.dhde.hannover.departures.widget.widget.DeparturesWidgetReceiver::class.java)
                        appWidgetManager.requestPinAppWidget(myProvider, null, null)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Widget zum Startbildschirm hinzufügen", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        item { SectionLabel("Verkehrsmittel") }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Wähle aus, welche Verkehrsmittel angezeigt werden sollen", color = TextSub, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    val allTypes = listOf("Stadtbahn", "Bus", "S-Bahn")
                    allTypes.forEach { type ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                val newSet = if (type in transportTypes) transportTypes - type else transportTypes + type
                                if (newSet.isNotEmpty()) {
                                    scope.launch { repo.setTransportTypes(newSet) }
                                }
                            }.padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = type in transportTypes,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = Teal, uncheckedColor = TextSub)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(type, color = TextMain, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    
        item { SectionLabel("Widget Einstellungen") }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Favoriten pro Zeile", color = Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Anzahl der Schnellwahl-Tasten pro Zeile (0 bis 5)", color = TextSub, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = localMaxFavs,
                        onValueChange = { localMaxFavs = it },
                        onValueChangeFinished = { scope.launch { repo.setMaxFavorites(localMaxFavs.roundToInt()) } },
                        valueRange = 0f..5f,
                        steps = 4,
                        colors = SliderDefaults.colors(
                            thumbColor = Teal,
                            activeTrackColor = Teal,
                            inactiveTrackColor = Color(0xFF333344)
                        )
                    )
                    Text("${localMaxFavs.roundToInt()} Knöpfe pro Zeile", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.End))

                    HorizontalDivider(color = Color(0xFF333344), thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                    Text("Zeilen für Favoriten", color = Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Anzahl der Zeilen für Schnellwahl-Tasten (1 bis 3)", color = TextSub, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = localMaxFavRows,
                        onValueChange = { localMaxFavRows = it },
                        onValueChangeFinished = { scope.launch { repo.setMaxFavRows(localMaxFavRows.roundToInt()) } },
                        valueRange = 1f..3f,
                        steps = 1,
                        colors = SliderDefaults.colors(
                            thumbColor = Teal,
                            activeTrackColor = Teal,
                            inactiveTrackColor = Color(0xFF333344)
                        )
                    )
                    Text("${localMaxFavRows.roundToInt()} Zeilen", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.End))

                    HorizontalDivider(color = Color(0xFF333344), thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                    Text("Maximale Abfahrten", color = Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Zeilen im Widget (1 bis 15, 15 = unbegrenzt)", color = TextSub, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = localMaxRows,
                        onValueChange = { localMaxRows = it },
                        onValueChangeFinished = { scope.launch { repo.setMaxRows(localMaxRows.roundToInt()) } },
                        valueRange = 1f..15f,
                        steps = 13,
                        colors = SliderDefaults.colors(
                            thumbColor = Teal,
                            activeTrackColor = Teal,
                            inactiveTrackColor = Color(0xFF333344)
                        )
                    )
                    val rowLabel = if (localMaxRows.roundToInt() >= 15) "Unbegrenzt" else "${localMaxRows.roundToInt()} Zeilen"
                    Text(rowLabel, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.End))
                }
            }
        }
    }
    
    LaunchedEffect(maxFavsFlow, maxFavRowsFlow, maxRowsFlow, transportTypes) {
        de.dhde.hannover.departures.widget.widget.DeparturesWidget().updateAll(context)
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
            Text("Linien filtern: $titleName", color = Teal, fontSize = 18.sp, fontWeight = FontWeight.Bold) 
        },
        text = {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Teal)
                }
            } else if (availableLines.isEmpty()) {
                Text("Konnte aktuell keine Linien für diese Station finden.", color = TextSub)
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
                                colors = CheckboxDefaults.colors(checkedColor = Teal, uncheckedColor = TextSub)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Linie $line", color = TextMain, fontSize = 16.sp)
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
                        repo.setFavoriteLineFilter(fav.id, filterToSave)
                        onDismiss()
                    }
                },
                enabled = hasLoaded
            ) { Text("Speichern", color = Teal) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen", color = TextSub) }
        },
        containerColor = CardBg,
        titleContentColor = Teal,
        textContentColor = TextMain
    )
}


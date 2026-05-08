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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import de.dhde.hannover.departures.widget.api.StationSearchResult
import de.dhde.hannover.departures.widget.api.UestraApi
import de.dhde.hannover.departures.widget.data.FavoritesRepository
import de.dhde.hannover.departures.widget.widget.WidgetTickerWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.glance.appwidget.updateAll
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permission result handled silently */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                ConfigurationScreen(repo)
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
    SEARCH("Suchen", Icons.Default.Search),
    OPTIONS("Optionen", Icons.Default.Settings),
    FAVORITES("Favoriten", Icons.Default.Star),
    HELP("Hilfe", Icons.Default.Info)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ConfigurationScreen(repo: FavoritesRepository) {
    var currentScreen by remember { mutableStateOf(AppScreen.SEARCH) }
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
                            "Aktiv: $activeStationName",
                            fontSize = 12.sp,
                            color    = Teal,
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
                AppScreen.SEARCH -> SearchScreen(repo)
                AppScreen.OPTIONS -> OptionsScreen(repo)
                AppScreen.FAVORITES -> FavoritesScreen(repo)
                AppScreen.HELP -> HelpScreen()
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
                    onSelect = {
                        scope.launch {
                            repo.setActiveStation(location.id, location.name)
                            query   = ""
                        }
                    },
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
    val favorites by repo.favoritesFlow.collectAsState(initial = emptyList())
    val activeStationId by repo.activeStationId.collectAsState(initial = null)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DarkBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionLabel("Meine Favoriten") }

        if (favorites.isEmpty()) {
            item {
                Text("Noch keine Favoriten. Suche eine Haltestelle und tippe auf ⭐.", color = TextSub, fontSize = 13.sp)
            }
        } else {
            items(favorites) { fav ->
                val isActive = fav.id == activeStationId
                FavoriteRow(
                    fav      = fav,
                    isActive = isActive,
                    onSelect = { scope.launch { repo.setActiveStation(fav.id, fav.name) } },
                    onDelete = { scope.launch { repo.removeFavorite(fav.id)         } },
                    onMove   = { up -> scope.launch { repo.moveFavorite(fav.id, up) } },
                    onAlias  = { alias -> scope.launch { repo.setFavoriteAlias(fav.id, alias) } }
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
        item { HelpCard() }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Teal.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Support & Open Source", color = Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    
                    val context = LocalContext.current
                    val intentHandler = { url: String ->
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        context.startActivity(intent)
                    }

                    Button(
                        onClick = { intentHandler("https://www.buymeacoffee.com/dhde") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFDD00)),
                        modifier = Modifier.wrapContentWidth().align(Alignment.CenterHorizontally),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.LocalCafe, contentDescription = null, tint = Color.Black)
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
        }
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
    onSelect: () -> Unit,
    onToggleFav: () -> Unit
) {
    Card(
        colors  = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFF0F2A2A) else CardBg
        ),
        shape   = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                // Anzeige-Name: Ortsteil/Halt bevorzugen, falls Komma vorhanden
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
    onMove: (Boolean) -> Unit,
    onAlias: (String?) -> Unit
) {
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

    Card(
        colors  = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFF0F2A2A) else CardBg
        ),
        shape   = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { onMove(true) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, null, tint = TextSub)
                }
                IconButton(onClick = { onMove(false) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = TextSub)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                val displayName = fav.alias ?: fav.name.substringAfter(", ").ifBlank { fav.name }
                Text(
                    displayName,
                    color      = if (isActive) Teal else TextMain,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp
                )
                Text(fav.name, color = TextSub, fontSize = 11.sp)
            }
            IconButton(onClick = { showAliasDialog = true }) {
                Icon(Icons.Default.Edit, null, tint = Teal, modifier = Modifier.size(20.dp))
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
                    Icon(androidx.compose.material.icons.Icons.Default.Refresh, null, tint = Teal, modifier = Modifier.size(20.dp))
                },
                title = "Aktualisierung",
                description = "Das Widget frischt sich ca. alle 15 Minuten selbst auf. Für sofortige Echtzeitdaten tippe auf den kleinen Refresh-Pfeil oben rechts."
            )
            HorizontalDivider(color = Color(0xFF333344), thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
            HelpItem(
                icon = { 
                    Icon(androidx.compose.material.icons.Icons.Default.LocationOn, null, tint = Teal, modifier = Modifier.size(20.dp))
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
    
    var localMaxFavs by remember(maxFavsFlow) { mutableStateOf(maxFavsFlow.toFloat()) }
    var localMaxFavRows by remember(maxFavRowsFlow) { mutableStateOf(maxFavRowsFlow.toFloat()) }
    var localMaxRows by remember(maxRowsFlow) { mutableStateOf(maxRowsFlow.toFloat()) }
    
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DarkBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
    
    LaunchedEffect(maxFavsFlow, maxFavRowsFlow, maxRowsFlow) {
        de.dhde.hannover.departures.widget.widget.DeparturesWidget().updateAll(context)
    }
}

package com.uestra.widgetapp

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
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.filled.Info
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
import com.uestra.widgetapp.api.StationSearchResult
import com.uestra.widgetapp.api.UestraApi
import com.uestra.widgetapp.data.FavoritesRepository
import com.uestra.widgetapp.widget.WidgetTickerWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
                            "Üstra Widget",
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
    var results    by remember { mutableStateOf<List<StationSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    val favorites        by repo.favoritesFlow.collectAsState(initial = emptyList())
    val activeStationId  by repo.activeStationId.collectAsState(initial = null)

    val context = LocalContext.current
    val stopsRepo = remember { com.uestra.widgetapp.data.StopsRepository(context) }

    // Debounced search
    LaunchedEffect(query) {
        if (query.length < 2) {
            results     = emptyList()
            searchError = null
            return@LaunchedEffect
        }
        delay(400)
        isSearching = true
        searchError = null
        try {
            val allStops = stopsRepo.getAllStops()
            results = allStops.filter { it.name.contains(query, ignoreCase = true) }.take(15)
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
            items(results) { location ->
                val isFav   = favorites.any { it.id == location.id }
                val isActive = location.id == activeStationId

                SearchResultRow(
                    location = location,
                    isFav    = isFav,
                    isActive = isActive,
                    onSelect = {
                        scope.launch {
                            repo.setActiveStation(location.id, location.name)
                            query   = ""
                            results = emptyList()
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
                if (displayName != location.name) {
                    Text(location.name, color = TextSub, fontSize = 11.sp)
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
    fav: com.uestra.widgetapp.data.FavoriteStation,
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
                    Icon(androidx.compose.ui.res.painterResource(android.R.drawable.ic_popup_sync), null, tint = Teal, modifier = Modifier.size(20.dp))
                },
                title = "Aktualisierung",
                description = "Das Widget frischt sich ca. alle 15 Minuten selbst auf. Für sofortige Echtzeitdaten tippe auf den kleinen Refresh-Pfeil oben rechts."
            )
            HorizontalDivider(color = Color(0xFF333344), thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
            HelpItem(
                icon = { 
                    Icon(androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_mylocation), null, tint = Teal, modifier = Modifier.size(20.dp))
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
        Column {
            Text(title, color = Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(description, color = TextSub, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

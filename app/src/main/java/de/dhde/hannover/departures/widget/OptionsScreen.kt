package de.dhde.hannover.departures.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
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
import de.dhde.hannover.departures.widget.ui.UestraColors
import de.dhde.hannover.departures.widget.R
import kotlinx.coroutines.launch
import androidx.glance.appwidget.updateAll
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsScreen(repo: de.dhde.hannover.departures.widget.data.FavoritesRepository) {
    val scope = rememberCoroutineScope()
    var showAllMessages by remember { mutableStateOf(false) }
    val maxFavsFlow by repo.maxFavoritesFlow.collectAsState(initial = 3)
    val maxFavRowsFlow by repo.maxFavRowsFlow.collectAsState(initial = 1)
    val maxRowsFlow by repo.maxRowsFlow.collectAsState(initial = 10)
    val transportTypes by repo.transportTypesFlow.collectAsState(initial = setOf("Stadtbahn", "Bus", "S-Bahn"))
    val ignoredMessages by repo.ignoredMessagesFlow.collectAsState(initial = emptySet())
    val seenMessages by repo.seenMessagesFlow.collectAsState(initial = emptyMap())
    val favoritesHeight by repo.favoritesHeightFlow.collectAsState(initial = "STANDARD")
    val filterHeight by repo.filterHeightFlow.collectAsState(initial = "STANDARD")
    val autoRefreshOnInteraction by repo.autoRefreshOnInteractionFlow.collectAsState(initial = false)
    val groupDepartures by repo.groupDeparturesFlow.collectAsState(initial = true)
    val maxGroupedDeparturesFlow by repo.maxGroupedDeparturesFlow.collectAsState(initial = 2)
    val groupedFontSize by repo.groupedFontSizeFlow.collectAsState(initial = "STANDARD")
    val allowDuplicates by repo.allowDuplicatesFlow.collectAsState(initial = false)

    var showDuplicatesWarning by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showMessageSheet by remember { mutableStateOf(false) }

    if (showDuplicatesWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicatesWarning = false },
            title = { Text("Warnung", color = UestraColors.AccentRed) },
            text = { Text("Achtung: Wenn du diese Option ausschaltest, werden alle mehrfach angelegten Haltestellen gelöscht. Möchtest du fortfahren?", color = UestraColors.TextMain) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.setAllowDuplicates(false) }
                    showDuplicatesWarning = false
                }) { Text("Deaktivieren", color = UestraColors.AccentRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicatesWarning = false }) { Text("Abbrechen", color = UestraColors.TextSub) }
            },
            containerColor = UestraColors.CardBg,
            titleContentColor = UestraColors.Teal
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Zurücksetzen", color = UestraColors.AccentRed) },
            text = { Text("Setzt alle Einstellungen auf die Standardwerte zurück. Mehrfach angelegte Haltestellen werden dabei entfernt. Ausgeblendete Meldungen bleiben erhalten.", color = UestraColors.TextMain) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.setTransportTypes(setOf("Stadtbahn", "Bus"))
                        repo.setGroupDepartures(true)
                        repo.setMaxGroupedDepartures(2)
                        repo.setGroupedFontSize("STANDARD")
                        repo.setMaxFavorites(3)
                        repo.setMaxFavRows(1)
                        repo.setFavoritesHeight("STANDARD")
                        repo.setFilterHeight("STANDARD")
                        repo.setMaxRows(10)
                        repo.setAutoRefreshOnInteraction(false)
                        repo.setAllowDuplicates(false)
                    }
                    showResetConfirm = false
                }) { Text("Zurücksetzen", color = UestraColors.AccentRed) }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Abbrechen", color = UestraColors.TextSub) } },
            containerColor = UestraColors.CardBg, titleContentColor = UestraColors.AccentRed
        )
    }

    if (showMessageSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showMessageSheet = false },
            sheetState = sheetState,
            containerColor = UestraColors.CardBg
        ) {
            MessageFilterSheetContent(
                seenMessages = seenMessages,
                ignoredMessages = ignoredMessages,
                transportTypes = transportTypes,
                showAll = showAllMessages,
                onToggleShowAll = { showAllMessages = !showAllMessages },
                repo = repo,
                scope = scope
            )
        }
    }

    var localMaxFavs by remember(maxFavsFlow) { mutableStateOf(maxFavsFlow.toFloat()) }
    var localMaxFavRows by remember(maxFavRowsFlow) { mutableStateOf(maxFavRowsFlow.toFloat()) }
    var localMaxRows by remember(maxRowsFlow) { mutableStateOf(maxRowsFlow.toFloat()) }
    var localMaxGroupedDepartures by remember(maxGroupedDeparturesFlow) { mutableStateOf(maxGroupedDeparturesFlow.toFloat()) }

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(UestraColors.DarkBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { AddWidgetButton(context) }
        item { OptionsGroupHeader("Anzeige") }
        item { TransportTypesCard(transportTypes, repo, scope) }
        item { MessageFilterRow(ignoredCount = ignoredMessages.size) { showMessageSheet = true } }
        item {
            GroupDeparturesCard(
                groupDepartures = groupDepartures,
                localMaxRows = localMaxRows,
                onLocalMaxRowsChange = { localMaxRows = it },
                onMaxRowsFinished = { scope.launch { repo.setMaxRows(localMaxRows.roundToInt()) } },
                localMaxGroupedDepartures = localMaxGroupedDepartures,
                onLocalMaxGroupedDeparturesChange = { localMaxGroupedDepartures = it },
                onMaxGroupedDeparturesFinished = { scope.launch { repo.setMaxGroupedDepartures(localMaxGroupedDepartures.roundToInt()) } },
                groupedFontSize = groupedFontSize,
                repo = repo,
                scope = scope
            )
        }
        item { OptionsGroupHeader("Größe & Layout") }
        item {
            FavoritesLayoutCard(
                favoritesHeight = favoritesHeight,
                localMaxFavs = localMaxFavs,
                onLocalMaxFavsChange = { localMaxFavs = it },
                onMaxFavsFinished = { scope.launch { repo.setMaxFavorites(localMaxFavs.roundToInt()) } },
                localMaxFavRows = localMaxFavRows,
                onLocalMaxFavRowsChange = { localMaxFavRows = it },
                onMaxFavRowsFinished = { scope.launch { repo.setMaxFavRows(localMaxFavRows.roundToInt()) } },
                repo = repo,
                scope = scope
            )
        }
        item { FilterHeightCard(filterHeight, repo, scope) }
        item { OptionsGroupHeader("Verhalten") }
        item { ApiRefreshCard(autoRefreshOnInteraction, repo, scope) }
        item { OptionsGroupHeader("Erweitert") }
        item {
            AdvancedCard(
                allowDuplicates = allowDuplicates,
                onRequestDisable = { showDuplicatesWarning = true },
                repo = repo,
                scope = scope
            )
        }
        item {
            OutlinedButton(
                onClick = { showResetConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = UestraColors.AccentRed)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Auf Standard zurücksetzen")
            }
        }
    }

    LaunchedEffect(maxFavsFlow, maxFavRowsFlow, maxRowsFlow, transportTypes, ignoredMessages, favoritesHeight, filterHeight, autoRefreshOnInteraction, groupDepartures, maxGroupedDeparturesFlow, groupedFontSize, allowDuplicates) {
        de.dhde.hannover.departures.widget.widget.DeparturesWidget().updateAll(context)
    }
}

@Composable
private fun OptionsGroupHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = UestraColors.Teal,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun SizeOptionList(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    preview: @Composable (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { (value, label) ->
            val isSel = value == selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSel) UestraColors.TealSurface else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(10.dp)
            ) {
                Text(label, color = if (isSel) UestraColors.Teal else UestraColors.TextSub,
                    fontSize = 12.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.width(80.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { preview(value) }
            }
        }
    }
}

@Composable
private fun AddWidgetButton(context: android.content.Context) {
    val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
    if (appWidgetManager.isRequestPinAppWidgetSupported) {
        Button(
            onClick = {
                val myProvider = android.content.ComponentName(context, de.dhde.hannover.departures.widget.widget.DeparturesWidgetReceiver::class.java)
                appWidgetManager.requestPinAppWidget(myProvider, null, null)
            },
            colors = ButtonDefaults.buttonColors(containerColor = UestraColors.Teal),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Widget zum Startbildschirm hinzufügen", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun TransportTypesCard(
    transportTypes: Set<String>,
    repo: de.dhde.hannover.departures.widget.data.FavoritesRepository,
    scope: kotlinx.coroutines.CoroutineScope
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UestraColors.CardBg),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Wähle aus, welche Verkehrsmittel angezeigt werden sollen", color = UestraColors.TextSub, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(12.dp))
            val allTypes = listOf("Stadtbahn", "Bus", "S-Bahn", "DB", "Fernbus")
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
                        colors = CheckboxDefaults.colors(checkedColor = UestraColors.Teal, uncheckedColor = UestraColors.TextSub)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(type, color = UestraColors.TextMain, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun MessageFilterRow(ignoredCount: Int, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UestraColors.CardBg),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Meldungen ausblenden", color = UestraColors.Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    if (ignoredCount > 0) "$ignoredCount ausgeblendet" else "Wiederkehrende Meldungen verwalten",
                    color = UestraColors.TextSub, fontSize = 12.sp
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = UestraColors.TextSub)
        }
    }
}

@Composable
private fun MessageFilterSheetContent(
    seenMessages: Map<String, de.dhde.hannover.departures.widget.data.SeenMessageEntry>,
    ignoredMessages: Set<String>,
    transportTypes: Set<String>,
    showAll: Boolean,
    onToggleShowAll: () -> Unit,
    repo: de.dhde.hannover.departures.widget.data.FavoritesRepository,
    scope: kotlinx.coroutines.CoroutineScope
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
    ) {
        Text("Meldungen ausblenden", color = UestraColors.Teal, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Wiederkehrende Meldungen, die du nicht mehr sehen willst.", color = UestraColors.TextSub, fontSize = 12.sp)
        if (ignoredMessages.isNotEmpty()) {
            TextButton(
                onClick = { scope.launch { repo.setIgnoredMessages(emptySet()) } },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Alle wieder einblenden (${ignoredMessages.size})", color = UestraColors.Teal)
            }
        }
        Spacer(Modifier.height(12.dp))

        // Schwellenwert: Meldungen die >= 5x aufgetaucht sind, können gefiltert werden
        val threshold = 5
        val allTypesActive = transportTypes.containsAll(setOf("Stadtbahn", "Bus", "S-Bahn", "DB", "Fernbus"))
        val filterableMessages = seenMessages
            .filter { entry ->
                entry.value.count >= threshold &&
                if (entry.value.transportTypes.isEmpty()) {
                    // Alte Einträge ohne Typ-Info: nur zeigen wenn alle Typen aktiv
                    allTypesActive
                } else {
                    entry.value.transportTypes.any { it in transportTypes }
                }
            }
            .entries
            .sortedByDescending { it.value.count }
        if (filterableMessages.isEmpty()) {
            Text(
                "Noch keine häufigen Meldungen bekannt. Die App lernt automatisch welche " +
                "Infomeldungen regelmäßig von der API kommen (ab $threshold Mal). " +
                "Lade das Widget einige Male neu, um Meldungen hier anzuzeigen.",
                color = UestraColors.TextSub, fontSize = 12.sp
            )
        } else {
            Text(
                "Wähle aus, welche häufig auftauchenden Meldungen ausgeblendet werden sollen:",
                color = UestraColors.TextSub, fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            val displayMessages = if (showAll || filterableMessages.size <= 5) {
                filterableMessages
            } else {
                filterableMessages.take(5)
            }

            displayMessages.forEach { (msgText, entry) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        val newSet = if (msgText in ignoredMessages) ignoredMessages - msgText else ignoredMessages + msgText
                        scope.launch { repo.setIgnoredMessages(newSet) }
                    }.padding(vertical = 6.dp)
                ) {
                    Checkbox(
                        checked = msgText in ignoredMessages,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = UestraColors.Teal, uncheckedColor = UestraColors.TextSub)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(msgText, color = UestraColors.TextMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        val countLabel = if (entry.count >= 10000) "10k+× gesehen" else "${entry.count}× gesehen"
                        Text(countLabel, color = UestraColors.TextSub, fontSize = 11.sp)
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                repo.removeSeenMessage(msgText)
                                // Auch aus ignoredMessages entfernen falls aktiv
                                if (msgText in ignoredMessages) {
                                    repo.setIgnoredMessages(ignoredMessages - msgText)
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Meldung entfernen",
                            tint = UestraColors.IconMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (filterableMessages.size > 5) {
                TextButton(
                    onClick = onToggleShowAll,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(
                        text = if (showAll) "Weniger anzeigen" else "Alle ${filterableMessages.size} anzeigen",
                        color = UestraColors.Teal
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoritesLayoutCard(
    favoritesHeight: String,
    localMaxFavs: Float,
    onLocalMaxFavsChange: (Float) -> Unit,
    onMaxFavsFinished: () -> Unit,
    localMaxFavRows: Float,
    onLocalMaxFavRowsChange: (Float) -> Unit,
    onMaxFavRowsFinished: () -> Unit,
    repo: de.dhde.hannover.departures.widget.data.FavoritesRepository,
    scope: kotlinx.coroutines.CoroutineScope
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UestraColors.CardBg),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Favoriten pro Zeile", color = UestraColors.Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Anzahl der Schnellwahl-Tasten pro Zeile (0 bis 5)", color = UestraColors.TextSub, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = localMaxFavs,
                onValueChange = onLocalMaxFavsChange,
                onValueChangeFinished = onMaxFavsFinished,
                valueRange = 0f..5f,
                steps = 4,
                colors = SliderDefaults.colors(
                    thumbColor = UestraColors.Teal,
                    activeTrackColor = UestraColors.Teal,
                    inactiveTrackColor = UestraColors.Divider
                )
            )
            Text("${localMaxFavs.roundToInt()} Knöpfe pro Zeile", color = UestraColors.TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.End))
            HorizontalDivider(color = UestraColors.Divider, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

            Text("Zeilen für Favoriten", color = UestraColors.Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Anzahl der Zeilen für Schnellwahl-Tasten (1 bis 3)", color = UestraColors.TextSub, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = localMaxFavRows,
                onValueChange = onLocalMaxFavRowsChange,
                onValueChangeFinished = onMaxFavRowsFinished,
                valueRange = 1f..3f,
                steps = 1,
                colors = SliderDefaults.colors(
                    thumbColor = UestraColors.Teal,
                    activeTrackColor = UestraColors.Teal,
                    inactiveTrackColor = UestraColors.Divider
                )
            )
            Text("${localMaxFavRows.roundToInt()} Zeilen", color = UestraColors.TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.End))

            HorizontalDivider(color = UestraColors.Divider, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))



            Text("Höhe der Favoriten-Buttons", color = UestraColors.Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Wähle die visuelle Größe aus", color = UestraColors.TextSub, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            SizeOptionList(
                options = listOf("KOMPAKT" to "Kompakt", "STANDARD" to "Standard", "GROSS" to "Groß"),
                selected = favoritesHeight,
                onSelect = { scope.launch { repo.setFavoritesHeight(it) } }
            ) { mode ->
                FavoriteButtonVisual("Klingerstr.", isSelected = true, mode = mode, modifier = Modifier.widthIn(max = 120.dp))
            }
        }
    }
}

@Composable
private fun FilterHeightCard(
    filterHeight: String,
    repo: de.dhde.hannover.departures.widget.data.FavoritesRepository,
    scope: kotlinx.coroutines.CoroutineScope
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UestraColors.CardBg),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Höhe der Filter-Buttons", color = UestraColors.Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Wähle die visuelle Größe aus", color = UestraColors.TextSub, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            SizeOptionList(
                options = listOf("KOMPAKT" to "Kompakt", "STANDARD" to "Standard", "GROSS" to "Groß"),
                selected = filterHeight,
                onSelect = { scope.launch { repo.setFilterHeight(it) } }
            ) { mode ->
                SegmentButtonVisual(R.drawable.ic_widget_bus, isActive = true, activeColor = UestraColors.AccentRed, mode = mode)
            }
        }
    }
}

@Composable
private fun ApiRefreshCard(
    autoRefreshOnInteraction: Boolean,
    repo: de.dhde.hannover.departures.widget.data.FavoritesRepository,
    scope: kotlinx.coroutines.CoroutineScope
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UestraColors.CardBg),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { scope.launch { repo.setAutoRefreshOnInteraction(!autoRefreshOnInteraction) } }
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("API-Refresh bei Zeitumschaltung", color = UestraColors.Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "Wenn aktiv: Beim Umschalten zwischen Minuten und Uhrzeit werden " +
                        "neue Daten von der API geladen (falls älter als 60 Sek.). " +
                        "Standard: AUS (nur Redraw, kein Netzwerkaufruf).",
                        color = UestraColors.TextSub, fontSize = 12.sp, lineHeight = 18.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = autoRefreshOnInteraction,
                    onCheckedChange = { scope.launch { repo.setAutoRefreshOnInteraction(it) } },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = UestraColors.Teal)
                )
            }
        }
    }
}

@Composable
private fun GroupDeparturesCard(
    groupDepartures: Boolean,
    localMaxRows: Float,
    onLocalMaxRowsChange: (Float) -> Unit,
    onMaxRowsFinished: () -> Unit,
    localMaxGroupedDepartures: Float,
    onLocalMaxGroupedDeparturesChange: (Float) -> Unit,
    onMaxGroupedDeparturesFinished: () -> Unit,
    groupedFontSize: String,
    repo: de.dhde.hannover.departures.widget.data.FavoritesRepository,
    scope: kotlinx.coroutines.CoroutineScope
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UestraColors.CardBg),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { scope.launch { repo.setGroupDepartures(!groupDepartures) } }
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Abfahrten gruppieren", color = UestraColors.Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "Fasst nachfolgende Abfahrten derselben Linie und Richtung in einer Zeile zusammen.",
                        color = UestraColors.TextSub, fontSize = 12.sp, lineHeight = 18.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = groupDepartures,
                    onCheckedChange = { scope.launch { repo.setGroupDepartures(it) } },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = UestraColors.Teal)
                )
            }

                if (!groupDepartures) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Maximale Abfahrten", color = UestraColors.Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Zeilen im Widget (1 bis 15, 15 = unbegrenzt)", color = UestraColors.TextSub, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = localMaxRows,
                        onValueChange = onLocalMaxRowsChange,
                        onValueChangeFinished = onMaxRowsFinished,
                        valueRange = 1f..15f,
                        steps = 13,
                        colors = SliderDefaults.colors(
                            thumbColor = UestraColors.Teal,
                            activeTrackColor = UestraColors.Teal,
                            inactiveTrackColor = UestraColors.Divider
                        )
                    )
                    val rowLabel = if (localMaxRows.roundToInt() >= 15) "Unbegrenzt" else "${localMaxRows.roundToInt()} Zeilen"
                    Text(rowLabel, color = UestraColors.TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.End))
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Zusätzliche Abfahrten pro Linie", color = UestraColors.Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Anzahl weiterer Abfahrten, die klein neben der Hauptzeit angezeigt werden (0 bis 5)", color = UestraColors.TextSub, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = localMaxGroupedDepartures,
                        onValueChange = onLocalMaxGroupedDeparturesChange,
                        onValueChangeFinished = onMaxGroupedDeparturesFinished,
                        valueRange = 0f..5f,
                        steps = 4,
                        colors = SliderDefaults.colors(
                            thumbColor = UestraColors.Teal,
                            activeTrackColor = UestraColors.Teal,
                            inactiveTrackColor = UestraColors.Divider
                        )
                    )
                    Text("${localMaxGroupedDepartures.roundToInt()} weitere Abfahrten", color = UestraColors.TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.End))

                    HorizontalDivider(color = UestraColors.Divider, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                    Text("Schriftgröße der Folge-Abfahrten", color = UestraColors.Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Größe der kleinen Zeitangaben neben der Hauptabfahrt", color = UestraColors.TextSub, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    val sampleMain = remember {
                        de.dhde.hannover.departures.widget.api.FlatDeparture(
                            line = "Stadtbahn 3", lineId = "de:rnv:3:H", destination = "Wettbergen", number = "3",
                            transportTypes = setOf(de.dhde.hannover.departures.widget.api.TransportType.TRAM),
                            departureTime = java.time.Instant.now().plusSeconds(240).toString(),
                            plannedTime = null, estimatedTime = null, delayMinutes = null, lineShort = "3",
                            isCancelled = false, messages = emptyList()
                        )
                    }
                    val sampleSubs = remember {
                        listOf(9L, 14L, 21L, 28L).map { m -> sampleMain.copy(departureTime = java.time.Instant.now().plusSeconds(m * 60).toString()) }
                    }
                    SizeOptionList(
                        options = listOf("KLEIN" to "Klein", "STANDARD" to "Standard", "GROSS" to "Groß"),
                        selected = groupedFontSize,
                        onSelect = { scope.launch { repo.setGroupedFontSize(it) } }
                    ) { mode ->
                        WidgetFlatDepartureRow(sampleMain, sampleSubs, "MIN", false, mode, onInfoClick = {})
                    }
                }
        }
    }
}

@Composable
private fun AdvancedCard(
    allowDuplicates: Boolean,
    onRequestDisable: () -> Unit,
    repo: de.dhde.hannover.departures.widget.data.FavoritesRepository,
    scope: kotlinx.coroutines.CoroutineScope
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UestraColors.CardBg),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (allowDuplicates) {
                            onRequestDisable()
                        } else {
                            scope.launch { repo.setAllowDuplicates(true) }
                        }
                    }
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Haltestellen duplizieren", color = UestraColors.Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "Erlaubt es, dieselbe Haltestelle mehrfach anzulegen, um unterschiedliche Filter (z.B. Richtung Home / City) zu speichern.",
                        color = UestraColors.TextSub, fontSize = 12.sp, lineHeight = 18.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = allowDuplicates,
                    onCheckedChange = {
                        if (!it) {
                            onRequestDisable()
                        } else {
                            scope.launch { repo.setAllowDuplicates(true) }
                        }
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = UestraColors.Teal)
                )
            }
        }
    }
}

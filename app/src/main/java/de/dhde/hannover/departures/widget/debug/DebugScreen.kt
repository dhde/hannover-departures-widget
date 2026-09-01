package de.dhde.hannover.departures.widget.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dhde.hannover.departures.widget.data.WidgetSessionStore
import de.dhde.hannover.departures.widget.ui.UestraColors
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext

@Composable
fun DebugScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionStore = remember { WidgetSessionStore(context) }
    val entries by DebugLog.entries.collectAsState()
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize().background(UestraColors.DarkBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                text = "${entries.size} Zeilen",
                color = UestraColors.TextSub,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                scope.launch { listState.animateScrollToItem(maxOf(0, entries.size - 1)) }
            }) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Nach unten", tint = UestraColors.TextMain)
            }
            IconButton(onClick = { DebugLog.clear() }) {
                Icon(Icons.Default.Delete, contentDescription = "Leeren", tint = UestraColors.TextMain)
            }
        }

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(
                    "Noch keine Debug-Ausgaben.\nWidget-Refresh o.ä. auslösen.",
                    color = UestraColors.TextSub,
                    fontSize = 12.sp,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            ) {
                items(entries) { entry ->
                    DebugLine(entry)
                }
            }
        }

        OutlinedButton(
            onClick = {
                scope.launch {
                    sessionStore.setDebugMode(false)
                    android.widget.Toast.makeText(
                        context,
                        "Debug-Modus deaktiviert",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Text("Debug-Modus deaktivieren", color = UestraColors.TextMain)
        }
    }
}

@Composable
private fun DebugLine(entry: DebugLog.Entry) {
    val time = remember(entry.timestampMs) {
        val d = java.util.Date(entry.timestampMs)
        val fmt = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.GERMAN)
        fmt.format(d)
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row {
            Text(
                text = time,
                color = UestraColors.TextSub,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = entry.message,
                color = UestraColors.TextMain,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (entry.stack != null) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
        entry.stack?.let { stack ->
            Text(
                text = stack,
                color = Color(0xFFFFB3B3),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 24.dp, top = 2.dp),
            )
        }
    }
}

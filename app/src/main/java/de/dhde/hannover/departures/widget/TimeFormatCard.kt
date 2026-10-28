package de.dhde.hannover.departures.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dhde.hannover.departures.widget.data.FavoritesRepository
import de.dhde.hannover.departures.widget.data.TimeFormat
import de.dhde.hannover.departures.widget.ui.UestraColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun TimeFormatCard(
    countdownFormat: String,
    clockFormat: String,
    repo: FavoritesRepository,
    scope: CoroutineScope
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UestraColors.CardBg),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Zeitformat", color = UestraColors.Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Tippen auf die Zeit wechselt zwischen Countdown und Uhrzeit", color = UestraColors.TextSub, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            FormatPicker(
                title = "Countdown",
                presets = TimeFormat.COUNTDOWN_PRESETS,
                current = countdownFormat,
                help = "{m} Minuten · {s} Sekunden · {ss} Sekunden zweistellig\n" +
                    "[ … ] entfällt, wenn die Werte darin 0 sind\n" +
                    "Sekunden erst in den letzten 2 Minuten",
                isValid = TimeFormat::isValidCountdownPattern,
                preview = { p -> listOf(462L, 102L, 35L).joinToString(" · ") { TimeFormat.formatCountdown(p, it) } },
                onSave = { scope.launch { repo.setCountdownFormat(it) } }
            )
            Spacer(modifier = Modifier.height(12.dp))
            FormatPicker(
                title = "Uhrzeit",
                presets = TimeFormat.CLOCK_PRESETS,
                current = clockFormat,
                help = "HH Stunde · mm Minute · ss Sekunde · a AM/PM\n" +
                    "Fester Text in 'Anführungszeichen', z. B. HH:mm 'Uhr'",
                isValid = TimeFormat::isValidClockPattern,
                preview = { p -> TimeFormat.formatClock(p, Instant.now()) },
                onSave = { scope.launch { repo.setClockFormat(it) } }
            )
        }
    }
}

@Composable
private fun FormatPicker(
    title: String,
    presets: List<Pair<String, String>>,
    current: String,
    help: String,
    isValid: (String) -> Boolean,
    preview: (String) -> String,
    onSave: (String) -> Unit
) {
    // null = aus gespeichertem Muster ableiten; danach entscheidet die Auswahl des Nutzers
    var customChoice by remember { mutableStateOf<Boolean?>(null) }
    val customMode = customChoice ?: presets.none { it.first == current }
    var text by remember { mutableStateOf(current) }
    val radioColors = RadioButtonDefaults.colors(selectedColor = UestraColors.Teal)

    Text(title, color = UestraColors.TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    presets.forEach { (pattern, label) ->
        val select = { customChoice = false; onSave(pattern) }
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = select),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = !customMode && current == pattern, onClick = select, colors = radioColors)
            Text(label, color = UestraColors.TextMain, fontSize = 14.sp)
        }
    }
    val selectCustom = { customChoice = true; text = current }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = selectCustom),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = customMode, onClick = selectCustom, colors = radioColors)
        Text("Eigenes Format…", color = UestraColors.TextMain, fontSize = 14.sp)
    }
    if (customMode) {
        val valid = isValid(text)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; if (isValid(it)) onSave(it) },
            isError = !valid,
            singleLine = true,
            label = { Text("Muster") },
            modifier = Modifier.fillMaxWidth()
        )
        Text(help, color = UestraColors.TextSub, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        Text(
            text = if (valid) "Vorschau: ${preview(text)}" else "Ungültiges Format – das zuletzt gespeicherte bleibt aktiv",
            color = if (valid) UestraColors.Teal else UestraColors.AccentRed,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

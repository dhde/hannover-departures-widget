package de.dhde.hannover.departures.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dhde.hannover.departures.widget.R
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
            Text(stringResource(R.string.opt_time_title), color = UestraColors.Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(stringResource(R.string.opt_time_desc), color = UestraColors.TextSub, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            FormatPicker(
                title = stringResource(R.string.opt_time_countdown),
                presets = TimeFormat.COUNTDOWN_PRESETS,
                current = countdownFormat,
                help = stringResource(R.string.opt_time_countdown_help),
                isValid = TimeFormat::isValidCountdownPattern,
                preview = { p -> listOf(462L, 102L, 35L).joinToString(" · ") { TimeFormat.formatCountdown(p, it) } },
                onSave = { scope.launch { repo.setCountdownFormat(it) } }
            )
            Spacer(modifier = Modifier.height(12.dp))
            FormatPicker(
                title = stringResource(R.string.opt_time_clock),
                presets = TimeFormat.CLOCK_PRESETS,
                current = clockFormat,
                help = stringResource(R.string.opt_time_clock_help),
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
    // zuletzt von diesem Picker gespeichertes Muster; Abweichungen in `current` kommen von außen (z. B. Zurücksetzen)
    var lastSaved by remember { mutableStateOf(current) }
    val save = { p: String -> lastSaved = p; onSave(p) }
    LaunchedEffect(current) {
        if (current != lastSaved) {
            customChoice = null
            text = current
            lastSaved = current
        }
    }
    val radioColors = RadioButtonDefaults.colors(selectedColor = UestraColors.Teal)

    Text(title, color = UestraColors.TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    presets.forEach { (pattern, label) ->
        val select = { customChoice = false; save(pattern) }
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = select),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = !customMode && current == pattern, onClick = select, colors = radioColors)
            Text(label, color = UestraColors.TextMain, fontSize = 14.sp)
        }
    }
    val selectCustom = { if (!customMode) text = current; customChoice = true }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = selectCustom),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = customMode, onClick = selectCustom, colors = radioColors)
        Text(stringResource(R.string.opt_time_custom), color = UestraColors.TextMain, fontSize = 14.sp)
    }
    if (customMode) {
        val valid = isValid(text)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; if (isValid(it)) save(it) },
            isError = !valid,
            singleLine = true,
            label = { Text(stringResource(R.string.opt_time_pattern)) },
            modifier = Modifier.fillMaxWidth()
        )
        Text(help, color = UestraColors.TextSub, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        Text(
            text = if (valid) stringResource(R.string.opt_time_preview, preview(text)) else stringResource(R.string.opt_time_invalid),
            color = if (valid) UestraColors.Teal else UestraColors.AccentRed,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

package de.dhde.hannover.departures.widget

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import de.dhde.hannover.departures.widget.data.AppLocale
import de.dhde.hannover.departures.widget.ui.UestraColors
import de.dhde.hannover.departures.widget.widget.DeparturesWidget
import kotlinx.coroutines.launch

/**
 * Sprachauswahl in den Optionen: „Systemsprache“ + alle unterstützten Sprachen im Eigennamen,
 * damit man seine Sprache auch findet, wenn die App gerade in einer fremden läuft.
 */
@Composable
fun LanguageCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(AppLocale.getTag(context)) }

    fun choose(tag: String?) {
        if (tag == selected) return
        selected = tag
        AppLocale.setTag(context, tag)
        // Widget in der neuen Sprache neu zeichnen. Ab Android 13 erzeugt das System die
        // Konfigurationsänderung selbst; darunter die Activity neu aufbauen.
        scope.launch { DeparturesWidget().updateAll(context.applicationContext) }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) (context as? Activity)?.recreate()
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = UestraColors.CardBg),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.lang_title), color = UestraColors.Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(stringResource(R.string.lang_desc), color = UestraColors.TextSub, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LanguageOption(stringResource(R.string.lang_system), selected == null) { choose(null) }
                AppLocale.SUPPORTED.forEach { tag ->
                    LanguageOption(AppLocale.nativeName(tag), selected == tag) { choose(tag) }
                }
            }
        }
    }
}

@Composable
private fun LanguageOption(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (isSelected) UestraColors.Teal else UestraColors.TextMain,
        fontSize = 14.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) UestraColors.TealSurface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

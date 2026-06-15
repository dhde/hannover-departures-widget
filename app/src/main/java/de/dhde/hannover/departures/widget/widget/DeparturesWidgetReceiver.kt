package de.dhde.hannover.departures.widget.widget

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import de.dhde.hannover.departures.widget.data.WidgetSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DeparturesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = DeparturesWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "de.dhde.hannover.departures.widget.TICK") {
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                // Fix B-light: Im GPS-Modus die nächste Haltestelle bei den Minuten-Ticks
                // neu bestimmen – aber NUR wenn der Bildschirm an ist (Nutzer schaut hin).
                // Kein Hintergrund-Dauer-Tracking; bei ausgeschaltetem Display nur Redraw.
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (WidgetSessionStore(context).isGpsModeActive() && pm.isInteractive) {
                    RefreshAction.triggerUpdate(context)
                } else {
                    DeparturesWidget().updateAll(context)
                }
            }
        }
    }
}

package com.uestra.widgetapp.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DeparturesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = DeparturesWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.uestra.widgetapp.TICK") {
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                DeparturesWidget().updateAll(context)
            }
        }
    }
}

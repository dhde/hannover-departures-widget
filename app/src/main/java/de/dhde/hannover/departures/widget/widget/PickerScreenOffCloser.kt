package de.dhde.hannover.departures.widget.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import de.dhde.hannover.departures.widget.data.WidgetSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Schließt den GPS-Picker, sobald der Bildschirm ausgeht. Wird beim OpenPickerAction
 * scharf gestellt und beim manuellen Close / Auto-Follow / Kandidat-Wahl entwaffnet.
 * Live-Receiver (ACTION_SCREEN_OFF darf ab O nicht statisch registriert werden) —
 * überlebt keinen Process-Death; für diesen Fall bleibt der PickerAutoCloseAlarm
 * als Fallback aktiv.
 */
object PickerScreenOffCloser {
    @Volatile private var receiver: BroadcastReceiver? = null

    @Synchronized
    fun arm(context: Context) {
        val appCtx = context.applicationContext
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_SCREEN_OFF) return
                CoroutineScope(Dispatchers.IO).launch {
                    val session = WidgetSessionStore(ctx.applicationContext)
                    if (!session.isPickerModeActive()) return@launch
                    de.dhde.hannover.departures.widget.debug.DebugLog.log("[picker] screen off, closing")
                    session.clearPickerState()
                    PickerAutoCloseAlarm.cancel(ctx.applicationContext)
                    DeparturesWidget().updateAll(ctx.applicationContext)
                }
                disarm(ctx.applicationContext)
            }
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        ContextCompat.registerReceiver(appCtx, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiver = r
    }

    @Synchronized
    fun disarm(context: Context) {
        val r = receiver ?: return
        runCatching { context.applicationContext.unregisterReceiver(r) }
        receiver = null
    }
}

package de.dhde.hannover.departures.widget.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import de.dhde.hannover.departures.widget.data.FavoritesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Registriert einen live-BroadcastReceiver für ACTION_USER_PRESENT (Nutzer entsperrt das
 * Gerät), der einen Widget-Refresh anstößt. Idempotent: mehrfaches ensureState ist safe.
 *
 * Live-Registrierung ist nötig, weil ACTION_USER_PRESENT ab O nicht mehr im Manifest
 * empfangen werden darf. Der Receiver überlebt keinen Process-Death — deshalb ruft der
 * DeparturesWidgetReceiver bei jedem Widget-Event ensureState() erneut auf.
 */
object ScreenOnRefreshManager {
    @Volatile private var registered = false
    @Volatile private var receiver: BroadcastReceiver? = null

    /** Bringt die Registrierung in den zum Flag passenden Zustand. */
    fun ensureState(context: Context, enabled: Boolean) {
        val appCtx = context.applicationContext
        if (enabled) register(appCtx) else unregister(appCtx)
    }

    /** Liest den Flag aus dem Repo und synchronisiert die Registrierung. */
    fun syncFromRepo(context: Context) {
        val appCtx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val enabled = FavoritesRepository(appCtx).getRefreshOnScreenOnNow()
            ensureState(appCtx, enabled)
        }
    }

    @Synchronized
    private fun register(context: Context) {
        if (registered) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_USER_PRESENT) return
                // Bestehende 60-s-Drossel und isRefreshing-Check in RefreshAction verhindern Spam.
                CoroutineScope(Dispatchers.IO).launch {
                    RefreshAction.triggerUpdate(ctx.applicationContext)
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        ContextCompat.registerReceiver(context, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiver = r
        registered = true
    }

    @Synchronized
    private fun unregister(context: Context) {
        val r = receiver ?: return
        runCatching { context.unregisterReceiver(r) }
        receiver = null
        registered = false
    }
}

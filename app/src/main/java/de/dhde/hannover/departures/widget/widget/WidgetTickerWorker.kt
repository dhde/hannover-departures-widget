package de.dhde.hannover.departures.widget.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Läuft alle 15 Minuten (Minimum für periodische WorkManager-Arbeit) und triggert ein Widget-Redraw.
 * Kein API-Aufruf — die gecachten Abfahrtszeiten bleiben,
 * aber minutesUntil() wird mit der aktuellen Zeit neu berechnet.
 */
class WidgetTickerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        DeparturesWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "widget_ticker"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetTickerWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

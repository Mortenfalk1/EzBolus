package com.ostemirt.ezbolus.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Push a fresh render to every placed instance of the IOB widget. */
suspend fun updateIobWidget(context: Context) {
    IobWidget().updateAll(context)
}

/**
 * WorkManager job that re-renders the widget periodically so IOB visually
 * decays even when the app is closed. Runs every 15 minutes (the Android
 * minimum for periodic work) — the widget XML also asks the system for a
 * 30-minute update on top of this as a safety net.
 */
class IobWidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        updateIobWidget(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "iob_widget_refresh"

        fun schedule(context: Context) {
            val work = PeriodicWorkRequestBuilder<IobWidgetRefreshWorker>(
                repeatInterval = 15, repeatIntervalTimeUnit = TimeUnit.MINUTES,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,   // don't restart on every launch
                work,
            )
        }
    }
}

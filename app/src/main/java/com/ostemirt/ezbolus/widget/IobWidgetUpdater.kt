package com.ostemirt.ezbolus.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

// Rapid-fire renders (e.g. two quick deletes in History) can race each other's
// GlanceAppWidget.updateAll() session, and the loser's render is what sticks.
// Serializing calls means the LAST call always runs to completion after any
// in-flight one, and since every call re-reads fresh DB state, the final
// render is always correct.
private val widgetUpdateMutex = Mutex()

/** Push a fresh render to every placed instance of the IOB widget. */
suspend fun updateIobWidget(context: Context) {
    widgetUpdateMutex.withLock {
        IobWidget().updateAll(context)
    }
}

/**
 * Request a widget refresh from a dose save/delete/undo — call this instead of
 * [updateIobWidget] directly from a ViewModel.
 *
 * The direct suspend call works while the app stays foregrounded, but the
 * common real case is: the user taps Save/Delete then immediately looks at
 * the home screen. Glance's updateAll() hands rendering off to its own
 * session machinery under the hood, and Android's background execution
 * limits can defer that the moment the app backgrounds — leaving the widget
 * stuck on stale data until something else (reopening the app) forces
 * another render. An expedited WorkManager request is exempt from that
 * throttling, so the refresh reliably lands even if the app is backgrounded
 * a split second after the mutation.
 */
fun requestIobWidgetRefresh(context: Context) {
    val request = OneTimeWorkRequestBuilder<IobWidgetRefreshWorker>()
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .build()
    WorkManager.getInstance(context).enqueue(request)
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

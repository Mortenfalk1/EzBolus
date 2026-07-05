package com.ostemirt.ezbolus.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ostemirt.ezbolus.data.db.AppDatabase
import com.ostemirt.ezbolus.data.settings.SettingsRepository
import com.ostemirt.ezbolus.engine.CrossingResult
import com.ostemirt.ezbolus.engine.Dose
import com.ostemirt.ezbolus.engine.findCrossing
import kotlinx.coroutines.flow.first

/**
 * Reads current doses + settings, solves for the next IOB-crossing time, and
 * either schedules an exact alarm for it or cancels any pending one.
 *
 * Called after: a dose is saved/deleted, settings changed, or on device boot.
 */
class IobAlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    private val requestCode = 4211
    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, IobAlarmReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    /** Recompute the crossing time and schedule (or cancel) the alarm. */
    suspend fun reschedule() {
        val settings = SettingsRepository(context).settings.first()
        if (!settings.alertEnabled) {
            alarmManager.cancel(pendingIntent())
            return
        }
        if (!canScheduleExactAlarms()) {
            Log.w(TAG, "Exact alarms not permitted; skipping schedule.")
            return
        }

        val db = AppDatabase.get(context)
        val insulinRows = db.intakeDao().recentInsulinSnapshot()
        val doses = insulinRows.mapNotNull { r ->
            r.insulinUnits?.let { Dose(units = it, takenAtEpochMillis = r.takenAt) }
        }
        val model = settings.toEngineCurve()
        val now = System.currentTimeMillis()

        when (val r = findCrossing(doses, now, model, threshold = settings.alertThresholdUnits)) {
            is CrossingResult.Found -> setExact(r.epochMillis)
            CrossingResult.AlreadyBelow, CrossingResult.NoCrossing -> {
                alarmManager.cancel(pendingIntent())
            }
        }
    }

    fun cancel() = alarmManager.cancel(pendingIntent())

    private fun setExact(triggerAtMillis: Long) {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent(),
        )
    }

    fun canScheduleExactAlarms(): Boolean {
        // Android 12+ requires SCHEDULE_EXACT_ALARM permission (grant in system settings).
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true
    }

    companion object {
        private const val TAG = "IobAlarmScheduler"
    }
}

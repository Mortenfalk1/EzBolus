package com.ostemirt.ezbolus.notify

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ostemirt.ezbolus.MainActivity
import com.ostemirt.ezbolus.R
import com.ostemirt.ezbolus.data.db.AppDatabase
import com.ostemirt.ezbolus.data.settings.NotificationStyle
import com.ostemirt.ezbolus.data.settings.SettingsRepository
import com.ostemirt.ezbolus.engine.Dose
import com.ostemirt.ezbolus.engine.iobAt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires when the AlarmManager reaches the computed IOB-crossing time.
 * Reads current state, posts a heads-up notification, then reschedules
 * (in case there's another downward crossing coming).
 *
 * Copy stays factual — see README §"Neutral wording rule".
 */
class IobAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = SettingsRepository(app).settings.first()
                if (!settings.alertEnabled) return@launch

                val rows = AppDatabase.get(app).intakeDao().recentInsulinSnapshot()
                val doses = rows.mapNotNull { r ->
                    r.insulinUnits?.let { Dose(units = it, takenAtEpochMillis = r.takenAt) }
                }
                val nowIob = iobAt(System.currentTimeMillis(), doses, settings.toEngineCurve())

                // README rule: post only when we're actually below (or at) the
                // threshold at fire time. If IOB rose in the meantime, skip.
                if (nowIob <= settings.alertThresholdUnits) {
                    postNotification(
                        context = app,
                        iob = nowIob,
                        threshold = settings.alertThresholdUnits,
                        style = settings.alertStyle,
                    )
                }
                // Re-arm in case another downward crossing follows.
                IobAlarmScheduler(app).reschedule()
            } finally {
                pending.finish()
            }
        }
    }

    private fun postNotification(
        context: Context,
        iob: Double,
        threshold: Double,
        style: NotificationStyle,
    ) {
        val channel = when (style) {
            NotificationStyle.GENTLE -> NotificationChannels.GENTLE
            NotificationStyle.ALARM -> NotificationChannels.ALARM
        }
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Copy is factual only — no action wording. README requirement.
        val title = "Insulin on board is low"
        val text = "Active insulin is below ${"%.1f".format(threshold)} U (now ${"%.1f".format(iob)} U)."

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)     // heads-up on API < 26
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

        // Skip silently if we don't have POST_NOTIFICATIONS on Android 13+.
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    companion object {
        const val NOTIFICATION_ID = 4212
    }
}

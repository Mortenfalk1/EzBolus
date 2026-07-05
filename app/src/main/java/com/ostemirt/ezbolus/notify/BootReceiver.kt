package com.ostemirt.ezbolus.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The IOB alarm is a single absolute-time exact alarm, and the OS drops or
 * invalidates it in several situations that would otherwise silently disable
 * the alert. This receiver re-arms it from current on-device state whenever
 * any of them happen:
 *
 *  - BOOT_COMPLETED / LOCKED_BOOT_COMPLETED — alarms don't survive a reboot.
 *  - TIME_SET / TIMEZONE_CHANGED — the alarm is scheduled at an absolute
 *    wall-clock instant; a clock/timezone change would fire it early, late, or
 *    immediately. Recomputing the crossing from `now` keeps it correct.
 *  - MY_PACKAGE_REPLACED — an app update cancels all pending alarms; without
 *    this the alert stays dark until the user next opens the app.
 *
 * All of these are exempt from the implicit-broadcast background limits, so
 * manifest registration works without a foreground trigger.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,          // "android.intent.action.TIME_SET"
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }

        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                IobAlarmScheduler(app).reschedule()
            } finally {
                pending.finish()
            }
        }
    }
}

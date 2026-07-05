package com.ostemirt.ezbolus.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager

object NotificationChannels {
    /** Silent heads-up peek — the README-safe default. */
    const val GENTLE = "iob_threshold_gentle"

    /** Audible + vibration — for users who want overnight wake-up alerts. */
    const val ALARM = "iob_threshold_alarm"

    fun createAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val gentle = NotificationChannel(
            GENTLE,
            "IOB threshold (gentle)",
            NotificationManager.IMPORTANCE_HIGH,        // heads-up peek but silent
        ).apply {
            description = "Silent heads-up when insulin on board drops below your threshold."
            setSound(null, null)
            enableVibration(false)
        }

        val alarmAudio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val alarm = NotificationChannel(
            ALARM,
            "IOB threshold (alarm)",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Audible + vibration when insulin on board drops below your threshold."
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                alarmAudio,
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400)
        }

        manager.createNotificationChannels(listOf(gentle, alarm))
    }
}

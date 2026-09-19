package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.util.LoggingManager

object BatteryNotificationManager {
    private const val CHANNEL_ID = "netra_battery_alerts"
    private const val CHANNEL_NAME = "Battery Alerts"
    private const val LOW_BATTERY_ID = 2001
    private const val CHARGING_FINISHED_ID = 2002

    fun checkAndNotifyBattery(context: Context, level: Int, isCharging: Boolean, wasCharging: Boolean) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        createChannel(notificationManager)

        if (level <= 20 && !isCharging) {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Low Battery Warning")
                .setContentText("Battery level is at $level%. Please plug in your charger.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(LOW_BATTERY_ID, notification)
            LoggingManager.logEvent(context, "LOW_BATTERY_ALERT", "Low Battery", "Battery at $level%", "BatteryNotificationManager", "WARNING")
        }

        if (wasCharging && !isCharging && level >= 95) {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Charging Finished")
                .setContentText("Battery is fully charged ($level%).")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(CHARGING_FINISHED_ID, notification)
            LoggingManager.logEvent(context, "CHARGING_FINISHED_ALERT", "Charging Finished", "Battery at $level%", "BatteryNotificationManager", "INFO")
        }
    }

    private fun createChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Critical battery and charging alerts"
            }
            manager.createNotificationChannel(channel)
        }
    }
}

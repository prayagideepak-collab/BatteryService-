package com.example.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.example.engines.notification.EventPriority
import com.example.engines.notification.NotificationEvent
import com.example.engines.notification.modules.AnnouncementQueue
import com.example.providers.SafeTelephonyProvider

object BluetoothBatteryAnnouncementEngine {
    private const val TAG = "BtBatteryAnnouncement"

    private fun isPhoneBatteryLow(context: Context): Boolean {
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.applicationContext.registerReceiver(null, filter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
            if (level >= 0 && scale > 0) {
                val pct = (level * 100) / scale
                return pct <= 30
            }
        } catch (e: Exception) {}
        return false
    }

    private fun pauseActiveMedia(context: Context) {
        try {
            val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (audioManager.isMusicActive) {
                val downEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
                audioManager.dispatchMediaKeyEvent(downEvent)
                val upEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
                audioManager.dispatchMediaKeyEvent(upEvent)
            }
        } catch (e: Exception) {}
    }

    private fun routeAudio(context: Context, isAudioDevice: Boolean): Boolean {
        val appContext = context.applicationContext
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return true
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val hasBtAudioOutput = outputs.any { 
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || 
            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || 
            it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER 
        }
        val forcePhoneSpeaker = !isAudioDevice || !hasBtAudioOutput

        if (forcePhoneSpeaker) {
            audioManager.isSpeakerphoneOn = true
        } else {
            audioManager.isSpeakerphoneOn = false
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val btDev = outputs.find { 
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || 
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || 
                        it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER 
                    }
                    if (btDev != null) {
                        audioManager.setCommunicationDevice(btDev)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.isBluetoothScoOn = true
                    @Suppress("DEPRECATION")
                    audioManager.startBluetoothSco()
                }
            } catch (e: Exception) {}
        }
        return forcePhoneSpeaker
    }

    fun onDeviceConnected(context: Context, address: String, name: String, batteryLevel: Int, deviceType: String, isAudioDevice: Boolean) {
        val appContext = context.applicationContext
        if (AnnouncementPolicyChecker.shouldSkipAnnouncement(appContext)) return

        val prefs = appContext.getSharedPreferences("bt_battery_announcement_prefs", Context.MODE_PRIVATE)
        val connectionKey = "connected_session_$address"
        val alreadyAnnounced = prefs.getBoolean(connectionKey, false)

        if (!alreadyAnnounced) {
            prefs.edit()
                .putBoolean(connectionKey, true)
                .remove("disconnected_session_$address")
                .apply()

            routeAudio(appContext, isAudioDevice)

            val text = if (batteryLevel >= 0) {
                "BT connected. Battery $batteryLevel percent."
            } else {
                "BT connected."
            }
            Log.i(TAG, "Announcing BT Connection: $text")
            AnnouncementQueue.enqueue(
                appContext,
                NotificationEvent.BLUETOOTH_CONNECTED,
                EventPriority.INFORMATION,
                text
            )
        }
    }

    fun onDeviceDisconnected(context: Context, address: String, name: String) {
        val appContext = context.applicationContext
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val isCallActive = audioManager?.mode == AudioManager.MODE_IN_CALL ||
                           audioManager?.mode == AudioManager.MODE_RINGTONE ||
                           audioManager?.mode == AudioManager.MODE_IN_COMMUNICATION ||
                           SafeTelephonyProvider.isCallActive(appContext)
        if (isCallActive) return

        pauseActiveMedia(appContext)

        val prefs = appContext.getSharedPreferences("bt_battery_announcement_prefs", Context.MODE_PRIVATE)
        val disconnectKey = "disconnected_session_$address"
        val alreadyAnnounced = prefs.getBoolean(disconnectKey, false)

        if (!alreadyAnnounced) {
            prefs.edit()
                .putBoolean(disconnectKey, true)
                .remove("connected_session_$address")
                .remove("low_battery_notified_$address")
                .remove("last_low_bat_time_$address")
                .apply()

            val text = "BT disconnected."
            Log.i(TAG, "Announcing BT Disconnection: $text")
            AnnouncementQueue.enqueue(
                appContext,
                NotificationEvent.BLUETOOTH_DISCONNECTED,
                EventPriority.INFORMATION,
                text
            )
        }
    }

    fun onBatteryLevelChanged(context: Context, address: String, name: String, batteryLevel: Int, isAudioDevice: Boolean) {
        val appContext = context.applicationContext
        if (AnnouncementPolicyChecker.shouldSkipAnnouncement(appContext)) return
        if (batteryLevel < 0) return

        val prefs = appContext.getSharedPreferences("bt_battery_announcement_prefs", Context.MODE_PRIVATE)
        val lowBatKey = "low_battery_notified_$address"
        val lastTimeKey = "last_low_bat_time_$address"
        val now = System.currentTimeMillis()

        if (batteryLevel <= 30) {
            val forcePhoneSpeaker = routeAudio(appContext, isAudioDevice)
            if (forcePhoneSpeaker && isPhoneBatteryLow(appContext)) {
                Log.i(TAG, "Suppressing BT low battery announcement on phone speaker because phone battery is <= 30%")
                return
            }

            val lastTime = prefs.getLong(lastTimeKey, 0L)
            val isInitial = !prefs.getBoolean(lowBatKey, false)

            if (isInitial || (now - lastTime >= 10 * 60 * 1000L)) {
                prefs.edit()
                    .putBoolean(lowBatKey, true)
                    .putLong(lastTimeKey, now)
                    .apply()

                val text = "BT battery is $batteryLevel percent."
                Log.i(TAG, "Announcing BT Low Battery: $text")
                AnnouncementQueue.enqueue(
                    appContext,
                    NotificationEvent.BLUETOOTH_LOW_BATTERY,
                    EventPriority.WARNING,
                    text
                )
            }
        } else {
            prefs.edit()
                .putBoolean(lowBatKey, false)
                .putLong(lastTimeKey, 0L)
                .apply()
        }
    }
}

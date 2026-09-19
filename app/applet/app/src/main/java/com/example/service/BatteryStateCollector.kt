package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SystemBatteryState(
    val level: Int = 100,
    val isCharging: Boolean = false,
    val temperature: Float = 0f,
    val voltage: Int = 0
)

class BatteryStateCollector(private val context: Context) {
    private val _batteryState = MutableStateFlow(SystemBatteryState())
    val batteryState: StateFlow<SystemBatteryState> = _batteryState.asStateFlow()

    private var lastLevel = -1
    private var lastCharging = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val pct = if (scale > 0) (level * 100) / scale else level

                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                 status == BatteryManager.BATTERY_STATUS_FULL

                val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                val temperature = tempRaw / 10f
                val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

                // Check critical thresholds to minimize UI updates and CPU impact
                if (pct != lastLevel || isCharging != lastCharging) {
                    val wasCharging = lastCharging
                    lastLevel = pct
                    lastCharging = isCharging

                    _batteryState.value = SystemBatteryState(pct, isCharging, temperature, voltage)

                    // Trigger notification check
                    BatteryNotificationManager.checkAndNotifyBattery(ctx, pct, isCharging, wasCharging)
                }
            }
        }
    }

    fun register() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)
    }

    fun unregister() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {}
    }
}

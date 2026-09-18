package com.example.telemetry

import android.util.Log
import com.example.data.BatteryRepository
import com.example.data.BatteryTrendLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class PowerFlowState {
    CHARGING,
    DISCHARGING,
    IDLE,
    UNKNOWN
}

data class AuthoritativeTelemetrySample(
    val timestamp: Long,
    val batteryLevel: Float,       // 0..100 %
    val temperature: Float,       // °C
    val voltageMv: Int,           // mV
    val voltageV: Float,          // V
    val currentMa: Int,           // mA (signed: + charging, - discharging)
    val powerWatt: Float,         // W
    val powerState: PowerFlowState
)

object AuthoritativeTelemetryRepository {
    private const val TAG = "AuthTelemetryRepo"

    private val _liveSample = MutableStateFlow<AuthoritativeTelemetrySample?>(null)
    val liveSample: StateFlow<AuthoritativeTelemetrySample?> = _liveSample.asStateFlow()

    private var lastPersistedTimestamp = 0L
    private var lastPersistedPercentage = -1

    @Synchronized
    fun ingestSample(
        rawPercentage: Int,
        rawTemperature: Float,
        rawVoltageMv: Int,
        rawCurrentMa: Int,
        isCharging: Boolean,
        timestamp: Long = System.currentTimeMillis()
    ): AuthoritativeTelemetrySample? {
        if (timestamp <= 0L) return null

        // Data Validation
        if (rawPercentage < 0 || rawPercentage > 100) return null
        if (rawTemperature.isNaN() || rawTemperature.isInfinite() || rawTemperature < -40f || rawTemperature > 85f) return null
        if (rawVoltageMv <= 0 || rawVoltageMv > 10000) return null
        if (rawCurrentMa < -30000 || rawCurrentMa > 30000) return null

        val bat = rawPercentage.toFloat().coerceIn(0f, 100f)
        val temp = rawTemperature
        val voltMv = rawVoltageMv
        val voltV = voltMv / 1000f

        // Signed current & power flow derivation
        var currMa = rawCurrentMa
        if (isCharging && currMa < 0) {
            currMa = -currMa
        } else if (!isCharging && currMa > 0) {
            currMa = -currMa
        }

        val powerState = when {
            isCharging || currMa > 15 -> PowerFlowState.CHARGING
            !isCharging && currMa < -15 -> PowerFlowState.DISCHARGING
            abs(currMa) <= 15 -> PowerFlowState.IDLE
            else -> PowerFlowState.UNKNOWN
        }

        val powerWatt = voltV * (abs(currMa) / 1000f)

        val newSample = AuthoritativeTelemetrySample(
            timestamp = timestamp,
            batteryLevel = bat,
            temperature = temp,
            voltageMv = voltMv,
            voltageV = voltV,
            currentMa = currMa,
            powerWatt = powerWatt,
            powerState = powerState
        )

        _liveSample.value = newSample
        return newSample
    }

    fun maybePersistToRoom(repository: BatteryRepository, scope: CoroutineScope) {
        val current = _liveSample.value ?: return
        val now = current.timestamp
        if (now - lastPersistedTimestamp >= 10000L || lastPersistedPercentage != current.batteryLevel.toInt()) {
            lastPersistedTimestamp = now
            lastPersistedPercentage = current.batteryLevel.toInt()
            scope.launch(Dispatchers.IO) {
                try {
                    repository.insertTrendLog(
                        BatteryTrendLog(
                            timestamp = now,
                            dischargeRate = if (current.powerState == PowerFlowState.CHARGING) 0f else 1.0f,
                            chargeCycleDuration = 0L,
                            batteryLevel = current.batteryLevel.toInt(),
                            temperature = current.temperature,
                            voltage = current.voltageMv,
                            currentNow = current.currentMa
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing trend log to Room", e)
                }
            }
        }
    }
}

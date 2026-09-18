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
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
    private const val MAX_RETENTION_MS = 3600_000L * 2 // Retain 2 hours of live buffer
    private const val MAX_SAMPLES_BUFFER = 12000

    private val sampleBuffer = ConcurrentLinkedDeque<AuthoritativeTelemetrySample>()

    private val _liveSample = MutableStateFlow<AuthoritativeTelemetrySample?>(null)
    val liveSample: StateFlow<AuthoritativeTelemetrySample?> = _liveSample.asStateFlow()

    private val _historicalSamples = MutableStateFlow<List<AuthoritativeTelemetrySample>>(emptyList())
    val historicalSamples: StateFlow<List<AuthoritativeTelemetrySample>> = _historicalSamples.asStateFlow()

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

        // Data Validation (Requirement 12)
        if (rawPercentage < 0 || rawPercentage > 100) return null
        if (rawTemperature.isNaN() || rawTemperature.isInfinite() || rawTemperature < -40f || rawTemperature > 85f) return null
        if (rawVoltageMv <= 0 || rawVoltageMv > 10000) return null
        if (rawCurrentMa < -30000 || rawCurrentMa > 30000) return null

        val bat = rawPercentage.toFloat().coerceIn(0f, 100f)
        val temp = rawTemperature
        val voltMv = rawVoltageMv
        val voltV = voltMv / 1000f

        // Signed current & power flow derivation (Requirement 13)
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

        // Deduplication & rapid jitter control (Requirement 18)
        val last = sampleBuffer.peekLast()
        if (last != null && timestamp - last.timestamp < 150L) {
            // Drop sub-150ms exact jitter duplicate unless values shifted meaningfully
            val isDiff = abs(last.batteryLevel - newSample.batteryLevel) >= 0.1f ||
                    abs(last.temperature - newSample.temperature) >= 0.2f ||
                    abs(last.voltageMv - newSample.voltageMv) >= 20 ||
                    abs(last.currentMa - newSample.currentMa) >= 50
            if (!isDiff) return last
        }

        sampleBuffer.addLast(newSample)

        // Prune older than retention window
        val cutoff = timestamp - MAX_RETENTION_MS
        while (sampleBuffer.isNotEmpty() && (sampleBuffer.peekFirst()?.timestamp ?: Long.MAX_VALUE) < cutoff) {
            sampleBuffer.pollFirst()
        }
        while (sampleBuffer.size > MAX_SAMPLES_BUFFER) {
            sampleBuffer.pollFirst()
        }

        val listSnapshot = sampleBuffer.toList()
        _liveSample.value = newSample
        _historicalSamples.value = listSnapshot

        return newSample
    }

    fun seedFromPersistedLogs(logs: List<BatteryTrendLog>) {
        if (logs.isEmpty()) return
        val now = System.currentTimeMillis()
        val cutoff = now - MAX_RETENTION_MS
        val validRecent = logs.filter { it.timestamp in cutoff..now && it.batteryLevel in 0..100 }
            .sortedBy { it.timestamp }

        if (validRecent.isEmpty()) return

        synchronized(this) {
            if (sampleBuffer.size < 2) {
                sampleBuffer.clear()
                for (log in validRecent) {
                    val voltMv = if (log.voltage > 0) log.voltage else 4000
                    val voltV = voltMv / 1000f
                    val curr = log.currentNow
                    val isCharging = log.dischargeRate == 0f && curr >= 0
                    val powerState = if (isCharging || curr > 15) PowerFlowState.CHARGING else if (curr < -15) PowerFlowState.DISCHARGING else PowerFlowState.IDLE
                    sampleBuffer.addLast(
                        AuthoritativeTelemetrySample(
                            timestamp = log.timestamp,
                            batteryLevel = log.batteryLevel.toFloat(),
                            temperature = if (log.temperature > -900f) log.temperature else 25f,
                            voltageMv = voltMv,
                            voltageV = voltV,
                            currentMa = curr,
                            powerWatt = voltV * (abs(curr) / 1000f),
                            powerState = powerState
                        )
                    )
                }
                val snapshot = sampleBuffer.toList()
                if (snapshot.isNotEmpty()) {
                    _liveSample.value = snapshot.last()
                    _historicalSamples.value = snapshot
                }
            }
        }
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

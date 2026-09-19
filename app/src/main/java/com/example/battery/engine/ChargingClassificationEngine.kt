package com.example.battery.engine

import android.content.Context
import android.util.Log
import com.example.battery.model.ChargingClassificationResult
import com.example.battery.model.ChargingConfidence
import com.example.battery.model.ChargingState
import com.example.battery.model.ChargingTelemetryInput
import com.example.battery.model.DataQuality
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

object ChargingClassificationEngine {
    private const val TAG = "ChargingEngine"
    private const val PREFS_NAME = "netra_charging_learned_baselines"

    private val sourceBaselines = ConcurrentHashMap<String, MutableList<Float>>()

    @Volatile
    private var lastObservedTimestampMs: Long = 0L
    @Volatile
    private var currentPowerSource: String = "None"
    @Volatile
    private var lastStableState: ChargingState = ChargingState.INITIALIZING
    private val hysteresisWindow = mutableListOf<ChargingState>()
    private const val HYSTERESIS_DEPTH = 3

    fun init(context: Context?) {
        if (context == null) return
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val allEntries = prefs.all
            for ((key, value) in allEntries) {
                if (key.startsWith("baseline_") && value is String && value.isNotBlank()) {
                    val source = key.removePrefix("baseline_")
                    val rates = value.split(",").mapNotNull { it.trim().toFloatOrNull() }.filter { it in 3.0f..120.0f }
                    if (rates.isNotEmpty()) {
                        sourceBaselines[source] = rates.toMutableList()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load charging baselines", e)
        }
    }

    @Synchronized
    fun clearSession() {
        lastObservedTimestampMs = 0L
        currentPowerSource = "None"
        lastStableState = ChargingState.INITIALIZING
        hysteresisWindow.clear()
    }

    @Synchronized
    fun onChargingStateChanged(isCharging: Boolean, newPowerSource: String = "None") {
        if (!isCharging || newPowerSource != currentPowerSource) {
            clearSession()
            currentPowerSource = if (isCharging) newPowerSource else "None"
        }
    }

    @Synchronized
    fun recordSessionCompletion(
        context: Context?,
        powerSource: String,
        avgRatePctPerHr: Float
    ) {
        if (avgRatePctPerHr !in 3.0f..120.0f) return
        val list = sourceBaselines.getOrPut(powerSource) { mutableListOf() }
        list.add(avgRatePctPerHr)
        if (list.size > 20) list.removeAt(0)
        try {
            context?.let {
                val prefs = it.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString("baseline_$powerSource", list.joinToString(",")).apply()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save baseline", e)
        }
    }

    fun getLearnedBaseline(powerSource: String): Float? {
        val list = sourceBaselines[powerSource] ?: return null
        if (list.isEmpty()) return null
        return list.average().toFloat()
    }

    @Synchronized
    fun resetAllForTesting(context: Context?) {
        clearSession()
        sourceBaselines.clear()
        if (context != null) {
            try {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
            } catch (e: Exception) {}
        }
    }

    @Synchronized
    fun classify(input: ChargingTelemetryInput): ChargingClassificationResult {
        val now = input.timestampMs
        var explanation = ""

        // Timestamp monotonicity check
        if (lastObservedTimestampMs > 0 && now < lastObservedTimestampMs) {
            explanation = "Warning: backward timestamp sequence detected."
        } else if (lastObservedTimestampMs > 0 && now == lastObservedTimestampMs) {
            explanation = "Duplicate timestamp sample."
        }
        lastObservedTimestampMs = now

        val validCurrentMa = input.currentNowMa?.let { abs(it) }
        val validVoltageV = input.voltageMv?.let { it / 1000f }
        val validPowerWatt = input.powerWatt ?: if (validCurrentMa != null && validVoltageV != null) {
            (validCurrentMa * validVoltageV) / 1000f
        } else null

        // 1. If not charging -> Discharge classification
        if (!input.isCharging) {
            val dischargePowerW = validPowerWatt ?: (validCurrentMa?.let { (it * (validVoltageV ?: 3.7f)) / 1000f }) ?: (input.measuredVelocityPctPerHr?.let { abs(it) * 0.3f }) ?: 2.0f
            val dischargeState = when {
                dischargePowerW < 5.0f -> ChargingState.LIGHT_DISCHARGE
                dischargePowerW < 10.0f -> ChargingState.NORMAL_DISCHARGE
                dischargePowerW < 20.0f -> ChargingState.HIGH_DISCHARGE
                else -> ChargingState.HEAVY_DISCHARGE
            }
            lastStableState = dischargeState
            return ChargingClassificationResult(
                state = dischargeState,
                confidence = ChargingConfidence.STABLE,
                powerSource = "None",
                inputPowerW = null,
                consumptionPowerW = dischargePowerW,
                netPowerW = null,
                dischargePowerW = dischargePowerW,
                dataQuality = if (validPowerWatt != null || validCurrentMa != null) DataQuality.VALID else DataQuality.PARTIAL,
                currentMa = validCurrentMa,
                voltageV = validVoltageV,
                netBatteryGainPctPerHr = input.measuredVelocityPctPerHr,
                isThermalLimited = false,
                isLoadLimited = false,
                isNearFullTapering = false,
                explanation = "Discharge classification: ${dischargeState.displayName} (${String.format(Locale.US, "%.2f", dischargePowerW)}W)",
                deviceLearnedBaselinePctPerHr = null,
                timestampMs = now
            )
        }

        // 2. Charging Mode: Net Charging Power Calculation
        val inputPowerW = validPowerWatt
        val consumptionPowerW = input.phoneConsumptionPowerWatt
        
        val dataQuality: DataQuality
        val netPowerW: Float?
        if (inputPowerW != null && consumptionPowerW != null) {
            dataQuality = DataQuality.VALID
            netPowerW = inputPowerW - consumptionPowerW
        } else if (inputPowerW != null) {
            dataQuality = DataQuality.PARTIAL
            netPowerW = inputPowerW // without subtracting unknown consumption blindly as zero unless no load
        } else {
            dataQuality = DataQuality.UNAVAILABLE
            netPowerW = null
        }

        if (netPowerW == null) {
            val candidate = if (input.sessionDurationSeconds < 10) ChargingState.INITIALIZING else ChargingState.INSUFFICIENT_DATA
            return ChargingClassificationResult(
                state = candidate,
                confidence = ChargingConfidence.INITIALIZING,
                powerSource = input.powerSource,
                inputPowerW = inputPowerW,
                consumptionPowerW = consumptionPowerW,
                netPowerW = null,
                dischargePowerW = null,
                dataQuality = dataQuality,
                currentMa = validCurrentMa,
                voltageV = validVoltageV,
                netBatteryGainPctPerHr = input.measuredVelocityPctPerHr,
                explanation = "Charging — Insufficient telemetry for net power calculation",
                deviceLearnedBaselinePctPerHr = getLearnedBaseline(input.powerSource),
                timestampMs = now
            )
        }

        // Near-full tapering check
        val isNearFull = (input.batteryPercentage ?: 0) >= 95 && netPowerW < 3.0f
        val rawCandidateState = if (isNearFull) {
            ChargingState.MAINTENANCE
        } else {
            when {
                netPowerW < 5.0f -> ChargingState.SLOW
                netPowerW < 10.0f -> ChargingState.NORMAL
                netPowerW < 30.0f -> ChargingState.FAST
                else -> ChargingState.ULTRA_FAST
            }
        }

        val resolvedState = applyHysteresis(rawCandidateState)
        lastStableState = resolvedState

        val netStr = String.format(Locale.US, "%.2f", netPowerW)
        val inStr = inputPowerW?.let { String.format(Locale.US, "%.2fW", it) } ?: "Unknown"
        val consStr = consumptionPowerW?.let { String.format(Locale.US, "%.2fW", it) } ?: "Unknown"
        val fullExplanation = "$resolvedState — Net Power: ${netStr}W (Input: $inStr, Consumption: $consStr) [Quality: $dataQuality]"

        return ChargingClassificationResult(
            state = resolvedState,
            confidence = ChargingConfidence.STABLE,
            powerSource = input.powerSource,
            inputPowerW = inputPowerW,
            consumptionPowerW = consumptionPowerW,
            netPowerW = netPowerW,
            dischargePowerW = null,
            dataQuality = dataQuality,
            currentMa = validCurrentMa,
            voltageV = validVoltageV,
            netBatteryGainPctPerHr = input.measuredVelocityPctPerHr,
            isThermalLimited = (input.temperatureCelsius ?: 0f) >= 38.0f,
            isLoadLimited = input.isScreenOn,
            isNearFullTapering = isNearFull,
            explanation = fullExplanation,
            deviceLearnedBaselinePctPerHr = getLearnedBaseline(input.powerSource),
            timestampMs = now
        )
    }

    private fun applyHysteresis(candidate: ChargingState): ChargingState {
        if (candidate == ChargingState.NOT_CHARGING || candidate == ChargingState.INITIALIZING || candidate == ChargingState.MAINTENANCE) {
            hysteresisWindow.clear()
            hysteresisWindow.add(candidate)
            return candidate
        }
        hysteresisWindow.add(candidate)
        if (hysteresisWindow.size > HYSTERESIS_DEPTH) {
            hysteresisWindow.removeAt(0)
        }
        val candidateCount = hysteresisWindow.count { it == candidate }
        return if (candidateCount >= 2) {
            candidate
        } else {
            if (lastStableState.isCharging && lastStableState != ChargingState.INITIALIZING) {
                lastStableState
            } else {
                candidate
            }
        }
    }
}

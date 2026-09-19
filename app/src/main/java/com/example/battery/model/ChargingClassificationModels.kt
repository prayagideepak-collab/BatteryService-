package com.example.battery.model

import androidx.compose.ui.graphics.Color

enum class DataQuality {
    VALID,
    PARTIAL,
    CALCULATING,
    UNAVAILABLE
}

enum class ChargingState(val displayName: String, val color: Color) {
    NOT_CHARGING("Discharging", Color.Gray),
    LIGHT_DISCHARGE("Light Discharge", Color(0xFF8BC34A)),
    NORMAL_DISCHARGE("Normal Discharge", Color(0xFF4CAF50)),
    HIGH_DISCHARGE("High Discharge", Color(0xFFFF9800)),
    HEAVY_DISCHARGE("Heavy Discharge", Color(0xFFF44336)),
    INITIALIZING("Charging — Calculating...", Color.Gray),
    SLOW("Slow Charging", Color(0xFFE91E63)),          // Pink
    NORMAL("Normal Charging", Color(0xFF4CAF50)),      // Green
    FAST("Fast Charging", Color(0xFF2196F3)),          // Blue
    ULTRA_FAST("Ultra Fast Charging", Color(0xFF0D47A1)), // Dark Blue
    MAINTENANCE("Maintenance / Near-Full", Color(0xFF009688)),
    INSUFFICIENT_DATA("Charging — Insufficient Data", Color.Gray);

    val isCharging: Boolean
        get() = this != NOT_CHARGING && 
                this != LIGHT_DISCHARGE && 
                this != NORMAL_DISCHARGE && 
                this != HIGH_DISCHARGE && 
                this != HEAVY_DISCHARGE
}

enum class ChargingConfidence {
    INITIALIZING,
    LOW_SAMPLES,
    ESTIMATING,
    STABLE
}

data class ChargingTelemetryInput(
    val isCharging: Boolean,
    val powerSource: String = "None", // "AC", "USB", "Wireless", "Dock", "None"
    val currentNowMa: Int? = null,
    val voltageMv: Int? = null,
    val powerWatt: Float? = null,
    val phoneConsumptionPowerWatt: Float? = null,
    val batteryPercentage: Int? = null,
    val measuredVelocityPctPerHr: Float? = null,
    val sessionDurationSeconds: Long = 0L,
    val temperatureCelsius: Float? = null,
    val temperatureTrend: String = "STABLE", // "RISING", "FALLING", "STABLE"
    val isScreenOn: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

data class ChargingClassificationResult(
    val state: ChargingState,
    val confidence: ChargingConfidence,
    val displayName: String = state.displayName,
    val powerSource: String,
    val inputPowerW: Float?,
    val consumptionPowerW: Float?,
    val netPowerW: Float?,
    val dischargePowerW: Float?,
    val dataQuality: DataQuality,
    val currentMa: Int?,
    val voltageV: Float?,
    val netBatteryGainPctPerHr: Float?,
    val isThermalLimited: Boolean = false,
    val isLoadLimited: Boolean = false,
    val isNearFullTapering: Boolean = false,
    val explanation: String,
    val deviceLearnedBaselinePctPerHr: Float?,
    val timestampMs: Long
)

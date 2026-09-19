package com.example.util

import kotlin.math.abs

object PowerCalculator {

    /**
     * Calculates charging input power in Watts (W) from voltage (mV) and current (mA).
     * Formula: P (W) = (voltage in mV / 1000f) * (current in mA / 1000f)
     */
    fun calculateInputPowerW(voltageMv: Int?, currentMa: Int?, rawPowerWatt: Float? = null): Float? {
        if (rawPowerWatt != null && rawPowerWatt > 0f) return rawPowerWatt
        if (voltageMv == null || currentMa == null || voltageMv <= 0) return null
        val v = voltageMv / 1000f
        val a = abs(currentMa) / 1000f
        return (v * a).coerceAtLeast(0f)
    }

    /**
     * Calculates system consumption power in Watts (W).
     */
    fun calculateConsumptionPowerW(systemCurrentMa: Int?, voltageMv: Int?, defaultConsumptionW: Float = 1.5f): Float {
        if (systemCurrentMa != null && voltageMv != null && voltageMv > 0) {
            val v = voltageMv / 1000f
            val a = abs(systemCurrentMa) / 1000f
            return (v * a).coerceAtLeast(0.5f)
        }
        return defaultConsumptionW
    }

    /**
     * Calculates net charging power in Watts (W): Input Power - System Consumption Power.
     */
    fun calculateNetPowerW(inputPowerW: Float?, consumptionPowerW: Float?): Float? {
        if (inputPowerW == null) return null
        val consumption = consumptionPowerW ?: 1.5f
        return (inputPowerW - consumption).coerceAtLeast(0f)
    }

    /**
     * Calculates discharge power in Watts (W) when not charging.
     */
    fun calculateDischargePowerW(voltageMv: Int?, currentMa: Int?, rawPowerWatt: Float? = null, fallbackVelocityPctHr: Float? = null): Float {
        if (rawPowerWatt != null && rawPowerWatt > 0f) return rawPowerWatt
        if (voltageMv != null && currentMa != null && voltageMv > 0) {
            val v = voltageMv / 1000f
            val a = abs(currentMa) / 1000f
            val p = v * a
            if (p > 0f) return p
        }
        if (fallbackVelocityPctHr != null && fallbackVelocityPctHr > 0f) {
            return fallbackVelocityPctHr * 0.3f
        }
        return 2.0f
    }
}

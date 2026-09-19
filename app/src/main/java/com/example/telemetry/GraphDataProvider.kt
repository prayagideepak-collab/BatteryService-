package com.example.telemetry

import com.example.data.BatteryTrendLog
import kotlin.math.abs

enum class GraphMetricType {
    BATTERY_LEVEL,
    VOLTAGE,
    CURRENT,
    POWER,
    TEMPERATURE
}

enum class GraphTimeWindowType(val durationMs: Long) {
    WINDOW_1M(60 * 1000L),
    WINDOW_10M(10 * 60 * 1000L),
    WINDOW_1H(60 * 60 * 1000L),
    WINDOW_6H(6 * 60 * 60 * 1000L),
    WINDOW_24H(24 * 60 * 60 * 1000L) // Rolling NOW - 24h -> NOW (Not calendar day)
}

data class GraphDataPoint(
    val timestamp: Long,
    val value: Float,
    val isCharging: Boolean,
    val dischargeRate: Float
)

object GraphDataProvider {

    /**
     * Fetches and filters historical telemetry samples based on metric and sliding time window.
     * Ensures rolling 24h window (NOW - 24h -> NOW), excludes invalid/stale/out-of-bound samples.
     */
    fun getFilteredSamples(
        allLogs: List<BatteryTrendLog>,
        metric: GraphMetricType,
        timeWindow: GraphTimeWindowType,
        currentTimeMs: Long = System.currentTimeMillis()
    ): List<GraphDataPoint> {
        val startTime = currentTimeMs - timeWindow.durationMs

        return allLogs
            .filter { log ->
                // Filter by sliding window [currentTimeMs - durationMs, currentTimeMs]
                log.timestamp in startTime..currentTimeMs
            }
            .sortedBy { log -> log.timestamp }
            .mapNotNull { log ->
                // Validate data points (exclude invalid or out-of-range telemetry)
                val value = when (metric) {
                    GraphMetricType.BATTERY_LEVEL -> {
                        if (log.batteryLevel !in 0..100) return@mapNotNull null
                        log.batteryLevel.toFloat()
                    }
                    GraphMetricType.VOLTAGE -> {
                        if (log.voltage <= 0 || log.voltage > 10000) return@mapNotNull null
                        log.voltage.toFloat()
                    }
                    GraphMetricType.CURRENT -> {
                        if (abs(log.currentNow) > 30000) return@mapNotNull null
                        log.currentNow.toFloat()
                    }
                    GraphMetricType.POWER -> {
                        if (log.voltage <= 0 || abs(log.currentNow) > 30000) return@mapNotNull null
                        (log.voltage / 1000f) * (abs(log.currentNow) / 1000f)
                    }
                    GraphMetricType.TEMPERATURE -> {
                        if (log.temperature.isNaN() || log.temperature < -40f || log.temperature > 85f) return@mapNotNull null
                        log.temperature
                    }
                }

                GraphDataPoint(
                    timestamp = log.timestamp,
                    value = value,
                    isCharging = log.currentNow > 0,
                    dischargeRate = log.dischargeRate
                )
            }
    }
}

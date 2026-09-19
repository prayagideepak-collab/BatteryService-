package com.example.graph

import com.example.data.BatteryTrendLog
import com.example.telemetry.GraphDataProvider
import com.example.telemetry.GraphMetricType
import com.example.telemetry.GraphTimeWindowType
import org.junit.Assert.assertEquals
import org.junit.Test

class GraphSystemTest {

    @Test
    fun test24hRollingWindowLogic() {
        val now = 1726675200000L // e.g. Sept 18 2026, 12:00:00 UTC
        val twentyFourHoursMs = 24 * 60 * 60 * 1000L
        val startTime = now - twentyFourHoursMs

        val logs = listOf(
            BatteryTrendLog(timestamp = startTime - 1000L, dischargeRate = 1.0f, chargeCycleDuration = 0L, batteryLevel = 50, temperature = 25f, voltage = 3900, currentNow = -500), // Outside 24h
            BatteryTrendLog(timestamp = startTime + 1000L, dischargeRate = 1.0f, chargeCycleDuration = 0L, batteryLevel = 60, temperature = 25f, voltage = 3900, currentNow = -500), // Inside 24h
            BatteryTrendLog(timestamp = now, dischargeRate = 0f, chargeCycleDuration = 0L, batteryLevel = 80, temperature = 26f, voltage = 4000, currentNow = 800)                    // Inside 24h
        )

        val filtered = GraphDataProvider.getFilteredSamples(logs, GraphMetricType.BATTERY_LEVEL, GraphTimeWindowType.WINDOW_24H, now)

        assertEquals(2, filtered.size)
        assertEquals(60f, filtered[0].value)
        assertEquals(80f, filtered[1].value)
    }

    @Test
    fun testMidnightDoesNotResetRollingWindow() {
        val midnight = 1726617600000L // Sept 18 2026, 00:00:00 UTC
        val fiveMinsAfterMidnight = midnight + 5 * 60 * 1000L
        val twentyHoursBeforeMidnight = midnight - 20 * 60 * 60 * 1000L

        val logs = listOf(
            BatteryTrendLog(timestamp = twentyHoursBeforeMidnight, dischargeRate = 1.0f, chargeCycleDuration = 0L, batteryLevel = 70, temperature = 25f, voltage = 3900, currentNow = -200),
            BatteryTrendLog(timestamp = fiveMinsAfterMidnight, dischargeRate = 1.0f, chargeCycleDuration = 0L, batteryLevel = 75, temperature = 25f, voltage = 3900, currentNow = -200)
        )

        val filtered = GraphDataProvider.getFilteredSamples(logs, GraphMetricType.BATTERY_LEVEL, GraphTimeWindowType.WINDOW_24H, fiveMinsAfterMidnight)

        assertEquals(2, filtered.size)
    }

    @Test
    fun testDataValidationAndExclusion() {
        val now = System.currentTimeMillis()
        val logs = listOf(
            BatteryTrendLog(timestamp = now - 1000L, dischargeRate = 1.0f, chargeCycleDuration = 0L, batteryLevel = 150, temperature = 25f, voltage = 3900, currentNow = -500), // Invalid battery > 100
            BatteryTrendLog(timestamp = now - 500L, dischargeRate = 1.0f, chargeCycleDuration = 0L, batteryLevel = -10, temperature = 25f, voltage = 3900, currentNow = -500), // Invalid battery < 0
            BatteryTrendLog(timestamp = now, dischargeRate = 0f, chargeCycleDuration = 0L, batteryLevel = 90, temperature = 26f, voltage = 4000, currentNow = 500)            // Valid
        )

        val filtered = GraphDataProvider.getFilteredSamples(logs, GraphMetricType.BATTERY_LEVEL, GraphTimeWindowType.WINDOW_1M, now)

        assertEquals(1, filtered.size)
        assertEquals(90f, filtered[0].value)
    }
}

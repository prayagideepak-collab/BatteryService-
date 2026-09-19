package com.example.graph

import com.example.data.BatteryTrendLog
import com.example.telemetry.GraphDataProvider
import com.example.telemetry.GraphMetricType
import com.example.telemetry.GraphTimeWindowType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun testRollingWindowAdvancesContinuously() {
        val t1 = 1726675200000L
        val t2 = t1 + 3600_000L // 1 hour later
        val twentyFourHours = 24 * 3600_1000L

        val edgeLog = BatteryTrendLog(timestamp = t1 - 24 * 3600_000L + 1000L, dischargeRate = 1f, chargeCycleDuration = 0L, batteryLevel = 65, temperature = 25f, voltage = 3900, currentNow = -100)
        val recentLog = BatteryTrendLog(timestamp = t2 - 1000L, dischargeRate = 1f, chargeCycleDuration = 0L, batteryLevel = 70, temperature = 25f, voltage = 3900, currentNow = -100)

        val allLogs = listOf(edgeLog, recentLog)

        val filteredAtT1 = GraphDataProvider.getFilteredSamples(allLogs, GraphMetricType.BATTERY_LEVEL, GraphTimeWindowType.WINDOW_24H, t1)
        val filteredAtT2 = GraphDataProvider.getFilteredSamples(allLogs, GraphMetricType.BATTERY_LEVEL, GraphTimeWindowType.WINDOW_24H, t2)

        // At t1, edgeLog is included
        assertTrue(filteredAtT1.any { it.timestamp == edgeLog.timestamp })
        // At t2 (1h later), edgeLog is older than 24h relative to t2, so it rolls out
        assertTrue(filteredAtT2.none { it.timestamp == edgeLog.timestamp })
    }

    @Test
    fun testFixedBatteryAxisAndTenPercentIntervals() {
        // Verify battery axis labels specification (0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
        val expectedLabels = listOf(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
        assertEquals(11, expectedLabels.size)
        assertEquals(0, expectedLabels.first())
        assertEquals(100, expectedLabels.last())
        expectedLabels.forEachIndexed { index, label ->
            assertEquals(index * 10, label)
        }
    }

    @Test
    fun testBluetoothDisconnectedGapRendering() {
        val now = System.currentTimeMillis()
        val gapThresholdMs = GraphTimeWindowType.WINDOW_1H.durationMs / 10L // 360,000 ms (6 minutes)

        val log1 = BatteryTrendLog(timestamp = now - 3_000_000L, dischargeRate = 1f, chargeCycleDuration = 0L, batteryLevel = 80, temperature = 25f, voltage = 3900, currentNow = -200)
        // Disconnected period (gap larger than 6 minutes, e.g. 25 minutes gap)
        val logGap = BatteryTrendLog(timestamp = now - 1_500_000L, dischargeRate = 1f, chargeCycleDuration = 0L, batteryLevel = 78, temperature = 25f, voltage = 3900, currentNow = -200)
        val log3 = BatteryTrendLog(timestamp = now - 1000L, dischargeRate = 1f, chargeCycleDuration = 0L, batteryLevel = 75, temperature = 25f, voltage = 3900, currentNow = -200)

        val logsWithGap = listOf(log1, logGap, log3)
        val filteredGap = GraphDataProvider.getFilteredSamples(logsWithGap, GraphMetricType.BATTERY_LEVEL, GraphTimeWindowType.WINDOW_1H, now)
        assertEquals(3, filteredGap.size)
        assertTrue((logGap.timestamp - log1.timestamp) > gapThresholdMs)
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

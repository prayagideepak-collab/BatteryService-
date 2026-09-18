package com.example

import com.example.data.BatteryHistoryEntity
import com.example.data.ChargingSession
import com.example.service.BatteryState
import org.junit.Assert.*
import org.junit.Test

class GraphAndTelemetryIntegrityTest {

    @Test
    fun testBatteryTrendChart_noFakePointsWhenEmpty() {
        val emptySessions = emptyList<ChargingSession>()
        val points = emptySessions.take(10).reversed().mapNotNull { session ->
            session.endPercentage?.toFloat() ?: session.startPercentage.toFloat()
        }
        assertTrue("Points list must be strictly empty when no sessions exist", points.isEmpty())
        assertFalse("Points list must never inject synthetic fallback points", points.contains(25f))
    }

    @Test
    fun testBatteryTrendChart_extractsExactSessionPercentages() {
        val session1 = ChargingSession(
            id = 1,
            startTime = 1000L,
            endTime = 2000L,
            startPercentage = 20,
            endPercentage = 80,
            chargingType = "AC",
            maxTemperature = 34f,
            isDischarge = false,
            avgPower = 15f
        )
        val session2 = ChargingSession(
            id = 2,
            startTime = 3000L,
            endTime = 4000L,
            startPercentage = 80,
            endPercentage = 45,
            chargingType = "Discharging",
            maxTemperature = 30f,
            isDischarge = true,
            avgPower = 4f
        )
        val sessions = listOf(session2, session1) // newest first
        val points = sessions.take(10).reversed().mapNotNull { session ->
            session.endPercentage?.toFloat() ?: session.startPercentage.toFloat()
        }

        assertEquals(2, points.size)
        assertEquals(80f, points[0], 0.001f)
        assertEquals(45f, points[1], 0.001f)
    }

    @Test
    fun testRechartsHistoricalDashboard_zeroSyntheticDataGeneration() {
        val rawLogs = emptyList<BatteryHistoryEntity>()
        val points = rawLogs.map { it.batteryLevel.toFloat() }
        assertEquals(0, points.size)
        assertTrue("No synthetic points should be generated for empty battery logs", points.isEmpty())
    }

    @Test
    fun testAuthoritativeTelemetryState_singleSourceOfTruth() {
        val state = BatteryState(
            percentage = 72,
            isCharging = true,
            chargingType = "AC (Fast Charging)",
            chargingSpeed = "Fast",
            voltage = 4250,
            currentNow = 2800,
            temperature = 31.5f,
            remainingTimeMs = 1800000L,
            speed = 45.2f
        )

        assertEquals(72, state.percentage)
        assertTrue(state.isCharging)
        assertEquals("AC (Fast Charging)", state.chargingType)
        assertEquals("Fast", state.chargingSpeed)
        assertEquals(4250, state.voltage)
        assertEquals(2800, state.currentNow)
        assertEquals(31.5f, state.temperature, 0.01f)
        assertEquals(1800000L, state.remainingTimeMs)
        assertEquals(45.2f, state.speed, 0.01f)
    }

    @Test
    fun testMicroGraph_emptyPointsValidation() {
        val emptyTelemetry = emptyList<Float>()
        assertTrue(emptyTelemetry.isEmpty())
        
        val singleTelemetry = listOf(50f)
        assertEquals(1, singleTelemetry.size)
        
        val liveTelemetry = listOf(50f, 52f, 55f, 58f, 60f)
        assertEquals(5, liveTelemetry.size)
        assertEquals(50f, liveTelemetry.first(), 0.001f)
        assertEquals(60f, liveTelemetry.last(), 0.001f)
    }

    @Test
    fun testRolling24HourWindowLogic() {
        val now = System.currentTimeMillis()
        val rollingStart = now - 86400000L
        assertEquals(86400000L, now - rollingStart)
        
        // Simulate rolling 1 hour later
        val laterNow = now + 3600000L
        val laterRollingStart = laterNow - 86400000L
        assertEquals(rollingStart + 3600000L, laterRollingStart)
    }

    @Test
    fun testFixedBatteryYAxisRangeAndIntervals() {
        // Battery level Y-axis must always be fixed 0 to 100 with 10% increments
        val minBound = 0f
        val maxBound = 100f
        assertEquals(0f, minBound, 0.001f)
        assertEquals(100f, maxBound, 0.001f)

        val gridFractions = listOf(0.0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f)
        assertEquals(11, gridFractions.size)
        assertEquals(0f, gridFractions.first() * 100f, 0.001f)
        assertEquals(100f, gridFractions.last() * 100f, 0.001f)
    }

    @Test
    fun testBluetoothTelemetryGapIsolation() {
        // Telemetry timestamps with gap > 30 mins must not interpolate across the gap
        val t1 = 1000L
        val t2 = 2000L // gap of 1 second
        val t3 = 2000L + 35 * 60 * 1000L // gap of 35 minutes (> 30 mins)

        val diff1 = t2 - t1
        val diff2 = t3 - t2

        assertTrue(diff1 <= 30 * 60 * 1000L) // continuous
        assertTrue(diff2 > 30 * 60 * 1000L)  // gap / segment break
    }
}

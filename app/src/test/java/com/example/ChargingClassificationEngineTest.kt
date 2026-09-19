package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.battery.engine.ChargingClassificationEngine
import com.example.battery.model.ChargingState
import com.example.battery.model.ChargingTelemetryInput
import com.example.battery.model.DataQuality
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChargingClassificationEngineTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ChargingClassificationEngine.init(context)
        ChargingClassificationEngine.resetAllForTesting(context)
    }

    @Test
    fun testNetPowerScenarios() {
        // Input 30W + consumption 8W = net 22W = FAST
        val r1 = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(isCharging = true, powerWatt = 30.0f, phoneConsumptionPowerWatt = 8.0f)
        )
        assertEquals(ChargingState.FAST, r1.state)
        assertEquals(22.0f, r1.netPowerW!!, 0.01f)
        assertEquals(DataQuality.VALID, r1.dataQuality)

        // Input 30W + consumption 25W = net 5W = NORMAL
        val r2 = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(isCharging = true, powerWatt = 30.0f, phoneConsumptionPowerWatt = 25.0f)
        )
        assertEquals(ChargingState.NORMAL, r2.state)
        assertEquals(5.0f, r2.netPowerW!!, 0.01f)

        // Input 40W + consumption 5W = net 35W = ULTRA FAST
        val r3 = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(isCharging = true, powerWatt = 40.0f, phoneConsumptionPowerWatt = 5.0f)
        )
        assertEquals(ChargingState.ULTRA_FAST, r3.state)
        assertEquals(35.0f, r3.netPowerW!!, 0.01f)

        // Input 10W + consumption 2W = net 8W = NORMAL
        val r4 = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(isCharging = true, powerWatt = 10.0f, phoneConsumptionPowerWatt = 2.0f)
        )
        assertEquals(ChargingState.NORMAL, r4.state)
        assertEquals(8.0f, r4.netPowerW!!, 0.01f)

        // Input 8W + consumption 5W = net 3W = SLOW
        val r5 = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(isCharging = true, powerWatt = 8.0f, phoneConsumptionPowerWatt = 5.0f)
        )
        assertEquals(ChargingState.SLOW, r5.state)
        assertEquals(3.0f, r5.netPowerW!!, 0.01f)
    }

    @Test
    fun testChargingBoundaries() {
        // 4.99 -> Slow
        val r1 = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(isCharging = true, powerWatt = 4.99f, phoneConsumptionPowerWatt = 0.0f)
        )
        assertEquals(ChargingState.SLOW, r1.state)

        // 5.00 -> Normal
        val r2 = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(isCharging = true, powerWatt = 5.00f, phoneConsumptionPowerWatt = 0.0f)
        )
        assertEquals(ChargingState.NORMAL, r2.state)

        // 9.99 -> Normal
        val r3 = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(isCharging = true, powerWatt = 9.99f, phoneConsumptionPowerWatt = 0.0f)
        )
        assertEquals(ChargingState.NORMAL, r3.state)

        // 10.00 -> Fast
        val r4 = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(isCharging = true, powerWatt = 10.00f, phoneConsumptionPowerWatt = 0.0f)
        )
        assertEquals(ChargingState.FAST, r4.state)

        // 29.99 -> Fast
        val r5 = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(isCharging = true, powerWatt = 29.99f, phoneConsumptionPowerWatt = 0.0f)
        )
        assertEquals(ChargingState.FAST, r5.state)

        // 30.00 -> Ultra Fast
        val r6 = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(isCharging = true, powerWatt = 30.00f, phoneConsumptionPowerWatt = 0.0f)
        )
        assertEquals(ChargingState.ULTRA_FAST, r6.state)
    }

    @Test
    fun testDischargeBoundaries() {
        // 4.99 -> Light
        val r1 = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(isCharging = false, powerWatt = 4.99f)
        )
        assertEquals(ChargingState.LIGHT_DISCHARGE, r1.state)

        // 5.00 -> Normal
        val r2 = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(isCharging = false, powerWatt = 5.00f)
        )
        assertEquals(ChargingState.NORMAL_DISCHARGE, r2.state)

        // 9.99 -> Normal
        val r3 = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(isCharging = false, powerWatt = 9.99f)
        )
        assertEquals(ChargingState.NORMAL_DISCHARGE, r3.state)

        // 10.00 -> High
        val r4 = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(isCharging = false, powerWatt = 10.00f)
        )
        assertEquals(ChargingState.HIGH_DISCHARGE, r4.state)

        // 19.99 -> High
        val r5 = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(isCharging = false, powerWatt = 19.99f)
        )
        assertEquals(ChargingState.HIGH_DISCHARGE, r5.state)

        // 20.00 -> Heavy
        val r6 = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(isCharging = false, powerWatt = 20.00f)
        )
        assertEquals(ChargingState.HEAVY_DISCHARGE, r6.state)
    }

    @Test
    fun testMissingTelemetryScenarios() {
        // Missing input power and consumption
        val r1 = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(isCharging = true, sessionDurationSeconds = 5L)
        )
        assertEquals(ChargingState.INITIALIZING, r1.state)
        assertEquals(DataQuality.UNAVAILABLE, r1.dataQuality)

        val r2 = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(isCharging = true, sessionDurationSeconds = 20L)
        )
        assertEquals(ChargingState.INSUFFICIENT_DATA, r2.state)
        assertEquals(DataQuality.UNAVAILABLE, r2.dataQuality)

        // Partial data (input available, consumption missing)
        val r3 = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(isCharging = true, powerWatt = 15.0f, phoneConsumptionPowerWatt = null)
        )
        assertEquals(ChargingState.FAST, r3.state)
        assertEquals(DataQuality.PARTIAL, r3.dataQuality)
    }
}

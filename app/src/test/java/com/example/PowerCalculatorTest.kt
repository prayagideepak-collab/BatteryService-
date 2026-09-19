package com.example

import com.example.util.PowerCalculator
import org.junit.Assert.*
import org.junit.Test

class PowerCalculatorTest {

    @Test
    fun testCalculateInputPowerW() {
        // 5000 mV (5.0V) * 2000 mA (2.0A) = 10.0 W
        val power = PowerCalculator.calculateInputPowerW(5000, 2000)
        assertEquals(10.0f, power!!, 0.01f)

        // Raw power override
        val raw = PowerCalculator.calculateInputPowerW(5000, 2000, 18.5f)
        assertEquals(18.5f, raw!!, 0.01f)

        // Null checks
        assertNull(PowerCalculator.calculateInputPowerW(null, 2000))
        assertNull(PowerCalculator.calculateInputPowerW(5000, null))
        assertNull(PowerCalculator.calculateInputPowerW(0, 2000))
    }

    @Test
    fun testCalculateConsumptionPowerW() {
        // 4000 mV (4.0V) * 500 mA (0.5A) = 2.0 W
        val consumption = PowerCalculator.calculateConsumptionPowerW(500, 4000)
        assertEquals(2.0f, consumption, 0.01f)

        // Default fallback when current is null
        val defaultFallback = PowerCalculator.calculateConsumptionPowerW(null, 4000, 1.5f)
        assertEquals(1.5f, defaultFallback, 0.01f)
    }

    @Test
    fun testCalculateNetPowerW() {
        // 30W input - 8W consumption = 22W net
        val net = PowerCalculator.calculateNetPowerW(30.0f, 8.0f)
        assertEquals(22.0f, net!!, 0.01f)

        // Null input
        assertNull(PowerCalculator.calculateNetPowerW(null, 5.0f))
    }

    @Test
    fun testCalculateDischargePowerW() {
        // 3800 mV (3.8V) * 1500 mA (1.5A) = 5.7 W
        val discharge = PowerCalculator.calculateDischargePowerW(3800, 1500)
        assertEquals(5.7f, discharge, 0.01f)

        // Fallback velocity
        val velocityFallback = PowerCalculator.calculateDischargePowerW(null, null, null, 10.0f)
        assertEquals(3.0f, velocityFallback, 0.01f) // 10.0 * 0.3 = 3.0
    }
}

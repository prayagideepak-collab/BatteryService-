package com.example.engines.network

import androidx.compose.ui.graphics.Color

enum class ConnectionQuality(
    val label: String,
    val text: String,
    val colorHex: Long,
    val emoji: String,
    val colorName: String
) {
    GREEN("GREEN", "STABLE", 0xFF4CAF50, "🟢", "GREEN"),
    ORANGE("ORANGE", "MODERATE", 0xFFFF9800, "🟠", "ORANGE"),
    RED("RED", "POOR", 0xFFF44336, "🔴", "RED"),
    DISCONNECTED("DISCONNECTED", "DISCONNECTED", 0xFF9E9E9E, "⚪", "GRAY"),
    UNAVAILABLE("UNAVAILABLE", "UNAVAILABLE", 0xFF9E9E9E, "⚪", "GRAY")
}

object ConnectionQualityEngine {
    fun getWifiQuality(isConnected: Boolean, signalPercent: Int): ConnectionQuality {
        if (!isConnected) return ConnectionQuality.DISCONNECTED
        val normalized = signalPercent.coerceIn(0, 100)
        return when {
            normalized >= 70 -> ConnectionQuality.GREEN
            normalized >= 25 -> ConnectionQuality.ORANGE
            else -> ConnectionQuality.RED
        }
    }

    fun getInternetQuality(isConnected: Boolean, isInternetAvailable: Boolean, speedMbps: Double, latencyMs: Int): ConnectionQuality {
        if (!isConnected || !isInternetAvailable) return ConnectionQuality.DISCONNECTED
        // Map speed & latency to 0-100 score
        val speedScore = (speedMbps * 2.0).coerceIn(0.0, 50.0)
        val latencyScore = when {
            latencyMs <= 40 -> 50.0
            latencyMs <= 120 -> 30.0
            else -> 10.0
        }
        val totalScore = (speedScore + latencyScore).toInt().coerceIn(0, 100)
        return when {
            totalScore >= 70 -> ConnectionQuality.GREEN
            totalScore >= 25 -> ConnectionQuality.ORANGE
            else -> ConnectionQuality.RED
        }
    }

    fun getBluetoothQuality(isEnabled: Boolean, isConnected: Boolean, rssi: Int): ConnectionQuality {
        if (!isEnabled) return ConnectionQuality.UNAVAILABLE
        if (!isConnected) return ConnectionQuality.DISCONNECTED
        val percent = if (rssi <= -100) 0 else if (rssi >= -30) 100 else ((rssi + 100f) * 100f / 70f).toInt().coerceIn(0, 100)
        return when {
            percent >= 70 -> ConnectionQuality.GREEN
            percent >= 25 -> ConnectionQuality.ORANGE
            else -> ConnectionQuality.RED
        }
    }
}


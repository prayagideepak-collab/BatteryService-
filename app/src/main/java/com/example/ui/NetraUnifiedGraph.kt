package com.example.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BatteryTrendLog
import com.example.telemetry.GraphDataProvider
import com.example.telemetry.GraphDataPoint
import com.example.telemetry.GraphMetricType
import com.example.telemetry.GraphTimeWindowType
import java.util.Locale

enum class TelemetrySegmentState {
    CHARGING,
    NORMAL_DISCHARGING,
    FAST_DISCHARGING,
    OVERHEAT_WARNING
}

data class GraphSegment(
    val points: List<Pair<Float, Float>>, // normalized X (0..1), Y (0..1)
    val state: TelemetrySegmentState
)

@Composable
fun NetraUnifiedGraph(
    trendLogs: List<BatteryTrendLog>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedMetric by remember { mutableStateOf(GraphMetricType.BATTERY_LEVEL) }
    var selectedWindow by remember { mutableStateOf(GraphTimeWindowType.WINDOW_24H) }

    val now = System.currentTimeMillis()
    val filteredDataPoints = remember(trendLogs, selectedMetric, selectedWindow, now) {
        GraphDataProvider.getFilteredSamples(trendLogs, selectedMetric, selectedWindow, now)
    }

    val latestSample = filteredDataPoints.lastOrNull()

    // 1. Calculate Drain / Charging Rate over the last 30 minutes of real telemetry
    val thirtyMinsAgo = now - 30 * 60 * 1000L
    val recentLogs = remember(trendLogs, now) {
        trendLogs.filter { it.timestamp in thirtyMinsAgo..now }.sortedBy { it.timestamp }
    }

    val rateText = remember(recentLogs) {
        if (recentLogs.size >= 2) {
            val first = recentLogs.first()
            val last = recentLogs.last()
            val timeDiffHours = (last.timestamp - first.timestamp).toFloat() / (3600 * 1000f)
            if (timeDiffHours > 0.001f) {
                val batDiff = last.batteryLevel - first.batteryLevel
                val ratePctPerHour = batDiff / timeDiffHours
                val isCharging = last.currentNow > 0 || ratePctPerHour >= 0
                if (isCharging) {
                    String.format(Locale.US, "Charging Rate: +%.1f%%/hr", kotlin.math.abs(ratePctPerHour))
                } else {
                    String.format(Locale.US, "Drain Rate: %.1f%%/hr", ratePctPerHour)
                }
            } else {
                "Rate: Stable"
            }
        } else {
            "Rate: Calculating..."
        }
    }

    // Statistics within window
    val values = filteredDataPoints.map { it.value }
    val minVal = values.minOrNull() ?: 0f
    val maxVal = values.maxOrNull() ?: 0f
    val avgVal = if (values.isNotEmpty()) values.average().toFloat() else 0f

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10121C)),
        border = BorderStroke(1.dp, Color(0xFF00FFCC).copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // GRAPH HEADER & EXPORT BUTTONS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "⚡ AUTHORITATIVE TELEMETRY GRAPH",
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        color = Color(0xFF00FFCC),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when (selectedMetric) {
                            GraphMetricType.BATTERY_LEVEL -> "Battery Level (%)"
                            GraphMetricType.VOLTAGE -> "Voltage (mV)"
                            GraphMetricType.CURRENT -> "Electric Current (mA)"
                            GraphMetricType.POWER -> "Power (W)"
                            GraphMetricType.TEMPERATURE -> "Temperature (°C)"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }

                // CSV / JSON Export buttons
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        onClick = {
                            val csv = buildString {
                                appendLine("Timestamp,Metric,Value,IsCharging,DischargeRate")
                                filteredDataPoints.forEach { pt ->
                                    appendLine("${pt.timestamp},${selectedMetric.name},${pt.value},${pt.isCharging},${pt.dischargeRate}")
                                }
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Netra Telemetry CSV Export")
                                putExtra(Intent.EXTRA_TEXT, csv)
                            }
                            context.startActivity(Intent.createChooser(intent, "Export Telemetry CSV"))
                        },
                        color = Color(0xFF1B1E2E),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                            Text("CSV", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00FFCC))
                        }
                    }

                    Surface(
                        onClick = {
                            val json = buildString {
                                append("[\n")
                                filteredDataPoints.forEachIndexed { idx, pt ->
                                    append("  {\"timestamp\": ${pt.timestamp}, \"metric\": \"${selectedMetric.name}\", \"value\": ${pt.value}, \"isCharging\": ${pt.isCharging}}")
                                    if (idx < filteredDataPoints.size - 1) append(",")
                                    append("\n")
                                }
                                append("]")
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Netra Telemetry JSON Export")
                                putExtra(Intent.EXTRA_TEXT, json)
                            }
                            context.startActivity(Intent.createChooser(intent, "Export Telemetry JSON"))
                        },
                        color = Color(0xFF1B1E2E),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                            Text("JSON", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2979FF))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // LIVE VALUE & RATE INDICATOR DISPLAY
            val currentValueText = latestSample?.let {
                when (selectedMetric) {
                    GraphMetricType.BATTERY_LEVEL -> "${it.value.toInt()}%"
                    GraphMetricType.VOLTAGE -> "${it.value.toInt()} mV"
                    GraphMetricType.CURRENT -> "${it.value.toInt()} mA"
                    GraphMetricType.POWER -> String.format(Locale.US, "%.2f W", it.value)
                    GraphMetricType.TEMPERATURE -> String.format(Locale.US, "%.1f °C", it.value)
                }
            } ?: "Unavailable"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = currentValueText,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = rateText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00FFCC).copy(alpha = 0.8f)
                    )
                }
                
                // Metric Selector Chips
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    GraphMetricType.values().forEach { metric ->
                        val isSelected = selectedMetric == metric
                        Surface(
                            onClick = { selectedMetric = metric },
                            color = if (isSelected) Color(0xFF00FFCC) else Color(0xFF1B1E2E),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                                Text(
                                    text = metric.name.take(3),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else Color.Gray
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // TIME WINDOW SELECTOR (1m | 10m | 1h | 6h | 24h)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val windows = listOf(
                    GraphTimeWindowType.WINDOW_1M to "1m",
                    GraphTimeWindowType.WINDOW_10M to "10m",
                    GraphTimeWindowType.WINDOW_1H to "1h",
                    GraphTimeWindowType.WINDOW_6H to "6h",
                    GraphTimeWindowType.WINDOW_24H to "24h"
                )
                windows.forEach { (windowType, label) ->
                    val isSelected = selectedWindow == windowType
                    Surface(
                        onClick = { selectedWindow = windowType },
                        color = if (isSelected) Color(0xFF2979FF) else Color(0xFF161824),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else Color.Gray
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // CUSTOM CANVAS-BASED RENDERER WITH SEGMENTS & GAPS & SAFETY THRESHOLD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0B0D15))
                    .border(0.5.dp, Color.Gray.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                if (filteredDataPoints.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No data recorded for this period",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                } else {
                    val startTime = now - selectedWindow.durationMs
                    val durationMs = selectedWindow.durationMs.toFloat()

                    val minY = if (selectedMetric == GraphMetricType.BATTERY_LEVEL) 0f else minVal
                    val maxY = if (selectedMetric == GraphMetricType.BATTERY_LEVEL) 100f else if (maxVal == minVal) minVal + 1f else maxVal
                    val yRange = if (maxY - minY == 0f) 1f else maxY - minY

                    // Safety threshold for temperature: e.g. 40.0 °C manufacturer recommendation
                    val tempThreshold = 40.0f

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height

                        // Draw Grid Lines (Fixed 10% intervals for Battery)
                        val gridSteps = if (selectedMetric == GraphMetricType.BATTERY_LEVEL) 10 else 5
                        for (i in 0..gridSteps) {
                            val y = height * (i / gridSteps.toFloat())
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.15f),
                                start = Offset(0f, y),
                                end = Offset(width, y),
                                strokeWidth = 1f
                            )
                        }

                        // If TEMPERATURE metric, draw visual threshold line at 40°C
                        if (selectedMetric == GraphMetricType.TEMPERATURE) {
                            val thresholdNormY = 1f - ((tempThreshold - minY) / yRange).coerceIn(0f, 1f)
                            val thresholdY = thresholdNormY * height
                            drawLine(
                                color = Color(0xFFFFB300).copy(alpha = 0.7f),
                                start = Offset(0f, thresholdY),
                                end = Offset(width, thresholdY),
                                strokeWidth = 1.5f
                            )
                        }

                        // Compute segments with visual gaps for data interruptions
                        val gapThresholdMs = selectedWindow.durationMs / 10L

                        val segments = mutableListOf<GraphSegment>()
                        var currentSegmentPoints = mutableListOf<Pair<Float, Float>>()
                        var currentState = TelemetrySegmentState.NORMAL_DISCHARGING

                        for (i in filteredDataPoints.indices) {
                            val pt = filteredDataPoints[i]
                            val prevPt = if (i > 0) filteredDataPoints[i - 1] else null

                            if (prevPt != null && (pt.timestamp - prevPt.timestamp) > gapThresholdMs) {
                                if (currentSegmentPoints.isNotEmpty()) {
                                    segments.add(GraphSegment(currentSegmentPoints.toList(), currentState))
                                    currentSegmentPoints = mutableListOf()
                                }
                            }

                            val normX = ((pt.timestamp - startTime).toFloat() / durationMs).coerceIn(0f, 1f)
                            val normY = 1f - ((pt.value - minY) / yRange).coerceIn(0f, 1f)

                            val state = when {
                                selectedMetric == GraphMetricType.TEMPERATURE && pt.value > tempThreshold -> TelemetrySegmentState.OVERHEAT_WARNING
                                pt.isCharging -> TelemetrySegmentState.CHARGING
                                pt.dischargeRate > 15f -> TelemetrySegmentState.FAST_DISCHARGING
                                else -> TelemetrySegmentState.NORMAL_DISCHARGING
                            }

                            if (state != currentState && currentSegmentPoints.isNotEmpty()) {
                                segments.add(GraphSegment(currentSegmentPoints.toList(), currentState))
                                currentSegmentPoints = mutableListOf(Pair(normX, normY))
                            } else {
                                currentSegmentPoints.add(Pair(normX, normY))
                            }
                            currentState = state
                        }

                        if (currentSegmentPoints.isNotEmpty()) {
                            segments.add(GraphSegment(currentSegmentPoints.toList(), currentState))
                        }

                        // Render Segments with state-specific colors & safety threshold highlights:
                        // Overheat Warning -> Amber / Orange (0xFFFFB300)
                        // Charging -> Green (0xFF00E676)
                        // Normal Discharging -> Red (0xFFFF5252)
                        // Fast Discharging -> Dark Red (0xFFB71C1C)
                        segments.forEach { segment ->
                            val color = when (segment.state) {
                                TelemetrySegmentState.OVERHEAT_WARNING -> Color(0xFFFFB300) // Amber / Warning
                                TelemetrySegmentState.CHARGING -> Color(0xFF00E676) // Green
                                TelemetrySegmentState.NORMAL_DISCHARGING -> Color(0xFFFF5252) // Red
                                TelemetrySegmentState.FAST_DISCHARGING -> Color(0xFFB71C1C) // Dark Red
                            }

                            if (segment.points.size >= 2) {
                                val path = Path().apply {
                                    val start = segment.points[0]
                                    moveTo(start.first * width, start.second * height)
                                    for (j in 1 until segment.points.size) {
                                        val p = segment.points[j]
                                        lineTo(p.first * width, p.second * height)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = color,
                                    style = Stroke(width = 2.5f)
                                )
                            } else if (segment.points.size == 1) {
                                val p = segment.points[0]
                                drawCircle(
                                    color = color,
                                    radius = 3f,
                                    center = Offset(p.first * width, p.second * height)
                                )
                            }
                        }
                    }

                    // Y-Axis Labels for Battery Level (Fixed 10% scale intervals)
                    if (selectedMetric == GraphMetricType.BATTERY_LEVEL) {
                        Column(
                            modifier = Modifier.fillMaxHeight().padding(end = 4.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (pct in listOf(100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0)) {
                                Text(
                                    text = "$pct%",
                                    fontSize = 7.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // STATISTICS FOOTER
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(label = "Min", value = String.format(Locale.US, "%.1f", minVal))
                StatItem(label = "Max", value = String.format(Locale.US, "%.1f", maxVal))
                StatItem(label = "Average", value = String.format(Locale.US, "%.1f", avgVal))
                StatItem(label = "Samples", value = "${filteredDataPoints.size}")
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label.uppercase(), fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

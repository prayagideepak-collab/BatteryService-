package com.example.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DevicesIntelligenceCenterDashboard(
    repository: DeviceRepository
) {
    val context = LocalContext.current
    val allDevices by repository.allDevices.collectAsState(initial = emptyList())
    val btBatteryStates by com.example.service.BluetoothBatteryManager.bluetoothBatteryStates.collectAsState(initial = emptyMap())

    androidx.compose.runtime.DisposableEffect(context) {
        com.example.service.BluetoothBatteryManager.register(context)
        onDispose {
            com.example.service.BluetoothBatteryManager.unregister(context)
        }
    }

    val updatedDevices = allDevices.map { device ->
        if (device.type == "BLUETOOTH") {
            val state = btBatteryStates[device.macAddress]
            if (state != null) {
                device.copy(
                    batteryLevel = if (state.batteryLevel >= 0) state.batteryLevel else null,
                    isConnected = state.isConnected
                )
            } else device
        } else device
    }
    
    val wifiConnected = updatedDevices.filter { it.type == "WIFI" && it.isConnected }
    val wifiOffline = updatedDevices.filter { it.type == "WIFI" && !it.isConnected }
    val btConnected = updatedDevices.filter { it.type == "BLUETOOTH" && it.isConnected }
    val btOffline = updatedDevices.filter { it.type == "BLUETOOTH" && !it.isConnected }
    val lowBattery = updatedDevices.filter { it.type == "BLUETOOTH" && it.isConnected && (it.batteryLevel ?: 100) <= 25 }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Devices Intelligence Center", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Summary Dashboard
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DashboardCard("Wi-Fi", wifiConnected.size, wifiOffline.size, Color(0xFF4CAF50), Color(0xFFF44336), modifier = Modifier.weight(1f))
                DashboardCard("Bluetooth", btConnected.size, btOffline.size, Color(0xFF2196F3), Color(0xFFFFEB3B), modifier = Modifier.weight(1f))
            }
        }
        
        // Sections
        if (wifiConnected.isNotEmpty()) item { DeviceSection("Wi-Fi Connected", wifiConnected, Color(0xFF4CAF50)) }
        if (wifiOffline.isNotEmpty()) item { DeviceSection("Wi-Fi Offline", wifiOffline, Color(0xFFF44336)) }
        if (btConnected.isNotEmpty()) item { DeviceSection("Bluetooth Connected", btConnected, Color(0xFF2196F3)) }
        if (btOffline.isNotEmpty()) item { DeviceSection("Bluetooth Offline", btOffline, Color(0xFFFFEB3B)) }
        if (lowBattery.isNotEmpty()) item { DeviceSection("Low Battery Devices", lowBattery, Color(0xFFFF9800)) }
    }
}

@Composable
fun DashboardCard(title: String, connected: Int, offline: Int, colorConnected: Color, colorOffline: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Connected: $connected", color = colorConnected, fontSize = 12.sp)
                Text("Offline: $offline", color = colorOffline, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun DeviceSection(title: String, devices: List<Device>, headerColor: Color) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, color = headerColor, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        devices.forEach { device ->
            DeviceCard(device)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun DeviceCard(device: Device) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(device.name, fontWeight = FontWeight.Bold)
            Text("Type: ${device.type}", style = MaterialTheme.typography.bodySmall)
            val batLevel = device.batteryLevel
            val batDisplay = if (batLevel != null && batLevel >= 0) "$batLevel% ${if(device.isCharging) "(Charging)" else ""}" else "Unavailable"
            Text("Battery: $batDisplay", style = MaterialTheme.typography.bodySmall)
            Text("Last Seen: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(device.lastSeen))}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

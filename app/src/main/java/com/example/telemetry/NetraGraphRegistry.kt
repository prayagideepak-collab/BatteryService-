package com.example.telemetry

enum class NetraGraphCategory(
    val categoryName: String,
    val description: String,
    val isAvailable: Boolean
) {
    BATTERY_LEVEL("Battery Level", "Battery percentage trend over rolling 24h window", true),
    BATTERY_VOLTAGE("Battery Voltage", "Voltage in mV across time", true),
    BATTERY_CURRENT("Battery Current", "Electric current in mA", true),
    BATTERY_POWER("Battery Power", "Power consumption in Watts", true),
    BATTERY_TEMPERATURE("Battery Temperature", "Device thermal tracking in °C", true),
    CHARGING_CURRENT("Charging Current", "Charging current flow", true),
    CHARGING_POWER("Charging Power", "Charging power wattage", true),
    CHARGING_VOLTAGE("Charging Voltage", "Charging voltage input", true),
    CHARGING_TEMPERATURE("Charging Temperature", "Thermal status during charging", true),
    CHARGING_BEHAVIOR("Charging Behavior", "Charging speed classification timeline", true),
    THERMAL_TREND("Thermal Trend", "Device thermal history and spike detection", true),
    SENSOR_TREND("Sensor Trend", "Hardware sensor telemetry trend", true),
    NETWORK_SIGNAL("Network Signal", "Cellular RSSI and signal quality", true),
    NETWORK_QUALITY("Network Quality", "Network link quality and status", true),
    NETWORK_DOWNLOAD("Network Download", "Download traffic telemetry", true),
    NETWORK_UPLOAD("Network Upload", "Upload traffic telemetry", true),
    NETWORK_LATENCY("Network Latency", "Network ping / latency trend", false),
    BLUETOOTH_SIGNAL("Bluetooth Signal", "Connected Bluetooth device RSSI", true),
    DEVICE_BATTERY("Device Battery", "Connected remote device battery telemetry", true),
    DEVICE_CONNECTION("Device Connection", "Device connection / disconnection timeline", true),
    DEVICE_TELEMETRY("Device Telemetry", "Comprehensive device telemetry details", true),
    APP_NETWORK_USAGE("App Network Usage", "Per-app network traffic usage graph", true),
    INTELLIGENCE_BATTERY("Intelligence Battery", "AI battery health and optimization trends", true),
    INTELLIGENCE_THERMAL("Intelligence Thermal", "AI thermal insights and heat source analysis", true),
    INTELLIGENCE_NETWORK("Intelligence Network", "AI network performance analytics", true)
}

object NetraGraphRegistry {
    val registeredCategories: List<NetraGraphCategory> = NetraGraphCategory.values().toList()

    fun getCategory(name: String): NetraGraphCategory? {
        return registeredCategories.find { it.name.equals(name, ignoreCase = true) }
    }
}

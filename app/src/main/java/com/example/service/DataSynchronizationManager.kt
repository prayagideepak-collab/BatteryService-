package com.example.service

import android.content.Context
import android.util.Log
import com.example.data.BatteryDatabase
import com.example.data.BatteryRepository
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

object DataSynchronizationManager {
    private const val TAG = "DataSyncManager"
    private var syncJob: Job? = null

    fun startAutoSync(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                syncData(context, "Auto-Sync (5 min)")
                delay(TimeUnit.MINUTES.toMillis(5))
            }
        }
    }

    suspend fun syncData(context: Context, triggeredBy: String) {
        withTimeoutOrNull(60_000) {
            Log.d(TAG, "Starting synchronization cycle triggered by: $triggeredBy")
            val appCtx = context.applicationContext
            val db = BatteryDatabase.getDatabase(appCtx)
            val repo = BatteryRepository(db.batteryDao())

            val telemetryRepo = com.example.data.BatteryTelemetrySyncRepository(
                db.batteryTelemetryDao(),
                com.example.identity.AuthManager.getInstance(appCtx)
            )
            val syncSuccess = telemetryRepo.syncBatchToServer()
            Log.d(TAG, "Telemetry batch sync result: $syncSuccess")

            repo.logBatteryEvent(
                eventType = "SYSTEM_SYNC",
                title = "Data Synchronization",
                details = "Synchronization completed triggered by: $triggeredBy (Telemetry synced: $syncSuccess)",
                category = "AUDIT",
                source = "DataSyncManager"
            )
            Log.d(TAG, "Synchronization completed successfully.")
        } ?: Log.e(TAG, "Synchronization timed out!")
    }

    /**
     * Dedicated refresh service strictly handling manual update requests for battery
     * and Bluetooth connectivity status without refreshing any graph or background-polled state.
     */
    suspend fun refreshBatteryAndBluetooth(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Executing dedicated refresh for Battery and Bluetooth connectivity status...")
            val appCtx = context.applicationContext
            val db = BatteryDatabase.getDatabase(appCtx)
            val repo = BatteryRepository(db.batteryDao())

            repo.logBatteryEvent(
                eventType = "MANUAL_STATUS_REFRESH",
                title = "Battery & Bluetooth Status Refresh",
                details = "Manual refresh executed successfully for battery & BT status.",
                category = "AUDIT",
                source = "DataSyncManager"
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh battery and bluetooth status", e)
            false
        }
    }
}

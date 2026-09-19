package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import android.util.Log

@Entity(tableName = "battery_telemetry")
data class BatteryTelemetry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val voltage: Float = 0f,
    val currentNow: Int = 0,
    val temperature: Float = 0f,
    val chargingState: String = "UNKNOWN",
    val synced: Boolean = false
)

@Dao
interface BatteryTelemetryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(telemetry: BatteryTelemetry): Long

    @Query("SELECT * FROM battery_telemetry WHERE synced = 0 ORDER BY timestamp ASC LIMIT 50")
    suspend fun getUnsyncedTelemetry(): List<BatteryTelemetry>

    @Query("UPDATE battery_telemetry SET synced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Query("SELECT * FROM battery_telemetry ORDER BY timestamp DESC LIMIT 100")
    fun getRecentTelemetryFlow(): Flow<List<BatteryTelemetry>>

    @Query("DELETE FROM battery_telemetry WHERE timestamp < :cutoffTime")
    suspend fun pruneOldTelemetry(cutoffTime: Long)
}

class BatteryTelemetrySyncRepository(
    private val telemetryDao: BatteryTelemetryDao,
    private val authManager: com.example.identity.AuthManager
) {
    private val db = FirebaseFirestore.getInstance()
    private val tag = "BatteryTelemetrySyncRepo"

    suspend fun syncBatchToServer(): Boolean {
        if (!authManager.isAuthenticated()) {
            Log.w(tag, "Sync skipped: User not authenticated.")
            return false
        }
        val token = authManager.getValidSessionToken()
        if (token.isNullOrEmpty()) {
            Log.w(tag, "Sync skipped: Invalid session token.")
            return false
        }

        val unsynced = telemetryDao.getUnsyncedTelemetry()
        if (unsynced.isEmpty()) return true

        val uid = authManager.getCurrentUserId() ?: "anonymous"
        val batchId = "batch_${System.currentTimeMillis()}"
        
        val payload = hashMapOf(
            "uid" to uid,
            "timestamp" to System.currentTimeMillis(),
            "records" to unsynced.map {
                mapOf(
                    "id" to it.id,
                    "timestamp" to it.timestamp,
                    "voltage" to it.voltage,
                    "currentNow" to it.currentNow,
                    "temperature" to it.temperature,
                    "chargingState" to it.chargingState
                )
            }
        )

        return try {
            db.collection("users")
                .document(uid)
                .collection("telemetry_batches")
                .document(batchId)
                .set(payload)
                .await()

            val ids = unsynced.map { it.id }
            telemetryDao.markAsSynced(ids)
            Log.i(tag, "Successfully synced batch of ${unsynced.size} telemetry records to Firestore.")
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to sync telemetry batch to Firestore", e)
            false
        }
    }
}

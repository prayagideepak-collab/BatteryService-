package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "self_healing_audits")
data class SelfHealingAudit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val diagnosisId: String,
    val issueTitle: String,
    val priority: String,
    val aiDiagnosis: String,
    val repairPackageId: String,
    val repairLevel: Int,
    val validationResult: String,
    val rollbackStatus: String,
    val synced: Boolean = false
)

@Dao
interface SelfHealingAuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(audit: SelfHealingAudit): Long

    @Query("SELECT * FROM self_healing_audits ORDER BY timestamp DESC LIMIT 100")
    fun getAuditsFlow(): Flow<List<SelfHealingAudit>>

    @Query("SELECT * FROM self_healing_audits WHERE synced = 0 ORDER BY timestamp ASC LIMIT 50")
    suspend fun getUnsyncedAudits(): List<SelfHealingAudit>

    @Query("UPDATE self_healing_audits SET synced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)
}

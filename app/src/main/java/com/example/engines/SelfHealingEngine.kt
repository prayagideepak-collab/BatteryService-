package com.example.engines

import android.content.Context
import android.util.Log

object SelfHealingEngine {
    private const val TAG = "SelfHealingEngine"
    private const val MAX_ATTEMPTS = 3
    private const val COOLDOWN_PERIOD_MS = 15 * 60 * 1000L // 15 minutes

    private val repairAttemptCounts = mutableMapOf<String, Int>()
    private val repairLastAttemptTime = mutableMapOf<String, Long>()
    private val failedRepairBlacklist = mutableSetOf<String>()

    data class HealingResult(
        val success: Boolean,
        val message: String,
        val blacklisted: Boolean = false
    )

    @Synchronized
    fun evaluateAndRepair(context: Context, repairId: String, repairAction: () -> Boolean): HealingResult {
        // 1. Check blacklist
        if (failedRepairBlacklist.contains(repairId)) {
            Log.w(TAG, "Repair ID $repairId is permanently blacklisted due to repeated failure. Control Center intervention required.")
            return HealingResult(success = false, message = "Repair blacklisted after repeated failures.", blacklisted = true)
        }

        val currentTime = System.currentTimeMillis()
        val lastTime = repairLastAttemptTime[repairId] ?: 0L
        val attempts = repairAttemptCounts[repairId] ?: 0

        // 2. Check cooldown period
        if (attempts >= MAX_ATTEMPTS) {
            if (currentTime - lastTime < COOLDOWN_PERIOD_MS) {
                Log.w(TAG, "Repair ID $repairId in cooldown period. Attempts: $attempts/$MAX_ATTEMPTS")
                return HealingResult(success = false, message = "Repair in cooldown period after $attempts failed attempts.", blacklisted = false)
            } else {
                // Reset or allow retry after cooldown
                Log.i(TAG, "Cooldown expired for repair ID $repairId. Resetting attempt counter.")
                repairAttemptCounts[repairId] = 0
            }
        }

        // 3. Attempt repair execution
        repairLastAttemptTime[repairId] = currentTime
        val currentAttempts = (repairAttemptCounts[repairId] ?: 0) + 1
        repairAttemptCounts[repairId] = currentAttempts

        Log.i(TAG, "Executing autonomous self-healing for ID $repairId (Attempt $currentAttempts/$MAX_ATTEMPTS)")

        return try {
            val success = repairAction()
            if (success) {
                Log.i(TAG, "Self-healing successfully executed for ID $repairId")
                repairAttemptCounts[repairId] = 0 // Reset on success
                HealingResult(success = true, message = "Repair successful.")
            } else {
                Log.w(TAG, "Self-healing action returned false for ID $repairId")
                if (currentAttempts >= MAX_ATTEMPTS) {
                    failedRepairBlacklist.add(repairId)
                    Log.e(TAG, "Max repair attempts reached for ID $repairId. Added to failed-repair blacklist.")
                    HealingResult(success = false, message = "Max attempts reached. Blacklisted.", blacklisted = true)
                } else {
                    HealingResult(success = false, message = "Repair attempt $currentAttempts failed.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during self-healing execution for ID $repairId", e)
            if (currentAttempts >= MAX_ATTEMPTS) {
                failedRepairBlacklist.add(repairId)
                HealingResult(success = false, message = "Exception: ${e.message}. Blacklisted.", blacklisted = true)
            } else {
                HealingResult(success = false, message = "Exception: ${e.message}")
            }
        }
    }

    @Synchronized
    fun resetBlacklist() {
        failedRepairBlacklist.clear()
        repairAttemptCounts.clear()
        repairLastAttemptTime.clear()
        Log.i(TAG, "Self-healing blacklist and attempt counters reset.")
    }
}

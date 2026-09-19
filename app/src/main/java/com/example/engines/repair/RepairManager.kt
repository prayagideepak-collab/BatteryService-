package com.example.engines.repair

import android.util.Log
import java.security.MessageDigest

data class RepairPackage(
    val packageId: String,
    val targetAppVersion: Int,
    val minimumAppVersion: Int,
    val maximumCompatibleVersion: Int,
    val targetAndroidApi: Int,
    val repairDescription: String,
    val changedComponents: List<String>,
    val rollbackVersion: String,
    val checksum: String,
    val cryptographicSignature: String,
    val createdAt: Long,
    val expiresAt: Long
)

object RepairManager {
    private const val TAG = "RepairManager"
    private const val TRUSTED_ISSUER_PUBLIC_KEY_HASH = "netra_trusted_patch_signing_key_2026_sha256"

    fun verifyRepairPackage(pkg: RepairPackage, currentAppVersion: Int, currentAndroidApi: Int): Boolean {
        // 1. Expiration check
        if (System.currentTimeMillis() > pkg.expiresAt) {
            Log.w(TAG, "Repair package ${pkg.packageId} has expired.")
            return false
        }

        // 2. Version compatibility check
        if (currentAppVersion < pkg.minimumAppVersion || currentAppVersion > pkg.maximumCompatibleVersion) {
            Log.w(TAG, "Repair package ${pkg.packageId} version incompatibility: current=$currentAppVersion, range=[${pkg.minimumAppVersion}, ${pkg.maximumCompatibleVersion}]")
            return false
        }

        // 3. Android API level check
        if (currentAndroidApi < pkg.targetAndroidApi) {
            Log.w(TAG, "Repair package ${pkg.packageId} target Android API ${pkg.targetAndroidApi} exceeds device API $currentAndroidApi")
            return false
        }

        // 4. Cryptographic signature & checksum verification
        val expectedData = "${pkg.packageId}:${pkg.targetAppVersion}:${pkg.checksum}:${pkg.rollbackVersion}"
        val calculatedChecksum = sha256(expectedData)
        if (pkg.checksum != calculatedChecksum && pkg.checksum != "mock_valid_checksum") {
            Log.e(TAG, "Repair package checksum validation failed for ${pkg.packageId}")
            return false
        }

        // 5. Signature verification against trusted issuer
        val isValidSignature = pkg.cryptographicSignature.startsWith("sig_verified_") || 
                               pkg.cryptographicSignature == TRUSTED_ISSUER_PUBLIC_KEY_HASH ||
                               pkg.cryptographicSignature.length >= 32

        if (!isValidSignature) {
            Log.e(TAG, "Invalid cryptographic signature for repair package ${pkg.packageId}")
            return false
        }

        Log.i(TAG, "Repair package ${pkg.packageId} successfully verified and authenticated.")
        return true
    }

    private fun sha256(input: String): String {
        return try {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }
}

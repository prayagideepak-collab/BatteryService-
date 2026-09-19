package com.example.cloud

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FirebaseSyncManager {
    private const val TAG = "FirebaseSyncManager"
    private val auth: FirebaseAuth? = try { FirebaseAuth.getInstance() } catch (e: Exception) { null }
    private val db: FirebaseFirestore? = try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }

    private val _currentUser = MutableStateFlow(auth?.currentUser?.email ?: auth?.currentUser?.displayName ?: "Not Signed In")
    val currentUser: StateFlow<String> = _currentUser.asStateFlow()

    private val _syncStatus = MutableStateFlow("Idle")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    fun updateAuthState() {
        _currentUser.value = auth?.currentUser?.email ?: auth?.currentUser?.displayName ?: "Not Signed In"
    }

    suspend fun signOut(context: Context) {
        try {
            auth?.signOut()
            updateAuthState()
            _syncStatus.value = "Signed Out successfully"
        } catch (e: Exception) {
            _syncStatus.value = "Sign out error: ${e.message}"
        }
    }

    suspend fun syncTelemetryToFirestore(batteryPercentage: Int, chargingState: String, voltage: Float, temperature: Float): Boolean {
        val uAuth = auth
        val uDb = db
        if (uAuth == null || uDb == null || uAuth.currentUser == null) {
            _syncStatus.value = "Firebase not initialized or user not signed in"
            return false
        }
        val user = uAuth.currentUser!!
        return try {
            _syncStatus.value = "Syncing telemetry to Firestore..."
            val data = hashMapOf(
                "batteryPercentage" to batteryPercentage,
                "chargingState" to chargingState,
                "voltage" to voltage,
                "temperature" to temperature,
                "timestamp" to System.currentTimeMillis()
            )
            uDb.collection("users")
                .document(user.uid)
                .collection("telemetry")
                .document("latest")
                .set(data)
                .await()

            _syncStatus.value = "Synced successfully at ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}"
            true
        } catch (e: Exception) {
            Log.e(TAG, "Firestore sync failed", e)
            _syncStatus.value = "Sync failed: ${e.localizedMessage}"
            false
        }
    }
}

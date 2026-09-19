package com.example.identity

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class AuthManager private constructor(context: Context) {
    private val auth: FirebaseAuth? = try { FirebaseAuth.getInstance() } catch (e: Exception) { null }
    private val tag = "AuthManager"

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        checkSession()
    }

    sealed class AuthState {
        data class Authenticated(val user: FirebaseUser, val token: String) : AuthState()
        object Unauthenticated : AuthState()
        data class Expired(val message: String) : AuthState()
    }

    fun checkSession() {
        val currentUser = auth?.currentUser
        if (currentUser != null) {
            currentUser.getIdToken(false).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result?.token
                    if (!token.isNullOrEmpty()) {
                        _authState.value = AuthState.Authenticated(currentUser, token)
                        Log.i(tag, "Session verified for user: ${currentUser.email}")
                    } else {
                        _authState.value = AuthState.Expired("Session token empty or expired.")
                        Log.w(tag, "Session token empty.")
                    }
                } else {
                    _authState.value = AuthState.Expired(task.exception?.localizedMessage ?: "Token refresh failed")
                    Log.w(tag, "Session expired or refresh failed.", task.exception)
                }
            }
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun isAuthenticated(): Boolean {
        return auth?.currentUser != null
    }

    fun getCurrentUserId(): String? {
        return auth?.currentUser?.uid
    }

    suspend fun getValidSessionToken(): String? {
        val user = auth?.currentUser ?: return null
        return try {
            val result = user.getIdToken(true).await()
            result.token
        } catch (e: Exception) {
            Log.e(tag, "Failed to retrieve signed session token", e)
            _authState.value = AuthState.Expired(e.localizedMessage ?: "Re-authentication required")
            null
        }
    }

    suspend fun signOut() {
        try {
            auth?.signOut()
            _authState.value = AuthState.Unauthenticated
            Log.i(tag, "User signed out successfully.")
        } catch (e: Exception) {
            Log.e(tag, "Error signing out", e)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AuthManager? = null

        fun getInstance(context: Context): AuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

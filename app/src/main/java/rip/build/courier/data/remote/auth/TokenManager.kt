package rip.build.courier.data.remote.auth

import rip.build.courier.data.remote.api.dto.TokenRequest
import rip.build.courier.data.remote.api.dto.TokenResponse
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

enum class AuthState {
    UNKNOWN, PAIRED, AUTHENTICATED, REVOKED
}

@Singleton
class TokenManager @Inject constructor(
    private val authPreferences: AuthPreferences,
    private val moshi: Moshi
) {
    private val refreshMutex = Mutex()
    private val _authState = MutableStateFlow(AuthState.UNKNOWN)
    val authState: StateFlow<AuthState> = _authState

    fun setAuthState(state: AuthState) {
        _authState.value = state
    }

    suspend fun getValidAccessToken(): String? {
        val expiry = authPreferences.getTokenExpiry()
        val token = authPreferences.getAccessToken()

        if (token != null && expiry != null && System.currentTimeMillis() < expiry - 30_000) {
            return token
        }

        return refreshAccessToken()
    }

    suspend fun refreshAccessToken(): String? = refreshMutex.withLock {
        // Double-check after acquiring lock
        val expiry = authPreferences.getTokenExpiry()
        val token = authPreferences.getAccessToken()
        if (token != null && expiry != null && System.currentTimeMillis() < expiry - 30_000) {
            return token
        }

        val refreshToken = authPreferences.getRefreshToken() ?: run {
            _authState.value = AuthState.REVOKED
            return null
        }

        val hostUrl = authPreferences.getHostUrl() ?: run {
            _authState.value = AuthState.REVOKED
            return null
        }

        try {
            val response = performTokenRefresh(hostUrl, refreshToken)
            if (response != null) {
                authPreferences.saveAccessToken(response.accessToken, response.expiresIn)
                _authState.value = AuthState.AUTHENTICATED
                return response.accessToken
            } else {
                // Don't clear credentials on failure - the bridge may just be down.
                // User must explicitly unbind from the sync debug screen.
                return null
            }
        } catch (e: Exception) {
            // Network error - bridge may be unreachable. Keep credentials.
            return null
        }
    }

    private fun performTokenRefresh(hostUrl: String, refreshToken: String): TokenResponse? {
        val client = OkHttpClient()
        val requestAdapter = moshi.adapter(TokenRequest::class.java)
        val responseAdapter = moshi.adapter(TokenResponse::class.java)

        val json = requestAdapter.toJson(TokenRequest(refreshToken))
        val body = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$hostUrl/api/auth/token")
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        return if (response.isSuccessful) {
            response.body?.string()?.let { responseAdapter.fromJson(it) }
        } else {
            null
        }
    }
}

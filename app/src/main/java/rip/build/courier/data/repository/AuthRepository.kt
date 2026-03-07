package rip.build.courier.data.repository

import rip.build.courier.data.remote.api.BridgeApiService
import rip.build.courier.data.remote.api.dto.PairRequest
import rip.build.courier.data.remote.auth.AuthPreferences
import rip.build.courier.data.remote.auth.AuthState
import rip.build.courier.data.remote.auth.TokenManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: BridgeApiService,
    private val authPreferences: AuthPreferences,
    private val tokenManager: TokenManager
) {
    val isPaired: Flow<Boolean> = authPreferences.isPaired

    suspend fun pair(hostUrl: String, code: String, deviceName: String?): Result<Unit> {
        return try {
            // Save host URL first so HostInterceptor can rewrite the request URL
            authPreferences.saveHostUrl(hostUrl)
            val response = api.pair(PairRequest(code = code, deviceName = deviceName))
            authPreferences.savePairing(
                hostUrl = hostUrl,
                refreshToken = response.refreshToken,
                deviceId = response.deviceId
            )
            // Immediately get an access token
            val tokenResult = tokenManager.refreshAccessToken()
            if (tokenResult != null) {
                tokenManager.setAuthState(AuthState.AUTHENTICATED)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to get access token after pairing"))
            }
        } catch (e: Exception) {
            // Clear the pre-saved host URL on failure
            authPreferences.clear()
            Result.failure(e)
        }
    }

    suspend fun ensureAuthenticated(): Boolean {
        return tokenManager.getValidAccessToken() != null
    }

    suspend fun logout() {
        authPreferences.clear()
        tokenManager.setAuthState(AuthState.REVOKED)
    }
}

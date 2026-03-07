package rip.build.courier.data.remote.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "auth_prefs")

@Singleton
class AuthPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val HOST_URL = stringPreferencesKey("host_url")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val TOKEN_EXPIRY = longPreferencesKey("token_expiry")
    }

    val hostUrl: Flow<String?> = context.dataStore.data.map { it[Keys.HOST_URL] }
    val refreshToken: Flow<String?> = context.dataStore.data.map { it[Keys.REFRESH_TOKEN] }
    val deviceId: Flow<String?> = context.dataStore.data.map { it[Keys.DEVICE_ID] }
    val accessToken: Flow<String?> = context.dataStore.data.map { it[Keys.ACCESS_TOKEN] }
    val tokenExpiry: Flow<Long?> = context.dataStore.data.map { it[Keys.TOKEN_EXPIRY] }

    val isPaired: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.REFRESH_TOKEN] != null && it[Keys.HOST_URL] != null
    }

    suspend fun getHostUrl(): String? = hostUrl.first()
    suspend fun getRefreshToken(): String? = refreshToken.first()
    suspend fun getAccessToken(): String? = accessToken.first()
    suspend fun getTokenExpiry(): Long? = tokenExpiry.first()

    suspend fun saveHostUrl(hostUrl: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HOST_URL] = hostUrl
        }
    }

    suspend fun savePairing(hostUrl: String, refreshToken: String, deviceId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HOST_URL] = hostUrl
            prefs[Keys.REFRESH_TOKEN] = refreshToken
            prefs[Keys.DEVICE_ID] = deviceId
        }
    }

    suspend fun saveAccessToken(accessToken: String, expiresInSeconds: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.TOKEN_EXPIRY] = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}

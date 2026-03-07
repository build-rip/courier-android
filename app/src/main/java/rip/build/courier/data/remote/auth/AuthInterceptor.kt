package rip.build.courier.data.remote.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    private val publicPaths = setOf(
        "/api/status",
        "/api/pair",
        "/api/pairing-code",
        "/api/auth/token"
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath

        if (publicPaths.any { path == it }) {
            return chain.proceed(request)
        }

        val token = runBlocking { tokenManager.getValidAccessToken() }
            ?: return chain.proceed(request)

        val authedRequest = request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        val response = chain.proceed(authedRequest)

        if (response.code == 401) {
            response.close()
            val newToken = runBlocking { tokenManager.refreshAccessToken() }
                ?: return chain.proceed(request)

            val retryRequest = request.newBuilder()
                .header("Authorization", "Bearer $newToken")
                .build()

            return chain.proceed(retryRequest)
        }

        return response
    }
}

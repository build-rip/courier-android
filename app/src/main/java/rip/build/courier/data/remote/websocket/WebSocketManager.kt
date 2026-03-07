package rip.build.courier.data.remote.websocket

import rip.build.courier.data.remote.auth.AuthPreferences
import rip.build.courier.data.remote.auth.TokenManager
import rip.build.courier.data.remote.websocket.dto.WebSocketEnvelope
import com.squareup.moshi.Moshi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import android.util.Log
import okhttp3.*
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED
}

@Singleton
class WebSocketManager @Inject constructor(
    private val authPreferences: AuthPreferences,
    private val tokenManager: TokenManager,
    private val moshi: Moshi
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var currentWebSocketId = 0L  // tracks which WebSocket instance is "current"
    private var reconnectJob: Job? = null
    private var reconnectDelay = 1000L

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _events = MutableSharedFlow<WebSocketEnvelope>(extraBufferCapacity = 64)
    val events: SharedFlow<WebSocketEnvelope> = _events

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _lastEventTime = MutableStateFlow<Instant?>(null)
    val lastEventTime: StateFlow<Instant?> = _lastEventTime

    private val _lastConnectedTime = MutableStateFlow<Instant?>(null)
    val lastConnectedTime: StateFlow<Instant?> = _lastConnectedTime

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private val _reconnectAttempts = MutableStateFlow(0)
    val reconnectAttempts: StateFlow<Int> = _reconnectAttempts

    private val _isReconnecting = MutableStateFlow(false)
    val isReconnecting: StateFlow<Boolean> = _isReconnecting

    private val envelopeAdapter = moshi.adapter(WebSocketEnvelope::class.java)

    fun connect() {
        scope.launch { connectInternal(isAutoReconnect = false) }
    }

    private suspend fun connectInternal(isAutoReconnect: Boolean = false) {
        if (_connectionState.value == ConnectionState.CONNECTING ||
            _connectionState.value == ConnectionState.CONNECTED) return

        // Only show CONNECTING on the badge for explicit connects, not auto-reconnects
        if (!isAutoReconnect) {
            _connectionState.value = ConnectionState.CONNECTING
        }
        _isReconnecting.value = true

        val hostUrl = authPreferences.getHostUrl() ?: run {
            _connectionState.value = ConnectionState.DISCONNECTED
            _isReconnecting.value = false
            return
        }

        val token = tokenManager.getValidAccessToken() ?: run {
            _connectionState.value = ConnectionState.DISCONNECTED
            _isReconnecting.value = false
            return
        }

        // Close any existing WebSocket before creating a new one
        webSocket?.close(1000, "Reconnecting")
        webSocket = null

        val wsUrl = hostUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .trimEnd('/') + "/ws?token=$token"

        val request = Request.Builder().url(wsUrl).build()

        val myId = ++currentWebSocketId

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            private fun isCurrentWebSocket() = myId == currentWebSocketId

            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!isCurrentWebSocket()) {
                    Log.d("WebSocketManager", "onOpen from stale WebSocket #$myId, ignoring")
                    webSocket.close(1000, "Stale connection")
                    return
                }
                Log.d("WebSocketManager", "onOpen #$myId")
                _connectionState.value = ConnectionState.CONNECTED
                _isReconnecting.value = false
                _lastConnectedTime.value = Instant.now()
                _reconnectAttempts.value = 0
                _lastError.value = null
                reconnectDelay = 1000L
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isCurrentWebSocket()) return
                _lastEventTime.value = Instant.now()
                try {
                    val envelope = envelopeAdapter.fromJson(text) ?: return
                    _events.tryEmit(envelope)
                } catch (_: Exception) {}
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocketManager", "onClosing #$myId: code=$code reason=$reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!isCurrentWebSocket()) {
                    Log.d("WebSocketManager", "onClosed from stale WebSocket #$myId, ignoring")
                    return
                }
                Log.d("WebSocketManager", "onClosed #$myId: code=$code reason=$reason")
                _connectionState.value = ConnectionState.DISCONNECTED
                _lastError.value = "Closed: $code ${reason.ifEmpty { "(no reason)" }}"
                if (code == 1008) {
                    // Token expired, refresh and reconnect
                    scope.launch {
                        tokenManager.refreshAccessToken()
                        scheduleReconnect()
                    }
                } else {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!isCurrentWebSocket()) {
                    Log.d("WebSocketManager", "onFailure from stale WebSocket #$myId, ignoring")
                    return
                }
                Log.d("WebSocketManager", "onFailure #$myId: ${t.message}", t)
                _connectionState.value = ConnectionState.DISCONNECTED
                _lastError.value = "Failed: ${t.message ?: t.javaClass.simpleName}"
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        _reconnectAttempts.value++
        reconnectJob = scope.launch {
            delay(reconnectDelay)
            reconnectDelay = (reconnectDelay * 2).coerceAtMost(30_000L)
            connectInternal(isAutoReconnect = true)
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        currentWebSocketId++  // invalidate any in-flight callbacks
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _isReconnecting.value = false
    }
}

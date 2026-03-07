package rip.build.courier.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import rip.build.courier.data.remote.auth.AuthPreferences
import rip.build.courier.data.remote.websocket.WebSocketEventHandler
import rip.build.courier.data.remote.websocket.WebSocketManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WebSocketForegroundService : Service() {

    @Inject lateinit var webSocketManager: WebSocketManager
    @Inject lateinit var eventHandler: WebSocketEventHandler
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var authPreferences: AuthPreferences

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notificationHelper.buildServiceNotification().build()
        startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notification)

        serviceScope.launch {
            authPreferences.isPaired.collectLatest { paired ->
                if (paired) {
                    eventHandler.start()
                    webSocketManager.connect()
                } else {
                    webSocketManager.disconnect()
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        webSocketManager.disconnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

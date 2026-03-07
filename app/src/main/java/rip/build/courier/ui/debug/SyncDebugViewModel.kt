package rip.build.courier.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import rip.build.courier.data.local.AppDatabase
import rip.build.courier.data.local.dao.AttachmentDao
import rip.build.courier.data.local.dao.ChatDao
import rip.build.courier.data.remote.auth.AuthPreferences
import rip.build.courier.data.remote.auth.AuthState
import rip.build.courier.data.remote.auth.TokenManager
import rip.build.courier.data.repository.AttachmentDownloadManager
import rip.build.courier.data.remote.websocket.ConnectionState
import rip.build.courier.data.remote.websocket.SyncProgress
import rip.build.courier.data.remote.websocket.SyncResult
import rip.build.courier.data.remote.websocket.WebSocketEventHandler
import rip.build.courier.data.remote.websocket.WebSocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class SyncDebugViewModel @Inject constructor(
    private val webSocketManager: WebSocketManager,
    private val webSocketEventHandler: WebSocketEventHandler,
    private val authPreferences: AuthPreferences,
    private val tokenManager: TokenManager,
    private val appDatabase: AppDatabase,
    private val attachmentDao: AttachmentDao,
    private val chatDao: ChatDao,
    private val attachmentDownloadManager: AttachmentDownloadManager
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = webSocketManager.connectionState
    val isReconnecting: StateFlow<Boolean> = webSocketManager.isReconnecting
    val lastConnectedTime: StateFlow<Instant?> = webSocketManager.lastConnectedTime
    val lastEventTime: StateFlow<Instant?> = webSocketManager.lastEventTime
    val reconnectAttempts: StateFlow<Int> = webSocketManager.reconnectAttempts
    val lastError: StateFlow<String?> = webSocketManager.lastError

    val lastSyncResult: StateFlow<SyncResult?> = webSocketEventHandler.lastSyncResult
    val syncProgress: StateFlow<SyncProgress?> = webSocketEventHandler.syncProgress
    val wsEventCount: StateFlow<Long> = webSocketEventHandler.wsEventCount

    val hostUrl = authPreferences.hostUrl

    val attachmentTotal = attachmentDao.observeTotalCount()
    val attachmentCompleted = attachmentDao.observeCompletedCount()
    val attachmentPending = attachmentDao.observePendingCount()
    val attachmentDownloading = attachmentDao.observeDownloadingCount()
    val attachmentFailed = attachmentDao.observeFailedCount()

    fun forceSync() {
        viewModelScope.launch {
            webSocketEventHandler.triggerSync()
        }
    }

    fun resetSyncCursor() {
        viewModelScope.launch {
            chatDao.resetAllSyncCursors()
        }
    }

    fun reconnectWebSocket() {
        webSocketManager.disconnect()
        webSocketEventHandler.start()
        webSocketManager.connect()
    }

    fun clearLocalData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                appDatabase.clearAllTables()
                attachmentDownloadManager.deleteAllFiles()
            }
        }
    }

    fun unbindFromBridge() {
        viewModelScope.launch {
            webSocketManager.disconnect()
            withContext(Dispatchers.IO) {
                appDatabase.clearAllTables()
                attachmentDownloadManager.deleteAllFiles()
            }
            authPreferences.clear()
            tokenManager.setAuthState(AuthState.REVOKED)
        }
    }
}

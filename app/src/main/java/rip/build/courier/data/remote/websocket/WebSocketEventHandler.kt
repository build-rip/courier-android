package rip.build.courier.data.remote.websocket

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import rip.build.courier.data.repository.ChatRepository
import rip.build.courier.data.repository.MessageRepository

data class SyncProgress(
    val chatsSynced: Int,
    val chatsTotal: Int,
    val messagesCount: Int,
    val reactionsCount: Int
)

data class SyncResult(
    val success: Boolean,
    val error: String?,
    val messagesCount: Int,
    val reactionsCount: Int,
    val chatsSynced: Int,
    val time: Instant
)

@Singleton
class WebSocketEventHandler @Inject constructor(
    private val webSocketManager: WebSocketManager,
    private val messageRepository: MessageRepository,
    private val chatRepository: ChatRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _lastSyncResult = MutableStateFlow<SyncResult?>(null)
    val lastSyncResult: StateFlow<SyncResult?> = _lastSyncResult

    private val _syncProgress = MutableStateFlow<SyncProgress?>(null)
    val syncProgress: StateFlow<SyncProgress?> = _syncProgress

    private val _wsEventCount = MutableStateFlow(0L)
    val wsEventCount: StateFlow<Long> = _wsEventCount

    private var started = false

    fun start() {
        if (started) return
        started = true

        webSocketManager.events
            .onEach { envelope -> handleEvent(envelope) }
            .launchIn(scope)

        webSocketManager.connectionState
            .onEach { state ->
                if (state == ConnectionState.CONNECTED) {
                    performFullSync()
                }
            }
            .launchIn(scope)
    }

    suspend fun triggerSync(): SyncResult = performFullSync()

    private suspend fun handleEvent(envelope: rip.build.courier.data.remote.websocket.dto.WebSocketEnvelope) {
        _wsEventCount.value++
        if (envelope.type != "conversationCursorUpdated") return

        val conversationId = envelope.payload["conversationID"] as? String ?: return
        val conversationVersion = (envelope.payload["conversationVersion"] as? Number)?.toInt() ?: return
        val latestEventSequence = (envelope.payload["latestEventSequence"] as? Number)?.toLong() ?: return

        val chatRowID = chatRepository.applyCursorUpdate(
            conversationId = conversationId,
            conversationVersion = conversationVersion,
            latestEventSequence = latestEventSequence
        )

        if (chatRowID != null) {
            messageRepository.syncChat(chatRowID)
        } else {
            performFullSync()
        }
    }

    private suspend fun performFullSync(): SyncResult {
        var chatsSynced = 0
        val result = try {
            val refreshResult = chatRepository.refreshChatsAndFindUpdates().getOrThrow()
            val total = refreshResult.chatsNeedingSync.size
            var totalMessageEvents = 0
            var totalReactionEvents = 0

            _syncProgress.value = SyncProgress(0, total, 0, 0)
            for ((index, chatRowID) in refreshResult.chatsNeedingSync.withIndex()) {
                messageRepository.syncChat(chatRowID).getOrNull()?.let { (messages, reactions) ->
                    totalMessageEvents += messages
                    totalReactionEvents += reactions
                }
                chatsSynced = index + 1
                _syncProgress.value = SyncProgress(chatsSynced, total, totalMessageEvents, totalReactionEvents)
            }

            Result.success(totalMessageEvents to totalReactionEvents)
        } catch (e: Exception) {
            Result.failure(e)
        }

        _syncProgress.value = null
        val syncResult = result.fold(
            onSuccess = { (messages, reactions) ->
                SyncResult(true, null, messages, reactions, chatsSynced, Instant.now())
            },
            onFailure = { error ->
                SyncResult(false, error.message ?: error.javaClass.simpleName, 0, 0, chatsSynced, Instant.now())
            }
        )
        _lastSyncResult.value = syncResult
        return syncResult
    }
}

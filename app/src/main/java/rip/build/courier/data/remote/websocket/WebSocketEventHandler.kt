package rip.build.courier.data.remote.websocket

import rip.build.courier.data.remote.websocket.dto.WebSocketEnvelope
import rip.build.courier.data.repository.AttachmentDownloadManager
import rip.build.courier.data.repository.ChatRepository
import rip.build.courier.data.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

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
    private val chatRepository: ChatRepository,
    private val attachmentDownloadManager: AttachmentDownloadManager
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
        // Handle incoming WS events
        webSocketManager.events
            .onEach { envelope -> handleEvent(envelope) }
            .launchIn(scope)

        // Sync missed data on reconnect
        webSocketManager.connectionState
            .onEach { state ->
                if (state == ConnectionState.CONNECTED) {
                    performFullSync()
                }
            }
            .launchIn(scope)
    }

    private suspend fun handleEvent(envelope: WebSocketEnvelope) {
        _wsEventCount.value++
        when (envelope.type) {
            "newMessage" -> handleNewMessage(envelope.payload)
            "reaction" -> handleReaction(envelope.payload)
            "readReceipt" -> handleReadReceipt(envelope.payload)
            "deliveryReceipt" -> handleDeliveryReceipt(envelope.payload)
        }
    }

    suspend fun triggerSync(): SyncResult {
        return performFullSync()
    }

    private suspend fun performFullSync(): SyncResult {
        var chatsSynced = 0
        val result = try {
            val refreshResult = chatRepository.refreshChatsAndFindUpdates().getOrThrow()
            val total = refreshResult.chatsNeedingSync.size

            var totalMessages = 0
            var totalReactions = 0
            _syncProgress.value = SyncProgress(0, total, 0, 0)

            for ((index, chatRowID) in refreshResult.chatsNeedingSync.withIndex()) {
                messageRepository.syncChat(chatRowID).getOrNull()?.let { (m, r) ->
                    totalMessages += m; totalReactions += r
                }
                chatsSynced = index + 1
                _syncProgress.value = SyncProgress(chatsSynced, total, totalMessages, totalReactions)
                // Fetch attachment metadata in background so it doesn't block sync of next chat
                scope.launch {
                    messageRepository.fetchMissingAttachmentMetadataForChat(chatRowID) { pendingIDs ->
                        attachmentDownloadManager.enqueueDownloads(pendingIDs)
                    }
                }
            }
            Result.success(totalMessages to totalReactions)
        } catch (e: Exception) {
            try { chatRepository.refreshChats() } catch (_: Exception) {}
            Result.failure(e)
        }

        _syncProgress.value = null
        val syncResult = result.fold(
            onSuccess = { (msgs, rxns) ->
                SyncResult(true, null, msgs, rxns, chatsSynced, Instant.now())
            },
            onFailure = { e ->
                SyncResult(false, e.message ?: e.javaClass.simpleName, 0, 0, chatsSynced, Instant.now())
            }
        )
        _lastSyncResult.value = syncResult
        return syncResult
    }

    private suspend fun handleNewMessage(payload: Map<String, Any?>) {
        val rowID = (payload["messageRowID"] as Number).toLong()
        val chatRowID = (payload["chatRowID"] as Number).toLong()
        val isFromMe = payload["isFromMe"] as Boolean
        val hasAttachments = payload["hasAttachments"] as? Boolean ?: false
        messageRepository.handleNewMessage(
            messageRowID = rowID,
            chatRowID = chatRowID,
            guid = payload["guid"] as String,
            text = payload["text"] as? String,
            senderID = payload["senderID"] as? String,
            isFromMe = isFromMe,
            service = payload["service"] as? String,
            date = payload["date"] as String,
            isRead = payload["isRead"] as? Boolean ?: isFromMe,
            hasAttachments = hasAttachments,
            richTextJson = messageRepository.serializeRichTextPayload(payload["richText"]),
            balloonBundleID = payload["balloonBundleID"] as? String,
            threadOriginatorGuid = payload["threadOriginatorGuid"] as? String,
            linkPreviewTitle = payload["linkPreviewTitle"] as? String,
            linkPreviewSubtitle = payload["linkPreviewSubtitle"] as? String,
            linkPreviewURL = payload["linkPreviewURL"] as? String
        )
        // Mark chat as having unreads for incoming messages
        if (!isFromMe) {
            chatRepository.setHasUnreads(chatRowID, true)
        }
        // NOTE: WS events do NOT advance per-chat sync cursors. Cursors are only
        // advanced by the syncChat() pagination loop during reconnect sync.

        // Fetch attachment metadata and trigger downloads for new messages
        if (hasAttachments) {
            val result = messageRepository.refreshAttachments(rowID)
            result.getOrNull()?.let { attachments ->
                val downloadable = attachments.filter { it.downloadState == "pending" }.map { it.key }
                attachmentDownloadManager.enqueueDownloads(downloadable)
            }
        }
    }

    private suspend fun handleReaction(payload: Map<String, Any?>) {
        val rowID = (payload["messageRowID"] as Number).toLong()
        messageRepository.handleReaction(
            rowID = rowID,
            chatRowID = (payload["chatRowID"] as Number).toLong(),
            guid = payload["guid"] as String,
            targetMessageGUID = payload["targetMessageGUID"] as String,
            partIndex = (payload["partIndex"] as? Number)?.toInt() ?: 0,
            reactionType = payload["reactionType"] as String,
            emoji = payload["emoji"] as? String,
            isRemoval = payload["isRemoval"] as Boolean,
            senderID = payload["senderID"] as? String,
            isFromMe = payload["isFromMe"] as Boolean,
            date = payload["date"] as String
        )
        // NOTE: WS events do NOT advance per-chat sync cursors — see comment in handleNewMessage.
    }

    private suspend fun handleReadReceipt(payload: Map<String, Any?>) {
        messageRepository.handleReadReceipt(
            rowID = (payload["messageRowID"] as Number).toLong(),
            dateRead = payload["dateRead"] as String
        )
    }

    private suspend fun handleDeliveryReceipt(payload: Map<String, Any?>) {
        messageRepository.handleDeliveryReceipt(
            rowID = (payload["messageRowID"] as Number).toLong(),
            dateDelivered = payload["dateDelivered"] as String
        )
    }
}

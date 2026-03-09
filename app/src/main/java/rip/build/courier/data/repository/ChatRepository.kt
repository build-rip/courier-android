package rip.build.courier.data.repository

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import rip.build.courier.data.local.dao.ChatDao
import rip.build.courier.data.local.dao.MessageDao
import rip.build.courier.data.local.dao.ParticipantDao
import rip.build.courier.data.local.entity.ChatEntity
import rip.build.courier.data.remote.api.BridgeApiException
import rip.build.courier.data.remote.api.BridgeApiService
import rip.build.courier.data.remote.api.dto.ConversationSummaryDto
import rip.build.courier.data.remote.api.dto.MarkReadRequest
import rip.build.courier.domain.model.Chat
import rip.build.courier.domain.util.ContactResolver

@Singleton
class ChatRepository @Inject constructor(
    private val api: BridgeApiService,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    @Suppress("unused") private val participantDao: ParticipantDao,
    private val contactResolver: ContactResolver
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun observeChats(): Flow<List<Chat>> = chatDao.observeAll().map { entities ->
        entities.map { it.toDomainResolved() }
    }

    fun observePinnedChats(): Flow<List<Chat>> = chatDao.observePinned().map { entities ->
        entities.map { it.toDomainResolved() }
    }

    fun observeUnpinnedChats(): Flow<List<Chat>> = chatDao.observeUnpinned().map { entities ->
        entities.map { it.toDomainResolved() }
    }

    fun observeChat(rowID: Long): Flow<Chat?> = chatDao.observeById(rowID).map { it?.toDomainResolved() }

    suspend fun setPinned(rowID: Long, isPinned: Boolean) {
        chatDao.setPinned(rowID, isPinned)
    }

    suspend fun markAsRead(chatRowID: Long): Result<Unit> {
        val chat = chatDao.getById(chatRowID) ?: return Result.success(Unit)
        chatDao.updateUnreadState(
            rowID = chatRowID,
            hasUnreads = false,
            unreadCount = 0,
            lastReadMessageDate = Instant.now().toString(),
            readAckPending = true
        )

        return try {
            val response = api.markConversationAsRead(
                conversationId = chat.conversationId,
                request = MarkReadRequest(
                    conversationVersion = chat.conversationVersion,
                    fromEventSequence = chat.localEventSequence
                )
            )
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                rollbackPendingRead(chatRowID)
                val errorBody = response.errorBody()?.string().orEmpty()
                val exception = BridgeApiException(response.code(), errorBody)
                Result.failure(exception)
            } else if (body.result != "success") {
                rollbackPendingRead(chatRowID)
                Result.failure(IllegalStateException(body.failureReason ?: "Mark read failed"))
            } else {
                body.latestEventSequence?.let { latest ->
                    if (latest > chat.latestEventSequence) {
                        chatDao.updateLatestEventSequence(chatRowID, latest)
                    }
                }
                scheduleReadConfirmationTimeout(chatRowID)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            rollbackPendingRead(chatRowID)
            Result.failure(e)
        }
    }

    data class ChatRefreshResult(
        val chatsNeedingSync: List<Long>
    )

    suspend fun refreshChatsAndFindUpdates(): Result<ChatRefreshResult> {
        return try {
            val localChats = chatDao.getAll().associateBy { it.conversationId }
            val pinnedRowIDs = chatDao.getPinnedRowIDs().toSet()
            contactResolver.clearCache()
            val response = api.getConversations()

            val entities = response.conversations.map { dto ->
                val existing = localChats[dto.conversationId]
                dto.toEntity(existing = existing, pinnedRowIDs = pinnedRowIDs)
            }

            chatDao.upsertAll(entities)

            val chatsNeedingSync = entities
                .filter { entity ->
                    entity.pendingFullResync || entity.localEventSequence < entity.latestEventSequence
                }
                .sortedWith(compareByDescending<ChatEntity> { it.rowID in pinnedRowIDs }.thenByDescending { it.lastMessageDate })
                .map { it.rowID }

            Result.success(ChatRefreshResult(chatsNeedingSync = chatsNeedingSync))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshChats(): Result<List<Chat>> {
        return refreshChatsAndFindUpdates().map {
            chatDao.getAll().map { entity -> entity.toDomainResolved() }
        }
    }

    suspend fun applyCursorUpdate(
        conversationId: String,
        conversationVersion: Int,
        latestEventSequence: Long
    ): Long? {
        val chat = chatDao.getByConversationId(conversationId) ?: return null

        val requiresResync = chat.conversationVersion != conversationVersion
        val needsSync = requiresResync || latestEventSequence > chat.localEventSequence

        if (!needsSync) {
            return null
        }

        chatDao.upsert(
            chat.copy(
                conversationVersion = conversationVersion,
                latestEventSequence = if (requiresResync) latestEventSequence else maxOf(latestEventSequence, chat.latestEventSequence),
                localEventSequence = if (requiresResync) 0 else chat.localEventSequence,
                pendingFullResync = requiresResync || chat.pendingFullResync
            )
        )

        return chat.rowID
    }

    private fun scheduleReadConfirmationTimeout(chatRowID: Long) {
        scope.launch {
            delay(MUTATION_CONFIRMATION_TIMEOUT_MS)
            val chat = chatDao.getById(chatRowID) ?: return@launch
            if (chat.readAckPending) {
                rollbackPendingRead(chatRowID)
            }
        }
    }

    private suspend fun rollbackPendingRead(chatRowID: Long) {
        val unreadCount = messageDao.countUnreadIncoming(chatRowID)
        chatDao.updateUnreadState(
            rowID = chatRowID,
            hasUnreads = unreadCount > 0,
            unreadCount = unreadCount,
            lastReadMessageDate = messageDao.getLastReadIncomingDate(chatRowID),
            readAckPending = false
        )
    }

    private fun ConversationSummaryDto.toEntity(existing: ChatEntity?, pinnedRowIDs: Set<Long>): ChatEntity {
        val rowID = existing?.rowID ?: stableId("conversation", conversationId)
        val versionChanged = existing != null && existing.conversationVersion != conversationVersion
        val resetLocalState = existing == null || versionChanged

        return ChatEntity(
            rowID = rowID,
            conversationId = conversationId,
            guid = chatGuid,
            chatIdentifier = chatIdentifier ?: existing?.chatIdentifier ?: conversationId,
            displayName = displayName,
            serviceName = serviceName,
            isGroup = chatIdentifier.isNullOrBlank(),
            lastMessageDate = if (resetLocalState) null else existing?.lastMessageDate,
            lastMessageText = if (resetLocalState) null else existing?.lastMessageText,
            lastMessageIsFromMe = if (resetLocalState) null else existing?.lastMessageIsFromMe,
            isPinned = rowID in pinnedRowIDs,
            conversationVersion = conversationVersion,
            latestEventSequence = latestEventSequence,
            localEventSequence = if (resetLocalState) 0 else existing?.localEventSequence ?: 0,
            pendingFullResync = versionChanged || existing?.pendingFullResync == true,
            readAckPending = if (resetLocalState) false else existing?.readAckPending ?: false,
            hasUnreads = if (resetLocalState) false else existing?.hasUnreads ?: false,
            unreadCount = if (resetLocalState) 0 else existing?.unreadCount ?: 0,
            lastReadMessageDate = if (resetLocalState) null else existing?.lastReadMessageDate
        )
    }

    private fun ChatEntity.toDomainResolved(): Chat {
        val contactInfo = if (!isGroup) {
            contactResolver.resolve(chatIdentifier)
        } else {
            null
        }

        return Chat(
            rowID = rowID,
            guid = guid,
            chatIdentifier = chatIdentifier,
            displayName = displayName,
            serviceName = serviceName,
            isGroup = isGroup,
            lastMessageDate = lastMessageDate,
            lastMessageText = lastMessageText,
            lastMessageIsFromMe = lastMessageIsFromMe,
            isPinned = isPinned,
            hasUnreads = hasUnreads,
            unreadCount = unreadCount,
            lastReadMessageDate = lastReadMessageDate,
            resolvedName = contactInfo?.displayName,
            avatarUri = contactInfo?.photoUri,
            avatarInitials = contactInfo?.initials ?: ContactResolver.initialsFrom(displayName)
        )
    }

    private companion object {
        const val MUTATION_CONFIRMATION_TIMEOUT_MS = 15_000L
    }
}

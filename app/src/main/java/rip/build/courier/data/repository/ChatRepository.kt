package rip.build.courier.data.repository

import rip.build.courier.data.local.dao.ChatDao
import rip.build.courier.data.local.dao.MessageDao
import rip.build.courier.data.local.dao.ParticipantDao
import rip.build.courier.data.local.entity.ChatEntity
import rip.build.courier.data.local.entity.ParticipantEntity
import rip.build.courier.data.remote.api.BridgeApiService
import rip.build.courier.domain.model.Chat
import rip.build.courier.domain.model.Participant
import rip.build.courier.domain.util.ContactResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val api: BridgeApiService,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val participantDao: ParticipantDao,
    private val contactResolver: ContactResolver
) {
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

    suspend fun setHasUnreads(chatRowID: Long, hasUnreads: Boolean) {
        val unreadCount = if (hasUnreads) 1 else 0
        chatDao.updateUnreadState(chatRowID, hasUnreads, unreadCount, null)
    }

    suspend fun markAsRead(chatRowID: Long) {
        messageDao.markAllIncomingRead(chatRowID)
        chatDao.updateUnreadState(chatRowID, false, 0, java.time.Instant.now().toString())
        try {
            api.markChatAsRead(chatRowID)
        } catch (_: Exception) {
            // Best-effort: the local unread state is already cleared
        }
    }

    data class ChatRefreshResult(
        val chatsNeedingSync: List<Long>
    )

    suspend fun refreshChatsAndFindUpdates(): Result<ChatRefreshResult> {
        return try {
            val localChats = chatDao.getAll().associateBy { it.rowID }
            val pinnedRowIDs = chatDao.getPinnedRowIDs().toSet()
            contactResolver.clearCache()
            val response = api.getChats()

            val chatsNeedingSync = response.chats.filter { dto ->
                val local = localChats[dto.rowID]
                local == null
                    || dto.maxMessageRowID > local.lastMessageSyncRowID
                    || dto.maxReactionRowID > local.lastReactionSyncRowID
                    || (dto.maxReadReceiptDate != null && (local?.lastReadReceiptSyncTimestamp == null || dto.maxReadReceiptDate > local.lastReadReceiptSyncTimestamp))
                    || (dto.maxDeliveryReceiptDate != null && (local?.lastDeliveryReceiptSyncTimestamp == null || dto.maxDeliveryReceiptDate > local.lastDeliveryReceiptSyncTimestamp))
            }.sortedWith(
                compareByDescending<rip.build.courier.data.remote.api.dto.ChatDto> { it.rowID in pinnedRowIDs }
                    .thenByDescending { it.lastMessageDate }
            ).map { it.rowID }

            // Upsert chat metadata, preserving sync cursors and pin state
            val entities = response.chats.map { dto ->
                val existing = localChats[dto.rowID]
                ChatEntity(
                    rowID = dto.rowID,
                    guid = dto.guid,
                    chatIdentifier = dto.chatIdentifier,
                    displayName = dto.displayName,
                    serviceName = dto.serviceName,
                    isGroup = dto.isGroup,
                    lastMessageDate = dto.lastMessageDate,
                    lastMessageText = dto.lastMessageText,
                    lastMessageIsFromMe = dto.lastMessageIsFromMe,
                    isPinned = dto.rowID in pinnedRowIDs,
                    lastMessageSyncRowID = existing?.lastMessageSyncRowID ?: 0,
                    lastReactionSyncRowID = existing?.lastReactionSyncRowID ?: 0,
                    lastReadReceiptSyncTimestamp = existing?.lastReadReceiptSyncTimestamp,
                    lastDeliveryReceiptSyncTimestamp = existing?.lastDeliveryReceiptSyncTimestamp,
                    hasUnreads = dto.hasUnreads,
                    unreadCount = dto.unreadCount,
                    lastReadMessageDate = dto.lastReadMessageDate,
                    maxReadReceiptDate = dto.maxReadReceiptDate,
                    maxDeliveryReceiptDate = dto.maxDeliveryReceiptDate
                )
            }
            chatDao.upsertAll(entities)
            response.chats.forEach { dto -> dto.lastReadMessageDate?.let { messageDao.markIncomingReadThrough(dto.rowID, it) } }

            Result.success(ChatRefreshResult(chatsNeedingSync))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshChats(): Result<List<Chat>> {
        return try {
            val localChats = chatDao.getAll().associateBy { it.rowID }
            val pinnedRowIDs = chatDao.getPinnedRowIDs().toSet()
            contactResolver.clearCache()
            val response = api.getChats()
            val entities = response.chats.map { dto ->
                val existing = localChats[dto.rowID]
                ChatEntity(
                    rowID = dto.rowID,
                    guid = dto.guid,
                    chatIdentifier = dto.chatIdentifier,
                    displayName = dto.displayName,
                    serviceName = dto.serviceName,
                    isGroup = dto.isGroup,
                    lastMessageDate = dto.lastMessageDate,
                    lastMessageText = dto.lastMessageText,
                    lastMessageIsFromMe = dto.lastMessageIsFromMe,
                    isPinned = dto.rowID in pinnedRowIDs,
                    lastMessageSyncRowID = existing?.lastMessageSyncRowID ?: 0,
                    lastReactionSyncRowID = existing?.lastReactionSyncRowID ?: 0,
                    lastReadReceiptSyncTimestamp = existing?.lastReadReceiptSyncTimestamp,
                    lastDeliveryReceiptSyncTimestamp = existing?.lastDeliveryReceiptSyncTimestamp,
                    hasUnreads = dto.hasUnreads,
                    unreadCount = dto.unreadCount,
                    lastReadMessageDate = dto.lastReadMessageDate,
                    maxReadReceiptDate = dto.maxReadReceiptDate,
                    maxDeliveryReceiptDate = dto.maxDeliveryReceiptDate
                )
            }
            chatDao.upsertAll(entities)
            response.chats.forEach { dto -> dto.lastReadMessageDate?.let { messageDao.markIncomingReadThrough(dto.rowID, it) } }
            Result.success(entities.map { it.toDomainResolved() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshParticipants(chatRowID: Long): Result<List<Participant>> {
        return try {
            val dtos = api.getParticipants(chatRowID)
            val entities = dtos.map { dto ->
                ParticipantEntity(
                    rowID = dto.rowID,
                    chatRowID = chatRowID,
                    id = dto.id,
                    service = dto.service
                )
            }
            participantDao.deleteByChatId(chatRowID)
            participantDao.upsertAll(entities)
            Result.success(dtos.map { Participant(it.rowID, it.id, it.service) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeParticipants(chatRowID: Long): Flow<List<Participant>> =
        participantDao.observeByChatId(chatRowID).map { entities ->
            entities.map { Participant(it.rowID, it.id, it.service) }
        }

    private fun ChatEntity.toDomainResolved(): Chat {
        val contactInfo = if (!isGroup) {
            contactResolver.resolve(chatIdentifier)
        } else null

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
            avatarInitials = contactInfo?.initials
                ?: ContactResolver.initialsFrom(displayName)
        )
    }
}

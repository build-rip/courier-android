package rip.build.courier.data.repository

import android.util.Log
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import rip.build.courier.data.local.dao.AttachmentDao
import rip.build.courier.data.local.dao.ChatDao
import rip.build.courier.data.local.dao.ConversationEventDao
import rip.build.courier.data.local.dao.MessageDao
import rip.build.courier.data.local.dao.ReactionDao
import rip.build.courier.data.local.entity.AttachmentEntity
import rip.build.courier.data.local.entity.ChatEntity
import rip.build.courier.data.local.entity.ConversationEventEntity
import rip.build.courier.data.local.entity.MessageEntity
import rip.build.courier.data.local.entity.ReactionEntity
import rip.build.courier.data.remote.api.BridgeApiException
import rip.build.courier.data.remote.api.BridgeApiService
import rip.build.courier.data.remote.api.dto.ConversationEventDto
import rip.build.courier.data.remote.api.dto.ConversationReactionEventPayloadDto
import rip.build.courier.data.remote.api.dto.ConversationResyncRequiredDto
import rip.build.courier.data.remote.api.dto.MessageCreatedEventPayloadDto
import rip.build.courier.data.remote.api.dto.MessageDeletedEventPayloadDto
import rip.build.courier.data.remote.api.dto.MessageDeliveredUpdatedEventPayloadDto
import rip.build.courier.data.remote.api.dto.MessageEditedEventPayloadDto
import rip.build.courier.data.remote.api.dto.MessageReadUpdatedEventPayloadDto
import rip.build.courier.data.remote.api.dto.MutationResponseDto
import rip.build.courier.data.remote.api.dto.NormalizedAttachmentDto
import rip.build.courier.data.remote.api.dto.RichTextDto
import rip.build.courier.data.remote.api.dto.SendMessageRequest
import rip.build.courier.data.remote.api.dto.TapbackRequest
import rip.build.courier.domain.model.AttachmentInfo
import rip.build.courier.domain.model.Message
import rip.build.courier.domain.model.Reaction
import rip.build.courier.domain.model.ReactionType
import rip.build.courier.domain.model.ReplyContext
import rip.build.courier.domain.model.RichText
import rip.build.courier.domain.model.RichTextAttributes
import rip.build.courier.domain.model.RichTextPart
import rip.build.courier.domain.model.SendStatus
import retrofit2.Response

@Singleton
class MessageRepository @Inject constructor(
    private val api: BridgeApiService,
    private val messageDao: MessageDao,
    private val reactionDao: ReactionDao,
    private val attachmentDao: AttachmentDao,
    private val chatDao: ChatDao,
    private val conversationEventDao: ConversationEventDao,
    private val moshi: Moshi
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val syncMutexes = ConcurrentHashMap<Long, Mutex>()

    private val anyAdapter = moshi.adapter(Any::class.java)
    private val richTextAdapter = moshi.adapter(RichTextDto::class.java)
    private val resyncRequiredAdapter = moshi.adapter(ConversationResyncRequiredDto::class.java)
    private val messageCreatedAdapter = moshi.adapter(MessageCreatedEventPayloadDto::class.java)
    private val messageEditedAdapter = moshi.adapter(MessageEditedEventPayloadDto::class.java)
    private val messageDeletedAdapter = moshi.adapter(MessageDeletedEventPayloadDto::class.java)
    private val reactionAdapter = moshi.adapter(ConversationReactionEventPayloadDto::class.java)
    private val messageReadAdapter = moshi.adapter(MessageReadUpdatedEventPayloadDto::class.java)
    private val messageDeliveredAdapter = moshi.adapter(MessageDeliveredUpdatedEventPayloadDto::class.java)

    fun observeMessages(chatRowID: Long): Flow<List<Message>> =
        combine(
            messageDao.observeByChatId(chatRowID),
            reactionDao.observeByChatId(chatRowID)
        ) { messages, reactions ->
            val reactionsByGuid = reactions.groupBy { it.targetMessageGUID }
            val domainMessages = messages.map { message ->
                message.toDomain(reactionsByGuid[message.guid].orEmpty().map { it.toDomain() })
            }

            val byGuid = domainMessages.associateBy { it.guid }
            val replyCounts = domainMessages
                .mapNotNull { it.threadOriginatorGuid }
                .groupingBy { it }
                .eachCount()

            domainMessages.map { message ->
                val replyContext = message.threadOriginatorGuid?.let { parentGuid ->
                    byGuid[parentGuid]?.let { parent ->
                        ReplyContext(
                            parentText = parent.text,
                            parentSenderID = parent.senderID,
                            parentIsFromMe = parent.isFromMe,
                            parentHasAttachments = parent.hasAttachments
                        )
                    }
                }

                val replyCount = replyCounts[message.guid] ?: 0
                if (replyContext != null || replyCount > 0) {
                    message.copy(replyContext = replyContext, replyCount = replyCount)
                } else {
                    message
                }
            }
        }

    fun observeAttachments(messageRowID: Long): Flow<List<AttachmentInfo>> =
        attachmentDao.observeByMessageId(messageRowID).map { attachments -> attachments.map { it.toDomain() } }

    fun observeMediaForChat(chatRowID: Long): Flow<List<AttachmentInfo>> =
        attachmentDao.observeMediaByChatId(chatRowID).map { attachments -> attachments.map { it.toDomain() } }

    suspend fun syncChat(chatRowID: Long): Result<Pair<Int, Int>> {
        val mutex = syncMutexes.getOrPut(chatRowID) { Mutex() }
        return mutex.withLock {
            try {
                var chat = chatDao.getById(chatRowID) ?: return@withLock Result.success(0 to 0)
                if (chat.pendingFullResync) {
                    wipeConversationData(chat, chat.conversationVersion, chat.latestEventSequence)
                    chat = chatDao.getById(chatRowID) ?: chat
                }

                var localSequence = chat.localEventSequence
                var conversationVersion = chat.conversationVersion
                var totalMessageEvents = 0
                var totalReactionEvents = 0

                while (localSequence < chat.latestEventSequence || localSequence == 0L) {
                    val response = api.getConversationEvents(
                        conversationId = chat.conversationId,
                        conversationVersion = conversationVersion,
                        from = localSequence,
                        limit = SYNC_PAGE_SIZE
                    )

                    if (response.code() == 409) {
                        val resync = response.errorBody()?.string()?.let(resyncRequiredAdapter::fromJson)
                            ?: throw BridgeApiException(409, "resyncRequired")
                        wipeConversationData(chat, resync.conversationVersion, resync.latestEventSequence)
                        conversationVersion = resync.conversationVersion
                        localSequence = 0L
                        chat = chatDao.getById(chatRowID) ?: chat.copy(
                            conversationVersion = resync.conversationVersion,
                            latestEventSequence = resync.latestEventSequence,
                            localEventSequence = 0L,
                            pendingFullResync = false
                        )
                        continue
                    }

                    val body = response.body()
                    if (!response.isSuccessful || body == null) {
                        val errorBody = response.errorBody()?.string().orEmpty()
                        throw BridgeApiException(response.code(), errorBody)
                    }

                    val newEvents = body.events.map { dto -> dto.toEntity() }
                    if (newEvents.isNotEmpty()) {
                        conversationEventDao.upsertAll(newEvents)
                    }

                    totalMessageEvents += body.events.count { it.eventType.startsWith("message") }
                    totalReactionEvents += body.events.count { it.eventType.startsWith("reaction") }

                    localSequence = body.nextFrom
                    chatDao.updateSyncState(
                        chatRowID = chatRowID,
                        latestEventSequence = body.latestEventSequence,
                        localEventSequence = localSequence,
                        pendingFullResync = false
                    )

                    if (!body.hasMore || body.nextFrom >= body.latestEventSequence) {
                        break
                    }
                }

                rebuildConversationProjection(chatRowID)
                reconcilePendingMessages(chatRowID)
                reconcilePendingReactions(chatRowID)
                Result.success(totalMessageEvents to totalReactionEvents)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun sendMessage(chatRowID: Long, text: String): Result<Unit> {
        val chat = chatDao.getById(chatRowID) ?: return Result.failure(IllegalArgumentException("Conversation not found"))
        val pendingRowID = -System.nanoTime()
        val pending = MessageEntity(
            rowID = pendingRowID,
            guid = "local-${UUID.randomUUID()}",
            chatRowID = chatRowID,
            text = text,
            senderID = null,
            date = Instant.now().toString(),
            dateRead = null,
            dateDelivered = null,
            isRead = true,
            isFromMe = true,
            service = chat.serviceName,
            hasAttachments = false,
            sendStatus = "sending"
        )
        messageDao.upsert(pending)

        return mutate(
            conversation = chat,
            request = {
                api.sendMessage(
                    conversationId = chat.conversationId,
                    request = SendMessageRequest(
                        text = text,
                        conversationVersion = chat.conversationVersion,
                        fromEventSequence = chat.localEventSequence
                    )
                )
            },
            onAccepted = {
                schedulePendingMessageTimeout(pendingRowID)
                Result.success(Unit)
            },
            onRejected = { error ->
                messageDao.updateSendStatus(pendingRowID, "failed", error)
                Result.failure(IllegalStateException(error))
            },
            onError = { error ->
                messageDao.updateSendStatus(pendingRowID, "failed", error.message ?: "Network error")
                Result.failure(error)
            }
        )
    }

    suspend fun retryMessage(pendingRowID: Long) {
        val entity = messageDao.getByRowID(pendingRowID) ?: return
        val chat = chatDao.getById(entity.chatRowID) ?: return
        val text = entity.text ?: return
        messageDao.updateSendStatus(pendingRowID, "sending", null)

        mutate(
            conversation = chat,
            request = {
                api.sendMessage(
                    conversationId = chat.conversationId,
                    request = SendMessageRequest(
                        text = text,
                        conversationVersion = chat.conversationVersion,
                        fromEventSequence = chat.localEventSequence
                    )
                )
            },
            onAccepted = {
                schedulePendingMessageTimeout(pendingRowID)
                Result.success(Unit)
            },
            onRejected = { error ->
                messageDao.updateSendStatus(pendingRowID, "failed", error)
                Result.failure(IllegalStateException(error))
            },
            onError = { error ->
                messageDao.updateSendStatus(pendingRowID, "failed", error.message ?: "Network error")
                Result.failure(error)
            }
        )
    }

    suspend fun deletePendingMessage(pendingRowID: Long) {
        messageDao.deleteByRowID(pendingRowID)
    }

    suspend fun sendTapback(
        chatRowID: Long,
        type: String,
        messageGUID: String? = null,
        partIndex: Int = 0,
        emoji: String? = null
    ): Result<Unit> {
        val chat = chatDao.getById(chatRowID) ?: return Result.failure(IllegalArgumentException("Conversation not found"))
        val targetGuid = messageGUID ?: return Result.failure(IllegalArgumentException("messageGUID required"))
        val previousReaction = reactionDao.findMyConfirmedReaction(targetGuid, partIndex)
        if (previousReaction != null) {
            reactionDao.deleteByRowID(previousReaction.rowID)
        }

        val pendingRowID = -System.nanoTime()
        val pending = ReactionEntity(
            rowID = pendingRowID,
            guid = "local-${UUID.randomUUID()}",
            chatRowID = chatRowID,
            targetMessageGUID = targetGuid,
            partIndex = partIndex,
            reactionType = type,
            emoji = emoji,
            isRemoval = false,
            senderID = null,
            isFromMe = true,
            date = Instant.now().toString(),
            sendStatus = "sending"
        )
        reactionDao.upsert(pending)

        return mutate(
            conversation = chat,
            request = {
                api.sendTapback(
                    conversationId = chat.conversationId,
                    request = TapbackRequest(
                        type = type,
                        messageGUID = targetGuid,
                        partIndex = partIndex,
                        emoji = emoji,
                        conversationVersion = chat.conversationVersion,
                        fromEventSequence = chat.localEventSequence
                    )
                )
            },
            onAccepted = {
                schedulePendingReactionTimeout(pendingRowID) {
                    previousReaction?.let { reactionDao.upsert(it) }
                }
                Result.success(Unit)
            },
            onRejected = { error ->
                previousReaction?.let { reactionDao.upsert(it) }
                reactionDao.updateSendStatus(pendingRowID, "failed", error)
                Result.failure(IllegalStateException(error))
            },
            onError = { error ->
                previousReaction?.let { reactionDao.upsert(it) }
                reactionDao.updateSendStatus(pendingRowID, "failed", error.message ?: "Network error")
                Result.failure(error)
            }
        )
    }

    suspend fun removeTapback(
        chatRowID: Long,
        type: String,
        messageGUID: String,
        partIndex: Int = 0,
        emoji: String? = null
    ): Result<Unit> {
        val chat = chatDao.getById(chatRowID) ?: return Result.failure(IllegalArgumentException("Conversation not found"))
        val previousReaction = reactionDao.findMyConfirmedReaction(messageGUID, partIndex)
        previousReaction?.let { reactionDao.deleteByRowID(it.rowID) }

        val pendingRowID = -System.nanoTime()
        val pendingRemoval = ReactionEntity(
            rowID = pendingRowID,
            guid = "local-${UUID.randomUUID()}",
            chatRowID = chatRowID,
            targetMessageGUID = messageGUID,
            partIndex = partIndex,
            reactionType = type,
            emoji = emoji,
            isRemoval = true,
            senderID = previousReaction?.senderID,
            isFromMe = true,
            date = previousReaction?.date ?: Instant.now().toString(),
            sendStatus = "sending"
        )
        reactionDao.upsert(pendingRemoval)

        return mutate(
            conversation = chat,
            request = {
                api.removeTapback(
                    conversationId = chat.conversationId,
                    request = TapbackRequest(
                        type = type,
                        messageGUID = messageGUID,
                        partIndex = partIndex,
                        emoji = emoji,
                        conversationVersion = chat.conversationVersion,
                        fromEventSequence = chat.localEventSequence
                    )
                )
            },
            onAccepted = {
                schedulePendingReactionTimeout(pendingRowID) {
                    restoreRemovedReaction(pendingRowID)
                }
                Result.success(Unit)
            },
            onRejected = { error ->
                reactionDao.deleteByRowID(pendingRowID)
                previousReaction?.let { reactionDao.upsert(it) }
                Result.failure(IllegalStateException(error))
            },
            onError = { error ->
                reactionDao.deleteByRowID(pendingRowID)
                previousReaction?.let { reactionDao.upsert(it) }
                Result.failure(error)
            }
        )
    }

    suspend fun retryTapback(pendingRowID: Long) {
        val entity = reactionDao.getByRowID(pendingRowID) ?: return
        val chat = chatDao.getById(entity.chatRowID) ?: return
        val previousReaction = if (entity.isRemoval) {
            null
        } else {
            reactionDao.findMyConfirmedReaction(entity.targetMessageGUID, entity.partIndex)?.also {
                reactionDao.deleteByRowID(it.rowID)
            }
        }
        reactionDao.updateSendStatus(pendingRowID, "sending", null)

        mutate(
            conversation = chat,
            request = {
                val request = TapbackRequest(
                    type = entity.reactionType,
                    messageGUID = entity.targetMessageGUID,
                    partIndex = entity.partIndex,
                    emoji = entity.emoji,
                    conversationVersion = chat.conversationVersion,
                    fromEventSequence = chat.localEventSequence
                )
                if (entity.isRemoval) {
                    api.removeTapback(chat.conversationId, request)
                } else {
                    api.sendTapback(chat.conversationId, request)
                }
            },
            onAccepted = {
                schedulePendingReactionTimeout(pendingRowID) {
                    if (entity.isRemoval) {
                        restoreRemovedReaction(pendingRowID)
                    } else {
                        previousReaction?.let { reactionDao.upsert(it) }
                    }
                }
                Result.success(Unit)
            },
            onRejected = { error ->
                if (entity.isRemoval) {
                    restoreRemovedReaction(pendingRowID)
                } else {
                    previousReaction?.let { reactionDao.upsert(it) }
                    reactionDao.updateSendStatus(pendingRowID, "failed", error)
                }
                Result.failure(IllegalStateException(error))
            },
            onError = { error ->
                if (entity.isRemoval) {
                    restoreRemovedReaction(pendingRowID)
                } else {
                    previousReaction?.let { reactionDao.upsert(it) }
                    reactionDao.updateSendStatus(pendingRowID, "failed", error.message ?: "Network error")
                }
                Result.failure(error)
            }
        )
    }

    suspend fun deletePendingReaction(pendingRowID: Long) {
        reactionDao.deleteByRowID(pendingRowID)
    }

    private suspend fun rebuildConversationProjection(chatRowID: Long) {
        val chat = chatDao.getById(chatRowID) ?: return
        val events = conversationEventDao.getByConversationId(chat.conversationId)
        val existingAttachments = attachmentDao.getByChatId(chatRowID).associateBy { it.guid }

        val messagesById = linkedMapOf<String, ReducedMessageState>()
        val reactionsByMessageId = mutableMapOf<String, MutableMap<ReactionIdentity, ReducedReactionState>>()

        for (event in events) {
            when (event.eventType) {
                EVENT_MESSAGE_CREATED -> run {
                    parsePayload(messageCreatedAdapter, event)?.let { payload ->
                        messagesById[payload.messageID] = ReducedMessageState(
                            messageID = payload.messageID,
                            senderID = payload.senderID,
                            isFromMe = payload.isFromMe,
                            service = payload.service,
                            sentAt = payload.sentAt,
                            text = payload.text,
                            richText = payload.richText,
                            attachments = payload.attachments,
                            replyToMessageID = payload.replyToMessageID,
                            deliveredAt = null,
                            readAt = null,
                            deletedAt = null
                        )
                    }
                }

                EVENT_MESSAGE_EDITED -> parsePayload(messageEditedAdapter, event)?.let { payload ->
                    messagesById[payload.messageID]?.let { message ->
                        messagesById[payload.messageID] = message.copy(
                            text = payload.text ?: message.text,
                            richText = payload.richText ?: message.richText
                        )
                    }
                }

                EVENT_MESSAGE_DELETED -> parsePayload(messageDeletedAdapter, event)?.let { payload ->
                    messagesById[payload.messageID]?.let { message ->
                        messagesById[payload.messageID] = message.copy(deletedAt = payload.deletedAt)
                    }
                }

                EVENT_MESSAGE_READ_UPDATED -> parsePayload(messageReadAdapter, event)?.let { payload ->
                    messagesById[payload.messageID]?.let { message ->
                        messagesById[payload.messageID] = message.copy(readAt = payload.readAt)
                    }
                }

                EVENT_MESSAGE_DELIVERED_UPDATED -> parsePayload(messageDeliveredAdapter, event)?.let { payload ->
                    messagesById[payload.messageID]?.let { message ->
                        messagesById[payload.messageID] = message.copy(deliveredAt = payload.deliveredAt)
                    }
                }

                EVENT_REACTION_SET -> parsePayload(reactionAdapter, event)?.let { payload ->
                    if (messagesById.containsKey(payload.messageID)) {
                        reactionsByMessageId.getOrPut(payload.messageID) { linkedMapOf() }[
                            ReactionIdentity(payload)
                        ] = ReducedReactionState(payload)
                    }
                }

                EVENT_REACTION_REMOVED -> parsePayload(reactionAdapter, event)?.let { payload ->
                    reactionsByMessageId[payload.messageID]?.remove(ReactionIdentity(payload))
                }
            }
        }

        val reducedMessages = messagesById.values.sortedWith(compareBy<ReducedMessageState>({ it.sentAt }, { it.messageID }))
        val authoritativeMessages = reducedMessages.map { reduced -> reduced.toEntity(chatRowID) }
        val authoritativeAttachments = reducedMessages.flatMap { reduced ->
            if (reduced.deletedAt == null) {
                reduced.attachments.map { attachment -> attachment.toEntity(reduced.messageID, existingAttachments) }
            } else {
                emptyList()
            }
        }
        val authoritativeReactions = reactionsByMessageId.flatMap { (messageId, reactions) ->
            reactions.values
                .sortedWith(compareBy<ReducedReactionState>({ it.partIndex }, { it.reactionType }, { it.emoji ?: "" }, { it.actorID ?: "" }, { it.sentAt }))
                .map { reaction -> reaction.toEntity(chatRowID, messageId) }
        }

        attachmentDao.deleteByChatId(chatRowID)
        messageDao.deleteConfirmedByChatId(chatRowID)
        reactionDao.deleteConfirmedForChat(chatRowID)
        if (authoritativeMessages.isNotEmpty()) messageDao.upsertAll(authoritativeMessages)
        if (authoritativeReactions.isNotEmpty()) reactionDao.upsertAll(authoritativeReactions)
        if (authoritativeAttachments.isNotEmpty()) attachmentDao.upsertAll(authoritativeAttachments)

        val visibleMessages = authoritativeMessages.filter { it.deletedAt == null && (it.text != null || it.richText != null || it.hasAttachments) }
        val lastMessage = visibleMessages.maxWithOrNull(compareBy<MessageEntity>({ it.date }, { it.guid }))
        val authoritativeUnreadCount = authoritativeMessages.count { !it.isFromMe && !it.isRead && it.deletedAt == null }
        val keepOptimisticReadState = chat.readAckPending && authoritativeUnreadCount > 0
        val lastReadMessageDate = authoritativeMessages
            .filter { !it.isFromMe && it.isRead && it.deletedAt == null }
            .maxByOrNull { it.date }
            ?.date

        chatDao.upsert(
            chat.copy(
                lastMessageDate = lastMessage?.date,
                lastMessageText = lastMessage?.let { cleanMessageText(it.text) },
                lastMessageIsFromMe = lastMessage?.isFromMe,
                hasUnreads = if (keepOptimisticReadState) false else authoritativeUnreadCount > 0,
                unreadCount = if (keepOptimisticReadState) 0 else authoritativeUnreadCount,
                lastReadMessageDate = if (keepOptimisticReadState) chat.lastReadMessageDate else lastReadMessageDate,
                readAckPending = keepOptimisticReadState
            )
        )
    }

    private suspend fun reconcilePendingMessages(chatRowID: Long) {
        val pendingMessages = messageDao.getPendingByChatId(chatRowID)
        val confirmedMessages = messageDao.getByChatId(chatRowID)
            .filter { it.sendStatus == null && it.isFromMe }
            .sortedBy { it.date }
            .toMutableList()

        for (pending in pendingMessages.sortedBy { it.date }) {
            val matchIndex = confirmedMessages.indexOfFirst { confirmed ->
                confirmed.text == pending.text && isEventNearPending(pending.date, confirmed.date)
            }
            if (matchIndex >= 0) {
                confirmedMessages.removeAt(matchIndex)
                messageDao.deleteByRowID(pending.rowID)
            }
        }
    }

    private suspend fun reconcilePendingReactions(chatRowID: Long) {
        val pendingReactions = reactionDao.getPendingByChatId(chatRowID)
        val confirmedReactions = reactionDao.getByChatId(chatRowID).filter { it.sendStatus == null }

        for (pending in pendingReactions) {
            val confirmed = confirmedReactions.any { reaction ->
                reaction.targetMessageGUID == pending.targetMessageGUID &&
                    reaction.partIndex == pending.partIndex &&
                    reaction.reactionType == pending.reactionType &&
                    reaction.emoji == pending.emoji &&
                    reaction.isFromMe
            }

            if (!pending.isRemoval && confirmed) {
                reactionDao.deleteByRowID(pending.rowID)
            } else if (pending.isRemoval && !confirmed) {
                reactionDao.deleteByRowID(pending.rowID)
            }
        }
    }

    private suspend fun wipeConversationData(chat: ChatEntity, version: Int, latestEventSequence: Long) {
        attachmentDao.deleteByChatId(chat.rowID)
        messageDao.deleteByChatId(chat.rowID)
        reactionDao.deleteByChatId(chat.rowID)
        conversationEventDao.deleteByConversationId(chat.conversationId)
        chatDao.upsert(
            chat.copy(
                conversationVersion = version,
                latestEventSequence = latestEventSequence,
                localEventSequence = 0,
                pendingFullResync = false,
                readAckPending = false,
                hasUnreads = false,
                unreadCount = 0,
                lastReadMessageDate = null,
                lastMessageDate = null,
                lastMessageText = null,
                lastMessageIsFromMe = null
            )
        )
    }

    private suspend fun restoreRemovedReaction(pendingRowID: Long) {
        val pending = reactionDao.getByRowID(pendingRowID) ?: return
        reactionDao.deleteByRowID(pendingRowID)
        if (pending.senderID == null && !pending.isFromMe) return
        val restored = pending.copy(
            rowID = stableId(
                "reaction",
                listOf(
                    pending.targetMessageGUID,
                    pending.senderID.orEmpty(),
                    pending.isFromMe.toString(),
                    pending.partIndex.toString(),
                    pending.reactionType,
                    pending.emoji.orEmpty()
                ).joinToString("|")
            ),
            guid = reactionGuid(
                messageID = pending.targetMessageGUID,
                actorID = pending.senderID,
                isFromMe = pending.isFromMe,
                partIndex = pending.partIndex,
                reactionType = pending.reactionType,
                emoji = pending.emoji
            ),
            isRemoval = false,
            sendStatus = null,
            sendError = null
        )
        reactionDao.upsert(restored)
    }

    private fun schedulePendingMessageTimeout(pendingRowID: Long) {
        scope.launch {
            delay(MUTATION_CONFIRMATION_TIMEOUT_MS)
            val pending = messageDao.getByRowID(pendingRowID) ?: return@launch
            if (pending.sendStatus == "sending") {
                messageDao.updateSendStatus(pendingRowID, "failed", "Timed out waiting for event-log confirmation")
            }
        }
    }

    private fun schedulePendingReactionTimeout(pendingRowID: Long, onTimeout: suspend () -> Unit) {
        scope.launch {
            delay(MUTATION_CONFIRMATION_TIMEOUT_MS)
            val pending = reactionDao.getByRowID(pendingRowID) ?: return@launch
            if (pending.sendStatus == "sending") {
                if (pending.isRemoval) {
                    onTimeout()
                } else {
                    onTimeout()
                    reactionDao.updateSendStatus(pendingRowID, "failed", "Timed out waiting for event-log confirmation")
                }
            }
        }
    }

    private suspend fun <T> mutate(
        conversation: ChatEntity,
        request: suspend () -> Response<MutationResponseDto>,
        onAccepted: suspend () -> Result<T>,
        onRejected: suspend (String) -> Result<T>,
        onError: suspend (Exception) -> Result<T>
    ): Result<T> {
        return try {
            val response = request()
            val body = response.body()

            if (!response.isSuccessful || body == null) {
                val errorBody = response.errorBody()?.string().orEmpty()
                onError(BridgeApiException(response.code(), errorBody))
            } else {
                applyMutationCursor(conversation, body)
                if (body.result == "success") {
                    onAccepted()
                } else {
                    onRejected(body.failureReason ?: "Mutation rejected")
                }
            }
        } catch (e: Exception) {
            onError(e)
        }
    }

    private suspend fun applyMutationCursor(chat: ChatEntity, response: MutationResponseDto) {
        val returnedVersion = response.conversationVersion ?: chat.conversationVersion
        val versionChanged = returnedVersion != chat.conversationVersion
        chatDao.upsert(
            chat.copy(
                conversationVersion = returnedVersion,
                latestEventSequence = if (versionChanged) {
                    response.latestEventSequence ?: chat.latestEventSequence
                } else {
                    maxOf(chat.latestEventSequence, response.latestEventSequence ?: chat.latestEventSequence)
                },
                localEventSequence = if (versionChanged) 0 else chat.localEventSequence,
                pendingFullResync = versionChanged || chat.pendingFullResync
            )
        )
    }

    private fun ConversationEventDto.toEntity(): ConversationEventEntity = ConversationEventEntity(
        conversationId = conversationID,
        conversationVersion = conversationVersion,
        eventSequence = eventSequence,
        eventType = eventType,
        payloadJson = normalizePayloadJson(anyAdapter.toJson(payload))
    )

    private fun normalizePayloadJson(rawJson: String): String {
        return try {
            val parsed = anyAdapter.fromJson(rawJson) ?: return rawJson
            anyAdapter.toJson(normalizeNumericValues(parsed))
        } catch (_: Exception) {
            rawJson
        }
    }

    private fun <T> parsePayload(adapter: JsonAdapter<T>, event: ConversationEventEntity): T? {
        val payloadJson = normalizePayloadJson(event.payloadJson)
        return try {
            adapter.fromJson(payloadJson)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse ${event.eventType} seq=${event.eventSequence}: $payloadJson", e)
            null
        }
    }

    private fun normalizeNumericValues(value: Any?): Any? = when (value) {
        is Double -> {
            val longValue = value.toLong()
            if (value == longValue.toDouble()) longValue else value
        }
        is List<*> -> value.map(::normalizeNumericValues)
        is Map<*, *> -> value.entries.associate { (key, nestedValue) ->
            key.toString() to normalizeNumericValues(nestedValue)
        }
        else -> value
    }

    private fun ReducedMessageState.toEntity(chatRowID: Long): MessageEntity {
        val isDeleted = deletedAt != null
        val safeText = if (isDeleted) null else text
        val safeRichText = if (isDeleted) null else richText?.toJson()
        val safeAttachments = if (isDeleted) emptyList() else attachments
        return MessageEntity(
            rowID = stableId("message", messageID),
            guid = messageID,
            chatRowID = chatRowID,
            text = safeText,
            senderID = senderID,
            date = sentAt,
            dateRead = readAt,
            dateDelivered = deliveredAt,
            isRead = isFromMe || readAt != null,
            isFromMe = isFromMe,
            service = service,
            hasAttachments = safeAttachments.isNotEmpty(),
            richText = safeRichText,
            threadOriginatorGuid = replyToMessageID,
            deletedAt = deletedAt
        )
    }

    private fun NormalizedAttachmentDto.toEntity(
        messageID: String,
        existingAttachments: Map<String, AttachmentEntity>
    ): AttachmentEntity {
        val messageRowID = stableId("message", messageID)
        val localAttachmentId = stableId("attachment", attachmentID)
        val existing = existingAttachments[attachmentID]
        val downloadId = existing?.downloadId ?: attachmentID.toLongOrNull()
        val initialState = when {
            existing != null -> existing.downloadState
            downloadId == null -> "unavailable"
            totalBytes > MAX_AUTO_DOWNLOAD_BYTES -> "too_large"
            else -> "pending"
        }

        return AttachmentEntity(
            messageRowID = messageRowID,
            rowID = localAttachmentId,
            guid = attachmentID,
            downloadId = downloadId,
            mimeType = mimeType,
            transferName = transferName,
            totalBytes = totalBytes,
            isSticker = isSticker,
            downloadState = existing?.downloadState ?: initialState,
            downloadedBytes = existing?.downloadedBytes ?: 0,
            localFilePath = existing?.localFilePath,
            downloadAttempts = existing?.downloadAttempts ?: 0
        )
    }

    private fun ReducedReactionState.toEntity(chatRowID: Long, messageID: String): ReactionEntity {
        return ReactionEntity(
            rowID = stableId(
                "reaction",
                listOf(messageID, actorID.orEmpty(), isFromMe.toString(), partIndex.toString(), reactionType, emoji.orEmpty())
                    .joinToString("|")
            ),
            guid = reactionGuid(messageID, actorID, isFromMe, partIndex, reactionType, emoji),
            chatRowID = chatRowID,
            targetMessageGUID = messageID,
            partIndex = partIndex,
            reactionType = reactionType,
            emoji = emoji,
            isRemoval = false,
            senderID = actorID,
            isFromMe = isFromMe,
            date = sentAt
        )
    }

    private fun RichTextDto.toJson(): String = richTextAdapter.toJson(this)

    private fun String.toRichTextDto(): RichTextDto? =
        try {
            richTextAdapter.fromJson(this)
        } catch (_: Exception) {
            null
        }

    private fun RichTextDto.toDomain(): RichText = RichText(
        parts = parts.map { part ->
            RichTextPart(
                text = part.text,
                attributes = RichTextAttributes(
                    bold = part.attributes.bold == true,
                    italic = part.attributes.italic == true,
                    strikethrough = part.attributes.strikethrough == true,
                    underline = part.attributes.underline == true,
                    link = part.attributes.link,
                    mention = part.attributes.mention,
                    attachmentIndex = part.attributes.attachmentIndex
                )
            )
        }
    )

    private fun MessageEntity.toDomain(reactions: List<Reaction>): Message = Message(
        rowID = rowID,
        guid = guid,
        chatRowID = chatRowID,
        text = cleanMessageText(text),
        senderID = senderID,
        date = date,
        dateRead = dateRead,
        dateDelivered = dateDelivered,
        isRead = isRead,
        isFromMe = isFromMe,
        service = service,
        hasAttachments = hasAttachments,
        balloonBundleID = balloonBundleID,
        reactions = reactions,
        richText = richText?.toRichTextDto()?.toDomain(),
        threadOriginatorGuid = threadOriginatorGuid,
        sendStatus = sendStatus?.let { SendStatus.valueOf(it.uppercase()) },
        sendError = sendError,
        linkPreviewTitle = linkPreviewTitle,
        linkPreviewSubtitle = linkPreviewSubtitle,
        linkPreviewURL = linkPreviewURL
    )

    private fun ReactionEntity.toDomain(): Reaction = Reaction(
        rowID = rowID,
        guid = guid,
        targetMessageGUID = targetMessageGUID,
        partIndex = partIndex,
        reactionType = ReactionType.fromString(reactionType),
        emoji = emoji,
        isRemoval = isRemoval,
        senderID = senderID,
        isFromMe = isFromMe,
        date = date,
        sendStatus = sendStatus?.let { SendStatus.valueOf(it.uppercase()) },
        sendError = sendError
    )

    private fun AttachmentEntity.toDomain(): AttachmentInfo = AttachmentInfo(
        messageRowID = messageRowID,
        rowID = rowID,
        guid = guid,
        downloadId = downloadId,
        mimeType = mimeType,
        transferName = transferName,
        totalBytes = totalBytes,
        isSticker = isSticker,
        downloadState = downloadState,
        downloadedBytes = downloadedBytes,
        localFilePath = localFilePath,
        downloadAttempts = downloadAttempts
    )

    private fun cleanMessageText(raw: String?): String? =
        raw?.takeUnless { it.endsWith(".pluginPayloadAttachment") }

    private fun reactionGuid(
        messageID: String,
        actorID: String?,
        isFromMe: Boolean,
        partIndex: Int,
        reactionType: String,
        emoji: String?
    ): String = listOf(messageID, actorID.orEmpty(), isFromMe.toString(), partIndex.toString(), reactionType, emoji.orEmpty())
        .joinToString("|")

    private fun isEventNearPending(pendingDate: String, confirmedDate: String): Boolean {
        return try {
            val pendingInstant = Instant.parse(pendingDate)
            val confirmedInstant = Instant.parse(confirmedDate)
            !confirmedInstant.isBefore(pendingInstant.minusSeconds(5)) &&
                Duration.between(pendingInstant, confirmedInstant).abs() <= Duration.ofMinutes(5)
        } catch (_: Exception) {
            true
        }
    }

    private data class ReducedMessageState(
        val messageID: String,
        val senderID: String?,
        val isFromMe: Boolean,
        val service: String?,
        val sentAt: String,
        val text: String?,
        val richText: RichTextDto?,
        val attachments: List<NormalizedAttachmentDto>,
        val replyToMessageID: String?,
        val deliveredAt: String?,
        val readAt: String?,
        val deletedAt: String?
    )

    private data class ReducedReactionState(
        val actorID: String?,
        val isFromMe: Boolean,
        val sentAt: String,
        val partIndex: Int,
        val reactionType: String,
        val emoji: String?
    ) {
        constructor(payload: ConversationReactionEventPayloadDto) : this(
            actorID = payload.actorID,
            isFromMe = payload.isFromMe,
            sentAt = payload.sentAt,
            partIndex = payload.partIndex,
            reactionType = payload.reactionType,
            emoji = payload.emoji
        )
    }

    private data class ReactionIdentity(
        val actorID: String?,
        val isFromMe: Boolean,
        val partIndex: Int,
        val reactionType: String,
        val emoji: String?
    ) {
        constructor(payload: ConversationReactionEventPayloadDto) : this(
            actorID = payload.actorID,
            isFromMe = payload.isFromMe,
            partIndex = payload.partIndex,
            reactionType = payload.reactionType,
            emoji = payload.emoji
        )
    }

    companion object {
        private const val TAG = "MessageRepository"
        const val MAX_AUTO_DOWNLOAD_BYTES = 50L * 1024 * 1024

        private const val SYNC_PAGE_SIZE = 500
        private const val MUTATION_CONFIRMATION_TIMEOUT_MS = 15_000L

        private const val EVENT_MESSAGE_CREATED = "messageCreated"
        private const val EVENT_MESSAGE_EDITED = "messageEdited"
        private const val EVENT_MESSAGE_DELETED = "messageDeleted"
        private const val EVENT_REACTION_SET = "reactionSet"
        private const val EVENT_REACTION_REMOVED = "reactionRemoved"
        private const val EVENT_MESSAGE_READ_UPDATED = "messageReadUpdated"
        private const val EVENT_MESSAGE_DELIVERED_UPDATED = "messageDeliveredUpdated"
    }
}

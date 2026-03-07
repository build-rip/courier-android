package rip.build.courier.data.repository

import rip.build.courier.data.local.dao.AttachmentDao
import rip.build.courier.data.local.dao.ChatDao
import rip.build.courier.data.local.dao.MessageDao
import rip.build.courier.data.local.dao.ReactionDao
import rip.build.courier.data.local.entity.AttachmentEntity
import rip.build.courier.data.local.entity.ChatEntity
import rip.build.courier.data.local.entity.MessageEntity
import rip.build.courier.data.local.entity.ReactionEntity
import rip.build.courier.data.remote.api.BridgeApiException
import rip.build.courier.data.remote.api.BridgeApiService
import rip.build.courier.data.remote.api.dto.RichTextDto
import rip.build.courier.data.remote.api.dto.SendMessageRequest
import rip.build.courier.data.remote.api.dto.TapbackRequest
import rip.build.courier.domain.model.AttachmentInfo
import rip.build.courier.domain.model.AttachmentKey
import rip.build.courier.domain.model.Message
import rip.build.courier.domain.model.Reaction
import rip.build.courier.domain.model.ReactionType
import rip.build.courier.domain.model.ReplyContext
import rip.build.courier.domain.model.RichText
import rip.build.courier.domain.model.RichTextAttributes
import rip.build.courier.domain.model.RichTextPart
import rip.build.courier.domain.model.SendStatus
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val api: BridgeApiService,
    private val messageDao: MessageDao,
    private val reactionDao: ReactionDao,
    private val attachmentDao: AttachmentDao,
    private val chatDao: ChatDao,
    private val moshi: Moshi
) {
    private val richTextAdapter = moshi.adapter(RichTextDto::class.java)

    private fun RichTextDto.toJson(): String = richTextAdapter.toJson(this)

    private fun String.toRichTextDto(): RichTextDto? =
        try { richTextAdapter.fromJson(this) } catch (_: Exception) { null }

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

    private fun String?.toRichTextDomain(): RichText? =
        this?.toRichTextDto()?.toDomain()

    /**
     * Serialize a raw richText map from WebSocket payload to JSON string for storage.
     * The WebSocket payload arrives as Map<String, Any?>, so we re-serialize the richText
     * sub-map via Moshi's general-purpose adapter.
     */
    fun serializeRichTextPayload(richTextMap: Any?): String? {
        if (richTextMap == null) return null
        return try {
            val json = moshi.adapter(Any::class.java).toJson(richTextMap)
            // Verify it parses as valid RichTextDto before storing
            val dto = richTextAdapter.fromJson(json) ?: return null
            if (dto.parts.isEmpty()) return null
            json
        } catch (_: Exception) {
            null
        }
    }

    fun observeMessages(chatRowID: Long): Flow<List<Message>> =
        combine(
            messageDao.observeByChatId(chatRowID),
            reactionDao.observeByChatId(chatRowID)
        ) { messages, reactions ->
            val reactionsByGuid = reactions.groupBy { it.targetMessageGUID }
            val domainMessages = messages.map { msg ->
                val msgReactions = reactionsByGuid[msg.guid]?.map { it.toDomain() } ?: emptyList()
                msg.toDomain(msgReactions)
            }

            // Build lookup by GUID to resolve reply contexts
            val byGuid = domainMessages.associateBy { it.guid }
            // Count replies per originator GUID
            val replyCounts = domainMessages
                .mapNotNull { it.threadOriginatorGuid }
                .groupingBy { it }
                .eachCount()

            domainMessages.map { msg ->
                val replyContext = msg.threadOriginatorGuid?.let { originGuid ->
                    byGuid[originGuid]?.let { parent ->
                        ReplyContext(
                            parentText = parent.text,
                            parentSenderID = parent.senderID,
                            parentIsFromMe = parent.isFromMe,
                            parentHasAttachments = parent.hasAttachments
                        )
                    }
                }
                val count = replyCounts[msg.guid] ?: 0
                if (replyContext != null || count > 0) {
                    msg.copy(replyContext = replyContext, replyCount = count)
                } else {
                    msg
                }
            }
        }

    suspend fun refreshMessages(chatRowID: Long, after: Long? = null): Result<Unit> {
        return try {
            val dtos = api.getMessages(chatRowID, after = after)
            val entities = dtos.map { dto ->
                MessageEntity(
                    rowID = dto.rowID,
                    guid = dto.guid,
                    chatRowID = chatRowID,
                    text = dto.text,
                    senderID = dto.senderID,
                    date = dto.date,
                    dateRead = dto.dateRead,
                    dateDelivered = dto.dateDelivered,
                    isRead = dto.isRead,
                    isFromMe = dto.isFromMe,
                    service = dto.service,
                    hasAttachments = dto.hasAttachments,
                    richText = dto.richText?.toJson(),
                    balloonBundleID = dto.balloonBundleID,
                    threadOriginatorGuid = dto.threadOriginatorGuid,
                    linkPreviewTitle = dto.linkPreviewTitle,
                    linkPreviewSubtitle = dto.linkPreviewSubtitle,
                    linkPreviewURL = dto.linkPreviewURL
                )
            }
            messageDao.upsertAll(entities)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshReactions(chatRowID: Long, after: Long? = null): Result<Unit> {
        return try {
            val dtos = api.getReactions(chatRowID, after = after)
            val activeEntities = dtos.filter { !it.isRemoval }.map { dto ->
                ReactionEntity(
                    rowID = dto.rowID,
                    guid = dto.guid,
                    chatRowID = chatRowID,
                    targetMessageGUID = dto.targetMessageGUID,
                    partIndex = dto.partIndex,
                    reactionType = dto.reactionType,
                    emoji = dto.emoji,
                    isRemoval = false,
                    senderID = dto.senderID,
                    isFromMe = dto.isFromMe,
                    date = dto.date
                )
            }
            // Clear all server-confirmed reactions and replace with fresh data.
            // This ensures removed reactions don't linger in the local DB.
            reactionDao.deleteConfirmedForChat(chatRowID)
            reactionDao.upsertAll(activeEntities)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshAttachments(messageRowID: Long): Result<List<AttachmentInfo>> {
        return try {
            val dtos = api.getAttachments(messageRowID)
            // Skip rows already in the DB to preserve download state
            val existingRowIDs = attachmentDao.getExistingRowIDs(messageRowID).toSet()
            val newEntities = dtos.filter { it.rowID !in existingRowIDs }.map { dto ->
                val initialState = if (dto.totalBytes <= MAX_AUTO_DOWNLOAD_BYTES) "pending" else "too_large"
                AttachmentEntity(
                    rowID = dto.rowID,
                    messageRowID = messageRowID,
                    guid = dto.guid,
                    mimeType = dto.mimeType,
                    transferName = dto.transferName,
                    totalBytes = dto.totalBytes,
                    isSticker = dto.isSticker,
                    downloadState = initialState
                )
            }
            if (newEntities.isNotEmpty()) {
                attachmentDao.upsertAll(newEntities)
            }
            // Return all attachments for this message (including existing)
            val allEntities = attachmentDao.getByMessageId(messageRowID)
            Result.success(allEntities.map { it.toDomain() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val MAX_AUTO_DOWNLOAD_BYTES = 50L * 1024 * 1024 // 50 MB
    }

    suspend fun fetchMissingAttachmentMetadataForChat(
        chatRowID: Long,
        onPendingBatch: suspend (List<AttachmentKey>) -> Unit
    ) {
        val now = Instant.now()
        val chat = chatDao.getById(chatRowID)
        val isWarm = chat?.lastMessageDate?.let {
            try { Instant.parse(it).isAfter(now.minus(java.time.Duration.ofDays(30))) }
            catch (_: Exception) { false }
        } ?: false
        val days = if (isWarm) 180L else 30L
        val cutoff = now.minus(java.time.Duration.ofDays(days)).toString()
        val messageRowIDs = messageDao.getMessageRowIDsMissingAttachments(chatRowID, cutoff)
        for (msgRowID in messageRowIDs) {
            try {
                refreshAttachments(msgRowID).getOrNull()?.let { attachments ->
                    val pending = attachments.filter { it.downloadState == "pending" }.map { it.key }
                    if (pending.isNotEmpty()) onPendingBatch(pending)
                }
            } catch (_: Exception) {}
        }
        // Retry previously failed downloads (up to 3 attempts)
        attachmentDao.resetRetriableFailedDownloads()
        val remainingPending = attachmentDao.getAllPendingDownloads().map {
            AttachmentKey(messageRowID = it.messageRowID, attachmentRowID = it.rowID)
        }
        if (remainingPending.isNotEmpty()) onPendingBatch(remainingPending)
    }

    fun observeAttachments(messageRowID: Long): Flow<List<AttachmentInfo>> =
        attachmentDao.observeByMessageId(messageRowID).map { entities ->
            entities.map { it.toDomain() }
        }

    fun observeMediaForChat(chatRowID: Long): Flow<List<AttachmentInfo>> =
        attachmentDao.observeMediaByChatId(chatRowID).map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun sendMessage(chatRowID: Long, text: String): Result<Unit> {
        val pendingRowID = -System.nanoTime()
        val localGuid = "local-${UUID.randomUUID()}"
        val now = Instant.now().toString()

        // Optimistic insert
        val pending = MessageEntity(
            rowID = pendingRowID,
            guid = localGuid,
            chatRowID = chatRowID,
            text = text,
            senderID = null,
            date = now,
            dateRead = null,
            dateDelivered = null,
            isRead = true,
            isFromMe = true,
            service = "instant",
            hasAttachments = false,
            sendStatus = "sending"
        )
        messageDao.upsert(pending)

        return try {
            val response = api.sendMessage(chatRowID, SendMessageRequest(text = text))
            if (!response.isSuccessful) {
                val body = response.errorBody()?.string() ?: ""
                val ex = BridgeApiException(response.code(), body)
                messageDao.updateSendStatus(pendingRowID, "failed", "HTTP ${response.code()}: ${ex.displayMessage}")
                Result.failure(ex)
            } else {
                // Leave as "sending" — WebSocket reconciliation will clean up
                Result.success(Unit)
            }
        } catch (e: Exception) {
            messageDao.updateSendStatus(pendingRowID, "failed", e.message ?: "Network error")
            Result.failure(e)
        }
    }

    suspend fun sendTapback(chatRowID: Long, type: String, messageGUID: String? = null, partIndex: Int = 0, emoji: String? = null): Result<Unit> {
        val pendingRowID = -System.nanoTime()
        val localGuid = "local-${UUID.randomUUID()}"
        val now = Instant.now().toString()
        val targetGuid = messageGUID ?: return Result.failure(IllegalArgumentException("messageGUID required"))

        // Save and optimistically remove any existing confirmed reaction from me on this part
        val previousReaction = reactionDao.findMyConfirmedReaction(targetGuid, partIndex)
        if (previousReaction != null) {
            reactionDao.deleteByRowID(previousReaction.rowID)
        }

        // Optimistic insert
        val pending = ReactionEntity(
            rowID = pendingRowID,
            guid = localGuid,
            chatRowID = chatRowID,
            targetMessageGUID = targetGuid,
            partIndex = partIndex,
            reactionType = type,
            emoji = emoji,
            isRemoval = false,
            senderID = null,
            isFromMe = true,
            date = now,
            sendStatus = "sending"
        )
        reactionDao.upsert(pending)

        return try {
            val response = api.sendTapback(chatRowID, TapbackRequest(type = type, messageGUID = messageGUID, partIndex = partIndex, emoji = emoji))
            if (!response.isSuccessful) {
                val body = response.errorBody()?.string() ?: ""
                val ex = BridgeApiException(response.code(), body)
                // Revert: restore the old reaction so the user sees both
                // the current (confirmed) reaction and the failed new one
                if (previousReaction != null) reactionDao.upsert(previousReaction)
                reactionDao.updateSendStatus(pendingRowID, "failed", "HTTP ${response.code()}: ${ex.displayMessage}")
                Result.failure(ex)
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            if (previousReaction != null) reactionDao.upsert(previousReaction)
            reactionDao.updateSendStatus(pendingRowID, "failed", e.message ?: "Network error")
            Result.failure(e)
        }
    }

    suspend fun retryMessage(pendingRowID: Long) {
        val entity = messageDao.getByRowID(pendingRowID) ?: return
        val text = entity.text ?: return
        messageDao.updateSendStatus(pendingRowID, "sending", null)

        try {
            val response = api.sendMessage(entity.chatRowID, SendMessageRequest(text = text))
            if (!response.isSuccessful) {
                val body = response.errorBody()?.string() ?: ""
                val ex = BridgeApiException(response.code(), body)
                messageDao.updateSendStatus(pendingRowID, "failed", "HTTP ${response.code()}: ${ex.displayMessage}")
            }
        } catch (e: Exception) {
            messageDao.updateSendStatus(pendingRowID, "failed", e.message ?: "Network error")
        }
    }

    suspend fun deletePendingMessage(pendingRowID: Long) {
        messageDao.deleteByRowID(pendingRowID)
    }

    suspend fun removeTapback(chatRowID: Long, type: String, messageGUID: String, partIndex: Int = 0, emoji: String? = null): Result<Unit> {
        // Save the existing reaction before removing so we can revert on failure
        val previousReaction = reactionDao.findMyConfirmedReaction(messageGUID, partIndex)
        reactionDao.removeReaction(messageGUID, partIndex, type, null)

        return try {
            val response = api.removeTapback(chatRowID, TapbackRequest(type = type, messageGUID = messageGUID, partIndex = partIndex, emoji = emoji))
            if (!response.isSuccessful) {
                val body = response.errorBody()?.string() ?: ""
                val ex = BridgeApiException(response.code(), body)
                // Revert: restore the reaction since the removal failed
                if (previousReaction != null) reactionDao.upsert(previousReaction)
                Result.failure(ex)
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            if (previousReaction != null) reactionDao.upsert(previousReaction)
            Result.failure(e)
        }
    }

    suspend fun retryTapback(pendingRowID: Long) {
        val entity = reactionDao.getByRowID(pendingRowID) ?: return

        // Re-remove any reverted confirmed reaction from this part before retrying
        val previousReaction = reactionDao.findMyConfirmedReaction(entity.targetMessageGUID, entity.partIndex)
        if (previousReaction != null) {
            reactionDao.deleteByRowID(previousReaction.rowID)
        }

        reactionDao.updateSendStatus(pendingRowID, "sending", null)

        val request = TapbackRequest(type = entity.reactionType, messageGUID = entity.targetMessageGUID, partIndex = entity.partIndex, emoji = entity.emoji)
        try {
            val response = if (entity.isRemoval) {
                api.removeTapback(entity.chatRowID, request)
            } else {
                api.sendTapback(entity.chatRowID, request)
            }
            if (!response.isSuccessful) {
                val body = response.errorBody()?.string() ?: ""
                val ex = BridgeApiException(response.code(), body)
                if (previousReaction != null) reactionDao.upsert(previousReaction)
                reactionDao.updateSendStatus(pendingRowID, "failed", "HTTP ${response.code()}: ${ex.displayMessage}")
            }
        } catch (e: Exception) {
            if (previousReaction != null) reactionDao.upsert(previousReaction)
            reactionDao.updateSendStatus(pendingRowID, "failed", e.message ?: "Network error")
        }
    }

    suspend fun deletePendingReaction(pendingRowID: Long) {
        reactionDao.deleteByRowID(pendingRowID)
    }

    suspend fun syncChat(chatRowID: Long): Result<Pair<Int, Int>> {
        return try {
            val chat = chatDao.getById(chatRowID) ?: return Result.success(0 to 0)
            var totalMessages = 0
            var totalReactions = 0
            var msgCursor = chat.lastMessageSyncRowID
            var rxnCursor = chat.lastReactionSyncRowID
            var readCursor = chat.lastReadReceiptSyncTimestamp
            var deliveryCursor = chat.lastDeliveryReceiptSyncTimestamp

            do {
                val response = api.syncChat(
                    chatRowID,
                    msgAfter = msgCursor,
                    rxnAfter = rxnCursor,
                    readAfter = appleTimestamp(readCursor),
                    deliveryAfter = appleTimestamp(deliveryCursor),
                    limit = 500
                )

                if (response.messages.isNotEmpty()) {
                    val entities = response.messages.map { dto ->
                        MessageEntity(
                            rowID = dto.rowID,
                            guid = dto.guid,
                            chatRowID = dto.chatRowID,
                            text = dto.text,
                            senderID = dto.senderID,
                            date = dto.date,
                            dateRead = dto.dateRead,
                            dateDelivered = dto.dateDelivered,
                            isRead = dto.isRead,
                            isFromMe = dto.isFromMe,
                            service = dto.service,
                            hasAttachments = dto.hasAttachments,
                            richText = dto.richText?.toJson(),
                            balloonBundleID = dto.balloonBundleID,
                            threadOriginatorGuid = dto.threadOriginatorGuid,
                            linkPreviewTitle = dto.linkPreviewTitle,
                            linkPreviewSubtitle = dto.linkPreviewSubtitle,
                            linkPreviewURL = dto.linkPreviewURL
                        )
                    }
                    // Reconcile pending optimistic messages
                    for (msg in entities) {
                        if (msg.isFromMe) {
                            messageDao.findOldestPendingInChat(msg.chatRowID)?.let { pending ->
                                messageDao.deleteByRowID(pending.rowID)
                            }
                        }
                    }
                    messageDao.upsertAll(entities)
                    val latest = entities.maxBy { it.date }
                    ensureChatExistsAndUpdateLastMessage(
                        chatRowID = chatRowID,
                        senderID = latest.senderID,
                        date = latest.date,
                        text = cleanMessageText(latest.text),
                        isFromMe = latest.isFromMe
                    )
                    totalMessages += entities.size
                    msgCursor = response.messages.maxOf { it.rowID }
                    chatDao.updateMessageSyncCursor(chatRowID, msgCursor)
                }

                if (response.reactions.isNotEmpty()) {
                    for (dto in response.reactions) {
                        val reaction = ReactionEntity(
                            rowID = dto.rowID,
                            guid = dto.guid,
                            chatRowID = dto.chatRowID,
                            targetMessageGUID = dto.targetMessageGUID,
                            partIndex = dto.partIndex,
                            reactionType = dto.reactionType,
                            emoji = dto.emoji,
                            isRemoval = dto.isRemoval,
                            senderID = dto.senderID,
                            isFromMe = dto.isFromMe,
                            date = dto.date
                        )
                        if (reaction.isRemoval) {
                            reactionDao.removeReaction(reaction.targetMessageGUID, reaction.partIndex, reaction.reactionType, reaction.senderID)
                        } else {
                            if (reaction.isFromMe) {
                                reactionDao.findPendingReaction(reaction.targetMessageGUID, reaction.partIndex, reaction.reactionType)?.let { pending ->
                                    reactionDao.deleteByRowID(pending.rowID)
                                }
                            }
                            reactionDao.removeExistingReactionFromSender(reaction.targetMessageGUID, reaction.partIndex, reaction.senderID, reaction.isFromMe)
                            reactionDao.upsert(reaction)
                        }
                    }
                    totalReactions += response.reactions.size
                    rxnCursor = response.reactions.maxOf { it.rowID }
                    chatDao.updateReactionSyncCursor(chatRowID, rxnCursor)
                }

                if (response.readReceipts.isNotEmpty()) {
                    response.readReceipts.forEach { dto ->
                        messageDao.updateDateRead(dto.rowID, dto.dateRead)
                    }
                    readCursor = response.readReceipts.maxOf { it.dateRead }
                    chatDao.updateReadReceiptSyncCursor(chatRowID, readCursor)
                }

                if (response.deliveryReceipts.isNotEmpty()) {
                    response.deliveryReceipts.forEach { dto ->
                        messageDao.updateDateDelivered(dto.rowID, dto.dateDelivered)
                    }
                    deliveryCursor = response.deliveryReceipts.maxOf { it.dateDelivered }
                    chatDao.updateDeliveryReceiptSyncCursor(chatRowID, deliveryCursor)
                }
            } while (
                response.hasMoreMessages ||
                    response.hasMoreReactions ||
                    response.hasMoreReadReceipts ||
                    response.hasMoreDeliveryReceipts
            )

            chat.maxReadReceiptDate?.let { chatDao.updateReadReceiptSyncCursor(chatRowID, it) }
            chat.maxDeliveryReceiptDate?.let { chatDao.updateDeliveryReceiptSyncCursor(chatRowID, it) }
            chat.lastReadMessageDate?.let { messageDao.markIncomingReadThrough(chatRowID, it) }

            Result.success(totalMessages to totalReactions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun handleNewMessage(
        messageRowID: Long,
        chatRowID: Long,
        guid: String,
        text: String?,
        senderID: String?,
        isFromMe: Boolean,
        service: String?,
        date: String,
        isRead: Boolean,
        hasAttachments: Boolean,
        richTextJson: String? = null,
        balloonBundleID: String? = null,
        threadOriginatorGuid: String? = null,
        linkPreviewTitle: String? = null,
        linkPreviewSubtitle: String? = null,
        linkPreviewURL: String? = null
    ) {
        // Reconcile: if this is our own message, remove the oldest pending optimistic message
        if (isFromMe) {
            messageDao.findOldestPendingInChat(chatRowID)?.let { pending ->
                messageDao.deleteByRowID(pending.rowID)
            }
        }

        val entity = MessageEntity(
            rowID = messageRowID,
            guid = guid,
            chatRowID = chatRowID,
            text = text,
            senderID = senderID,
            date = date,
            dateRead = null,
            dateDelivered = null,
            isRead = isRead,
            isFromMe = isFromMe,
            service = service,
            hasAttachments = hasAttachments,
            richText = richTextJson,
            balloonBundleID = balloonBundleID,
            threadOriginatorGuid = threadOriginatorGuid,
            linkPreviewTitle = linkPreviewTitle,
            linkPreviewSubtitle = linkPreviewSubtitle,
            linkPreviewURL = linkPreviewURL
        )
        messageDao.upsert(entity)
        ensureChatExistsAndUpdateLastMessage(
            chatRowID = chatRowID,
            senderID = senderID,
            date = date,
            text = cleanMessageText(text),
            isFromMe = isFromMe
        )
    }

    /**
     * Ensure a chat row exists in the local DB before updating its last message.
     * If the chat is not yet in the DB (e.g. it was beyond the initial load limit),
     * create a stub entry so it appears in the chat list.
     */
    private suspend fun ensureChatExistsAndUpdateLastMessage(
        chatRowID: Long,
        senderID: String?,
        date: String,
        text: String?,
        isFromMe: Boolean
    ) {
        if (chatDao.exists(chatRowID) == 0) {
            // Chat not yet in local DB — create a stub entry.
            // Use senderID (phone/email) as chatIdentifier for 1:1 chats.
            val identifier = senderID ?: "chat$chatRowID"
            chatDao.upsert(
                ChatEntity(
                    rowID = chatRowID,
                    guid = "unknown-$chatRowID",
                    chatIdentifier = identifier,
                    displayName = null,
                    serviceName = null,
                    isGroup = senderID == null,
                    lastMessageDate = date,
                    lastMessageText = text,
                    lastMessageIsFromMe = isFromMe
                )
            )
        } else {
            chatDao.updateLastMessage(chatRowID, date, text, isFromMe)
        }
    }

    suspend fun handleReaction(
        rowID: Long,
        chatRowID: Long,
        guid: String,
        targetMessageGUID: String,
        partIndex: Int,
        reactionType: String,
        emoji: String?,
        isRemoval: Boolean,
        senderID: String?,
        isFromMe: Boolean,
        date: String
    ) {
        if (isRemoval) {
            reactionDao.removeReaction(targetMessageGUID, partIndex, reactionType, senderID)
        } else {
            // Reconcile: if this is our own reaction, remove the pending optimistic one
            if (isFromMe) {
                reactionDao.findPendingReaction(targetMessageGUID, partIndex, reactionType)?.let { pending ->
                    reactionDao.deleteByRowID(pending.rowID)
                }
            }

            // Enforce one-reaction-per-user-per-part: remove any existing reaction
            // from this sender on this message part before inserting the new one
            reactionDao.removeExistingReactionFromSender(targetMessageGUID, partIndex, senderID, isFromMe)

            val entity = ReactionEntity(
                rowID = rowID,
                guid = guid,
                chatRowID = chatRowID,
                targetMessageGUID = targetMessageGUID,
                partIndex = partIndex,
                reactionType = reactionType,
                emoji = emoji,
                isRemoval = false,
                senderID = senderID,
                isFromMe = isFromMe,
                date = date
            )
            reactionDao.upsert(entity)
        }
    }

    suspend fun handleReadReceipt(rowID: Long, dateRead: String) {
        messageDao.updateDateRead(rowID, dateRead)
    }

    suspend fun handleDeliveryReceipt(rowID: Long, dateDelivered: String) {
        messageDao.updateDateDelivered(rowID, dateDelivered)
    }

    /**
     * Text values like "GUID.pluginPayloadAttachment" are native platform internal
     * identifiers for plugin messages (link previews, Apple Pay, etc.) and
     * should not be shown to the user.
     */
    private fun cleanMessageText(raw: String?): String? =
        raw?.takeUnless { it.endsWith(".pluginPayloadAttachment") }

    private fun MessageEntity.toDomain(reactions: List<Reaction> = emptyList()) = Message(
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
        richText = richText.toRichTextDomain(),
        threadOriginatorGuid = threadOriginatorGuid,
        sendStatus = sendStatus?.let { SendStatus.valueOf(it.uppercase()) },
        sendError = sendError,
        linkPreviewTitle = linkPreviewTitle,
        linkPreviewSubtitle = linkPreviewSubtitle,
        linkPreviewURL = linkPreviewURL
    )

    private fun ReactionEntity.toDomain() = Reaction(
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

    private fun AttachmentEntity.toDomain() = AttachmentInfo(
        messageRowID = messageRowID,
        rowID = rowID,
        guid = guid,
        mimeType = mimeType,
        transferName = transferName,
        totalBytes = totalBytes,
        isSticker = isSticker,
        downloadState = downloadState,
        downloadedBytes = downloadedBytes,
        localFilePath = localFilePath,
        downloadAttempts = downloadAttempts
    )

    private fun appleTimestamp(isoValue: String?): Long =
        isoValue?.let {
            try {
                val unixMillis = Instant.parse(it).toEpochMilli()
                (unixMillis - 978307200000L) * 1_000_000
            } catch (_: Exception) {
                0L
            }
        } ?: 0L
}

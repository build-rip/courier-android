package rip.build.courier.data.remote.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NormalizedAttachmentDto(
    val attachmentID: String,
    val transferName: String? = null,
    val mimeType: String? = null,
    val totalBytes: Long = 0,
    val isSticker: Boolean = false
)

@JsonClass(generateAdapter = true)
data class MessageCreatedEventPayloadDto(
    val messageID: String,
    val senderID: String? = null,
    val isFromMe: Boolean,
    val service: String? = null,
    val sentAt: String,
    val text: String? = null,
    val richText: RichTextDto? = null,
    val attachments: List<NormalizedAttachmentDto> = emptyList(),
    val replyToMessageID: String? = null
)

@JsonClass(generateAdapter = true)
data class MessageEditedEventPayloadDto(
    val messageID: String,
    val editedAt: String,
    val text: String? = null,
    val richText: RichTextDto? = null
)

@JsonClass(generateAdapter = true)
data class MessageDeletedEventPayloadDto(
    val messageID: String,
    val deletedAt: String
)

@JsonClass(generateAdapter = true)
data class ConversationReactionEventPayloadDto(
    val messageID: String,
    val actorID: String? = null,
    val isFromMe: Boolean,
    val sentAt: String,
    val partIndex: Int = 0,
    val reactionType: String,
    val emoji: String? = null
)

@JsonClass(generateAdapter = true)
data class MessageReadUpdatedEventPayloadDto(
    val messageID: String,
    val readAt: String
)

@JsonClass(generateAdapter = true)
data class MessageDeliveredUpdatedEventPayloadDto(
    val messageID: String,
    val deliveredAt: String
)

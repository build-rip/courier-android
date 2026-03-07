package rip.build.courier.data.remote.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SyncMessageDto(
    val chatRowID: Long,
    val rowID: Long,
    val guid: String,
    val text: String?,
    val senderID: String?,
    val date: String,
    val dateRead: String?,
    val dateDelivered: String?,
    val isRead: Boolean,
    val isFromMe: Boolean,
    val service: String?,
    val hasAttachments: Boolean,
    val richText: RichTextDto? = null,
    val balloonBundleID: String? = null,
    val threadOriginatorGuid: String? = null,
    val linkPreviewTitle: String? = null,
    val linkPreviewSubtitle: String? = null,
    val linkPreviewURL: String? = null
)

@JsonClass(generateAdapter = true)
data class SyncReactionDto(
    val chatRowID: Long,
    val rowID: Long,
    val guid: String,
    val targetMessageGUID: String,
    val partIndex: Int = 0,
    val reactionType: String,
    val emoji: String? = null,
    val isRemoval: Boolean,
    val senderID: String?,
    val isFromMe: Boolean,
    val date: String
)

@JsonClass(generateAdapter = true)
data class SyncReadReceiptDto(
    val rowID: Long,
    val guid: String,
    val dateRead: String
)

@JsonClass(generateAdapter = true)
data class SyncDeliveryReceiptDto(
    val rowID: Long,
    val guid: String,
    val dateDelivered: String
)

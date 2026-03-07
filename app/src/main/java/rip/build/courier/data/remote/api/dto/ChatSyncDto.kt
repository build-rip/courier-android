package rip.build.courier.data.remote.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ChatSyncResponse(
    val messages: List<SyncMessageDto>,
    val reactions: List<SyncReactionDto>,
    val readReceipts: List<SyncReadReceiptDto> = emptyList(),
    val deliveryReceipts: List<SyncDeliveryReceiptDto> = emptyList(),
    val hasMoreMessages: Boolean,
    val hasMoreReactions: Boolean,
    val hasMoreReadReceipts: Boolean = false,
    val hasMoreDeliveryReceipts: Boolean = false
)

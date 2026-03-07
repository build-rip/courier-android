package rip.build.courier.data.remote.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ChatDto(
    val rowID: Long,
    val guid: String,
    val chatIdentifier: String,
    val displayName: String?,
    val serviceName: String?,
    val isGroup: Boolean,
    val lastMessageDate: String?,
    val lastMessageText: String?,
    val lastMessageIsFromMe: Boolean?,
    val maxMessageRowID: Long = 0,
    val maxReactionRowID: Long = 0,
    val hasUnreads: Boolean = false,
    val unreadCount: Int = 0,
    val lastReadMessageDate: String? = null,
    val maxReadReceiptDate: String? = null,
    val maxDeliveryReceiptDate: String? = null
)

@JsonClass(generateAdapter = true)
data class ChatListResponse(
    val chats: List<ChatDto>
)

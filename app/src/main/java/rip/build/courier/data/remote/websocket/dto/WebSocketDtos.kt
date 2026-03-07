package rip.build.courier.data.remote.websocket.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WebSocketEnvelope(
    val type: String,
    val payload: Map<String, Any?>,
    val timestamp: String
)

data class NewMessagePayload(
    val messageRowID: Long,
    val chatRowID: Long,
    val guid: String,
    val text: String?,
    val senderID: String?,
    val isFromMe: Boolean,
    val service: String?,
    val date: String,
    val hasAttachments: Boolean
)

data class ReactionPayload(
    val messageRowID: Long,
    val chatRowID: Long,
    val guid: String,
    val targetMessageGUID: String,
    val reactionType: String,
    val emoji: String? = null,
    val isRemoval: Boolean,
    val senderID: String?,
    val isFromMe: Boolean,
    val date: String
)

data class ReadReceiptPayload(
    val messageRowID: Long,
    val chatRowID: Long,
    val guid: String,
    val dateRead: String
)

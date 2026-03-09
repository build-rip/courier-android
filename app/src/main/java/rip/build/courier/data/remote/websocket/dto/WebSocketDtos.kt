package rip.build.courier.data.remote.websocket.dto

import com.squareup.moshi.JsonClass

data class WebSocketEnvelope(
    val type: String,
    val payload: Map<String, Any?>,
    val timestamp: String
)

@JsonClass(generateAdapter = true)
data class ConversationCursorUpdatedPayload(
    val conversationID: String,
    val conversationVersion: Int,
    val latestEventSequence: Long
)

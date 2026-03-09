package rip.build.courier.data.remote.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ConversationSummaryDto(
    val conversationId: String,
    val chatGuid: String,
    val chatIdentifier: String? = null,
    val displayName: String? = null,
    val serviceName: String? = null,
    val conversationVersion: Int,
    val latestEventSequence: Long,
    val indexedAt: String? = null,
    val indexStatus: String = "ready"
)

@JsonClass(generateAdapter = true)
data class ConversationListResponse(
    val conversations: List<ConversationSummaryDto>
)

data class ConversationEventDto(
    val conversationID: String,
    val conversationVersion: Int,
    val eventSequence: Long,
    val eventType: String,
    val payload: Map<String, Any?>
)

data class ConversationEventsResponseDto(
    val conversationId: String,
    val conversationVersion: Int,
    val latestEventSequence: Long,
    val from: Long,
    val nextFrom: Long,
    val hasMore: Boolean,
    val events: List<ConversationEventDto>
)

@JsonClass(generateAdapter = true)
data class ConversationResyncRequiredDto(
    val resyncRequired: Boolean,
    val conversationId: String,
    val conversationVersion: Int,
    val latestEventSequence: Long
)

@JsonClass(generateAdapter = true)
data class MutationResponseDto(
    val result: String,
    val conversationVersion: Int? = null,
    val latestEventSequence: Long? = null,
    val failureReason: String? = null
)

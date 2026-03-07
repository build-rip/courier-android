package rip.build.courier.data.remote.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ReactionDto(
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

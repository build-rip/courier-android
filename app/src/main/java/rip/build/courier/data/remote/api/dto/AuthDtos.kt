package rip.build.courier.data.remote.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PairRequest(
    val code: String,
    val deviceName: String?
)

@JsonClass(generateAdapter = true)
data class PairResponse(
    val refreshToken: String,
    val deviceId: String
)

@JsonClass(generateAdapter = true)
data class TokenRequest(
    val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class TokenResponse(
    val accessToken: String,
    val expiresIn: Int
)

@JsonClass(generateAdapter = true)
data class PairingCodeResponse(
    val code: String,
    val expiresInSeconds: Int,
    val pairingUrl: String
)

@JsonClass(generateAdapter = true)
data class SendMessageRequest(
    val text: String,
    val recipient: String? = null,
    val conversationVersion: Int? = null,
    val fromEventSequence: Long? = null
)

@JsonClass(generateAdapter = true)
data class TapbackRequest(
    val type: String,
    val messageGUID: String? = null,
    val partIndex: Int? = null,
    val emoji: String? = null,
    val conversationVersion: Int? = null,
    val fromEventSequence: Long? = null
)

@JsonClass(generateAdapter = true)
data class MarkReadRequest(
    val conversationVersion: Int? = null,
    val fromEventSequence: Long? = null
)

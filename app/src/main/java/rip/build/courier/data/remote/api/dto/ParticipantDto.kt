package rip.build.courier.data.remote.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ParticipantDto(
    val rowID: Long,
    val id: String,
    val service: String?
)

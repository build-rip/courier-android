package rip.build.courier.data.remote.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AttachmentDto(
    val rowID: Long,
    val guid: String,
    val mimeType: String?,
    val transferName: String?,
    val totalBytes: Long,
    val isSticker: Boolean
)

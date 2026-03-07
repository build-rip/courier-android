package rip.build.courier.data.remote.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MessageDto(
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

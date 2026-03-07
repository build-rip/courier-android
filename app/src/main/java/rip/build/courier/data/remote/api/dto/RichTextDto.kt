package rip.build.courier.data.remote.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RichTextDto(
    val parts: List<RichTextPartDto>
)

@JsonClass(generateAdapter = true)
data class RichTextPartDto(
    val text: String,
    val attributes: RichTextAttributesDto
)

@JsonClass(generateAdapter = true)
data class RichTextAttributesDto(
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val strikethrough: Boolean? = null,
    val underline: Boolean? = null,
    val link: String? = null,
    val mention: String? = null,
    val attachmentIndex: Int? = null
)

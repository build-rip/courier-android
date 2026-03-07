package rip.build.courier.domain.model

data class RichText(
    val parts: List<RichTextPart>
)

data class RichTextPart(
    val text: String,
    val attributes: RichTextAttributes
)

data class RichTextAttributes(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strikethrough: Boolean = false,
    val underline: Boolean = false,
    val link: String? = null,
    val mention: String? = null,
    val attachmentIndex: Int? = null
)

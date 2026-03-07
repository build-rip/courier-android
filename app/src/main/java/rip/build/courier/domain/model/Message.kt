package rip.build.courier.domain.model

data class Message(
    val rowID: Long,
    val guid: String,
    val chatRowID: Long,
    val text: String?,
    val senderID: String?,
    val date: String,
    val dateRead: String?,
    val dateDelivered: String?,
    val isRead: Boolean,
    val isFromMe: Boolean,
    val service: String?,
    val hasAttachments: Boolean,
    val balloonBundleID: String? = null,
    val reactions: List<Reaction> = emptyList(),
    val attachments: List<AttachmentInfo> = emptyList(),
    val richText: RichText? = null,
    val threadOriginatorGuid: String? = null,
    val replyContext: ReplyContext? = null,
    val replyCount: Int = 0,
    val sendStatus: SendStatus? = null,
    val sendError: String? = null,
    val linkPreviewTitle: String? = null,
    val linkPreviewSubtitle: String? = null,
    val linkPreviewURL: String? = null
)

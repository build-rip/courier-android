package rip.build.courier.domain.model

data class ReplyContext(
    val parentText: String?,
    val parentSenderID: String?,
    val parentIsFromMe: Boolean,
    val parentHasAttachments: Boolean
)

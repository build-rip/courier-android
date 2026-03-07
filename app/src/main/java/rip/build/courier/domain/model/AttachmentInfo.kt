package rip.build.courier.domain.model

data class AttachmentInfo(
    val messageRowID: Long,
    val rowID: Long,
    val guid: String,
    val mimeType: String?,
    val transferName: String?,
    val totalBytes: Long,
    val isSticker: Boolean,
    val downloadState: String = "pending",
    val downloadedBytes: Long = 0,
    val localFilePath: String? = null,
    val downloadAttempts: Int = 0
) {
    val key: AttachmentKey
        get() = AttachmentKey(messageRowID = messageRowID, attachmentRowID = rowID)

    val isImage: Boolean
        get() = mimeType?.startsWith("image/") == true

    val isVideo: Boolean
        get() = mimeType?.startsWith("video/") == true

    /** Plugin payload attachments with no detected mimeType contain opaque binary data that can't be displayed. */
    val isUndisplayablePluginPayload: Boolean
        get() = transferName?.endsWith(".pluginPayloadAttachment") == true && mimeType == null

    val isDownloaded: Boolean
        get() = downloadState == "completed" && localFilePath != null

    val isTooLarge: Boolean
        get() = downloadState == "too_large"

    val isDownloading: Boolean
        get() = downloadState == "downloading"

    val downloadFailed: Boolean
        get() = downloadState == "failed"

    val downloadExhausted: Boolean
        get() = downloadState == "failed" && downloadAttempts >= 3

    val downloadProgress: Float
        get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes.toFloat() else 0f
}

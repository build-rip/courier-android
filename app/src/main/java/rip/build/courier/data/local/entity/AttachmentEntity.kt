package rip.build.courier.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "attachments",
    primaryKeys = ["messageRowID", "rowID"],
    indices = [Index("rowID"), Index("messageRowID")]
)
data class AttachmentEntity(
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
)

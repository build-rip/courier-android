package rip.build.courier.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val rowID: Long,
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
    val richText: String? = null,
    val balloonBundleID: String? = null,
    val threadOriginatorGuid: String? = null,
    val sendStatus: String? = null,
    val sendError: String? = null,
    val linkPreviewTitle: String? = null,
    val linkPreviewSubtitle: String? = null,
    val linkPreviewURL: String? = null
)

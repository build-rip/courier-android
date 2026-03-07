package rip.build.courier.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val rowID: Long,
    val guid: String,
    val chatIdentifier: String,
    val displayName: String?,
    val serviceName: String?,
    val isGroup: Boolean,
    val lastMessageDate: String?,
    val lastMessageText: String?,
    val lastMessageIsFromMe: Boolean?,
    val isPinned: Boolean = false,
    val lastMessageSyncRowID: Long = 0,
    val lastReactionSyncRowID: Long = 0,
    val lastReadReceiptSyncTimestamp: String? = null,
    val lastDeliveryReceiptSyncTimestamp: String? = null,
    val hasUnreads: Boolean = false,
    val unreadCount: Int = 0,
    val lastReadMessageDate: String? = null,
    val maxReadReceiptDate: String? = null,
    val maxDeliveryReceiptDate: String? = null
)

package rip.build.courier.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val rowID: Long,
    val conversationId: String,
    val guid: String,
    val chatIdentifier: String,
    val displayName: String?,
    val serviceName: String?,
    val isGroup: Boolean,
    val lastMessageDate: String?,
    val lastMessageText: String?,
    val lastMessageIsFromMe: Boolean?,
    val isPinned: Boolean = false,
    val conversationVersion: Int = 0,
    val latestEventSequence: Long = 0,
    val localEventSequence: Long = 0,
    val pendingFullResync: Boolean = false,
    val readAckPending: Boolean = false,
    val hasUnreads: Boolean = false,
    val unreadCount: Int = 0,
    val lastReadMessageDate: String? = null
)

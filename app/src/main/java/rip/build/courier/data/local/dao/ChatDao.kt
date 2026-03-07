package rip.build.courier.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import rip.build.courier.data.local.entity.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY lastMessageDate DESC")
    fun observeAll(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE rowID = :rowID")
    fun observeById(rowID: Long): Flow<ChatEntity?>

    @Query("SELECT * FROM chats WHERE rowID = :rowID")
    suspend fun getById(rowID: Long): ChatEntity?

    @Upsert
    suspend fun upsert(chat: ChatEntity)

    @Upsert
    suspend fun upsertAll(chats: List<ChatEntity>)

    @Query("UPDATE chats SET lastMessageDate = :date, lastMessageText = :text, lastMessageIsFromMe = :isFromMe WHERE rowID = :chatRowID")
    suspend fun updateLastMessage(chatRowID: Long, date: String, text: String?, isFromMe: Boolean)

    @Query("SELECT COUNT(*) FROM chats WHERE rowID = :rowID")
    suspend fun exists(rowID: Long): Int

    @Query("UPDATE chats SET isPinned = :isPinned WHERE rowID = :rowID")
    suspend fun setPinned(rowID: Long, isPinned: Boolean)

    @Query("SELECT * FROM chats WHERE isPinned = 1 ORDER BY lastMessageDate DESC")
    fun observePinned(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE isPinned = 0 ORDER BY lastMessageDate DESC")
    fun observeUnpinned(): Flow<List<ChatEntity>>

    @Query("SELECT rowID FROM chats WHERE isPinned = 1")
    suspend fun getPinnedRowIDs(): List<Long>

    @Query("SELECT * FROM chats")
    suspend fun getAll(): List<ChatEntity>

    @Query("UPDATE chats SET lastMessageSyncRowID = :rowID WHERE rowID = :chatRowID")
    suspend fun updateMessageSyncCursor(chatRowID: Long, rowID: Long)

    @Query("UPDATE chats SET lastReactionSyncRowID = :rowID WHERE rowID = :chatRowID")
    suspend fun updateReactionSyncCursor(chatRowID: Long, rowID: Long)

    @Query("UPDATE chats SET lastReadReceiptSyncTimestamp = :timestamp WHERE rowID = :chatRowID")
    suspend fun updateReadReceiptSyncCursor(chatRowID: Long, timestamp: String?)

    @Query("UPDATE chats SET lastDeliveryReceiptSyncTimestamp = :timestamp WHERE rowID = :chatRowID")
    suspend fun updateDeliveryReceiptSyncCursor(chatRowID: Long, timestamp: String?)

    @Query("UPDATE chats SET lastMessageSyncRowID = 0, lastReactionSyncRowID = 0, lastReadReceiptSyncTimestamp = NULL, lastDeliveryReceiptSyncTimestamp = NULL")
    suspend fun resetAllSyncCursors()

    @Query("UPDATE chats SET hasUnreads = :hasUnreads WHERE rowID = :rowID")
    suspend fun updateHasUnreads(rowID: Long, hasUnreads: Boolean)

    @Query("UPDATE chats SET hasUnreads = :hasUnreads, unreadCount = :unreadCount, lastReadMessageDate = :lastReadMessageDate WHERE rowID = :rowID")
    suspend fun updateUnreadState(rowID: Long, hasUnreads: Boolean, unreadCount: Int, lastReadMessageDate: String?)
}

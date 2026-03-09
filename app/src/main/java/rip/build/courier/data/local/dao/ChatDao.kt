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

    @Query("SELECT * FROM chats WHERE conversationId = :conversationId")
    suspend fun getByConversationId(conversationId: String): ChatEntity?

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

    @Query("UPDATE chats SET latestEventSequence = :latestEventSequence, localEventSequence = :localEventSequence, pendingFullResync = :pendingFullResync WHERE rowID = :chatRowID")
    suspend fun updateSyncState(chatRowID: Long, latestEventSequence: Long, localEventSequence: Long, pendingFullResync: Boolean)

    @Query("UPDATE chats SET latestEventSequence = :latestEventSequence WHERE rowID = :chatRowID")
    suspend fun updateLatestEventSequence(chatRowID: Long, latestEventSequence: Long)

    @Query("UPDATE chats SET pendingFullResync = :pendingFullResync WHERE rowID = :chatRowID")
    suspend fun updatePendingFullResync(chatRowID: Long, pendingFullResync: Boolean)

    @Query("UPDATE chats SET readAckPending = :readAckPending WHERE rowID = :chatRowID")
    suspend fun updateReadAckPending(chatRowID: Long, readAckPending: Boolean)

    @Query("UPDATE chats SET localEventSequence = 0, latestEventSequence = 0, pendingFullResync = 1")
    suspend fun resetAllSyncState()

    @Query("UPDATE chats SET hasUnreads = :hasUnreads WHERE rowID = :rowID")
    suspend fun updateHasUnreads(rowID: Long, hasUnreads: Boolean)

    @Query("UPDATE chats SET hasUnreads = :hasUnreads, unreadCount = :unreadCount, lastReadMessageDate = :lastReadMessageDate, readAckPending = :readAckPending WHERE rowID = :rowID")
    suspend fun updateUnreadState(rowID: Long, hasUnreads: Boolean, unreadCount: Int, lastReadMessageDate: String?, readAckPending: Boolean)

    @Query("UPDATE chats SET lastMessageDate = NULL, lastMessageText = NULL, lastMessageIsFromMe = NULL, hasUnreads = 0, unreadCount = 0, lastReadMessageDate = NULL WHERE rowID = :chatRowID")
    suspend fun clearDerivedState(chatRowID: Long)
}

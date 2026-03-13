package rip.build.courier.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import rip.build.courier.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatRowID = :chatRowID ORDER BY date ASC")
    fun observeByChatId(chatRowID: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE guid = :guid")
    suspend fun getByGuid(guid: String): MessageEntity?

    @Query("SELECT MAX(rowID) FROM messages WHERE chatRowID = :chatRowID")
    suspend fun getMaxRowID(chatRowID: Long): Long?

    @Query("SELECT * FROM messages WHERE chatRowID = :chatRowID ORDER BY date ASC")
    suspend fun getByChatId(chatRowID: Long): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE sendStatus IS NULL")
    suspend fun countConfirmedMessages(): Int

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Upsert
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("UPDATE messages SET dateRead = :dateRead WHERE rowID = :rowID")
    suspend fun updateDateRead(rowID: Long, dateRead: String)

    @Query("UPDATE messages SET dateDelivered = :dateDelivered WHERE rowID = :rowID")
    suspend fun updateDateDelivered(rowID: Long, dateDelivered: String)

    @Query("UPDATE messages SET isRead = 1 WHERE chatRowID = :chatRowID AND isFromMe = 0 AND date <= :throughDate")
    suspend fun markIncomingReadThrough(chatRowID: Long, throughDate: String)

    @Query("UPDATE messages SET isRead = 1 WHERE chatRowID = :chatRowID AND isFromMe = 0")
    suspend fun markAllIncomingRead(chatRowID: Long)

    @Query("SELECT COUNT(*) FROM messages WHERE chatRowID = :chatRowID AND isFromMe = 0 AND isRead = 0 AND deletedAt IS NULL")
    suspend fun countUnreadIncoming(chatRowID: Long): Int

    @Query("SELECT MAX(date) FROM messages WHERE chatRowID = :chatRowID AND isFromMe = 0 AND isRead = 1 AND deletedAt IS NULL")
    suspend fun getLastReadIncomingDate(chatRowID: Long): String?

    @Query("UPDATE messages SET sendStatus = :status, sendError = :error WHERE rowID = :rowID")
    suspend fun updateSendStatus(rowID: Long, status: String?, error: String?)

    @Query("DELETE FROM messages WHERE rowID = :rowID")
    suspend fun deleteByRowID(rowID: Long)

    @Query("SELECT * FROM messages WHERE rowID = :rowID")
    suspend fun getByRowID(rowID: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE chatRowID = :chatRowID AND isFromMe = 1 AND sendStatus IS NOT NULL ORDER BY date ASC LIMIT 1")
    suspend fun findOldestPendingInChat(chatRowID: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE chatRowID = :chatRowID AND sendStatus IS NOT NULL ORDER BY date ASC")
    suspend fun getPendingByChatId(chatRowID: Long): List<MessageEntity>

    @Query("DELETE FROM messages WHERE chatRowID = :chatRowID AND sendStatus IS NULL")
    suspend fun deleteConfirmedByChatId(chatRowID: Long)

    @Query("DELETE FROM messages WHERE chatRowID = :chatRowID")
    suspend fun deleteByChatId(chatRowID: Long)

    @Query("""
        SELECT m.rowID FROM messages m
        WHERE m.hasAttachments = 1
        AND NOT EXISTS (SELECT 1 FROM attachments a WHERE a.messageRowID = m.rowID)
    """)
    suspend fun getMessageRowIDsMissingAttachments(): List<Long>

    @Query("""
        SELECT m.rowID FROM messages m
        WHERE m.chatRowID = :chatRowID AND m.hasAttachments = 1
        AND m.date >= :afterDate
        AND NOT EXISTS (SELECT 1 FROM attachments a WHERE a.messageRowID = m.rowID)
    """)
    suspend fun getMessageRowIDsMissingAttachments(chatRowID: Long, afterDate: String): List<Long>

}

package rip.build.courier.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import rip.build.courier.data.local.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE messageRowID = :messageRowID")
    fun observeByMessageId(messageRowID: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE messageRowID = :messageRowID")
    suspend fun getByMessageId(messageRowID: Long): List<AttachmentEntity>

    @Upsert
    suspend fun upsertAll(attachments: List<AttachmentEntity>)

    @Query("""
        UPDATE attachments
        SET downloadState = :state, downloadedBytes = :downloadedBytes, localFilePath = :path
        WHERE messageRowID = :messageRowID AND rowID = :rowID
    """)
    suspend fun updateDownloadState(messageRowID: Long, rowID: Long, state: String, downloadedBytes: Long, path: String?)

    @Query("""
        UPDATE attachments
        SET downloadState = 'failed', downloadedBytes = 0, localFilePath = NULL, downloadAttempts = downloadAttempts + 1
        WHERE messageRowID = :messageRowID AND rowID = :rowID
    """)
    suspend fun markDownloadFailed(messageRowID: Long, rowID: Long)

    @Query("UPDATE attachments SET downloadedBytes = :bytes WHERE messageRowID = :messageRowID AND rowID = :rowID")
    suspend fun updateDownloadProgress(messageRowID: Long, rowID: Long, bytes: Long)

    @Query("SELECT * FROM attachments WHERE downloadState = 'downloading'")
    suspend fun getStuckDownloads(): List<AttachmentEntity>

    @Query("SELECT rowID FROM attachments WHERE messageRowID = :messageRowID")
    suspend fun getExistingRowIDs(messageRowID: Long): List<Long>

    @Query("SELECT * FROM attachments WHERE messageRowID = :messageRowID AND rowID = :rowID")
    suspend fun getById(messageRowID: Long, rowID: Long): AttachmentEntity?

    @Query("SELECT * FROM attachments WHERE guid = :guid LIMIT 1")
    suspend fun getByGuid(guid: String): AttachmentEntity?

    @Query("SELECT a.* FROM attachments a INNER JOIN messages m ON a.messageRowID = m.rowID WHERE m.chatRowID = :chatRowID")
    suspend fun getByChatId(chatRowID: Long): List<AttachmentEntity>

    @Query("""
        SELECT a.* FROM attachments a
        INNER JOIN messages m ON a.messageRowID = m.rowID
        WHERE m.chatRowID = :chatRowID
          AND a.downloadState = 'pending'
    """)
    suspend fun getPendingByChatId(chatRowID: Long): List<AttachmentEntity>

    @Query("DELETE FROM attachments WHERE messageRowID IN (SELECT rowID FROM messages WHERE chatRowID = :chatRowID)")
    suspend fun deleteByChatId(chatRowID: Long)

    @Query("""
        SELECT a.* FROM attachments a
        INNER JOIN messages m ON a.messageRowID = m.rowID
        WHERE m.chatRowID = :chatRowID
          AND a.downloadState = 'completed'
          AND a.localFilePath IS NOT NULL
          AND (a.mimeType LIKE 'image/%' OR a.mimeType LIKE 'video/%')
        ORDER BY m.date DESC
    """)
    fun observeMediaByChatId(chatRowID: Long): Flow<List<AttachmentEntity>>

    @Query("UPDATE attachments SET downloadState = 'pending', downloadedBytes = 0, localFilePath = NULL WHERE downloadState = 'failed' AND downloadAttempts < 3")
    suspend fun resetRetriableFailedDownloads()

    @Query("""
        UPDATE attachments
        SET downloadState = 'pending', downloadedBytes = 0, localFilePath = NULL, downloadAttempts = 0
        WHERE messageRowID = :messageRowID AND rowID = :rowID
    """)
    suspend fun resetForManualRetry(messageRowID: Long, rowID: Long)

    @Query("SELECT * FROM attachments WHERE downloadState = 'pending'")
    suspend fun getAllPendingDownloads(): List<AttachmentEntity>

    @Query("SELECT COUNT(*) FROM attachments WHERE downloadState = 'completed'")
    fun observeCompletedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM attachments WHERE downloadState = 'pending'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM attachments WHERE downloadState = 'downloading'")
    fun observeDownloadingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM attachments WHERE downloadState = 'failed'")
    fun observeFailedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM attachments")
    fun observeTotalCount(): Flow<Int>
}

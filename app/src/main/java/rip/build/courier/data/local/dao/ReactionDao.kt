package rip.build.courier.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import rip.build.courier.data.local.entity.ReactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReactionDao {
    @Query("SELECT * FROM reactions WHERE chatRowID = :chatRowID AND isRemoval = 0")
    fun observeByChatId(chatRowID: Long): Flow<List<ReactionEntity>>

    @Query("SELECT * FROM reactions WHERE targetMessageGUID = :targetGuid AND isRemoval = 0")
    fun observeByTargetGuid(targetGuid: String): Flow<List<ReactionEntity>>

    @Query("SELECT MAX(rowID) FROM reactions WHERE chatRowID = :chatRowID")
    suspend fun getMaxRowID(chatRowID: Long): Long?

    @Query("SELECT * FROM reactions WHERE chatRowID = :chatRowID ORDER BY date ASC")
    suspend fun getByChatId(chatRowID: Long): List<ReactionEntity>

    @Query("SELECT COUNT(*) FROM reactions WHERE sendStatus IS NULL AND isRemoval = 0")
    suspend fun countConfirmedReactions(): Int

    @Upsert
    suspend fun upsert(reaction: ReactionEntity)

    @Upsert
    suspend fun upsertAll(reactions: List<ReactionEntity>)

    @Query("DELETE FROM reactions WHERE targetMessageGUID = :targetGuid AND partIndex = :partIndex AND reactionType = :type AND (senderID = :senderID OR (senderID IS NULL AND :senderID IS NULL))")
    suspend fun removeReaction(targetGuid: String, partIndex: Int, type: String, senderID: String?)

    // Delete all server-confirmed reactions for a chat (preserves pending optimistic ones)
    @Query("DELETE FROM reactions WHERE chatRowID = :chatRowID AND sendStatus IS NULL")
    suspend fun deleteConfirmedForChat(chatRowID: Long)

    @Query("DELETE FROM reactions WHERE chatRowID = :chatRowID")
    suspend fun deleteByChatId(chatRowID: Long)

    // Remove any existing reaction from a sender on a specific message part (enforces one-reaction-per-user-per-part)
    @Query("DELETE FROM reactions WHERE targetMessageGUID = :targetGuid AND partIndex = :partIndex AND (senderID = :senderID OR (senderID IS NULL AND :senderID IS NULL)) AND isFromMe = :isFromMe AND sendStatus IS NULL")
    suspend fun removeExistingReactionFromSender(targetGuid: String, partIndex: Int, senderID: String?, isFromMe: Boolean)

    @Query("UPDATE reactions SET sendStatus = :status, sendError = :error WHERE rowID = :rowID")
    suspend fun updateSendStatus(rowID: Long, status: String?, error: String?)

    @Query("DELETE FROM reactions WHERE rowID = :rowID")
    suspend fun deleteByRowID(rowID: Long)

    @Query("SELECT * FROM reactions WHERE rowID = :rowID")
    suspend fun getByRowID(rowID: Long): ReactionEntity?

    @Query("SELECT * FROM reactions WHERE targetMessageGUID = :targetGuid AND partIndex = :partIndex AND reactionType = :type AND isFromMe = 1 AND sendStatus IS NOT NULL LIMIT 1")
    suspend fun findPendingReaction(targetGuid: String, partIndex: Int, type: String): ReactionEntity?

    // Find the current user's confirmed (server-acknowledged) reaction on a message part
    @Query("SELECT * FROM reactions WHERE targetMessageGUID = :targetGuid AND partIndex = :partIndex AND isFromMe = 1 AND sendStatus IS NULL AND isRemoval = 0 LIMIT 1")
    suspend fun findMyConfirmedReaction(targetGuid: String, partIndex: Int): ReactionEntity?

    @Query("SELECT * FROM reactions WHERE chatRowID = :chatRowID AND sendStatus IS NOT NULL ORDER BY date ASC")
    suspend fun getPendingByChatId(chatRowID: Long): List<ReactionEntity>
}

package rip.build.courier.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import rip.build.courier.data.local.entity.ConversationEventEntity

@Dao
interface ConversationEventDao {
    @Upsert
    suspend fun upsertAll(events: List<ConversationEventEntity>)

    @Query("SELECT * FROM conversation_events WHERE conversationId = :conversationId ORDER BY eventSequence ASC")
    suspend fun getByConversationId(conversationId: String): List<ConversationEventEntity>

    @Query("DELETE FROM conversation_events WHERE conversationId = :conversationId")
    suspend fun deleteByConversationId(conversationId: String)
}

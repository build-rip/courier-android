package rip.build.courier.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "conversation_events",
    primaryKeys = ["conversationId", "eventSequence"],
    indices = [Index("conversationId")]
)
data class ConversationEventEntity(
    val conversationId: String,
    val conversationVersion: Int,
    val eventSequence: Long,
    val eventType: String,
    val payloadJson: String
)

package rip.build.courier.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reactions",
    indices = [Index("chatRowID"), Index("targetMessageGUID")]
)
data class ReactionEntity(
    @PrimaryKey val rowID: Long,
    val guid: String,
    val chatRowID: Long,
    val targetMessageGUID: String,
    val partIndex: Int = 0,
    val reactionType: String,
    val emoji: String? = null,
    val isRemoval: Boolean,
    val senderID: String?,
    val isFromMe: Boolean,
    val date: String,
    val sendStatus: String? = null,
    val sendError: String? = null
)

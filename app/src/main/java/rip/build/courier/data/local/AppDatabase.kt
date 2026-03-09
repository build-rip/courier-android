package rip.build.courier.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import rip.build.courier.data.local.dao.*
import rip.build.courier.data.local.entity.*

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        ReactionEntity::class,
        ParticipantEntity::class,
        AttachmentEntity::class,
        ConversationEventEntity::class
    ],
    version = 16,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun reactionDao(): ReactionDao
    abstract fun participantDao(): ParticipantDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun conversationEventDao(): ConversationEventDao
}

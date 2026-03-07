package rip.build.courier.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import rip.build.courier.data.local.AppDatabase
import rip.build.courier.data.local.dao.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE attachments ADD COLUMN downloadAttempts INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE chats ADD COLUMN lastMessageSyncRowID INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE chats ADD COLUMN lastReactionSyncRowID INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE chats ADD COLUMN hasUnreads INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE chats ADD COLUMN lastReadReceiptSyncTimestamp TEXT")
            db.execSQL("ALTER TABLE chats ADD COLUMN lastDeliveryReceiptSyncTimestamp TEXT")
            db.execSQL("ALTER TABLE chats ADD COLUMN unreadCount INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE chats ADD COLUMN lastReadMessageDate TEXT")
            db.execSQL("ALTER TABLE chats ADD COLUMN maxReadReceiptDate TEXT")
            db.execSQL("ALTER TABLE chats ADD COLUMN maxDeliveryReceiptDate TEXT")
            db.execSQL("ALTER TABLE messages ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE messages SET isRead = CASE WHEN isFromMe = 1 THEN 1 ELSE 0 END")
        }
    }

    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS attachments_new (
                    messageRowID INTEGER NOT NULL,
                    rowID INTEGER NOT NULL,
                    guid TEXT NOT NULL,
                    mimeType TEXT,
                    transferName TEXT,
                    totalBytes INTEGER NOT NULL,
                    isSticker INTEGER NOT NULL,
                    downloadState TEXT NOT NULL,
                    downloadedBytes INTEGER NOT NULL,
                    localFilePath TEXT,
                    downloadAttempts INTEGER NOT NULL,
                    PRIMARY KEY(messageRowID, rowID)
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO attachments_new (
                    messageRowID, rowID, guid, mimeType, transferName, totalBytes,
                    isSticker, downloadState, downloadedBytes, localFilePath, downloadAttempts
                )
                SELECT
                    messageRowID, rowID, guid, mimeType, transferName, totalBytes,
                    isSticker, downloadState, downloadedBytes, localFilePath, downloadAttempts
                FROM attachments
            """.trimIndent())
            db.execSQL("DROP TABLE attachments")
            db.execSQL("ALTER TABLE attachments_new RENAME TO attachments")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_attachments_rowID ON attachments(rowID)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_attachments_messageRowID ON attachments(messageRowID)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "courier_cache.db")
            .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun provideChatDao(db: AppDatabase): ChatDao = db.chatDao()
    @Provides fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()
    @Provides fun provideReactionDao(db: AppDatabase): ReactionDao = db.reactionDao()
    @Provides fun provideParticipantDao(db: AppDatabase): ParticipantDao = db.participantDao()
    @Provides fun provideAttachmentDao(db: AppDatabase): AttachmentDao = db.attachmentDao()
}

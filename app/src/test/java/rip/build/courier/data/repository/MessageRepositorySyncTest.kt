package rip.build.courier.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.robolectric.RobolectricTestRunner
import retrofit2.Response
import rip.build.courier.data.local.AppDatabase
import rip.build.courier.data.local.entity.ChatEntity
import rip.build.courier.data.local.entity.ConversationEventEntity
import rip.build.courier.data.local.entity.MessageEntity
import rip.build.courier.data.remote.api.BridgeApiService
import rip.build.courier.data.remote.api.dto.AttachmentDto
import rip.build.courier.data.remote.api.dto.ConversationEventDto
import rip.build.courier.data.remote.api.dto.ConversationEventsResponseDto
import rip.build.courier.data.remote.api.dto.ConversationListResponse
import rip.build.courier.data.remote.api.dto.MarkReadRequest
import rip.build.courier.data.remote.api.dto.MutationResponseDto
import rip.build.courier.data.remote.api.dto.PairRequest
import rip.build.courier.data.remote.api.dto.PairResponse
import rip.build.courier.data.remote.api.dto.SendMessageRequest
import rip.build.courier.data.remote.api.dto.TapbackRequest
import rip.build.courier.data.remote.api.dto.TokenRequest
import rip.build.courier.data.remote.api.dto.TokenResponse

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MessageRepositorySyncTest {
    private lateinit var db: AppDatabase
    private lateinit var api: FakeBridgeApiService
    private lateinit var repository: MessageRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        api = FakeBridgeApiService()
        repository = MessageRepository(
            api = api,
            messageDao = db.messageDao(),
            reactionDao = db.reactionDao(),
            attachmentDao = db.attachmentDao(),
            chatDao = db.chatDao(),
            conversationEventDao = db.conversationEventDao(),
            moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun syncChat_replaysAuthoritativeEventsIntoLocalState() = runTest {
        seedChat(latestEventSequence = 4)
        api.enqueueConversationEvents(
            ConversationEventsResponseDto(
                conversationId = CONVERSATION_ID,
                conversationVersion = 1,
                latestEventSequence = 4,
                from = 0,
                nextFrom = 4,
                hasMore = false,
                events = listOf(
                    event(
                        sequence = 1,
                        type = "messageCreated",
                        payload = mapOf(
                            "messageID" to "m1",
                            "senderID" to "+15555550100",
                            "isFromMe" to false,
                            "service" to "iMessage",
                            "sentAt" to "2026-03-09T00:00:00Z",
                            "text" to "hello",
                            "richText" to null,
                            "attachments" to emptyList<Map<String, Any?>>(),
                            "replyToMessageID" to null
                        )
                    ),
                    event(
                        sequence = 2,
                        type = "reactionSet",
                        payload = mapOf(
                            "messageID" to "m1",
                            "actorID" to "+15555550100",
                            "isFromMe" to false,
                            "sentAt" to "2026-03-09T00:00:01Z",
                            "partIndex" to 0,
                            "reactionType" to "love",
                            "emoji" to null
                        )
                    ),
                    event(
                        sequence = 3,
                        type = "messageDeliveredUpdated",
                        payload = mapOf(
                            "messageID" to "m1",
                            "deliveredAt" to "2026-03-09T00:00:02Z"
                        )
                    ),
                    event(
                        sequence = 4,
                        type = "messageReadUpdated",
                        payload = mapOf(
                            "messageID" to "m1",
                            "readAt" to "2026-03-09T00:00:03Z"
                        )
                    )
                )
            )
        )

        val result = repository.syncChat(CHAT_ROW_ID).getOrThrow()

        assertEquals(3 to 1, result)
        val chat = requireNotNull(db.chatDao().getById(CHAT_ROW_ID))
        assertEquals(4, chat.localEventSequence)
        assertEquals(4, chat.latestEventSequence)
        assertFalse(chat.pendingFullResync)
        assertFalse(chat.hasUnreads)

        val message = requireNotNull(db.messageDao().getByGuid("m1"))
        assertEquals("hello", message.text)
        assertEquals("2026-03-09T00:00:02Z", message.dateDelivered)
        assertEquals("2026-03-09T00:00:03Z", message.dateRead)
        assertEquals(true, message.isRead)

        val reactions = db.reactionDao().getByChatId(CHAT_ROW_ID)
        assertEquals(1, reactions.size)
        assertEquals("love", reactions.single().reactionType)

        val events = db.conversationEventDao().getByConversationId(CONVERSATION_ID)
        assertEquals(4, events.size)
    }

    @Test
    fun syncChat_retriesFromZeroAfterResyncRequired() = runTest {
        seedChat(conversationVersion = 1, latestEventSequence = 2, localEventSequence = 1)
        db.messageDao().upsert(
            MessageEntity(
                rowID = 77,
                guid = "old-message",
                chatRowID = CHAT_ROW_ID,
                text = "stale",
                senderID = "+1",
                date = "2026-03-08T00:00:00Z",
                dateRead = null,
                dateDelivered = null,
                isRead = false,
                isFromMe = false,
                service = "iMessage",
                hasAttachments = false
            )
        )
        db.conversationEventDao().upsertAll(
            listOf(
                ConversationEventEntity(
                    conversationId = CONVERSATION_ID,
                    conversationVersion = 1,
                    eventSequence = 1,
                    eventType = "messageCreated",
                    payloadJson = """
                        {
                          "messageID": "old-message",
                          "senderID": "+1",
                          "isFromMe": false,
                          "service": "iMessage",
                          "sentAt": "2026-03-08T00:00:00Z",
                          "text": "stale",
                          "richText": null,
                          "attachments": [],
                          "replyToMessageID": null
                        }
                    """.trimIndent()
                )
            )
        )

        api.enqueueResponse(resyncRequired(version = 2, latestEventSequence = 2))
        api.enqueueConversationEvents(
            ConversationEventsResponseDto(
                conversationId = CONVERSATION_ID,
                conversationVersion = 2,
                latestEventSequence = 2,
                from = 0,
                nextFrom = 2,
                hasMore = false,
                events = listOf(
                    event(
                        sequence = 1,
                        type = "messageCreated",
                        payload = mapOf(
                            "messageID" to "fresh-message",
                            "senderID" to "+15555550100",
                            "isFromMe" to false,
                            "service" to "iMessage",
                            "sentAt" to "2026-03-09T00:00:00Z",
                            "text" to "fresh",
                            "richText" to null,
                            "attachments" to emptyList<Map<String, Any?>>(),
                            "replyToMessageID" to null
                        )
                    ),
                    event(
                        sequence = 2,
                        type = "messageReadUpdated",
                        payload = mapOf(
                            "messageID" to "fresh-message",
                            "readAt" to "2026-03-09T00:00:01Z"
                        )
                    )
                )
            )
        )

        repository.syncChat(CHAT_ROW_ID).getOrThrow()

        val chat = requireNotNull(db.chatDao().getById(CHAT_ROW_ID))
        assertEquals(2, chat.conversationVersion)
        assertEquals(2, chat.localEventSequence)
        assertEquals(2, chat.latestEventSequence)
        assertNull(db.messageDao().getByGuid("old-message"))
        assertNotNull(db.messageDao().getByGuid("fresh-message"))
        assertEquals(listOf(1L, 0L), api.requests.map { it.from })
        assertEquals(listOf(1, 2), api.requests.map { it.conversationVersion })
    }

    @Test
    fun syncChat_fetchesMultiplePagesUntilCaughtUp() = runTest {
        seedChat(latestEventSequence = 2)
        api.enqueueConversationEvents(
            ConversationEventsResponseDto(
                conversationId = CONVERSATION_ID,
                conversationVersion = 1,
                latestEventSequence = 2,
                from = 0,
                nextFrom = 1,
                hasMore = true,
                events = listOf(
                    event(
                        sequence = 1,
                        type = "messageCreated",
                        payload = mapOf(
                            "messageID" to "m1",
                            "senderID" to "+15555550100",
                            "isFromMe" to false,
                            "service" to "iMessage",
                            "sentAt" to "2026-03-09T00:00:00Z",
                            "text" to "first",
                            "richText" to null,
                            "attachments" to emptyList<Map<String, Any?>>(),
                            "replyToMessageID" to null
                        )
                    )
                )
            )
        )
        api.enqueueConversationEvents(
            ConversationEventsResponseDto(
                conversationId = CONVERSATION_ID,
                conversationVersion = 1,
                latestEventSequence = 2,
                from = 1,
                nextFrom = 2,
                hasMore = false,
                events = listOf(
                    event(
                        sequence = 2,
                        type = "messageCreated",
                        payload = mapOf(
                            "messageID" to "m2",
                            "senderID" to "+15555550101",
                            "isFromMe" to false,
                            "service" to "iMessage",
                            "sentAt" to "2026-03-09T00:00:01Z",
                            "text" to "second",
                            "richText" to null,
                            "attachments" to emptyList<Map<String, Any?>>(),
                            "replyToMessageID" to null
                        )
                    )
                )
            )
        )

        repository.syncChat(CHAT_ROW_ID).getOrThrow()

        assertNotNull(db.messageDao().getByGuid("m1"))
        assertNotNull(db.messageDao().getByGuid("m2"))
        val chat = requireNotNull(db.chatDao().getById(CHAT_ROW_ID))
        assertEquals(2, chat.localEventSequence)
        assertEquals(listOf(0L, 1L), api.requests.map { it.from })
    }

    @Test
    fun syncChat_handlesOmittedOptionalPayloadFields() = runTest {
        seedChat(latestEventSequence = 3)
        api.enqueueConversationEvents(
            ConversationEventsResponseDto(
                conversationId = CONVERSATION_ID,
                conversationVersion = 1,
                latestEventSequence = 3,
                from = 0,
                nextFrom = 3,
                hasMore = false,
                events = listOf(
                    event(
                        sequence = 1,
                        type = "messageCreated",
                        payload = mapOf(
                            "messageID" to "m1",
                            "isFromMe" to false,
                            "service" to "iMessage",
                            "sentAt" to "2026-03-09T00:00:00Z",
                            "attachments" to emptyList<Map<String, Any?>>(),
                            "text" to "hello"
                        )
                    ),
                    event(
                        sequence = 2,
                        type = "messageEdited",
                        payload = mapOf(
                            "messageID" to "m1",
                            "editedAt" to "2026-03-09T00:00:01Z"
                        )
                    ),
                    event(
                        sequence = 3,
                        type = "reactionSet",
                        payload = mapOf(
                            "messageID" to "m1",
                            "isFromMe" to false,
                            "sentAt" to "2026-03-09T00:00:02Z",
                            "partIndex" to 0,
                            "reactionType" to "love"
                        )
                    )
                )
            )
        )

        repository.syncChat(CHAT_ROW_ID).getOrThrow()

        val message = requireNotNull(db.messageDao().getByGuid("m1"))
        assertNull(message.senderID)
        assertEquals("hello", message.text)

        val reactions = db.reactionDao().getByChatId(CHAT_ROW_ID)
        assertEquals(1, reactions.size)
        assertNull(reactions.single().senderID)
        assertNull(reactions.single().emoji)
    }

    private suspend fun seedChat(
        conversationVersion: Int = 1,
        latestEventSequence: Long,
        localEventSequence: Long = 0
    ) {
        db.chatDao().upsert(
            ChatEntity(
                rowID = CHAT_ROW_ID,
                conversationId = CONVERSATION_ID,
                guid = CONVERSATION_ID,
                chatIdentifier = "+15555550100",
                displayName = "Alex",
                serviceName = "iMessage",
                isGroup = false,
                lastMessageDate = null,
                lastMessageText = null,
                lastMessageIsFromMe = null,
                conversationVersion = conversationVersion,
                latestEventSequence = latestEventSequence,
                localEventSequence = localEventSequence
            )
        )
    }

    private fun event(sequence: Long, type: String, payload: Map<String, Any?>): ConversationEventDto {
        return ConversationEventDto(
            conversationID = CONVERSATION_ID,
            conversationVersion = 1,
            eventSequence = sequence,
            eventType = type,
            payload = payload
        )
    }

    private fun resyncRequired(version: Int, latestEventSequence: Long): Response<ConversationEventsResponseDto> {
        val body = """
            {
              "resyncRequired": true,
              "conversationId": "$CONVERSATION_ID",
              "conversationVersion": $version,
              "latestEventSequence": $latestEventSequence
            }
        """.trimIndent()
        return Response.error(409, body.toResponseBody("application/json".toMediaType()))
    }

    private class FakeBridgeApiService : BridgeApiService {
        val requests = mutableListOf<EventRequest>()
        private val queuedResponses = ArrayDeque<Response<ConversationEventsResponseDto>>()

        fun enqueueConversationEvents(response: ConversationEventsResponseDto) {
            queuedResponses.add(Response.success(response))
        }

        fun enqueueResponse(response: Response<ConversationEventsResponseDto>) {
            queuedResponses.add(response)
        }

        override suspend fun getConversations(): ConversationListResponse = ConversationListResponse(emptyList())

        override suspend fun getConversationEvents(
            conversationId: String,
            conversationVersion: Int,
            from: Long,
            limit: Int
        ): Response<ConversationEventsResponseDto> {
            requests += EventRequest(conversationId, conversationVersion, from, limit)
            return queuedResponses.removeFirst()
        }

        override suspend fun getAttachments(messageRowID: Long): List<AttachmentDto> = emptyList()

        override suspend fun sendMessage(
            conversationId: String,
            request: SendMessageRequest
        ): Response<MutationResponseDto> = unsupported()

        override suspend fun sendTapback(
            conversationId: String,
            request: TapbackRequest
        ): Response<MutationResponseDto> = unsupported()

        override suspend fun removeTapback(
            conversationId: String,
            request: TapbackRequest
        ): Response<MutationResponseDto> = unsupported()

        override suspend fun markConversationAsRead(
            conversationId: String,
            request: MarkReadRequest
        ): Response<MutationResponseDto> = unsupported()

        override suspend fun pair(request: PairRequest): PairResponse = unsupported()
        override suspend fun refreshToken(request: TokenRequest): TokenResponse = unsupported()

        private fun <T> unsupported(): T {
            throw UnsupportedOperationException("Not used in this test")
        }
    }

    private data class EventRequest(
        val conversationId: String,
        val conversationVersion: Int,
        val from: Long,
        val limit: Int
    )

    private companion object {
        const val CHAT_ROW_ID = 1L
        const val CONVERSATION_ID = "iMessage;-;+15555550100"
    }
}

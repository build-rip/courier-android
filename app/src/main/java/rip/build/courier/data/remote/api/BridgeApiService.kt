package rip.build.courier.data.remote.api

import rip.build.courier.data.remote.api.dto.*
import retrofit2.Response
import retrofit2.http.*

interface BridgeApiService {

    // Public endpoints

    @POST("api/pair")
    suspend fun pair(@Body request: PairRequest): PairResponse

    @POST("api/auth/token")
    suspend fun refreshToken(@Body request: TokenRequest): TokenResponse

    // Protected endpoints

    @GET("api/chats")
    suspend fun getChats(@Query("limit") limit: Int = 200): ChatListResponse

    @GET("api/chats/{id}/messages")
    suspend fun getMessages(
        @Path("id") chatRowID: Long,
        @Query("limit") limit: Int = 50,
        @Query("after") after: Long? = null
    ): List<MessageDto>

    @GET("api/chats/{id}/reactions")
    suspend fun getReactions(
        @Path("id") chatRowID: Long,
        @Query("limit") limit: Int = 200,
        @Query("after") after: Long? = null
    ): List<ReactionDto>

    @GET("api/chats/{id}/participants")
    suspend fun getParticipants(@Path("id") chatRowID: Long): List<ParticipantDto>

    @GET("api/messages/{id}/attachments")
    suspend fun getAttachments(@Path("id") messageRowID: Long): List<AttachmentDto>

    @POST("api/chats/{id}/messages")
    suspend fun sendMessage(
        @Path("id") chatRowID: Long,
        @Body request: SendMessageRequest
    ): Response<Unit>

    @POST("api/chats/{id}/tapback")
    suspend fun sendTapback(
        @Path("id") chatRowID: Long,
        @Body request: TapbackRequest
    ): Response<Unit>

    @HTTP(method = "DELETE", path = "api/chats/{id}/tapback", hasBody = true)
    suspend fun removeTapback(
        @Path("id") chatRowID: Long,
        @Body request: TapbackRequest
    ): Response<Unit>

    @POST("api/chats/{id}/read")
    suspend fun markChatAsRead(@Path("id") chatRowID: Long): Response<Unit>

    @GET("api/chats/{id}/sync")
    suspend fun syncChat(
        @Path("id") chatRowID: Long,
        @Query("msgAfter") msgAfter: Long = 0,
        @Query("rxnAfter") rxnAfter: Long = 0,
        @Query("readAfter") readAfter: Long = 0,
        @Query("deliveryAfter") deliveryAfter: Long = 0,
        @Query("limit") limit: Int = 500
    ): ChatSyncResponse
}

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

    @GET("api/conversations")
    suspend fun getConversations(): ConversationListResponse

    @GET("api/conversations/{id}/events")
    suspend fun getConversationEvents(
        @Path("id") conversationId: String,
        @Query("conversationVersion") conversationVersion: Int,
        @Query("from") from: Long,
        @Query("limit") limit: Int = 500
    ): Response<ConversationEventsResponseDto>

    @GET("api/messages/{id}/attachments")
    suspend fun getAttachments(@Path("id") messageRowID: Long): List<AttachmentDto>

    @POST("api/conversations/{id}/messages")
    suspend fun sendMessage(
        @Path("id") conversationId: String,
        @Body request: SendMessageRequest
    ): Response<MutationResponseDto>

    @POST("api/conversations/{id}/tapback")
    suspend fun sendTapback(
        @Path("id") conversationId: String,
        @Body request: TapbackRequest
    ): Response<MutationResponseDto>

    @HTTP(method = "DELETE", path = "api/conversations/{id}/tapback", hasBody = true)
    suspend fun removeTapback(
        @Path("id") conversationId: String,
        @Body request: TapbackRequest
    ): Response<MutationResponseDto>

    @POST("api/conversations/{id}/read")
    suspend fun markConversationAsRead(
        @Path("id") conversationId: String,
        @Body request: MarkReadRequest
    ): Response<MutationResponseDto>
}

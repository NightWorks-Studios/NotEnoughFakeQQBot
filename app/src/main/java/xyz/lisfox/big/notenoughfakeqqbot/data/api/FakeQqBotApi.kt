package xyz.lisfox.big.notenoughfakeqqbot.data.api

import kotlinx.serialization.json.JsonElement
import okhttp3.MultipartBody
import retrofit2.http.*
import xyz.lisfox.big.notenoughfakeqqbot.data.model.*

interface FakeQqBotApi {

    @POST("auth")
    suspend fun auth(): ApiResponse<JsonElement>

    @GET("bots")
    suspend fun listBots(): ApiResponse<List<BotInfo>>

    @GET("bots/{platform}/{selfId}/channels")
    suspend fun listChannels(
        @Path("platform") platform: String,
        @Path("selfId") selfId: String,
        @Query("limit") limit: Int = 200,
    ): ApiResponse<List<ChannelInfo>>

    @GET("bots/{platform}/{selfId}/channels/{channelId}/messages")
    suspend fun listMessages(
        @Path("platform") platform: String,
        @Path("selfId") selfId: String,
        @Path("channelId") channelId: String,
        @Query("limit") limit: Int = 50,
        @Query("before") before: Long? = null,
        @Query("after") after: Long? = null,
    ): ApiResponse<List<MessageEntity>>

    @GET("bots/{platform}/{selfId}/channels/{channelId}/info")
    suspend fun getChannelInfo(
        @Path("platform") platform: String,
        @Path("selfId") selfId: String,
        @Path("channelId") channelId: String,
    ): ApiResponse<ChannelInfo>

    @POST("bots/{platform}/{selfId}/channels/{channelId}/send")
    suspend fun sendText(
        @Path("platform") platform: String,
        @Path("selfId") selfId: String,
        @Path("channelId") channelId: String,
        @Body body: SendTextRequest,
    ): ApiResponse<SendTextResult>

    @DELETE("bots/{platform}/{selfId}/channels/{channelId}/messages")
    suspend fun deleteMessages(
        @Path("platform") platform: String,
        @Path("selfId") selfId: String,
        @Path("channelId") channelId: String,
    ): ApiResponse<JsonElement>

    @POST("bots/{platform}/{selfId}/channels/{channelId}/read")
    suspend fun markRead(
        @Path("platform") platform: String,
        @Path("selfId") selfId: String,
        @Path("channelId") channelId: String,
    ): ApiResponse<JsonElement>

    @GET("bots/{platform}/{selfId}/sync")
    suspend fun sync(
        @Path("platform") platform: String,
        @Path("selfId") selfId: String,
        @Query("afterId") afterId: Int = 0,
        @Query("limit") limit: Int = 200,
    ): ApiResponse<SyncResponse>

    @GET("unread")
    suspend fun getUnread(): ApiResponse<List<UnreadInfo>>

    @Multipart
    @POST("upload")
    suspend fun uploadImage(
        @Part file: MultipartBody.Part,
        @Query("platform") platform: String,
        @Query("selfId") selfId: String,
        @Query("channelId") channelId: String,
        @Query("chatType") chatType: String,
    ): ApiResponse<UploadResult>
}

@kotlinx.serialization.Serializable
data class UnreadInfo(
    val platform: String,
    val selfId: String,
    val channelId: String,
    val chatType: String,
    val count: Int,
    val lastMessageAt: Long,
)

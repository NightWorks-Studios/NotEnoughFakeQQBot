package xyz.lisfox.big.notenoughfakeqqbot.data.model

import kotlinx.serialization.Serializable

/**
 * 服务端 Bot 信息
 */
@Serializable
data class BotInfo(
    val platform: String,
    val selfId: String,
    val sid: String? = null,
    val status: String = "offline",
    val username: String? = null,
    val avatar: String? = null,
)

/**
 * 服务端频道/会话信息
 */
@Serializable
data class ChannelInfo(
    val platform: String,
    val selfId: String,
    val channelId: String,
    val chatType: String = "group",
    val canProactive: Boolean = false,
    val lastMessageAt: Long = 0,
    val messageCount: Int = 0,
    val lastNickname: String? = null,
    val lastContent: String? = null,
)

/**
 * 发送消息请求体
 */
@Serializable
data class SendTextRequest(
    val content: String,
    val chatType: String? = null,
    val guildId: String? = null,
)

/**
 * 发送消息结果
 */
@Serializable
data class SendTextResult(
    val mode: String,           // "proactive" | "fallback"
    val messageId: String? = null,
)

/**
 * 图片上传（直接发送）结果
 */
@Serializable
data class UploadResult(
    val messageId: String? = null,
    val width: Int = 0,
    val height: Int = 0,
)

/**
 * 增量同步响应
 */
@Serializable
data class SyncResponse(
    val messages: List<MessageEntity>,
    val hasMore: Boolean,
)

/**
 * 通用 API 响应包装
 */
@Serializable
data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T? = null,
)

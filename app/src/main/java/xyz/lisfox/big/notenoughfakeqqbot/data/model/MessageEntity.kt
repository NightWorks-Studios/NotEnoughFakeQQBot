package xyz.lisfox.big.notenoughfakeqqbot.data.model

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 服务端返回的消息结构，也是 Room 本地存储的主表
 */
@Entity(tableName = "messages")
@Serializable
data class MessageEntity(
    @PrimaryKey
    val id: Int,                    // 服务端自增 ID，用于增量同步
    val platform: String,
    val selfId: String,
    val channelId: String,
    val peerId: String? = null,
    val chatType: String? = null,   // "group" | "c2c"
    val guildId: String? = null,
    val userId: String,
    val nickname: String? = null,
    val avatar: String? = null,
    val quoteNickname: String? = null,
    val quoteContent: String? = null,
    val messageId: String,
    val content: String? = null,
    val timestamp: Long,
    val receivedAt: Long,
    val raw: String? = null,
)

/**
 * FTS 全文索引虚拟表，用于本地搜索
 */
@Fts4(contentEntity = MessageEntity::class)
@Entity(tableName = "messages_fts")
data class MessageFts(
    val nickname: String?,
    val content: String?,
)

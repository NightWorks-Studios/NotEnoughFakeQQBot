package xyz.lisfox.big.notenoughfakeqqbot.data.model

import androidx.room.Entity
import kotlinx.serialization.Serializable

/**
 * 本地会话表，维护会话列表的展示信息
 */
@Entity(tableName = "conversations", primaryKeys = ["platform", "selfId", "channelId"])
@Serializable
data class ConversationEntity(
    val platform: String,
    val selfId: String,
    val channelId: String,
    val chatType: String = "group",
    val displayName: String? = null,
    val lastMessage: String? = null,
    val lastMessageAt: Long = 0,
    val lastNickname: String? = null,
    val unreadCount: Int = 0,
    val lastReadAt: Long = 0,
    val pinned: Boolean = false,
    val canProactive: Boolean = false,
    val avatar: String? = null,
)

package xyz.lisfox.big.notenoughfakeqqbot.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 同步状态跟踪表
 */
@Entity(tableName = "sync_state")
data class SyncState(
    @PrimaryKey
    val key: String,            // "platform:selfId" 或 "global"
    val lastSyncId: Int = 0,    // 最后同步的服务端消息 ID
    val lastSyncAt: Long = 0,
)

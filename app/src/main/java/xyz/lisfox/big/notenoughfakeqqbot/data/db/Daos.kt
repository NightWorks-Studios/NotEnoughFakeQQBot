package xyz.lisfox.big.notenoughfakeqqbot.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import xyz.lisfox.big.notenoughfakeqqbot.data.model.MessageEntity
import xyz.lisfox.big.notenoughfakeqqbot.data.model.ConversationEntity
import xyz.lisfox.big.notenoughfakeqqbot.data.model.SyncState

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE platform = :platform AND selfId = :selfId AND channelId = :channelId ORDER BY receivedAt DESC LIMIT :limit")
    suspend fun getMessages(platform: String, selfId: String, channelId: String, limit: Int = 50): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Int): MessageEntity?

    @Query("SELECT * FROM messages WHERE platform = :platform AND selfId = :selfId AND channelId = :channelId ORDER BY receivedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesPaged(platform: String, selfId: String, channelId: String, limit: Int, offset: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE platform = :platform AND selfId = :selfId AND channelId = :channelId ORDER BY receivedAt DESC")
    fun observeMessages(platform: String, selfId: String, channelId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE platform = :platform AND selfId = :selfId AND channelId = :channelId AND receivedAt < :before ORDER BY receivedAt DESC LIMIT :limit")
    suspend fun getMessagesBefore(platform: String, selfId: String, channelId: String, before: Long, limit: Int = 50): List<MessageEntity>

    @Query("SELECT MAX(id) FROM messages WHERE platform = :platform AND selfId = :selfId")
    suspend fun getMaxId(platform: String, selfId: String): Int?

    @Query("SELECT MAX(id) FROM messages")
    suspend fun getGlobalMaxId(): Int?

    @Query("SELECT MIN(receivedAt) FROM messages WHERE platform = :platform AND selfId = :selfId AND channelId = :channelId")
    suspend fun getOldestReceivedAt(platform: String, selfId: String, channelId: String): Long?

    @Query("DELETE FROM messages WHERE platform = :platform AND selfId = :selfId AND channelId = :channelId")
    suspend fun deleteByChannel(platform: String, selfId: String, channelId: String)

    @Query("UPDATE messages SET recalled = 1, recalledAt = :recalledAt, recalledBy = :recalledBy WHERE id = :id")
    suspend fun markRecalled(id: Int, recalledAt: Long, recalledBy: String?)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun count(): Int

    // FTS 搜索
    @Query("SELECT messages.* FROM messages JOIN messages_fts ON messages.rowid = messages_fts.rowid WHERE messages_fts MATCH :query AND messages.recalled = 0 ORDER BY messages.receivedAt DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int = 100): List<MessageEntity>

    @Query("SELECT messages.* FROM messages JOIN messages_fts ON messages.rowid = messages_fts.rowid WHERE messages_fts MATCH :query AND messages.platform = :platform AND messages.selfId = :selfId AND messages.recalled = 0 ORDER BY messages.receivedAt DESC LIMIT :limit")
    suspend fun searchInBot(query: String, platform: String, selfId: String, limit: Int = 100): List<MessageEntity>
}

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conversations: List<ConversationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations WHERE platform = :platform AND selfId = :selfId ORDER BY pinned DESC, lastMessageAt DESC")
    fun observeConversations(platform: String, selfId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE platform = :platform AND selfId = :selfId ORDER BY pinned DESC, lastMessageAt DESC")
    suspend fun getConversations(platform: String, selfId: String): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE platform = :platform AND selfId = :selfId AND channelId = :channelId")
    suspend fun getConversation(platform: String, selfId: String, channelId: String): ConversationEntity?

    @Query("UPDATE conversations SET unreadCount = 0, lastReadAt = :readAt WHERE platform = :platform AND selfId = :selfId AND channelId = :channelId")
    suspend fun markRead(platform: String, selfId: String, channelId: String, readAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET unreadCount = unreadCount + 1, lastMessage = :lastMessage, lastMessageAt = :lastMessageAt, lastNickname = :lastNickname WHERE platform = :platform AND selfId = :selfId AND channelId = :channelId")
    suspend fun incrementUnread(platform: String, selfId: String, channelId: String, lastMessage: String?, lastMessageAt: Long, lastNickname: String?)

    @Query("UPDATE conversations SET pinned = :pinned WHERE platform = :platform AND selfId = :selfId AND channelId = :channelId")
    suspend fun setPinned(platform: String, selfId: String, channelId: String, pinned: Boolean)

    @Query("UPDATE conversations SET muted = :muted WHERE platform = :platform AND selfId = :selfId AND channelId = :channelId")
    suspend fun setMuted(platform: String, selfId: String, channelId: String, muted: Boolean)

    @Query("UPDATE conversations SET displayName = :displayName WHERE platform = :platform AND selfId = :selfId AND channelId = :channelId")
    suspend fun setDisplayName(platform: String, selfId: String, channelId: String, displayName: String?)

    @Query("DELETE FROM conversations WHERE platform = :platform AND selfId = :selfId AND channelId = :channelId")
    suspend fun delete(platform: String, selfId: String, channelId: String)

    @Query("SELECT SUM(unreadCount) FROM conversations WHERE platform = :platform AND selfId = :selfId")
    fun observeTotalUnread(platform: String, selfId: String): Flow<Int?>

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}

@Dao
interface SyncStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncState)

    @Query("SELECT * FROM sync_state WHERE `key` = :key")
    suspend fun get(key: String): SyncState?
}

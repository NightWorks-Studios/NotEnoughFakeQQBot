package xyz.lisfox.big.notenoughfakeqqbot.data.repository

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import retrofit2.HttpException
import xyz.lisfox.big.notenoughfakeqqbot.data.api.ApiClient
import xyz.lisfox.big.notenoughfakeqqbot.data.cache.ImageCacheManager
import xyz.lisfox.big.notenoughfakeqqbot.data.db.AppDatabase
import xyz.lisfox.big.notenoughfakeqqbot.data.model.*
import xyz.lisfox.big.notenoughfakeqqbot.data.websocket.WebSocketManager
import xyz.lisfox.big.notenoughfakeqqbot.data.websocket.WsEvent
import xyz.lisfox.big.notenoughfakeqqbot.service.NotificationHelper
import xyz.lisfox.big.notenoughfakeqqbot.util.extractImageUrls

class MessageRepository(
    private val db: AppDatabase,
    private val wsManager: WebSocketManager,
    private val imageCacheManager: ImageCacheManager,
    private val notificationHelper: NotificationHelper,
) {
    companion object {
        private const val TAG = "MessageRepo"
    }

    private val messageDao = db.messageDao()
    private val conversationDao = db.conversationDao()
    private val syncStateDao = db.syncStateDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    init {
        // 监听 WebSocket 事件
        scope.launch {
            wsManager.events.collect { event ->
                when (event) {
                    is WsEvent.NewMessage -> handleNewMessage(event.data)
                    is WsEvent.MessageRecalled -> handleMessageRecalled(event)
                    is WsEvent.ChannelUpdate -> handleChannelUpdate(event)
                    is WsEvent.BotStatus -> { /* handled by ViewModel */ }
                }
            }
        }
    }

    /**
     * 全量增量同步：拉取某个 Bot 下所有新消息
     */
    suspend fun syncAll(platform: String, selfId: String) {
        val key = "$platform:$selfId"
        val state = syncStateDao.get(key)
        var afterId = state?.lastSyncId ?: 0
        var totalSynced = 0

        while (true) {
            try {
                val response = ApiClient.api.sync(platform, selfId, afterId, 200)
                val data = response.data ?: break
                if (data.messages.isEmpty()) break

                messageDao.insertAll(data.messages)
                updateConversationsFromMessages(data.messages, platform, selfId)

                // 异步缓存所有图片
                cacheImagesFromMessages(data.messages)

                afterId = data.messages.maxOf { it.id }
                totalSynced += data.messages.size

                syncStateDao.upsert(SyncState(key, afterId, System.currentTimeMillis()))

                if (!data.hasMore) break
            } catch (e: Exception) {
                Log.w(TAG, "Sync failed: ${e.message}")
                break
            }
        }

        if (totalSynced > 0) {
            Log.i(TAG, "Synced $totalSynced messages for $key")
        }
    }

    /**
     * 获取本地消息（从 Room）
     */
    fun observeMessages(platform: String, selfId: String, channelId: String): Flow<List<MessageEntity>> {
        return messageDao.observeMessages(platform, selfId, channelId)
    }

    suspend fun getMessages(platform: String, selfId: String, channelId: String, limit: Int = 50): List<MessageEntity> {
        return messageDao.getMessages(platform, selfId, channelId, limit)
    }

    /**
     * 加载更多历史消息（先查本地，不够再从服务器拉）
     */
    suspend fun loadMoreMessages(platform: String, selfId: String, channelId: String, beforeReceivedAt: Long, limit: Int = 50): List<MessageEntity> {
        // 先查本地
        val local = messageDao.getMessagesBefore(platform, selfId, channelId, beforeReceivedAt, limit)
        if (local.size >= limit) return local

        // 本地不够，从服务器拉
        try {
            val response = ApiClient.api.listMessages(platform, selfId, channelId, limit, before = beforeReceivedAt)
            val remote = response.data ?: emptyList()
            if (remote.isNotEmpty()) {
                messageDao.insertAll(remote)
                cacheImagesFromMessages(remote)
            }
            return messageDao.getMessagesBefore(platform, selfId, channelId, beforeReceivedAt, limit)
        } catch (e: Exception) {
            Log.w(TAG, "Load more failed: ${e.message}")
            return local
        }
    }

    /**
     * 发送文本消息
     */
    suspend fun sendText(
        platform: String,
        selfId: String,
        channelId: String,
        content: String,
        chatType: String?,
        messageType: String = "text",
        keyboard: KeyboardConfig? = null,
    ): SendTextResult {
        val result = ApiClient.api.sendText(platform, selfId, channelId, SendTextRequest(content, chatType, messageType = messageType, keyboard = keyboard))
        if (result.code != 0) throw Exception(result.message)
        return result.data ?: throw Exception("empty response")
    }

    suspend fun recallMessage(message: MessageEntity) {
        try {
            val response = ApiClient.api.recallMessage(
                message.platform,
                message.selfId,
                message.channelId,
                message.id,
                RecallMessageRequest(message.chatType),
            )
            if (response.code != 0) throw Exception(response.message)
            val result = response.data ?: throw Exception("empty response")
            markMessageRecalled(message.id, result.recalledAt, result.recalledBy)
        } catch (e: HttpException) {
            throw Exception(parseHttpErrorMessage(e) ?: "HTTP ${e.code()} ${e.message()}")
        }
    }

    private fun parseHttpErrorMessage(error: HttpException): String? {
        return try {
            val body = error.response()?.errorBody()?.string() ?: return null
            val obj = json.parseToJsonElement(body).jsonObject
            obj["message"]?.jsonPrimitive?.contentOrNull
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 本地搜索消息
     */
    suspend fun searchMessages(query: String, platform: String? = null, selfId: String? = null): List<MessageEntity> {
        val ftsQuery = query.trim().replace("\"", "").let { "\"$it*\"" }
        return if (platform != null && selfId != null) {
            messageDao.searchInBot(ftsQuery, platform, selfId)
        } else {
            messageDao.search(ftsQuery)
        }
    }

    /**
     * 刷新会话列表（从服务器拉取最新频道列表并更新本地）
     */
    suspend fun refreshConversations(platform: String, selfId: String) {
        try {
            val response = ApiClient.api.listChannels(platform, selfId, 200)
            val channels = response.data ?: return
            val existing = conversationDao.getConversations(platform, selfId).associateBy { it.channelId }

            val entities = channels.map { ch ->
                val old = existing[ch.channelId]
                ConversationEntity(
                    platform = platform,
                    selfId = selfId,
                    channelId = ch.channelId,
                    chatType = ch.chatType,
                    displayName = old?.displayName,
                    lastMessage = ch.lastContent ?: old?.lastMessage,
                    lastMessageAt = ch.lastMessageAt,
                    lastNickname = ch.lastNickname ?: old?.lastNickname,
                    unreadCount = old?.unreadCount ?: 0,
                    lastReadAt = old?.lastReadAt ?: 0,
                    pinned = old?.pinned ?: false,
                    muted = old?.muted ?: false,
                    canProactive = ch.canProactive,
                    avatar = old?.avatar,
                )
            }
            conversationDao.insertAll(entities)
        } catch (e: Exception) {
            Log.w(TAG, "Refresh conversations failed: ${e.message}")
        }
    }

    fun observeConversations(platform: String, selfId: String): Flow<List<ConversationEntity>> {
        return conversationDao.observeConversations(platform, selfId)
    }

    fun observeTotalUnread(platform: String, selfId: String): Flow<Int> {
        return conversationDao.observeTotalUnread(platform, selfId).map { it ?: 0 }
    }

    suspend fun markConversationRead(platform: String, selfId: String, channelId: String) {
        conversationDao.markRead(platform, selfId, channelId)
        // 也通知服务端
        try {
            ApiClient.api.markRead(platform, selfId, channelId)
        } catch (_: Exception) {}
    }

    suspend fun pinConversation(platform: String, selfId: String, channelId: String, pinned: Boolean) {
        conversationDao.setPinned(platform, selfId, channelId, pinned)
    }

    suspend fun muteConversation(platform: String, selfId: String, channelId: String, muted: Boolean) {
        conversationDao.setMuted(platform, selfId, channelId, muted)
    }

    suspend fun deleteConversation(platform: String, selfId: String, channelId: String) {
        conversationDao.delete(platform, selfId, channelId)
    }

    suspend fun setConversationDisplayName(platform: String, selfId: String, channelId: String, name: String?) {
        conversationDao.setDisplayName(platform, selfId, channelId, name)
    }

    suspend fun getLocalMessageCount(): Int = messageDao.count()

    suspend fun clearAllData() {
        messageDao.deleteAll()
        conversationDao.deleteAll()
    }

    private suspend fun handleNewMessage(data: JsonObject) {
        try {
            val msg = json.decodeFromJsonElement<MessageEntity>(data)
            messageDao.insert(msg)

            // 异步缓存消息中的图片
            cacheImagesFromMessage(msg)

            // 弹出通知
            val existing = conversationDao.getConversation(msg.platform, msg.selfId, msg.channelId)
            notificationHelper.showMessageNotification(msg, existing?.displayName, muted = existing?.muted == true)

            // 更新会话
            if (existing != null) {
                conversationDao.incrementUnread(
                    msg.platform, msg.selfId, msg.channelId,
                    lastMessage = msg.content,
                    lastMessageAt = msg.receivedAt,
                    lastNickname = msg.nickname,
                )
            } else {
                conversationDao.insert(ConversationEntity(
                    platform = msg.platform,
                    selfId = msg.selfId,
                    channelId = msg.channelId,
                    chatType = msg.chatType ?: "group",
                    lastMessage = msg.content,
                    lastMessageAt = msg.receivedAt,
                    lastNickname = msg.nickname,
                    unreadCount = 1,
                ))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Handle new message failed: ${e.message}")
        }
    }

    private suspend fun handleChannelUpdate(event: WsEvent.ChannelUpdate) {
        val existing = conversationDao.getConversation(event.platform, event.selfId, event.channelId)
        if (existing == null) {
            conversationDao.insert(ConversationEntity(
                platform = event.platform,
                selfId = event.selfId,
                channelId = event.channelId,
                chatType = event.chatType,
            ))
        }
    }

    private suspend fun handleMessageRecalled(event: WsEvent.MessageRecalled) {
        if (event.id <= 0) return
        markMessageRecalled(event.id, event.recalledAt, event.recalledBy)
    }

    private suspend fun markMessageRecalled(id: Int, recalledAt: Long, recalledBy: String?) {
        val msg = messageDao.getById(id)
        messageDao.markRecalled(id, recalledAt, recalledBy)
        if (msg != null) {
            val latest = messageDao.getMessages(msg.platform, msg.selfId, msg.channelId, 1).firstOrNull()
            if (latest?.id == id) {
                val existing = conversationDao.getConversation(msg.platform, msg.selfId, msg.channelId)
                if (existing != null) {
                    conversationDao.insert(existing.copy(
                        lastMessage = "消息已撤回",
                        lastMessageAt = msg.receivedAt,
                        lastNickname = msg.nickname,
                    ))
                }
            }
        }
    }

    private suspend fun updateConversationsFromMessages(messages: List<MessageEntity>, platform: String, selfId: String) {
        // 按频道分组，更新会话的最后消息
        val grouped = messages.groupBy { it.channelId }
        for ((channelId, msgs) in grouped) {
            val latest = msgs.maxByOrNull { it.receivedAt } ?: continue
            val existing = conversationDao.getConversation(platform, selfId, channelId)
            if (existing != null) {
                if (latest.receivedAt > existing.lastMessageAt) {
                    conversationDao.insert(existing.copy(
                        lastMessage = latest.content,
                        lastMessageAt = latest.receivedAt,
                        lastNickname = latest.nickname,
                        chatType = latest.chatType ?: existing.chatType,
                    ))
                }
            } else {
                conversationDao.insert(ConversationEntity(
                    platform = platform,
                    selfId = selfId,
                    channelId = channelId,
                    chatType = latest.chatType ?: "group",
                    lastMessage = latest.content,
                    lastMessageAt = latest.receivedAt,
                    lastNickname = latest.nickname,
                ))
            }
        }
    }

    private fun cacheImagesFromMessage(msg: MessageEntity) {
        val urls = extractImageUrls(msg.content)
        if (urls.isNotEmpty()) {
            imageCacheManager.cacheAll(urls)
        }
    }

    private fun cacheImagesFromMessages(messages: List<MessageEntity>) {
        val urls = messages.flatMap { msg ->
            extractImageUrls(msg.content)
        }
        if (urls.isNotEmpty()) {
            imageCacheManager.cacheAll(urls)
        }
    }
}

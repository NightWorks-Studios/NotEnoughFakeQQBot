package xyz.lisfox.big.notenoughfakeqqbot.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import xyz.lisfox.big.notenoughfakeqqbot.App
import xyz.lisfox.big.notenoughfakeqqbot.data.model.MessageEntity
import xyz.lisfox.big.notenoughfakeqqbot.data.model.KeyboardConfig
import xyz.lisfox.big.notenoughfakeqqbot.data.model.SendTextResult

data class ChatUiState(
    val messages: List<MessageEntity> = emptyList(),
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val sendingState: SendingState = SendingState.Idle,
    val botName: String? = null,
    val botAvatar: String? = null,
    val uploadingImage: Boolean = false,
)

sealed class SendingState {
    data object Idle : SendingState()
    data object Sending : SendingState()
    data class Success(val result: SendTextResult) : SendingState()
    data class Error(val message: String) : SendingState()
}

class ChatViewModel : ViewModel() {
    private val repo = App.instance.messageRepository

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    private var platform = ""
    private var selfId = ""
    private var channelId = ""
    private var chatType: String? = null

    fun init(platform: String, selfId: String, channelId: String, chatType: String?) {
        this.platform = platform
        this.selfId = selfId
        this.channelId = channelId
        this.chatType = chatType

        // 获取 Bot 昵称和头像
        _uiState.update { it.copy(botName = "Bot") }

        // 标记已读
        viewModelScope.launch {
            repo.markConversationRead(platform, selfId, channelId)
        }

        // 从服务端获取 Bot 信息（昵称+头像）
        viewModelScope.launch {
            try {
                val response = xyz.lisfox.big.notenoughfakeqqbot.data.api.ApiClient.api.listBots()
                val bot = response.data?.find { it.platform == platform && it.selfId == selfId }
                if (bot != null) {
                    _uiState.update {
                        it.copy(
                            botName = bot.username ?: bot.sid ?: bot.selfId,
                            botAvatar = bot.avatar,
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        // 观察本地消息
        viewModelScope.launch {
            repo.observeMessages(platform, selfId, channelId).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }

        // 首次加载：确保有数据
        viewModelScope.launch {
            val local = repo.getMessages(platform, selfId, channelId, 50)
            if (local.isEmpty()) {
                // 从服务器拉取
                loadMoreMessages()
            }
        }
    }

    fun loadMoreMessages() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val currentMessages = _uiState.value.messages
            val before = currentMessages.minOfOrNull { it.receivedAt } ?: System.currentTimeMillis()
            val older = repo.loadMoreMessages(platform, selfId, channelId, before, 50)
            _uiState.update {
                it.copy(
                    isLoadingMore = false,
                    hasMore = older.isNotEmpty(),
                )
            }
        }
    }

    fun sendMessage(content: String, messageType: String = "text", keyboard: KeyboardConfig? = null) {
        if (content.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(sendingState = SendingState.Sending) }
            try {
                if (keyboard != null && messageType != "markdown") {
                    throw Exception("仅 Markdown 消息支持按钮")
                }
                val result = repo.sendText(platform, selfId, channelId, content, chatType, messageType, keyboard)
                _uiState.update { it.copy(sendingState = SendingState.Success(result)) }
                // 同步最新消息
                repo.syncAll(platform, selfId)
            } catch (e: Exception) {
                _uiState.update { it.copy(sendingState = SendingState.Error(e.message ?: "发送失败")) }
            }
        }
    }

    fun clearSendingState() {
        _uiState.update { it.copy(sendingState = SendingState.Idle) }
    }

    fun markRead() {
        viewModelScope.launch {
            repo.markConversationRead(platform, selfId, channelId)
        }
    }

    fun recallMessage(message: MessageEntity) {
        viewModelScope.launch {
            try {
                repo.recallMessage(message)
            } catch (e: Exception) {
                _uiState.update { it.copy(sendingState = SendingState.Error("撤回失败: ${e.message}")) }
            }
        }
    }

    /**
     * 上传图片并直接发送到当前频道
     */
    fun uploadImage(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(uploadingImage = true) }
            try {
                val context = App.instance
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: throw Exception("无法读取图片")
                inputStream.close()

                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val ext = when {
                    mimeType.contains("png") -> "png"
                    mimeType.contains("webp") -> "webp"
                    mimeType.contains("gif") -> "gif"
                    else -> "jpg"
                }

                val requestBody = bytes.toRequestBody(mimeType.toMediaType())
                val filePart = MultipartBody.Part.createFormData("file", "image.$ext", requestBody)

                val response = xyz.lisfox.big.notenoughfakeqqbot.data.api.ApiClient.api.uploadImage(
                    file = filePart,
                    platform = platform,
                    selfId = selfId,
                    channelId = channelId,
                    chatType = chatType ?: "group",
                )
                if (response.code == 0) {
                    _uiState.update { it.copy(sendingState = SendingState.Success(SendTextResult(mode = "proactive"))) }
                    // 同步最新消息
                    repo.syncAll(platform, selfId)
                } else {
                    _uiState.update { it.copy(sendingState = SendingState.Error(response.message)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(sendingState = SendingState.Error("发送图片失败: ${e.message}")) }
            } finally {
                _uiState.update { it.copy(uploadingImage = false) }
            }
        }
    }
}

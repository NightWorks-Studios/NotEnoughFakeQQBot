package xyz.lisfox.big.notenoughfakeqqbot.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import xyz.lisfox.big.notenoughfakeqqbot.App
import xyz.lisfox.big.notenoughfakeqqbot.data.model.BotInfo
import xyz.lisfox.big.notenoughfakeqqbot.data.model.ConversationEntity

class ConversationListViewModel : ViewModel() {
    private val repo = App.instance.messageRepository

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    fun observeConversations(platform: String, selfId: String): Flow<List<ConversationEntity>> {
        return repo.observeConversations(platform, selfId)
    }

    fun refresh(bot: BotInfo?) {
        if (bot == null) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repo.syncAll(bot.platform, bot.selfId)
                repo.refreshConversations(bot.platform, bot.selfId)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun pinConversation(conv: ConversationEntity, pinned: Boolean) {
        viewModelScope.launch {
            repo.pinConversation(conv.platform, conv.selfId, conv.channelId, pinned)
        }
    }

    fun deleteConversation(conv: ConversationEntity) {
        viewModelScope.launch {
            repo.deleteConversation(conv.platform, conv.selfId, conv.channelId)
        }
    }

    fun setDisplayName(conv: ConversationEntity, name: String?) {
        viewModelScope.launch {
            repo.setConversationDisplayName(conv.platform, conv.selfId, conv.channelId, name)
        }
    }
}

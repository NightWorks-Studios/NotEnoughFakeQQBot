package xyz.lisfox.big.notenoughfakeqqbot.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import xyz.lisfox.big.notenoughfakeqqbot.App
import xyz.lisfox.big.notenoughfakeqqbot.data.api.ApiClient
import xyz.lisfox.big.notenoughfakeqqbot.data.model.BotInfo
import xyz.lisfox.big.notenoughfakeqqbot.data.websocket.WsConnectionState
import xyz.lisfox.big.notenoughfakeqqbot.data.websocket.WsEvent

class MainViewModel : ViewModel() {
    private val app = App.instance
    private val prefs = app.preferences
    private val repo = app.messageRepository
    private val wsManager = app.wsManager

    private val _bots = MutableStateFlow<List<BotInfo>>(emptyList())
    val bots: StateFlow<List<BotInfo>> = _bots

    private val _currentBot = MutableStateFlow<BotInfo?>(null)
    val currentBot: StateFlow<BotInfo?> = _currentBot

    val connectionState: StateFlow<WsConnectionState> = wsManager.connectionState

    val totalUnread: StateFlow<Int> = _currentBot.flatMapLatest { bot ->
        if (bot != null) repo.observeTotalUnread(bot.platform, bot.selfId)
        else flowOf(0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        // 监听 Bot 状态变化
        viewModelScope.launch {
            wsManager.events.collect { event ->
                if (event is WsEvent.BotStatus) {
                    _bots.update { list ->
                        list.map {
                            if (it.platform == event.platform && it.selfId == event.selfId)
                                it.copy(status = event.status)
                            else it
                        }
                    }
                }
            }
        }

        // 初始化连接
        viewModelScope.launch {
            prefs.serverUrl.combine(prefs.token) { url, token -> url to token }
                .first()
                .let { (url, token) ->
                    if (url.isNotBlank()) {
                        initConnection(url, token)
                    }
                }
        }
    }

    fun initConnection(serverUrl: String, token: String) {
        viewModelScope.launch {
            ApiClient.configure(serverUrl, token)
            wsManager.connect(serverUrl, token)
            refreshBots()
        }
    }

    fun refreshBots() {
        viewModelScope.launch {
            try {
                val response = ApiClient.api.listBots()
                val bots = response.data ?: emptyList()
                _bots.value = bots

                // 恢复上次选择的 Bot，或选第一个
                val savedPlatform = prefs.currentPlatform.first()
                val savedSelfId = prefs.currentSelfId.first()
                val saved = bots.find { it.platform == savedPlatform && it.selfId == savedSelfId }
                selectBot(saved ?: bots.firstOrNull())
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun selectBot(bot: BotInfo?) {
        _currentBot.value = bot
        if (bot != null) {
            viewModelScope.launch {
                prefs.setCurrentBot(bot.platform, bot.selfId)
                // 触发同步
                repo.syncAll(bot.platform, bot.selfId)
                repo.refreshConversations(bot.platform, bot.selfId)
            }
        }
    }
}

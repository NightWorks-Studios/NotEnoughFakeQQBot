package xyz.lisfox.big.notenoughfakeqqbot.data.websocket

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.concurrent.TimeUnit

enum class WsConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED
}

sealed class WsEvent {
    data class NewMessage(val data: JsonObject) : WsEvent()
    data class MessageRecalled(
        val platform: String,
        val selfId: String,
        val channelId: String,
        val id: Int,
        val recalledAt: Long,
        val recalledBy: String?,
    ) : WsEvent()
    data class BotStatus(val platform: String, val selfId: String, val status: String) : WsEvent()
    data class ChannelUpdate(val platform: String, val selfId: String, val channelId: String, val chatType: String) : WsEvent()
}

class WebSocketManager {
    companion object {
        private const val TAG = "WsManager"
        private const val RECONNECT_DELAY_MS = 3000L
        private const val PING_INTERVAL_MS = 30000L
    }

    private var ws: WebSocket? = null
    private var client: OkHttpClient? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null
    private var pingJob: Job? = null
    private var serverUrl: String = ""
    private var token: String = ""
    private var shouldReconnect = false

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _connectionState = MutableStateFlow(WsConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WsConnectionState> = _connectionState

    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 100)
    val events: SharedFlow<WsEvent> = _events

    fun connect(serverUrl: String, token: String) {
        this.serverUrl = serverUrl.trimEnd('/')
        this.token = token
        shouldReconnect = true
        doConnect()
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()
        pingJob?.cancel()
        ws?.close(1000, "client disconnect")
        ws = null
        _connectionState.value = WsConnectionState.DISCONNECTED
    }

    private fun doConnect() {
        if (_connectionState.value == WsConnectionState.CONNECTING) return
        _connectionState.value = WsConnectionState.CONNECTING

        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

        val wsUrl = serverUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://") + "/ws"

        Log.i(TAG, "Connecting to: $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .apply {
                if (token.isNotBlank()) header("Authorization", "Bearer $token")
            }
            .build()

        ws = client!!.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected")
                _connectionState.value = WsConnectionState.CONNECTED
                startPing()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code $reason")
                _connectionState.value = WsConnectionState.DISCONNECTED
                pingJob?.cancel()
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure: ${t.message}")
                _connectionState.value = WsConnectionState.DISCONNECTED
                pingJob?.cancel()
                scheduleReconnect()
            }
        })
    }

    private fun handleMessage(text: String) {
        try {
            val obj = json.parseToJsonElement(text).jsonObject
            val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return
            val data = obj["data"]?.jsonObject

            when (type) {
                "message" -> {
                    if (data != null) {
                        _events.tryEmit(WsEvent.NewMessage(data))
                    }
                }
                "bot-status" -> {
                    if (data != null) {
                        _events.tryEmit(WsEvent.BotStatus(
                            platform = data["platform"]?.jsonPrimitive?.contentOrNull ?: "",
                            selfId = data["selfId"]?.jsonPrimitive?.contentOrNull ?: "",
                            status = data["status"]?.jsonPrimitive?.contentOrNull ?: "",
                        ))
                    }
                }
                "message-recalled" -> {
                    if (data != null) {
                        _events.tryEmit(WsEvent.MessageRecalled(
                            platform = data["platform"]?.jsonPrimitive?.contentOrNull ?: "",
                            selfId = data["selfId"]?.jsonPrimitive?.contentOrNull ?: "",
                            channelId = data["channelId"]?.jsonPrimitive?.contentOrNull ?: "",
                            id = data["id"]?.jsonPrimitive?.intOrNull ?: 0,
                            recalledAt = data["recalledAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis(),
                            recalledBy = data["recalledBy"]?.jsonPrimitive?.contentOrNull,
                        ))
                    }
                }
                "channel-update" -> {
                    if (data != null) {
                        _events.tryEmit(WsEvent.ChannelUpdate(
                            platform = data["platform"]?.jsonPrimitive?.contentOrNull ?: "",
                            selfId = data["selfId"]?.jsonPrimitive?.contentOrNull ?: "",
                            channelId = data["channelId"]?.jsonPrimitive?.contentOrNull ?: "",
                            chatType = data["chatType"]?.jsonPrimitive?.contentOrNull ?: "group",
                        ))
                    }
                }
                "pong" -> {
                    Log.d(TAG, "pong received")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse WS message: ${e.message}")
        }
    }

    private fun startPing() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                ws?.send("""{"type":"ping"}""")
            }
        }
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (shouldReconnect) {
                Log.i(TAG, "Reconnecting...")
                doConnect()
            }
        }
    }
}

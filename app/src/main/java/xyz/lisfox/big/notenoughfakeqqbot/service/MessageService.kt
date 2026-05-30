package xyz.lisfox.big.notenoughfakeqqbot.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import xyz.lisfox.big.notenoughfakeqqbot.App
import xyz.lisfox.big.notenoughfakeqqbot.MainActivity
import xyz.lisfox.big.notenoughfakeqqbot.R
import xyz.lisfox.big.notenoughfakeqqbot.data.api.ApiClient
import xyz.lisfox.big.notenoughfakeqqbot.data.websocket.WsConnectionState

/**
 * 前台 Service 保活 WebSocket 连接
 * 确保 APP 在后台时仍能接收消息推送
 */
class MessageService : Service() {
    companion object {
        private const val TAG = "MessageService"
        private const val FOREGROUND_NOTIFICATION_ID = 1001
        private const val CHANNEL_ID_SERVICE = "fakeqqbot_service"

        fun start(context: Context) {
            val intent = Intent(context, MessageService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MessageService::class.java))
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createServiceChannel()
        startForeground(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification())
        acquireWakeLock()
        ensureConnection()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        scope.cancel()
    }

    private fun createServiceChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                "消息服务",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "保持消息连接"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setContentTitle("消息服务运行中")
            .setContentText("保持连接以接收新消息")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "FakeQQBot::MessageService"
        ).apply {
            acquire(Long.MAX_VALUE)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    /**
     * 确保 WebSocket 连接存活
     */
    private fun ensureConnection() {
        scope.launch {
            val app = App.instance
            val prefs = app.preferences
            val url = prefs.serverUrl.first()
            val token = prefs.token.first()

            if (url.isNotBlank()) {
                ApiClient.configure(url, token)
                if (app.wsManager.connectionState.value != WsConnectionState.CONNECTED) {
                    app.wsManager.connect(url, token)
                }
            }

            // 监控连接状态，断开时更新通知
            app.wsManager.connectionState.collect { state ->
                val notification = buildStatusNotification(state)
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(FOREGROUND_NOTIFICATION_ID, notification)
            }
        }
    }

    private fun buildStatusNotification(state: WsConnectionState): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val statusText = when (state) {
            WsConnectionState.CONNECTED -> "已连接，等待新消息"
            WsConnectionState.CONNECTING -> "正在连接..."
            WsConnectionState.DISCONNECTED -> "连接已断开，等待重连"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setContentTitle("消息服务")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

package xyz.lisfox.big.notenoughfakeqqbot

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import xyz.lisfox.big.notenoughfakeqqbot.data.cache.ImageCacheManager
import xyz.lisfox.big.notenoughfakeqqbot.data.db.AppDatabase
import xyz.lisfox.big.notenoughfakeqqbot.data.prefs.AppPreferences
import xyz.lisfox.big.notenoughfakeqqbot.data.repository.MessageRepository
import xyz.lisfox.big.notenoughfakeqqbot.data.websocket.WebSocketManager
import xyz.lisfox.big.notenoughfakeqqbot.service.NotificationHelper

class App : Application() {
    lateinit var database: AppDatabase
        private set
    lateinit var preferences: AppPreferences
        private set
    lateinit var wsManager: WebSocketManager
        private set
    lateinit var messageRepository: MessageRepository
        private set
    lateinit var imageCacheManager: ImageCacheManager
        private set
    lateinit var notificationHelper: NotificationHelper
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
        preferences = AppPreferences(this)
        wsManager = WebSocketManager()
        imageCacheManager = ImageCacheManager(this)
        notificationHelper = NotificationHelper(this)
        messageRepository = MessageRepository(database, wsManager, imageCacheManager, notificationHelper)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "新消息",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "接收新消息通知"
            enableVibration(true)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "fakeqqbot_messages"

        @Volatile
        lateinit var instance: App
            private set
    }
}

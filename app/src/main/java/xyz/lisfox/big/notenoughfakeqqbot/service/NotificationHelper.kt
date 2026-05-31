package xyz.lisfox.big.notenoughfakeqqbot.service

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import xyz.lisfox.big.notenoughfakeqqbot.App
import xyz.lisfox.big.notenoughfakeqqbot.MainActivity
import xyz.lisfox.big.notenoughfakeqqbot.R
import xyz.lisfox.big.notenoughfakeqqbot.data.model.MessageEntity
import xyz.lisfox.big.notenoughfakeqqbot.util.extractPlainText
import xyz.lisfox.big.notenoughfakeqqbot.util.shortId

/**
 * 消息通知管理：收到新消息时弹出通知
 */
class NotificationHelper(private val context: Context) {
    companion object {
        private const val GROUP_KEY = "fakeqqbot_messages"
        private const val SUMMARY_ID = 2000
        private var nextNotificationId = 2001

        // 当前正在查看的会话（不弹通知）
        @Volatile
        private var activeConversationKey: String? = null

        fun setActiveConversation(platform: String, selfId: String, channelId: String) {
            activeConversationKey = "$platform:$selfId:$channelId"
        }

        fun clearActiveConversation() {
            activeConversationKey = null
        }
    }

    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    /**
     * 显示新消息通知
     */
    fun showMessageNotification(msg: MessageEntity, channelDisplayName: String? = null, muted: Boolean = false) {
        // 机器人自己发送的消息、本会话免打扰、用户正在查看的会话，都不弹系统通知。
        if (msg.userId == msg.selfId) return
        if (muted) return
        if (activeConversationKey == "${msg.platform}:${msg.selfId}:${msg.channelId}") return

        // 检查通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val channelTitle = channelDisplayName ?: shortId(msg.channelId)
        val senderName = msg.nickname ?: shortId(msg.userId)
        val contentText = extractPlainText(msg.content)

        // 点击通知跳转到聊天页
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_platform", msg.platform)
            putExtra("navigate_selfId", msg.selfId)
            putExtra("navigate_channelId", msg.channelId)
            putExtra("navigate_chatType", msg.chatType ?: "group")
            putExtra("navigate_title", channelTitle)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            msg.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, App.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(channelTitle)
            .setContentText("$senderName: $contentText")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$senderName: $contentText"))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .build()

        notificationManager.notify(nextNotificationId++, notification)

        // 摘要通知（分组）
        val summaryNotification = NotificationCompat.Builder(context, App.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("新消息")
            .setContentText("收到新消息")
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(SUMMARY_ID, summaryNotification)
    }

    /**
     * 清除某个频道的所有通知
     */
    fun clearChannelNotifications() {
        // 简单实现：清除所有消息通知
        notificationManager.cancelAll()
    }
}

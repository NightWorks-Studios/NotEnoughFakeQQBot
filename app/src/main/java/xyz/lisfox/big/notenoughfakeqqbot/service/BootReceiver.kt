package xyz.lisfox.big.notenoughfakeqqbot.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import xyz.lisfox.big.notenoughfakeqqbot.App

/**
 * 开机自启动：启动前台 Service 保持消息连接
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == "android.intent.action.QUICKBOOT_POWERON") {
            // 检查是否已配置
            val isConfigured = runBlocking {
                App.instance.preferences.isConfigured.first()
            }
            if (isConfigured) {
                MessageService.start(context)
            }
        }
    }
}

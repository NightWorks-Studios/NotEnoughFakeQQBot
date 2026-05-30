package xyz.lisfox.big.notenoughfakeqqbot.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import xyz.lisfox.big.notenoughfakeqqbot.App
import xyz.lisfox.big.notenoughfakeqqbot.data.api.ApiClient
import xyz.lisfox.big.notenoughfakeqqbot.data.model.BotInfo
import xyz.lisfox.big.notenoughfakeqqbot.data.websocket.WsConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentBot: BotInfo?,
    bots: List<BotInfo>,
    connectionState: WsConnectionState,
    onSelectBot: (BotInfo) -> Unit,
    onLogout: () -> Unit,
) {
    val app = App.instance
    val scope = rememberCoroutineScope()
    var messageCount by remember { mutableIntStateOf(0) }
    var showBotPicker by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        messageCount = app.messageRepository.getLocalMessageCount()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部标题
        Surface(
            color = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            // Bot 信息卡片
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "当前 Bot",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        if (currentBot != null) {
                            Text(
                                currentBot.username ?: "${currentBot.platform}:${currentBot.selfId}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val statusColor = when (currentBot.status) {
                                    "online" -> MaterialTheme.colorScheme.primary
                                    "offline", "disconnect" -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.tertiary
                                }
                                val statusText = when (currentBot.status) {
                                    "online" -> "在线"
                                    "offline" -> "离线"
                                    "disconnect" -> "已断开"
                                    "connect" -> "连接中"
                                    "reconnect" -> "重连中"
                                    else -> currentBot.status
                                }
                                Icon(
                                    Icons.Filled.Circle,
                                    contentDescription = null,
                                    modifier = Modifier.size(8.dp),
                                    tint = statusColor,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    statusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = statusColor,
                                )
                            }
                        } else {
                            Text(
                                "未选择",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                        if (bots.size > 1) {
                            Spacer(modifier = Modifier.height(12.dp))
                            FilledTonalButton(
                                onClick = { showBotPicker = true },
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text("切换 Bot")
                            }
                        }
                    }
                }
            }

            // 连接状态
            item {
                SettingsItem(
                    icon = Icons.Filled.Wifi,
                    title = "连接状态",
                    subtitle = when (connectionState) {
                        WsConnectionState.CONNECTED -> "已连接"
                        WsConnectionState.CONNECTING -> "连接中..."
                        WsConnectionState.DISCONNECTED -> "未连接"
                    },
                    statusColor = when (connectionState) {
                        WsConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                        WsConnectionState.CONNECTING -> MaterialTheme.colorScheme.tertiary
                        WsConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.error
                    },
                )
            }

            // 服务器地址
            item {
                SettingsItem(
                    icon = Icons.Filled.Cloud,
                    title = "服务器",
                    subtitle = ApiClient.baseUrl,
                )
            }

            // 本地缓存
            item {
                SettingsItem(
                    icon = Icons.Filled.Storage,
                    title = "本地消息缓存",
                    subtitle = "$messageCount 条消息",
                    onClick = { showClearDialog = true },
                )
            }

            // 后台保活设置
            item {
                val context = LocalContext.current
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val isIgnoring = pm.isIgnoringBatteryOptimizations(context.packageName)

                SettingsItem(
                    icon = Icons.Filled.BatteryChargingFull,
                    title = "后台保活",
                    subtitle = if (isIgnoring) "已忽略电池优化" else "点击关闭电池优化以保持消息推送",
                    statusColor = if (isIgnoring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    onClick = {
                        if (!isIgnoring) {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } else {
                            // 已经忽略，打开电池优化设置页
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        }
                    },
                )
            }

            // 自启动权限引导
            item {
                val context = LocalContext.current
                var showAutoStartDialog by remember { mutableStateOf(false) }

                SettingsItem(
                    icon = Icons.Filled.RocketLaunch,
                    title = "自启动权限",
                    subtitle = "部分手机需手动开启以保证后台运行",
                    onClick = { showAutoStartDialog = true },
                )

                if (showAutoStartDialog) {
                    AlertDialog(
                        onDismissRequest = { showAutoStartDialog = false },
                        title = { Text("开启自启动权限") },
                        text = {
                            Text("为确保消息及时推送，请在系统设置中允许本应用自启动。\n\n" +
                                    "不同品牌手机路径不同：\n" +
                                    "- 小米：设置 > 应用设置 > 自启动管理\n" +
                                    "- 华为：设置 > 应用 > 启动管理\n" +
                                    "- OPPO/vivo：设置 > 电池 > 后台耗电管理\n" +
                                    "- 三星：设置 > 电池 > 后台使用限制\n\n" +
                                    "点击\"前往设置\"将打开应用详情页。")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showAutoStartDialog = false
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }) { Text("前往设置") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAutoStartDialog = false }) { Text("稍后") }
                        },
                    )
                }
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                )
            }

            // 退出登录
            item {
                SettingsItem(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = "退出登录",
                    subtitle = "清除配置并返回登录页",
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = {
                        scope.launch {
                            app.wsManager.disconnect()
                            ApiClient.reset()
                            app.preferences.clear()
                            app.messageRepository.clearAllData()
                            onLogout()
                        }
                    },
                )
            }

            // 关于
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "NotEnoughFakeQQBot v1.0",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Bot 选择对话框
    if (showBotPicker) {
        AlertDialog(
            onDismissRequest = { showBotPicker = false },
            title = { Text("选择 Bot") },
            text = {
                Column {
                    bots.forEach { bot ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectBot(bot)
                                    showBotPicker = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = bot == currentBot,
                                onClick = {
                                    onSelectBot(bot)
                                    showBotPicker = false
                                },
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    bot.username ?: "${bot.platform}:${bot.selfId}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    "${bot.platform}:${bot.selfId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBotPicker = false }) { Text("取消") }
            },
        )
    }

    // 清除缓存对话框
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清除缓存") },
            text = { Text("确定要清除所有本地消息缓存吗？下次打开会重新同步。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        app.messageRepository.clearAllData()
                        messageCount = 0
                        showClearDialog = false
                    }
                }) { Text("清除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    titleColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    statusColor: androidx.compose.ui.graphics.Color? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = statusColor ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

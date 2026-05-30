package xyz.lisfox.big.notenoughfakeqqbot

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import xyz.lisfox.big.notenoughfakeqqbot.data.model.ConversationEntity
import xyz.lisfox.big.notenoughfakeqqbot.service.MessageService
import xyz.lisfox.big.notenoughfakeqqbot.service.NotificationHelper
import xyz.lisfox.big.notenoughfakeqqbot.ui.chat.ChatScreen
import xyz.lisfox.big.notenoughfakeqqbot.ui.contacts.ContactsScreen
import xyz.lisfox.big.notenoughfakeqqbot.ui.conversation.ConversationListScreen
import xyz.lisfox.big.notenoughfakeqqbot.ui.login.LoginScreen
import xyz.lisfox.big.notenoughfakeqqbot.ui.main.MainBottomBar
import xyz.lisfox.big.notenoughfakeqqbot.ui.main.MainViewModel
import xyz.lisfox.big.notenoughfakeqqbot.ui.search.SearchScreen
import xyz.lisfox.big.notenoughfakeqqbot.ui.settings.SettingsScreen
import xyz.lisfox.big.notenoughfakeqqbot.ui.theme.NotEnoughFakeQQBotTheme
import xyz.lisfox.big.notenoughfakeqqbot.util.shortId
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    // 通知点击传入的导航参数
    private var pendingNavPlatform: String? = null
    private var pendingNavSelfId: String? = null
    private var pendingNavChannelId: String? = null
    private var pendingNavChatType: String? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val isConfigured = runBlocking {
            App.instance.preferences.isConfigured.first()
        }

        // 请求通知权限
        requestNotificationPermission()

        // 处理通知点击 Intent
        handleNotificationIntent(intent)

        // 启动前台 Service
        if (isConfigured) {
            MessageService.start(this)
        }

        setContent {
            NotEnoughFakeQQBotTheme {
                AppNavigation(
                    startFromLogin = !isConfigured,
                    pendingNavPlatform = pendingNavPlatform,
                    pendingNavSelfId = pendingNavSelfId,
                    pendingNavChannelId = pendingNavChannelId,
                    pendingNavChatType = pendingNavChatType,
                    onPendingNavConsumed = {
                        pendingNavPlatform = null
                        pendingNavSelfId = null
                        pendingNavChannelId = null
                        pendingNavChatType = null
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        intent?.let {
            pendingNavPlatform = it.getStringExtra("navigate_platform")
            pendingNavSelfId = it.getStringExtra("navigate_selfId")
            pendingNavChannelId = it.getStringExtra("navigate_channelId")
            pendingNavChatType = it.getStringExtra("navigate_chatType")
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun AppNavigation(
    startFromLogin: Boolean,
    pendingNavPlatform: String?,
    pendingNavSelfId: String?,
    pendingNavChannelId: String?,
    pendingNavChatType: String?,
    onPendingNavConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = viewModel()
    val currentBot by mainViewModel.currentBot.collectAsState()
    val bots by mainViewModel.bots.collectAsState()
    val connectionState by mainViewModel.connectionState.collectAsState()
    val totalUnread by mainViewModel.totalUnread.collectAsState()

    val startDestination = if (startFromLogin) "login" else "main"

    // 处理通知点击导航
    LaunchedEffect(pendingNavPlatform, pendingNavChannelId) {
        if (!pendingNavPlatform.isNullOrBlank() && !pendingNavChannelId.isNullOrBlank()) {
            val title = shortId(pendingNavChannelId)
            val encodedChannelId = URLEncoder.encode(pendingNavChannelId, "UTF-8")
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val chatType = pendingNavChatType ?: "group"
            navController.navigate("chat/$pendingNavPlatform/$pendingNavSelfId/$encodedChannelId/$chatType/$encodedTitle") {
                launchSingleTop = true
            }
            onPendingNavConsumed()
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        // 登录页
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    mainViewModel.initConnection(
                        xyz.lisfox.big.notenoughfakeqqbot.data.api.ApiClient.baseUrl,
                        xyz.lisfox.big.notenoughfakeqqbot.data.api.ApiClient.token,
                    )
                    // 启动前台 Service
                    MessageService.start(App.instance)
                    navController.navigate("main") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        // 主页（带底部导航）
        composable("main") {
            val tabNavController = rememberNavController()

            // 离开聊天页时清除 activeChannelId
            DisposableEffect(Unit) {
                onDispose { NotificationHelper.activeChannelId = null }
            }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    MainBottomBar(navController = tabNavController, unreadCount = totalUnread)
                },
            ) { padding ->
                NavHost(
                    navController = tabNavController,
                    startDestination = "conversations",
                    modifier = Modifier.padding(padding),
                ) {
                    composable("conversations") {
                        NotificationHelper.activeChannelId = null
                        ConversationListScreen(
                            currentBot = currentBot,
                            onConversationClick = { conv ->
                                navigateToChat(navController, conv)
                            },
                            onSearchClick = {
                                val p = currentBot?.platform ?: ""
                                val s = currentBot?.selfId ?: ""
                                navController.navigate("search/$p/$s")
                            },
                        )
                    }
                    composable("contacts") {
                        NotificationHelper.activeChannelId = null
                        ContactsScreen(
                            currentBot = currentBot,
                            onContactClick = { conv ->
                                navigateToChat(navController, conv)
                            },
                        )
                    }
                    composable("profile") {
                        NotificationHelper.activeChannelId = null
                        SettingsScreen(
                            currentBot = currentBot,
                            bots = bots,
                            connectionState = connectionState,
                            onSelectBot = { mainViewModel.selectBot(it) },
                            onLogout = {
                                MessageService.stop(App.instance)
                                navController.navigate("login") {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                        )
                    }
                }
            }
        }

        // 聊天页
        composable(
            route = "chat/{platform}/{selfId}/{channelId}/{chatType}/{title}",
            arguments = listOf(
                navArgument("platform") { type = NavType.StringType },
                navArgument("selfId") { type = NavType.StringType },
                navArgument("channelId") { type = NavType.StringType },
                navArgument("chatType") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val platform = backStackEntry.arguments?.getString("platform") ?: ""
            val selfId = backStackEntry.arguments?.getString("selfId") ?: ""
            val channelId = URLDecoder.decode(backStackEntry.arguments?.getString("channelId") ?: "", "UTF-8")
            val chatType = backStackEntry.arguments?.getString("chatType")
            val title = URLDecoder.decode(backStackEntry.arguments?.getString("title") ?: "", "UTF-8")

            // 设置当前活跃频道（抑制通知）
            DisposableEffect(channelId) {
                NotificationHelper.activeChannelId = channelId
                onDispose { NotificationHelper.activeChannelId = null }
            }

            ChatScreen(
                platform = platform,
                selfId = selfId,
                channelId = channelId,
                chatType = chatType,
                title = title,
                onBack = { navController.popBackStack() },
                onImageClick = { url ->
                    // 图片全屏查看已在 ChatScreen 内部处理
                },
            )
        }

        // 搜索页
        composable(
            route = "search/{platform}/{selfId}",
            arguments = listOf(
                navArgument("platform") { type = NavType.StringType },
                navArgument("selfId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val platform = backStackEntry.arguments?.getString("platform")?.takeIf { it.isNotBlank() }
            val selfId = backStackEntry.arguments?.getString("selfId")?.takeIf { it.isNotBlank() }

            SearchScreen(
                platform = platform,
                selfId = selfId,
                onBack = { navController.popBackStack() },
                onMessageClick = { msg ->
                    val conv = ConversationEntity(
                        platform = msg.platform,
                        selfId = msg.selfId,
                        channelId = msg.channelId,
                        chatType = msg.chatType ?: "group",
                        lastNickname = msg.nickname,
                    )
                    navigateToChat(navController, conv)
                },
            )
        }
    }
}

private fun navigateToChat(navController: androidx.navigation.NavController, conv: ConversationEntity) {
    // 标题逻辑：有备注用备注，否则一律用短ID
    val title = conv.displayName?.takeIf { it.isNotBlank() } ?: shortId(conv.channelId)
    val encodedChannelId = URLEncoder.encode(conv.channelId, "UTF-8")
    val encodedTitle = URLEncoder.encode(title, "UTF-8")
    navController.navigate("chat/${conv.platform}/${conv.selfId}/$encodedChannelId/${conv.chatType}/$encodedTitle")
}

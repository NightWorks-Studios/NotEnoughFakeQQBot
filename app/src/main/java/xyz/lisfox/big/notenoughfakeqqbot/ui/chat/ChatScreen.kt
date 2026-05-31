package xyz.lisfox.big.notenoughfakeqqbot.ui.chat

import android.Manifest
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn as AndroidOptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import xyz.lisfox.big.notenoughfakeqqbot.App
import xyz.lisfox.big.notenoughfakeqqbot.data.model.KeyboardConfig
import xyz.lisfox.big.notenoughfakeqqbot.data.model.KeyboardRow
import xyz.lisfox.big.notenoughfakeqqbot.data.model.MessageEntity
import xyz.lisfox.big.notenoughfakeqqbot.data.model.MessageButton
import xyz.lisfox.big.notenoughfakeqqbot.data.websocket.WsConnectionState
import xyz.lisfox.big.notenoughfakeqqbot.util.ContentSegment
import xyz.lisfox.big.notenoughfakeqqbot.util.extractPlainText
import xyz.lisfox.big.notenoughfakeqqbot.util.formatTime
import xyz.lisfox.big.notenoughfakeqqbot.util.parseContentSegments
import xyz.lisfox.big.notenoughfakeqqbot.util.shortId
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    platform: String,
    selfId: String,
    channelId: String,
    chatType: String?,
    title: String,
    onBack: () -> Unit,
    onImageClick: (String) -> Unit = {},
    viewModel: ChatViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    var draft by remember { mutableStateOf("") }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    var showMorePanel by remember { mutableStateOf(false) }
    var openMorePanelAfterImeHidden by remember { mutableStateOf(false) }
    var messageType by remember { mutableStateOf("text") }
    var keyboardConfig by remember { mutableStateOf<KeyboardConfig?>(null) }
    var showButtonDialog by remember { mutableStateOf(false) }

    // 快捷短语
    var showQuickPhrases by remember { mutableStateOf(false) }
    val quickPhrases by App.instance.preferences.quickPhrases.collectAsState(initial = emptySet())
    var newPhraseInput by remember { mutableStateOf("") }

    // 图片发送预览
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }

    // 网络连接状态
    val connectionState by App.instance.wsManager.connectionState.collectAsState()

    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingImageUri = uri // 先预览，不直接发送
        }
    }

    LaunchedEffect(platform, selfId, channelId) {
        viewModel.init(platform, selfId, channelId, chatType)
    }

    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            showMorePanel = false
        } else if (openMorePanelAfterImeHidden) {
            openMorePanelAfterImeHidden = false
            showMorePanel = true
        }
    }

    // 进入聊天页自动标记已读
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            viewModel.markRead()
        }
    }

    // 新消息到达时自动滚动到底部
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty() && listState.firstVisibleItemIndex < 3) {
            listState.animateScrollToItem(0)
        }
    }

    // 图片全屏查看器
    if (fullscreenImageUrl != null) {
        ImageViewerDialog(
            imageUrl = fullscreenImageUrl!!,
            onDismiss = { fullscreenImageUrl = null },
        )
    }

    // 图片发送预览确认对话框
    if (pendingImageUri != null) {
        ImagePreviewDialog(
            uri = pendingImageUri!!,
            onConfirm = {
                viewModel.uploadImage(pendingImageUri!!)
                pendingImageUri = null
            },
            onDismiss = { pendingImageUri = null },
        )
    }

    if (showButtonDialog) {
        ButtonConfigDialog(
            initial = keyboardConfig,
            onDismiss = { showButtonDialog = false },
            onSave = { config ->
                keyboardConfig = config
                messageType = "markdown"
                showButtonDialog = false
                showMorePanel = false
            },
        )
    }

    // 快捷短语 BottomSheet
    if (showQuickPhrases) {
        ModalBottomSheet(
            onDismissRequest = { showQuickPhrases = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    "快捷短语",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                // 添加新短语
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = newPhraseInput,
                        onValueChange = { newPhraseInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("添加新短语...", style = MaterialTheme.typography.bodyMedium) },
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (newPhraseInput.isNotBlank()) {
                                scope.launch {
                                    App.instance.preferences.addQuickPhrase(newPhraseInput.trim())
                                    newPhraseInput = ""
                                }
                            }
                        },
                        enabled = newPhraseInput.isNotBlank(),
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "添加", modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (quickPhrases.isEmpty()) {
                    Text(
                        "暂无快捷短语，添加后可快速插入",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    quickPhrases.forEach { phrase ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable {
                                    draft = draft + phrase
                                    showQuickPhrases = false
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = phrase,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            App.instance.preferences.removeQuickPhrase(phrase)
                                        }
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "删除",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = if (chatType == "c2c") "私聊" else "群聊",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                // 网络断开提示横幅
                if (connectionState != WsConnectionState.CONNECTED) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (connectionState == WsConnectionState.CONNECTING)
                            MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.WifiOff,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (connectionState == WsConnectionState.CONNECTING)
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                else MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (connectionState == WsConnectionState.CONNECTING)
                                    "正在连接..." else "网络已断开，等待重连",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (connectionState == WsConnectionState.CONNECTING)
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                else MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            ChatInputBar(
                draft = draft,
                onDraftChange = {
                    draft = it
                    openMorePanelAfterImeHidden = false
                    showMorePanel = false
                },
                onSend = {
                    if (draft.isNotBlank()) {
                        if (keyboardConfig != null && messageType != "markdown") {
                            Toast.makeText(context, "仅 Markdown 消息支持按钮", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.sendMessage(draft, messageType, keyboardConfig)
                            draft = ""
                            messageType = "text"
                            keyboardConfig = null
                            openMorePanelAfterImeHidden = false
                            showMorePanel = false
                        }
                    }
                },
                onPickImage = { imagePickerLauncher.launch("image/*") },
                onQuickPhrase = { showQuickPhrases = true },
                onMore = {
                    if (showMorePanel) {
                        openMorePanelAfterImeHidden = false
                        showMorePanel = false
                    } else {
                        keyboardController?.hide()
                        if (imeVisible) {
                            openMorePanelAfterImeHidden = true
                        } else {
                            showMorePanel = true
                        }
                    }
                },
                messageType = messageType,
                keyboardConfig = keyboardConfig,
                showMorePanel = showMorePanel,
                onClearMarkdown = {
                    if (keyboardConfig == null) messageType = "text"
                    else Toast.makeText(context, "按钮消息需要 Markdown", Toast.LENGTH_SHORT).show()
                },
                onClearButtons = { keyboardConfig = null },
                onSelectMarkdown = {
                    messageType = "markdown"
                    openMorePanelAfterImeHidden = false
                    showMorePanel = false
                },
                onConfigButtons = {
                    keyboardController?.hide()
                    showButtonDialog = true
                },
                sendingState = uiState.sendingState,
                isUploading = uiState.uploadingImage,
            )
        },
    ) { padding ->
        val messages = uiState.messages
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            state = listState,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(messages.size, key = { messages[it].id }) { index ->
                val msg = messages[index]
                val isSelf = msg.userId == selfId

                // 时间分隔符
                val showTimeDivider = if (index < messages.size - 1) {
                    val prevMsg = messages[index + 1]
                    (msg.receivedAt - prevMsg.receivedAt) > 5 * 60 * 1000
                } else {
                    true
                }

                Column {
                    if (showTimeDivider) {
                        TimeDivider(timestamp = msg.receivedAt)
                    }
                    MessageBubble(
                        message = msg,
                        isSelf = isSelf,
                        botName = uiState.botName,
                        botAvatar = uiState.botAvatar,
                        onImageClick = { url -> fullscreenImageUrl = url },
                        onCopyText = { text ->
                            clipboardManager.setText(AnnotatedString(text))
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        },
                        onSavePhrase = { text ->
                            scope.launch {
                                App.instance.preferences.addQuickPhrase(text)
                                Toast.makeText(context, "已保存为快捷短语", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onRecall = { message -> viewModel.recallMessage(message) },
                        onPlusOne = { text -> viewModel.sendMessage(text, "text", null) },
                        onAtUser = if (chatType == "group") { userId, nickname ->
                            val atTag = "<qqbot-at-user id=\"$userId\" />"
                            draft = draft + atTag
                            messageType = "markdown"
                        } else null,
                    )
                }
            }

            // 加载更多
            if (uiState.hasMore) {
                item {
                    LaunchedEffect(Unit) {
                        viewModel.loadMoreMessages()
                    }
                    if (uiState.isLoadingMore) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }

    // 发送状态提示
    val sendingState = uiState.sendingState
    if (sendingState is SendingState.Error) {
        LaunchedEffect(sendingState) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearSendingState()
        }
    }
    if (sendingState is SendingState.Success) {
        LaunchedEffect(sendingState) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearSendingState()
        }
    }
}

/**
 * 时间分隔符
 */
@Composable
private fun TimeDivider(timestamp: Long) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = formatTimeDivider(timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 11.sp,
        )
    }
}

/**
 * 格式化时间分隔符文本
 */
private fun formatTimeDivider(timestamp: Long): String {
    val now = Calendar.getInstance()
    val msgTime = Calendar.getInstance().apply { timeInMillis = timestamp }

    val isToday = now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == msgTime.get(Calendar.DAY_OF_YEAR)

    val isYesterday = run {
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        yesterday.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) &&
                yesterday.get(Calendar.DAY_OF_YEAR) == msgTime.get(Calendar.DAY_OF_YEAR)
    }

    val isSameYear = now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR)

    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

    return when {
        isToday -> timeStr
        isYesterday -> "昨天 $timeStr"
        isSameYear -> SimpleDateFormat("M月d日 HH:mm", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessageEntity,
    isSelf: Boolean,
    botName: String?,
    botAvatar: String?,
    onImageClick: (String) -> Unit,
    onCopyText: (String) -> Unit,
    onSavePhrase: (String) -> Unit,
    onRecall: (MessageEntity) -> Unit,
    onPlusOne: (String) -> Unit,
    onAtUser: ((userId: String, nickname: String?) -> Unit)? = null,
) {
    val segments = remember(message.content) { parseContentSegments(message.content) }
    val plainText = remember(message.content) { extractPlainText(message.content) }
    val keyboardConfig = remember(message.keyboardJson) { parseKeyboardConfig(message.keyboardJson) }
    val canPlusOne = remember(segments, message.messageType, message.keyboardJson, plainText) {
        message.messageType != "markdown" &&
                keyboardConfig == null &&
                segments.size == 1 &&
                segments.firstOrNull() is ContentSegment.Text &&
                plainText.isNotBlank()
    }
    var showMenu by remember { mutableStateOf(false) }
    var showButtonDetails by remember { mutableStateOf(false) }

    if (message.recalled) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isSelf) "你撤回了一条消息" else "消息已撤回",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 11.sp,
            )
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isSelf) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            onAtUser?.invoke(message.userId, message.nickname)
                        },
                    ),
            ) {
                UserAvatar(
                    avatarUrl = message.avatar,
                    nickname = message.nickname,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 260.dp),
            horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start,
        ) {
            // 昵称（仅非自己的消息显示）
            if (!isSelf) {
                Text(
                    text = message.nickname ?: shortId(message.userId),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                )
            }

            // 引用消息
            if (!message.quoteNickname.isNullOrBlank() || !message.quoteContent.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(bottom = 2.dp),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        if (!message.quoteNickname.isNullOrBlank()) {
                            Text(
                                text = message.quoteNickname,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        QuoteContentPreview(content = message.quoteContent)
                    }
                }
            }

            // 图文混编内容
            segments.forEach { segment ->
                when (segment) {
                    is ContentSegment.Text -> {
                        Box {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = if (isSelf) 16.dp else 4.dp,
                                    topEnd = if (isSelf) 4.dp else 16.dp,
                                    bottomStart = 16.dp,
                                    bottomEnd = 16.dp,
                                ),
                                color = if (isSelf) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = { showMenu = true },
                                ),
                            ) {
                                Text(
                                    text = segment.text,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelf) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp,
                                )
                            }
                            MessageActionMenu(
                                expanded = showMenu,
                                canRecall = isSelf,
                                canPlusOne = canPlusOne,
                                onDismiss = { showMenu = false },
                                onCopy = { onCopyText(plainText); showMenu = false },
                                onPlusOne = { onPlusOne(plainText); showMenu = false },
                                onSavePhrase = { onSavePhrase(plainText); showMenu = false },
                                onRecall = { onRecall(message); showMenu = false },
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    is ContentSegment.Image -> {
                        Box {
                            CachedImage(
                                url = segment.url,
                                modifier = Modifier
                                    .widthIn(max = 200.dp)
                                    .heightIn(max = 260.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .combinedClickable(
                                        onClick = { onImageClick(segment.url) },
                                        onLongClick = { showMenu = true },
                                    ),
                            )
                            MessageActionMenu(
                                expanded = showMenu,
                                canRecall = isSelf,
                                canPlusOne = false,
                                onDismiss = { showMenu = false },
                                onCopy = { onCopyText(plainText); showMenu = false },
                                onPlusOne = {},
                                onSavePhrase = { onSavePhrase(plainText); showMenu = false },
                                onRecall = { onRecall(message); showMenu = false },
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    is ContentSegment.Audio -> {
                        val ctx = LocalContext.current
                        AudioPlayerBubble(
                            url = segment.url,
                            duration = segment.duration,
                            isSelf = isSelf,
                            onDownload = { downloadMedia(ctx, segment.url, "audio_${System.currentTimeMillis()}.mp3", "audio/mpeg") },
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    is ContentSegment.Video -> {
                        val ctx = LocalContext.current
                        VideoPlayerBubble(
                            url = segment.url,
                            isSelf = isSelf,
                            onDownload = { downloadMedia(ctx, segment.url, "video_${System.currentTimeMillis()}.mp4", "video/mp4") },
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    is ContentSegment.File -> {
                        val ctx = LocalContext.current
                        FileBubble(
                            name = segment.name,
                            isSelf = isSelf,
                            onDownload = { downloadMedia(ctx, segment.url, segment.name, "application/octet-stream") },
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }

            MessageMetaBadges(
                isMarkdown = message.messageType == "markdown",
                keyboardConfig = keyboardConfig,
                isSelf = isSelf,
                onButtonsClick = { showButtonDetails = true },
            )
        }

        if (isSelf) {
            Spacer(modifier = Modifier.width(8.dp))
            BotAvatar(
                botName = botName,
                botAvatar = botAvatar,
                modifier = Modifier.size(36.dp),
            )
        }
    }

    if (showButtonDetails && keyboardConfig != null) {
        ButtonDetailsDialog(
            keyboardConfig = keyboardConfig,
            onDismiss = { showButtonDetails = false },
        )
    }
}

private fun parseKeyboardConfig(raw: String?): KeyboardConfig? {
    if (raw.isNullOrBlank()) return null
    return try {
        Json { ignoreUnknownKeys = true; isLenient = true }.decodeFromString<KeyboardConfig>(raw)
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun MessageMetaBadges(
    isMarkdown: Boolean,
    keyboardConfig: KeyboardConfig?,
    isSelf: Boolean,
    onButtonsClick: () -> Unit,
) {
    val buttonCount = keyboardConfig?.rows?.sumOf { it.buttons.size } ?: 0
    if (!isMarkdown && buttonCount == 0) return

    Row(
        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isMarkdown) {
            MessageMetaBadge(text = "MD", isSelf = isSelf)
        }
        if (buttonCount > 0) {
            MessageMetaBadge(
                text = "按钮 $buttonCount",
                isSelf = isSelf,
                onClick = onButtonsClick,
            )
        }
    }
}

@Composable
private fun MessageMetaBadge(
    text: String,
    isSelf: Boolean,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier,
        shape = RoundedCornerShape(999.dp),
        color = if (isSelf) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        border = androidx.compose.foundation.BorderStroke(
            width = 0.6.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ButtonDetailsDialog(
    keyboardConfig: KeyboardConfig,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("按钮详情") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                keyboardConfig.rows.forEachIndexed { rowIndex, row ->
                    Text(
                        text = "第 ${rowIndex + 1} 行",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    row.buttons.forEach { button ->
                        ButtonDetailItem(button = button)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun ButtonDetailItem(button: MessageButton) {
    val typeLabel = when (button.action) {
        "link" -> "链接"
        "command" -> "指令"
        else -> "回调"
    }
    val value = when (button.action) {
        "link" -> button.url
        "command" -> button.command
        else -> button.data
    }.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = button.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (button.primary) {
                MessageMetaBadge(text = "主按钮", isSelf = false)
            }
        }
        Text(
            text = typeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (value.isNotBlank()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageActionMenu(
    expanded: Boolean,
    canRecall: Boolean,
    canPlusOne: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onPlusOne: () -> Unit,
    onSavePhrase: () -> Unit,
    onRecall: () -> Unit,
) {
    if (!expanded) return
    val density = androidx.compose.ui.platform.LocalDensity.current
    Popup(
        alignment = Alignment.TopCenter,
        offset = with(density) { IntOffset(0, -76.dp.roundToPx()) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = androidx.compose.ui.graphics.Color(0xE6333333),
            shadowElevation = 10.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MessageActionButton(Icons.Filled.ContentCopy, "复制", onCopy)
                if (canPlusOne) {
                    MessageActionButton(Icons.Filled.Add, "+1", onPlusOne)
                }
                MessageActionButton(Icons.Filled.BookmarkAdd, "短语", onSavePhrase)
                if (canRecall) {
                    MessageActionButton(Icons.Filled.Undo, "撤回", onRecall)
                }
            }
        }
    }
}

@Composable
private fun MessageActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            label,
            color = androidx.compose.ui.graphics.Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun QuoteContentPreview(content: String?) {
    val segments = remember(content) { parseContentSegments(content) }
    if (segments.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        segments.take(3).forEach { segment ->
            when (segment) {
                is ContentSegment.Text -> Text(
                    text = segment.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp,
                )
                is ContentSegment.Image -> AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(segment.url)
                        .crossfade(true)
                        .build(),
                    contentDescription = "引用图片",
                    modifier = Modifier
                        .size(width = 72.dp, height = 48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentScale = ContentScale.Crop,
                )
                is ContentSegment.Audio -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("[语音]", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is ContentSegment.Video -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PlayCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("[视频]", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is ContentSegment.File -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("[文件] ${segment.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun UserAvatar(
    avatarUrl: String?,
    nickname: String?,
    modifier: Modifier = Modifier,
) {
    if (!avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(avatarUrl)
                .crossfade(true)
                .build(),
            contentDescription = "头像",
            modifier = modifier.clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (nickname ?: "?").take(1),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun BotAvatar(
    botName: String?,
    botAvatar: String?,
    modifier: Modifier = Modifier,
) {
    if (!botAvatar.isNullOrBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(botAvatar)
                .crossfade(true)
                .build(),
            contentDescription = "Bot头像",
            modifier = modifier.clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (botName ?: "Bot").take(1),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/**
 * 带加载状态和错误反馈的图片组件
 */
@Composable
private fun CachedImage(
    url: String,
    modifier: Modifier = Modifier,
) {
    var retryKey by remember { mutableIntStateOf(0) }

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .setParameter("retry", retryKey)
            .build(),
        contentDescription = "图片",
        modifier = modifier,
        contentScale = ContentScale.Fit,
        loading = {
            Box(
                modifier = Modifier
                    .size(120.dp, 80.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        },
        error = {
            Box(
                modifier = Modifier
                    .size(120.dp, 80.dp)
                    .background(
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { retryKey++ },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.BrokenImage,
                        contentDescription = "加载失败",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            "点击重试",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        },
    )
}

/**
 * 语音/视频/文件等多媒体消息的占位气泡
 */
@Composable
private fun MediaPlaceholder(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelf: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelf) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isSelf) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelf) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 语音播放气泡：点击播放/暂停，带进度条和下载按钮
 */
@Composable
private fun AudioPlayerBubble(
    url: String,
    duration: Int?,
    isSelf: Boolean,
    onDownload: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var decodeState by remember { mutableStateOf<AudioDecodeState>(AudioDecodeState.Idle) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    DisposableEffect(url) {
        onDispose {
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    // 更新进度
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val player = exoPlayer ?: break
            val dur = player.duration.coerceAtLeast(1L)
            progress = player.currentPosition.toFloat() / dur.toFloat()
            kotlinx.coroutines.delay(200)
        }
    }

    fun startPlayback() {
        if (exoPlayer != null) {
            // 已解码，直接播放
            exoPlayer?.play()
            return
        }
        decodeState = AudioDecodeState.Decoding
        scope.launch(Dispatchers.IO) {
            try {
                // 下载 silk 文件
                val silkFile = java.io.File(context.cacheDir, "silk_${url.hashCode()}.silk")
                if (!silkFile.exists()) {
                    val conn = java.net.URL(url).openConnection()
                    conn.getInputStream().use { input ->
                        silkFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                // 解码为 wav
                val wavFile = java.io.File(context.cacheDir, "audio_${url.hashCode()}.wav")
                if (!wavFile.exists()) {
                    xyz.xxin.silkdecoder.SilkDecoder.decodeToWav(silkFile.absolutePath, wavFile.absolutePath, 24000)
                }
                withContext(Dispatchers.Main) {
                    val player = ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(Uri.fromFile(wavFile)))
                        prepare()
                        addListener(object : Player.Listener {
                            override fun onIsPlayingChanged(playing: Boolean) {
                                isPlaying = playing
                            }
                            override fun onPlaybackStateChanged(state: Int) {
                                if (state == Player.STATE_ENDED) {
                                    isPlaying = false
                                    progress = 0f
                                    seekTo(0)
                                    pause()
                                }
                            }
                        })
                        play()
                    }
                    exoPlayer = player
                    decodeState = AudioDecodeState.Ready
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    decodeState = AudioDecodeState.Error
                    Toast.makeText(context, "语音解码失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelf) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .widthIn(min = 160.dp, max = 240.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 播放/暂停
            IconButton(
                onClick = {
                    if (isPlaying) exoPlayer?.pause()
                    else startPlayback()
                },
                enabled = decodeState != AudioDecodeState.Decoding,
                modifier = Modifier.size(32.dp),
            ) {
                if (decodeState == AudioDecodeState.Decoding) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(20.dp),
                        tint = if (isSelf) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            // 进度条
            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = if (isSelf) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondary,
                    trackColor = if (isSelf) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    drawStopIndicator = {},
                )
                if (duration != null) {
                    Text(
                        "${duration}\"",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelf) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            // 下载
            IconButton(onClick = onDownload, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = "下载",
                    modifier = Modifier.size(16.dp),
                    tint = if (isSelf) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private enum class AudioDecodeState { Idle, Decoding, Ready, Error }

/**
 * 视频气泡：显示缩略图+播放图标，点击弹出全屏播放器
 */
@Composable
private fun VideoPlayerBubble(
    url: String,
    isSelf: Boolean,
    onDownload: () -> Unit,
) {
    var showPlayer by remember { mutableStateOf(false) }

    // 气泡：缩略图 + 播放按钮覆盖
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelf) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.85f))
                    .clickable { showPlayer = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayCircle,
                    contentDescription = "播放视频",
                    modifier = Modifier.size(48.dp),
                    tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                )
            }
            // 下载按钮
            Row(
                modifier = Modifier
                    .clickable { onDownload() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = "下载视频",
                    modifier = Modifier.size(16.dp),
                    tint = if (isSelf) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "保存视频",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelf) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // 全屏视频播放器 Dialog
    if (showPlayer) {
        VideoPlayerDialog(url = url, onDismiss = { showPlayer = false })
    }
}

/**
 * 全屏视频播放器弹窗
 */
@AndroidOptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPlayerDialog(
    url: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black),
        ) {
            // 视频播放器
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            // 关闭按钮
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/**
 * 文件气泡：显示文件名 + 下载按钮
 */
@Composable
private fun FileBubble(
    name: String,
    isSelf: Boolean,
    onDownload: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelf) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.clickable { onDownload() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.InsertDriveFile,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isSelf) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelf) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Filled.Download,
                contentDescription = "下载",
                modifier = Modifier.size(18.dp),
                tint = if (isSelf) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 下载媒体文件到公共目录（Downloads）
 */
private fun downloadMedia(context: android.content.Context, url: String, fileName: String, mimeType: String) {
    val request = android.app.DownloadManager.Request(Uri.parse(url))
        .setTitle(fileName)
        .setDescription("正在下载...")
        .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        .setMimeType(mimeType)
    val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
    dm.enqueue(request)
    Toast.makeText(context, "开始下载: $fileName", Toast.LENGTH_SHORT).show()
}

/**
 * 图片发送预览确认对话框
 */
@Composable
private fun ImagePreviewDialog(
    uri: Uri,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "发送图片",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "取消",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 图片预览
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "预览",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit,
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    },
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("发送")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onPickImage: () -> Unit,
    onQuickPhrase: () -> Unit,
    onMore: () -> Unit,
    messageType: String,
    keyboardConfig: KeyboardConfig?,
    showMorePanel: Boolean,
    onClearMarkdown: () -> Unit,
    onClearButtons: () -> Unit,
    onSelectMarkdown: () -> Unit,
    onConfigButtons: () -> Unit,
    sendingState: SendingState,
    isUploading: Boolean,
) {
    Column {
        // 发送状态提示
        when (sendingState) {
            is SendingState.Success -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = if (sendingState.result.mode == "proactive") "主动发送成功" else "被动回复成功",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            is SendingState.Error -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = "发送失败: ${sendingState.message}",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            else -> {}
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )

        if (messageType == "markdown" || keyboardConfig != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (messageType == "markdown") {
                    AssistChip(
                        onClick = onClearMarkdown,
                        label = { Text("Markdown") },
                        leadingIcon = { Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "移除", modifier = Modifier.size(14.dp)) },
                    )
                }
                if (keyboardConfig != null) {
                    val count = keyboardConfig.rows.sumOf { it.buttons.size }
                    AssistChip(
                        onClick = onClearButtons,
                        label = { Text("按钮 $count 个") },
                        leadingIcon = { Icon(Icons.Filled.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "移除", modifier = Modifier.size(14.dp)) },
                    )
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .then(if (showMorePanel) Modifier else Modifier.navigationBarsPadding().imePadding()),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 图片选择按钮
                IconButton(
                    onClick = onPickImage,
                    enabled = !isUploading,
                    modifier = Modifier.size(36.dp),
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Filled.Image,
                            contentDescription = "选择图片",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                // 快捷短语按钮
                IconButton(
                    onClick = onQuickPhrase,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Filled.FormatQuote,
                        contentDescription = "快捷短语",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }

                // 输入框
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 36.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 0.8.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    ),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (draft.isEmpty()) {
                            Text(
                                "输入消息...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                        BasicTextField(
                            value = draft,
                            onValueChange = onDraftChange,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            maxLines = 6,
                        )
                    }
                }

                // 发送按钮
                IconButton(
                    onClick = onMore,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        if (showMorePanel) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }

                // 发送按钮
                FilledIconButton(
                    onClick = onSend,
                    enabled = draft.isNotBlank() && sendingState !is SendingState.Sending,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    ),
                ) {
                    if (sendingState is SendingState.Sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        if (showMorePanel) {
            MoreOptionsPanel(
                onSelectMarkdown = onSelectMarkdown,
                onConfigButtons = onConfigButtons,
            )
        }
    }
}

@Composable
private fun MoreOptionsPanel(
    onSelectMarkdown: () -> Unit,
    onConfigButtons: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(238.dp)
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            )
            Row(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(30.dp),
                verticalAlignment = Alignment.Top,
            ) {
                MoreOptionItem(Icons.Filled.Code, "MD", onSelectMarkdown)
                MoreOptionItem(Icons.Filled.TouchApp, "按钮", onConfigButtons)
            }
        }
    }
}

@Composable
private fun MoreOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier
                .size(56.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 0.5.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(27.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ButtonConfigDialog(
    initial: KeyboardConfig?,
    onDismiss: () -> Unit,
    onSave: (KeyboardConfig) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var action by remember { mutableStateOf("link") }
    var value by remember { mutableStateOf("") }
    var primary by remember { mutableStateOf(false) }
    var buttons by remember {
        mutableStateOf(initial?.rows?.flatMap { it.buttons } ?: emptyList())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("配置按钮") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("按钮文字") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = action == "link", onClick = { action = "link" }, label = { Text("链接") })
                    FilterChip(selected = action == "command", onClick = { action = "command" }, label = { Text("指令") })
                    FilterChip(selected = action == "callback", onClick = { action = "callback" }, label = { Text("回调") })
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = {
                        Text(when (action) {
                            "link" -> "链接 URL"
                            "command" -> "指令文本"
                            else -> "回调数据"
                        })
                    },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = primary, onCheckedChange = { primary = it })
                    Text("主按钮样式")
                    Spacer(modifier = Modifier.weight(1f))
                    FilledTonalButton(
                        onClick = {
                            if (label.isNotBlank() && value.isNotBlank() && buttons.size < 10) {
                                buttons = buttons + MessageButton(
                                    label = label.trim(),
                                    action = action,
                                    url = if (action == "link") value.trim() else null,
                                    command = if (action == "command") value.trim() else null,
                                    data = if (action == "callback") value.trim() else null,
                                    primary = primary,
                                )
                                label = ""
                                value = ""
                                primary = false
                            }
                        },
                        enabled = label.isNotBlank() && value.isNotBlank() && buttons.size < 10,
                    ) { Text("添加") }
                }
                if (buttons.isNotEmpty()) {
                    Text("已添加", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        buttons.forEachIndexed { index, button ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${button.label} · ${when (button.action) { "link" -> "链接"; "command" -> "指令"; else -> "回调" }}", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                IconButton(onClick = { buttons = buttons.filterIndexed { i, _ -> i != index } }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = "删除", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val rows = buttons.chunked(2).map { KeyboardRow(it) }
                    onSave(KeyboardConfig(rows))
                },
                enabled = buttons.isNotEmpty(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 图片全屏查看器：支持双指缩放、单击关闭、保存到相册
 */
@Composable
private fun ImageViewerDialog(
    imageUrl: String,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 存储权限（Android 9 及以下需要）
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch { saveImageToGallery(context, imageUrl) }
        } else {
            Toast.makeText(context, "需要存储权限才能保存图片", Toast.LENGTH_SHORT).show()
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.95f))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                ) { onDismiss() },
        ) {
            // 图片居中
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "全屏图片",
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .align(Alignment.Center)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    ),
                contentScale = ContentScale.Fit,
            )

            // 保存按钮（右下角）
            FloatingActionButton(
                onClick = {
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                        permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        scope.launch { saveImageToGallery(context, imageUrl) }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Filled.SaveAlt, contentDescription = "保存图片")
            }
        }
    }
}

/**
 * 下载图片并保存到系统相册
 */
private suspend fun saveImageToGallery(context: android.content.Context, imageUrl: String) {
    withContext(Dispatchers.IO) {
        try {
            val url = URL(imageUrl)
            val connection = url.openConnection()
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            val bytes = connection.getInputStream().readBytes()

            val mimeType = connection.contentType ?: "image/jpeg"
            val ext = when {
                mimeType.contains("png") -> "png"
                mimeType.contains("webp") -> "webp"
                mimeType.contains("gif") -> "gif"
                else -> "jpg"
            }
            val filename = "IMG_${System.currentTimeMillis()}.$ext"

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FakeQQBot")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

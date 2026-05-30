package xyz.lisfox.big.notenoughfakeqqbot.ui.conversation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import xyz.lisfox.big.notenoughfakeqqbot.data.model.BotInfo
import xyz.lisfox.big.notenoughfakeqqbot.data.model.ConversationEntity
import xyz.lisfox.big.notenoughfakeqqbot.util.extractPlainText
import xyz.lisfox.big.notenoughfakeqqbot.util.formatConversationTime
import xyz.lisfox.big.notenoughfakeqqbot.util.shortId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    currentBot: BotInfo?,
    onConversationClick: (ConversationEntity) -> Unit,
    onSearchClick: () -> Unit,
    viewModel: ConversationListViewModel = viewModel(),
) {
    val conversations by remember(currentBot) {
        if (currentBot != null) viewModel.observeConversations(currentBot.platform, currentBot.selfId)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val isRefreshing by viewModel.isRefreshing.collectAsState()

    // 编辑昵称对话框状态
    var editingConversation by remember { mutableStateOf<ConversationEntity?>(null) }
    var editNickname by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部搜索栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSearchClick() },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "搜索",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "搜索",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }

        // 会话列表
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh(currentBot) },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (conversations.isEmpty() && !isRefreshing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "暂无会话",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(conversations, key = { "${it.platform}:${it.selfId}:${it.channelId}" }) { conv ->
                        ConversationItem(
                            conversation = conv,
                            onClick = { onConversationClick(conv) },
                            onLongClick = {
                                editingConversation = conv
                                editNickname = conv.displayName ?: ""
                            },
                            onPin = { viewModel.pinConversation(conv, !conv.pinned) },
                            onDelete = { viewModel.deleteConversation(conv) },
                        )
                    }
                }
            }
        }
    }

    // 编辑昵称对话框
    if (editingConversation != null) {
        AlertDialog(
            onDismissRequest = { editingConversation = null },
            title = { Text("设置备注名") },
            text = {
                Column {
                    Text(
                        "频道 ID: ${editingConversation!!.channelId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = editNickname,
                        onValueChange = { editNickname = it },
                        label = { Text("备注名") },
                        placeholder = { Text("留空使用默认名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val conv = editingConversation!!
                    viewModel.setDisplayName(conv, editNickname.trim().ifBlank { null })
                    editingConversation = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editingConversation = null }) { Text("取消") }
            },
        )
    }
}

/**
 * 获取会话显示名称：
 * - 有 displayName（用户自定义备注）→ 用它
 * - 否则 → shortId(channelId)
 */
private fun getConversationDisplayName(conversation: ConversationEntity): String {
    if (!conversation.displayName.isNullOrBlank()) return conversation.displayName
    return shortId(conversation.channelId)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ConversationItem(
    conversation: ConversationEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onPin()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surface
                }, label = "bg"
            )
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Filled.PushPin
                SwipeToDismissBoxValue.EndToStart -> Icons.Filled.Delete
                else -> null
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                if (icon != null) Icon(icon, contentDescription = null)
            }
        },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
            color = if (conversation.pinned) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 头像
                if (!conversation.avatar.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(conversation.avatar)
                            .crossfade(true)
                            .build(),
                        contentDescription = "头像",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (conversation.chatType == "c2c") MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.tertiaryContainer
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (conversation.chatType == "c2c") Icons.Filled.Person else Icons.Filled.Group,
                            contentDescription = null,
                            tint = if (conversation.chatType == "c2c") MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 内容
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = getConversationDisplayName(conversation),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatConversationTime(conversation.lastMessageAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val preview = buildString {
                            if (conversation.chatType == "group" && !conversation.lastNickname.isNullOrBlank()) {
                                append(conversation.lastNickname)
                                append(": ")
                            }
                            append(extractPlainText(conversation.lastMessage))
                        }
                        Text(
                            text = preview.ifBlank { " " },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                            lineHeight = 18.sp,
                        )
                        if (conversation.unreadCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ) {
                                Text(
                                    if (conversation.unreadCount > 99) "99+"
                                    else "${conversation.unreadCount}",
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

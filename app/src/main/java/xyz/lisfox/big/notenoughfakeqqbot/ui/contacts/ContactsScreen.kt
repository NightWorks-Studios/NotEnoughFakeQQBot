package xyz.lisfox.big.notenoughfakeqqbot.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import xyz.lisfox.big.notenoughfakeqqbot.App
import xyz.lisfox.big.notenoughfakeqqbot.data.model.BotInfo
import xyz.lisfox.big.notenoughfakeqqbot.data.model.ConversationEntity
import xyz.lisfox.big.notenoughfakeqqbot.util.shortId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    currentBot: BotInfo?,
    onContactClick: (ConversationEntity) -> Unit,
) {
    val repo = App.instance.messageRepository
    val conversations by remember(currentBot) {
        if (currentBot != null) repo.observeConversations(currentBot.platform, currentBot.selfId)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val groups = conversations.filter { it.chatType == "group" }
    val c2c = conversations.filter { it.chatType == "c2c" }

    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部标题
        Surface(
            color = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                text = "联系人",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }

        // Tab 切换
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
            },
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text(
                    "群聊 (${groups.size})",
                    modifier = Modifier.padding(vertical = 14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text(
                    "私聊 (${c2c.size})",
                    modifier = Modifier.padding(vertical = 14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }

        val displayList = if (selectedTab == 0) groups else c2c

        if (displayList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "暂无${if (selectedTab == 0) "群聊" else "私聊"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(displayList, key = { "${it.platform}:${it.selfId}:${it.channelId}" }) { conv ->
                    ContactItem(conv = conv, onClick = { onContactClick(conv) })
                }
            }
        }
    }
}

@Composable
private fun ContactItem(conv: ConversationEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 头像 — 与会话列表统一 48dp
        if (!conv.avatar.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(conv.avatar)
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
                        if (conv.chatType == "c2c") MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.tertiaryContainer
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (conv.chatType == "c2c") Icons.Filled.Person else Icons.Filled.Group,
                    contentDescription = null,
                    tint = if (conv.chatType == "c2c") MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conv.displayName ?: shortId(conv.channelId),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (conv.canProactive) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "支持主动发送",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                )
            }
        }
    }
}

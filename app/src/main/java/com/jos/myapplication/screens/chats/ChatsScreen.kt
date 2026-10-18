package com.jos.myapplication.screens.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jos.myapplication.components.AppBottomNavBar
import com.jos.myapplication.components.BottomNavTab
import com.jos.myapplication.components.Conversation
import com.jos.myapplication.components.InitialsAvatar
import com.jos.myapplication.ui.theme.BackgroundLight
import com.jos.myapplication.ui.theme.ChatSphereTheme
import com.jos.myapplication.ui.theme.DividerGray
import com.jos.myapplication.ui.theme.PurplePrimary
import com.jos.myapplication.ui.theme.SearchBarGray
import com.jos.myapplication.ui.theme.TextPrimary
import com.jos.myapplication.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    onConversationClick: (Conversation) -> Unit,
    onProfileClick: () -> Unit,
    conversations: List<Conversation> = emptyList(),
    isLoading: Boolean = false,
    errorMessage: String? = null,
    currentUserInitials: String = "?",
    onNewChatClick: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(conversations, query) {
        conversations.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.lastMessage.contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = BackgroundLight,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Just Chat", fontWeight = FontWeight.Bold, color = PurplePrimary, fontSize = 20.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onProfileClick) {
                        InitialsAvatar(
                            initials = currentUserInitials,
                            backgroundColor = Color(0xFFB39DDB),
                            size = 34.dp
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BackgroundLight)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewChatClick,
                shape = RoundedCornerShape(16.dp),
                containerColor = PurplePrimary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Edit, contentDescription = "New chat")
            }
        },
        bottomBar = {
            AppBottomNavBar(
                selectedTab = BottomNavTab.CHATS,
                onChatsClick = {},
                onProfileClick = onProfileClick
            )
        }
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search conversations", color = TextSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SearchBarGray,
                    unfocusedContainerColor = SearchBarGray,
                    disabledContainerColor = SearchBarGray,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PurplePrimary)
                }
                errorMessage != null -> EmptyState(errorMessage)
                filtered.isEmpty() -> EmptyState(
                    if (conversations.isEmpty()) "No conversations yet. Start a new chat to message someone."
                    else "No conversations match your search."
                )
                else -> LazyColumn(Modifier.weight(1f)) {
                    items(filtered, key = { it.id }) { conversation ->
                        ConversationRow(conversation) { onConversationClick(conversation) }
                        HorizontalDivider(color = DividerGray, thickness = 1.dp, modifier = Modifier.padding(start = 82.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, color = TextSecondary)
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InitialsAvatar(
            initials = conversation.initials,
            backgroundColor = conversation.avatarColor,
            size = 52.dp
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(conversation.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(conversation.lastMessage.ifBlank { "No messages yet" }, fontSize = 14.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(conversation.time, fontSize = 12.sp, color = TextSecondary)
    }
}

@Preview(showBackground = true)
@Composable
fun ChatsScreenPreview() {
    ChatSphereTheme {
        ChatsScreen(onConversationClick = {}, onProfileClick = {})
    }
}

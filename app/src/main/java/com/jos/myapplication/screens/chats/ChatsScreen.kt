package com.jos.myapplication.screens.chats

import androidx.compose.ui.tooling.preview.Preview
import com.jos.myapplication.ui.theme.ChatSphereTheme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jos.myapplication.components.AppBottomNavBar
import com.jos.myapplication.components.BottomNavTab
import com.jos.myapplication.components.Conversation
import com.jos.myapplication.components.InitialsAvatar
import com.jos.myapplication.components.sampleConversations
import com.jos.myapplication.ui.theme.BackgroundLight
import com.jos.myapplication.ui.theme.DividerGray
import com.jos.myapplication.ui.theme.PurplePrimary
import com.jos.myapplication.ui.theme.SearchBarGray
import com.jos.myapplication.ui.theme.TextPrimary
import com.jos.myapplication.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    onConversationClick: (Conversation) -> Unit,
    onProfileClick: () -> Unit
) {
    Scaffold(
        containerColor = BackgroundLight,

        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Just Chat",
                        fontWeight = FontWeight.Bold,
                        color = PurplePrimary,
                        fontSize = 20.sp
                    )
                },

                navigationIcon = {
                    IconButton(
                        onClick = onProfileClick
                    ) {
                        InitialsAvatar(
                            initials = "AK",
                            backgroundColor = Color(0xFFB39DDB),
                            size = 34.dp
                        )
                    }
                },

                actions = {
                    IconButton(
                        onClick = { }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextPrimary
                        )
                    }
                },

                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = BackgroundLight
                )
            )
        },

        floatingActionButton = {
            FloatingActionButton(
                onClick = { },
                shape = RoundedCornerShape(16.dp),
                containerColor = PurplePrimary,
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "New chat"
                )
            }
        },

        bottomBar = {
            AppBottomNavBar(
                selectedTab = BottomNavTab.CHATS,
                onChatsClick = { },
                onProfileClick = onProfileClick
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {

            ChatsSearchBar()

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(sampleConversations) { conversation ->

                    ConversationRow(
                        conversation = conversation,
                        onClick = {
                            onConversationClick(conversation)
                        }
                    )

                    androidx.compose.material3.HorizontalDivider(
                        color = DividerGray,
                        thickness = 1.dp,
                        modifier = Modifier.padding(start = 82.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatsSearchBar() {

    var query by remember {
        mutableStateOf("")
    }

    TextField(
        value = query,
        onValueChange = {
            query = it
        },

        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp,
                vertical = 8.dp
            ),

        placeholder = {
            Text(
                text = "Search conversations",
                color = TextSecondary
            )
        },

        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = TextSecondary
            )
        },

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
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            }
            .padding(
                horizontal = 16.dp,
                vertical = 12.dp
            ),

        verticalAlignment = Alignment.CenterVertically
    ) {

        InitialsAvatar(
            initials = conversation.initials,
            backgroundColor = conversation.avatarColor,
            size = 52.dp,
            isOnline = conversation.isOnline
        )

        Spacer(
            modifier = Modifier.width(14.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = conversation.name,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(
                modifier = Modifier.height(3.dp)
            )

            Text(
                text = conversation.lastMessage,
                fontSize = 14.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(
            modifier = Modifier.width(8.dp)
        )

        Column(
            horizontalAlignment = Alignment.End
        ) {

            Text(
                text = conversation.time,
                fontSize = 12.sp,
                color = if (conversation.unreadCount > 0) {
                    PurplePrimary
                } else {
                    TextSecondary
                },
                fontWeight = if (conversation.unreadCount > 0) {
                    FontWeight.Bold
                } else {
                    FontWeight.Normal
                }
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            if (conversation.unreadCount > 0) {

                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(PurplePrimary),

                    contentAlignment = Alignment.Center
                ) {

                    Text(
                        text = conversation.unreadCount.toString(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
@Preview(showBackground = true)
@Composable
fun ChatsScreenPreview() {
    ChatSphereTheme {
        ChatsScreen(
            onConversationClick = {},
            onProfileClick = {}
        )
    }
}
package com.jos.myapplication.screens.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jos.myapplication.components.InitialsAvatar
import com.jos.myapplication.data.ChatUser
import com.jos.myapplication.ui.theme.BackgroundLight
import com.jos.myapplication.ui.theme.ChatSphereTheme
import com.jos.myapplication.ui.theme.PurplePrimary
import com.jos.myapplication.ui.theme.TextPrimary
import com.jos.myapplication.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDiscoveryScreen(
    users: List<ChatUser>,
    isLoading: Boolean,
    errorMessage: String?,
    startingUserId: String?,
    startErrorMessage: String?,
    onUserClick: (ChatUser) -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        containerColor = BackgroundLight,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("New chat", color = PurplePrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BackgroundLight)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (startErrorMessage != null) {
                Text(startErrorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PurplePrimary)
                }
                errorMessage != null -> MessageState(errorMessage)
                users.isEmpty() -> MessageState("No other registered users were found.")
                else -> LazyColumn {
                    items(users, key = { it.uid }) { user ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = startingUserId == null) {
                                onUserClick(user)
                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InitialsAvatar(
                                initials = initialsFor(user.name),
                                backgroundColor = Color(0xFF90CAF9),
                                size = 48.dp
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(user.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                if (user.email.isNotBlank()) {
                                    Text(user.email, color = TextSecondary, fontSize = 13.sp)
                                }
                            }
                            if (startingUserId == user.uid) {
                                CircularProgressIndicator(Modifier.size(22.dp), color = PurplePrimary, strokeWidth = 2.dp)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(start = 78.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageState(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, color = TextSecondary)
    }
}

private fun initialsFor(name: String): String = name.split(" ")
    .filter(String::isNotBlank)
    .take(2)
    .joinToString("") { it.take(1).uppercase() }
    .ifBlank { "?" }

@Preview(showBackground = true)
@Composable
private fun UserDiscoveryPreview() {
    ChatSphereTheme {
        UserDiscoveryScreen(emptyList(), false, null, null, null, {}, {})
    }
}

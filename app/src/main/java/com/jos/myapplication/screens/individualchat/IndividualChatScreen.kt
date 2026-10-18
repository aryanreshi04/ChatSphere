package com.jos.myapplication.screens.individualchat

import androidx.compose.ui.tooling.preview.Preview
import com.jos.myapplication.ui.theme.ChatSphereTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jos.myapplication.components.ChatMessage
import com.jos.myapplication.components.InitialsAvatar
import com.jos.myapplication.components.MessageType
import com.jos.myapplication.ui.theme.BackgroundLight
import com.jos.myapplication.ui.theme.BorderGray
import com.jos.myapplication.ui.theme.BubbleReceived
import com.jos.myapplication.ui.theme.BubbleSent
import com.jos.myapplication.ui.theme.DividerChipGray
import com.jos.myapplication.ui.theme.PurpleLight
import com.jos.myapplication.ui.theme.PurplePrimary
import com.jos.myapplication.ui.theme.SurfaceWhite
import com.jos.myapplication.ui.theme.TextPrimary
import com.jos.myapplication.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndividualChatScreen(
    conversationId: String,
    currentUserUid: String,
    contactName: String,
    contactInitials: String,
    messages: List<ChatMessage>,
    messagesLoading: Boolean,
    loadErrorMessage: String?,
    onSendMessage: (String, (Exception?) -> Unit) -> Unit,
    onBackClick: () -> Unit
) {
    var messageText by remember {
        mutableStateOf("")
    }
    var sending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        containerColor = BackgroundLight,

        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InitialsAvatar(
                            initials = contactInitials,
                            backgroundColor = Color(0xFF90CAF9),
                            size = 38.dp
                        )

                        Spacer(
                            modifier = Modifier.width(10.dp)
                        )

                        Column {
                            Text(
                                text = contactName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextPrimary
                            )

                            Text(
                                text = "Direct message",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                },

                navigationIcon = {
                    IconButton(
                        onClick = onBackClick
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },

                actions = {
                    IconButton(
                        onClick = { }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = TextPrimary
                        )
                    }
                },

                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceWhite
                )
            )
        },

        bottomBar = {
            ChatInputBar(
                messageText = messageText,
                onMessageChange = {
                    messageText = it
                    sendError = null
                },
                sending = sending,
                onSend = {
                    val content = messageText.trim()
                    if (content.isNotEmpty() && !sending) {
                        sending = true
                        sendError = null
                        onSendMessage(content) { failure ->
                            sending = false
                            if (failure == null) messageText = ""
                            else sendError = failure.localizedMessage ?: "Message could not be sent. Check your connection and try again."
                        }
                    }
                }
            )
        }
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),

            state = listState,

            contentPadding = PaddingValues(
                vertical = 12.dp
            )
        ) {

            if (messagesLoading) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator(color = PurplePrimary) } }
            } else {
                if (loadErrorMessage != null) {
                    item { Text(loadErrorMessage, color = androidx.compose.material3.MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                }
                if (sendError != null) {
                    item { Text(sendError.orEmpty(), color = androidx.compose.material3.MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                }
                if (messages.isEmpty() && loadErrorMessage == null) {
                    item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("No messages yet. Say hello!", color = TextSecondary) } }
                }
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                }
            }
        }
    }
}

@Composable
private fun DateDivider(
    text: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),

        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(DividerChipGray)
                .padding(
                    horizontal = 14.dp,
                    vertical = 6.dp
                )
        ) {
            Text(
                text = text,
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage
) {
    val isSent = message.isSentByMe

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp,
                vertical = 4.dp
            ),

        horizontalAlignment =
            if (isSent) Alignment.End
            else Alignment.Start
    ) {

        when (message.type) {

            MessageType.TEXT -> {

                Box(
                    modifier = Modifier
                        .widthIn(max = 260.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart =
                                    if (isSent) 18.dp else 4.dp,
                                bottomEnd =
                                    if (isSent) 4.dp else 18.dp
                            )
                        )
                        .background(
                            if (isSent) BubbleSent
                            else BubbleReceived
                        )
                        .padding(
                            horizontal = 16.dp,
                            vertical = 10.dp
                        )
                ) {
                    Text(
                        text = message.text ?: "",
                        color =
                            if (isSent) Color.White
                            else TextPrimary,
                        fontSize = 15.sp
                    )
                }
            }

            MessageType.IMAGE -> {

                Box(
                    modifier = Modifier
                        .size(
                            width = 220.dp,
                            height = 150.dp
                        )
                        .clip(
                            RoundedCornerShape(18.dp)
                        )
                        .background(PurpleLight)
                        .border(
                            width = 2.dp,
                            color = PurplePrimary,
                            shape = RoundedCornerShape(18.dp)
                        ),

                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Image attachment",
                        tint = PurplePrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            MessageType.VOICE -> {

                Row(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(24.dp)
                        )
                        .background(SurfaceWhite)
                        .padding(
                            horizontal = 14.dp,
                            vertical = 10.dp
                        ),

                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PurpleLight),

                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play voice message",
                            tint = PurplePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(
                        modifier = Modifier.width(10.dp)
                    )

                    WaveformBars()

                    Spacer(
                        modifier = Modifier.width(10.dp)
                    )

                    Text(
                        text = message.voiceDuration,
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(
            modifier = Modifier.height(3.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message.time,
                color = TextSecondary,
                fontSize = 11.sp
            )

            if (isSent && message.isRead) {
                Spacer(
                    modifier = Modifier.width(4.dp)
                )

                Icon(
                    imageVector = Icons.Default.DoneAll,
                    contentDescription = "Read",
                    tint = PurplePrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun WaveformBars() {

    val heights = listOf(
        8, 16, 12, 20, 10,
        14, 8, 18, 10
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {

        heights.forEachIndexed { index, height ->

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (index < 5)
                            PurplePrimary
                        else
                            BorderGray
                    )
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    messageText: String,
    onMessageChange: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit
) {
    Surface(
        color = BackgroundLight
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 8.dp,
                    vertical = 8.dp
                ),

            verticalAlignment = Alignment.CenterVertically
        ) {

            IconButton(
                onClick = { }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Attach",
                    tint = TextPrimary
                )
            }

            TextField(
                value = messageText,
                onValueChange = onMessageChange,

                modifier = Modifier.weight(1f),

                placeholder = {
                    Text(
                        text = "Type a message",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                },

                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.EmojiEmotions,
                        contentDescription = "Emoji",
                        tint = TextSecondary
                    )
                },

                singleLine = true,

                shape = RoundedCornerShape(50),

                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite,
                    disabledContainerColor = SurfaceWhite,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )

            Spacer(
                modifier = Modifier.width(6.dp)
            )

            IconButton(
                onClick = { }
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice message",
                    tint = TextPrimary
                )
            }

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(PurplePrimary)
                    .clickable(enabled = messageText.isNotBlank() && !sending, onClick = onSend),

                contentAlignment = Alignment.Center
            ) {
                if (sending) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
@Preview(showBackground = true)
@Composable
fun IndividualChatScreenPreview() {
    ChatSphereTheme {
        IndividualChatScreen(
            conversationId = "preview",
            currentUserUid = "preview",
            contactName = "Alice Freeman",
            contactInitials = "AF",
            messages = emptyList(),
            messagesLoading = false,
            loadErrorMessage = null,
            onSendMessage = { _, callback -> callback(null) },
            onBackClick = {}
        )
    }
}

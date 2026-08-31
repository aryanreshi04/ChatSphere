package com.jos.myapplication.components

import androidx.compose.ui.graphics.Color

enum class MessageType {
    TEXT,
    IMAGE,
    VOICE
}

data class Conversation(
    val id: String,
    val name: String,
    val lastMessage: String,
    val time: String,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val avatarColor: Color,
    val initials: String
)

data class ChatMessage(
    val id: String,
    val type: MessageType,
    val text: String? = null,
    val time: String,
    val isSentByMe: Boolean,
    val isRead: Boolean = false,
    val voiceDuration: String = ""
)

val sampleConversations = listOf(
    Conversation(
        id = "1",
        name = "Alice Freeman",
        lastMessage = "Are we still on for lunch later?",
        time = "10:42 AM",
        unreadCount = 2,
        isOnline = true,
        avatarColor = Color(0xFFEF9A9A),
        initials = "AF"
    ),
    Conversation(
        id = "2",
        name = "Design Team Sync",
        lastMessage = "I'll upload the new Figma files.",
        time = "Yesterday",
        unreadCount = 0,
        isOnline = false,
        avatarColor = Color(0xFF90CAF9),
        initials = "DT"
    ),
    Conversation(
        id = "3",
        name = "Weekend Trip",
        lastMessage = "Sounds like a plan!",
        time = "Mon",
        unreadCount = 0,
        isOnline = true,
        avatarColor = Color(0xFFA5D6A7),
        initials = "WT"
    ),
    Conversation(
        id = "4",
        name = "Charlie Davis",
        lastMessage = "Thanks for your help earlier.",
        time = "12/04",
        unreadCount = 0,
        isOnline = false,
        avatarColor = Color(0xFFFFCC80),
        initials = "CD"
    ),
    Conversation(
        id = "5",
        name = "Support",
        lastMessage = "Your ticket has been resolved.",
        time = "10/04",
        unreadCount = 0,
        isOnline = true,
        avatarColor = Color(0xFFCE93D8),
        initials = "S"
    )
)

val sampleMessages = listOf(
    ChatMessage(
        id = "1",
        type = MessageType.TEXT,
        text = "Hey! How's the project going?",
        time = "10:42 AM",
        isSentByMe = false
    ),
    ChatMessage(
        id = "2",
        type = MessageType.TEXT,
        text = "It's coming along great! Just finishing the UI.",
        time = "10:45 AM",
        isSentByMe = true,
        isRead = true
    ),
    ChatMessage(
        id = "3",
        type = MessageType.IMAGE,
        time = "10:46 AM",
        isSentByMe = true,
        isRead = true
    ),
    ChatMessage(
        id = "4",
        type = MessageType.VOICE,
        time = "10:50 AM",
        isSentByMe = false,
        voiceDuration = "0:12"
    )
)
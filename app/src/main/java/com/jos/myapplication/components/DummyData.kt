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
    val initials: String,
    val otherUserId: String = "",
    val lastActivityMillis: Long = 0L
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

package com.jos.myapplication.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jos.myapplication.ChatSphereApp
import kotlinx.coroutines.launch

class ChatMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        ChatSphereApp.instance.scope.launch { runCatching { ChatNotifications.registerDevice(token) } }
    }
    override fun onMessageReceived(message: RemoteMessage) {
        val target = NotificationTarget.from(message.data) ?: return
        NotificationWorker.enqueue(applicationContext, target,message.priority==RemoteMessage.PRIORITY_HIGH)
    }
}

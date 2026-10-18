package com.jos.myapplication.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class ChatRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val conversations = db.collection("conversations")

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    // Opens an existing one-to-one chat without overwriting its messages/metadata.
    // If the current user hid this chat, opening it again unhides it for them only.
    fun createConversation(
        otherUserId: String,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val currentUserId = getCurrentUserId()
        if (currentUserId == null) {
            onFailure(IllegalStateException("Please log in first"))
            return
        }
        if (otherUserId.isBlank() || otherUserId == currentUserId) {
            onFailure(IllegalArgumentException("Invalid contact"))
            return
        }

        val conversationId = listOf(currentUserId, otherUserId)
            .sorted()
            .joinToString("_")
        val conversationRef = conversations.document(conversationId)
        val hiddenRef = db.collection("users")
            .document(currentUserId)
            .collection("hiddenChats")
            .document(conversationId)

        db.runTransaction { transaction ->
            val existing = transaction.get(conversationRef)
            val hidden = transaction.get(hiddenRef)
            if (existing.exists()) {
                val members = (existing.get("participants") as? List<*>)
                    ?.filterIsInstance<String>().orEmpty()
                if (members.size != 2 ||
                    currentUserId !in members || otherUserId !in members
                ) {
                    throw IllegalStateException("Conversation participants do not match")
                }
            } else {
                transaction.set(
                    conversationRef,
                    mapOf(
                        "participants" to listOf(currentUserId, otherUserId),
                        "lastMessage" to "",
                        "lastMessageTime" to System.currentTimeMillis(),
                        "lastMessageId" to ""
                    )
                )
            }
            if (hidden.exists()) {
                transaction.delete(hiddenRef)
            }
            conversationId
        }.addOnSuccessListener { id ->
            onSuccess(id)
        }.addOnFailureListener { exception ->
            onFailure(exception)
        }
    }

    // Atomic write: message and latest-message preview update together.
    fun sendMessage(
        conversationId: String,
        messageText: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val currentUserId = getCurrentUserId()
        if (currentUserId == null) {
            onFailure(IllegalStateException("Please log in first"))
            return
        }

        val text = messageText.trim()
        if (text.isEmpty()) {
            onFailure(IllegalArgumentException("Message cannot be empty"))
            return
        }

        val conversationRef = conversations.document(conversationId)
        conversationRef.get()
            .addOnSuccessListener { snapshot ->
                val participants = (snapshot.get("participants") as? List<*>)
                    ?.filterIsInstance<String>()
                    .orEmpty()

                if (participants.size != 2 || currentUserId !in participants) {
                    onFailure(IllegalStateException("You are not a participant in this chat"))
                    return@addOnSuccessListener
                }

                val now = System.currentTimeMillis()
                val messageRef = conversationRef.collection("messages").document()
                val batch = db.batch()

                batch.set(
                    messageRef,
                    mapOf(
                        "senderId" to currentUserId,
                        "text" to text,
                        "timestamp" to now
                    )
                )
                batch.update(
                    conversationRef,
                    mapOf(
                        "lastMessage" to text,
                        "lastMessageTime" to now,
                        "lastMessageId" to messageRef.id
                    )
                )

                batch.commit()
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { exception -> onFailure(exception) }
            }
            .addOnFailureListener { exception -> onFailure(exception) }
    }

    fun listenForMessages(
        conversationId: String,
        onMessagesChanged: (List<ChatMessage>) -> Unit,
        onFailure: (Exception) -> Unit
    ): ListenerRegistration {
        return conversations.document(conversationId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onFailure(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val messages = snapshot.documents.mapNotNull { doc ->
                        val senderId = doc.getString("senderId")
                        val text = doc.getString("text")
                        val timestamp = doc.getLong("timestamp")
                        if (senderId != null && text != null && timestamp != null) {
                            ChatMessage(senderId, text, timestamp)
                        } else {
                            null
                        }
                    }
                    onMessagesChanged(messages)
                }
            }
    }
}

data class ChatMessage(
    val senderId: String,
    val text: String,
    val timestamp: Long
)

package com.jos.myapplication.data

import androidx.compose.ui.graphics.Color
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.jos.myapplication.components.ChatMessage
import com.jos.myapplication.components.Conversation
import com.jos.myapplication.components.MessageType
import java.text.SimpleDateFormat
import java.util.Locale

data class ChatUser(
    val uid: String,
    val name: String,
    val email: String
)

/** Firestore access for one-to-one conversations and their messages. */
class ChatRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val conversations = db.collection("conversations")

    fun getUser(uid: String, onResult: (Result<ChatUser>) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    onResult(Result.failure(IllegalStateException("This user profile is no longer available.")))
                } else {
                    val email = document.getString("email").orEmpty()
                    val name = document.getString("name")?.ifBlank { email } ?: email
                    onResult(Result.success(ChatUser(uid, name.ifBlank { "User" }, email)))
                }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun listenUsers(
        currentUid: String,
        onResult: (Result<List<ChatUser>>) -> Unit
    ): ListenerRegistration = db.collection("users")
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onResult(Result.failure(error))
                return@addSnapshotListener
            }
            val users = snapshot?.documents.orEmpty()
                .filter { it.id != currentUid }
                .mapNotNull { document ->
                    val name = document.getString("name")?.trim().orEmpty()
                    val email = document.getString("email").orEmpty()
                    if (name.isBlank() && email.isBlank()) null
                    else ChatUser(document.id, name.ifBlank { email }, email)
                }
                .sortedBy { it.name.lowercase(Locale.getDefault()) }
            onResult(Result.success(users))
        }

    fun listenConversations(
        currentUid: String,
        onResult: (Result<List<Conversation>>) -> Unit
    ): ListenerRegistration {
        var revision = 0
        return conversations.whereArrayContains("participantUids", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onResult(Result.failure(error))
                    return@addSnapshotListener
                }
                val documents = snapshot?.documents.orEmpty()
                val thisRevision = ++revision
                if (documents.isEmpty()) {
                    onResult(Result.success(emptyList()))
                    return@addSnapshotListener
                }

                val results = arrayOfNulls<Conversation>(documents.size)
                var remaining = documents.size
                documents.forEachIndexed { index, document ->
                    val participants = document.get("participantUids") as? List<*>
                    val otherUid = participants?.filterIsInstance<String>()
                        ?.firstOrNull { it != currentUid }
                    if (otherUid == null) {
                        results[index] = conversationFrom(document.id, document.data.orEmpty(), "Unknown user", "")
                        remaining--
                        if (remaining == 0 && thisRevision == revision) {
                            onResult(Result.success(sortConversations(results)))
                        }
                    } else {
                        db.collection("users").document(otherUid).get()
                            .addOnSuccessListener { userDocument ->
                                val name = userDocument.getString("name")
                                    ?: userDocument.getString("email")
                                    ?: "Unknown user"
                                results[index] = conversationFrom(document.id, document.data.orEmpty(), name, otherUid)
                                remaining--
                                if (remaining == 0 && thisRevision == revision) {
                                    onResult(Result.success(sortConversations(results)))
                                }
                            }
                            .addOnFailureListener { failure ->
                                results[index] = conversationFrom(
                                    document.id,
                                    document.data.orEmpty(),
                                    "Unknown user",
                                    otherUid
                                )
                                remaining--
                                if (remaining == 0 && thisRevision == revision) {
                                    onResult(Result.success(sortConversations(results)))
                                }
                            }
                    }
                }
            }
    }

    fun ensureConversation(
        firstUid: String,
        secondUid: String,
        onResult: (Result<String>) -> Unit
    ) {
        if (firstUid == secondUid || firstUid.isBlank() || secondUid.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Choose another registered user.")))
            return
        }
        val participantUids = listOf(firstUid, secondUid).sorted()
        // Find conversations created by the previous SHA-ID implementation
        // before using the rules-compatible ID, so existing chats are reused.
        conversations.whereArrayContains("participantUids", firstUid).get()
            .addOnSuccessListener { snapshot ->
                val existing = snapshot.documents.firstOrNull { document ->
                    val participants = (document.get("participantUids") as? List<*>)
                        ?.filterIsInstance<String>()
                        ?.sorted()
                    participants == participantUids
                }
                if (existing != null) {
                    onResult(Result.success(existing.id))
                } else {
                    createConversationIfMissing(participantUids, onResult)
                }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    private fun createConversationIfMissing(
        participantUids: List<String>,
        onResult: (Result<String>) -> Unit
    ) {
        // Hex encoding keeps arbitrary UID characters out of the Firestore
        // path and gives rules a reversible way to authorize a missing-doc check.
        val conversationId = participantUids.joinToString("__", transform = ::encodeUid)
        val reference = conversations.document(conversationId)
        db.runTransaction { transaction ->
            val existing = transaction.get(reference)
            if (!existing.exists()) {
                transaction.set(
                    reference,
                    mapOf(
                        "participantUids" to participantUids,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "lastMessage" to "",
                        "lastSenderUid" to ""
                    )
                )
            }
            conversationId
        }.addOnSuccessListener { onResult(Result.success(it)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun listenMessages(
        conversationId: String,
        currentUid: String,
        onResult: (Result<List<ChatMessage>>) -> Unit
    ): ListenerRegistration = conversations.document(conversationId)
        .collection("messages")
        .orderBy("createdAt")
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onResult(Result.failure(error))
                return@addSnapshotListener
            }
            val messages = snapshot?.documents.orEmpty().map { document ->
                val senderUid = document.getString("senderUid").orEmpty()
                val timestamp = document.getTimestamp("createdAt")
                ChatMessage(
                    id = document.id,
                    type = MessageType.TEXT,
                    text = document.getString("text").orEmpty(),
                    time = timestamp?.let(::formatTime) ?: "Sending…",
                    isSentByMe = senderUid == currentUid
                )
            }
            onResult(Result.success(messages))
        }

    fun sendMessage(
        conversationId: String,
        senderUid: String,
        text: String,
        onComplete: (Exception?) -> Unit
    ) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            onComplete(IllegalArgumentException("Message cannot be empty."))
            return
        }
        val conversation = conversations.document(conversationId)
        val message = conversation.collection("messages").document()
        val batch = db.batch()
        batch.set(
            message,
            mapOf(
                "senderUid" to senderUid,
                "text" to cleanText,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            conversation,
            mapOf(
                "lastMessage" to cleanText,
                "lastSenderUid" to senderUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit()
            .addOnSuccessListener { onComplete(null) }
            .addOnFailureListener { onComplete(it) }
    }

    private fun conversationFrom(
        id: String,
        data: Map<String, Any>,
        name: String,
        otherUid: String
    ): Conversation {
        val timestamp = data["updatedAt"] as? Timestamp
        return Conversation(
            id = id,
            name = name,
            lastMessage = data["lastMessage"] as? String ?: "",
            time = timestamp?.let(::formatTime) ?: "",
            avatarColor = Color(0xFF90CAF9),
            initials = initialsFor(name),
            otherUserId = otherUid,
            lastActivityMillis = timestamp?.toDate()?.time ?: 0L
        )
    }

    private fun sortConversations(items: Array<Conversation?>): List<Conversation> =
        items.filterNotNull().sortedByDescending { it.lastActivityMillis }

    private fun initialsFor(name: String): String = name.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.take(1).uppercase(Locale.getDefault()) }
        .ifBlank { "?" }

    private fun encodeUid(uid: String): String = uid.toByteArray(Charsets.UTF_8)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun formatTime(timestamp: Timestamp): String =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(timestamp.toDate())
}

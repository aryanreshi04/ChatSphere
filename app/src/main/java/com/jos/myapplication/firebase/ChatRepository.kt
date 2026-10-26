package com.jos.myapplication.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.Source
import com.jos.myapplication.ChatSphereApp
import com.jos.myapplication.data.Message
import com.jos.myapplication.data.MessagePolicy
import com.jos.myapplication.data.SyncState
import com.jos.myapplication.network.awaitResult
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class ChatRepository(private val app: ChatSphereApp = ChatSphereApp.instance) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val conversations = db.collection("conversations")
    private val flushLock = Mutex()
    private val mediaClient = com.jos.myapplication.media.MediaClient(app)
    private val mediaJobs = java.util.concurrent.ConcurrentHashMap<String,kotlinx.coroutines.Deferred<org.json.JSONObject>>()
    private val cancelledMedia = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    val uploads = kotlinx.coroutines.flow.MutableStateFlow<Map<String,com.jos.myapplication.media.UploadProgress>>(emptyMap())
    suspend fun enqueuePhoto(conversation:String,photo:com.jos.myapplication.media.PreparedPhoto) {
        val owner=getCurrentUserId() ?: error("Please sign in")
        com.jos.myapplication.media.MediaPolicy.validateSize(photo.size,com.jos.myapplication.media.PhotoPreparation.maxBytes(app))
        val message=Message(UUID.randomUUID().toString(),owner,"",System.currentTimeMillis(),type="image",
            mimeType=photo.mimeType,mediaSize=photo.size,mediaSha256=photo.sha256,localMediaPath=photo.file.path)
        withContext(Dispatchers.IO){app.database.saveMessages(owner,conversation,listOf(message))}
        app.scope.launch { flush() }
    }
    suspend fun enqueueAttachment(conversation:String,attachment:com.jos.myapplication.media.PreparedAttachment) {
        val owner=getCurrentUserId() ?: error("Please sign in")
        require(attachment.type in setOf("video","audio"))
        require(com.jos.myapplication.media.MediaPolicy.type(attachment.mimeType)==attachment.type)
        com.jos.myapplication.media.MediaPolicy.validateSize(attachment.size,com.jos.myapplication.media.PhotoPreparation.maxBytes(app))
        val message=Message(UUID.randomUUID().toString(),owner,"",System.currentTimeMillis(),type=attachment.type,
            mimeType=attachment.mimeType,mediaSize=attachment.size,mediaSha256=attachment.sha256,localMediaPath=attachment.file.path,
            mediaName=attachment.name,durationMs=attachment.durationMs)
        withContext(Dispatchers.IO){app.database.saveMessages(owner,conversation,listOf(message))}
        app.scope.launch { flush() }
    }
    fun cancelUpload(conversation:String,message:Message) {
        if(message.senderId!=getCurrentUserId())return
        cancelledMedia.add(message.id);mediaJobs[message.id]?.cancel()
        app.scope.launch(Dispatchers.IO){app.database.saveMessages(message.senderId,conversation,listOf(message.copy(state=SyncState.FAILED,errorMessage="Upload stopped locally. Retry checks the original message ID.")))}
    }
    suspend fun mediaFile(conversation:String,message:Message,progress:(com.jos.myapplication.media.UploadProgress)->Unit={})=mediaClient.file(conversation,message,progress)
    private fun fromDocument(doc:com.google.firebase.firestore.DocumentSnapshot,local:Message?,state:SyncState):Message? {
        val sender=doc.getString("senderId") ?: return null
        val time=doc.getLong("timestamp") ?: return null
        return Message(doc.id,sender,doc.getString("text").orEmpty(),time,doc.getString("type")?:"text",
            doc.getDouble("latitude"),doc.getDouble("longitude"),local?.imageBase64,state,
            if(doc.getString("storageProvider")=="supabase")doc.getString("mediaPath")else null,
            doc.getString("mimeType"),doc.getLong("mediaSize"),doc.getString("mediaSha256"),local?.localMediaPath,mediaName=doc.getString("mediaName"),durationMs=doc.getLong("durationMs"))
    }
    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun createConversation(otherUserId: String, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        app.scope.launch {
            try {
                val owner = getCurrentUserId() ?: error("Please log in first")
                val id = MessagePolicy.conversationId(owner, otherUserId)
                if (app.connectivity.online.value) {
                    withTimeout(20_000) {
                        val ref = conversations.document(id)
                        val hidden = db.collection("users").document(owner).collection("hiddenChats").document(id)
                        db.runTransaction { tx ->
                            val existing = tx.get(ref)
                            val hiddenDoc = tx.get(hidden)
                            if (existing.exists()) {
                                val members = existing.get("participants") as? List<*>
                                check(members?.size == 2 && owner in members && otherUserId in members) { "Invalid conversation participants" }
                            } else {
                                tx.set(ref, mapOf("participants" to listOf(owner, otherUserId), "lastMessage" to "",
                                    "lastMessageTime" to System.currentTimeMillis(), "lastMessageId" to ""))
                            }
                            if (hiddenDoc.exists()) tx.delete(hidden)
                            id
                        }.awaitResult()
                    }
                }
                check(getCurrentUserId() == owner) { "Account changed; reopen this chat" }
                onSuccess(id) // Offline: deterministic address only, not a claim of server creation.
            } catch (e: Exception) {
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                onFailure(IllegalStateException(if (e is TimeoutCancellationException) "Opening chat timed out. Retry when connected." else e.message ?: "Could not open chat"))
            }
        }
    }

    suspend fun enqueue(conversation: String, text: String = "", type: String = "text",
                        latitude: Double? = null, longitude: Double? = null, image: String? = null) {
        val owner = getCurrentUserId() ?: error("Please log in first")
        require(type in setOf("text", "image", "location"))
        require(text.length <= MessagePolicy.MAX_TEXT)
        if (type == "text") require(text.isNotBlank()) { "Message cannot be empty" }
        if (type == "location") require(latitude != null && longitude != null && MessagePolicy.validLocation(latitude, longitude))
        if (type == "image") require(image != null && image.length <= 163840) { "Image is too large" }
        val message = Message(UUID.randomUUID().toString(), owner, text.trim(), System.currentTimeMillis(),
            type, latitude, longitude, image)
        withContext(Dispatchers.IO) { app.database.saveMessages(owner, conversation, listOf(message)) }
        // A composer clears only after durable local acceptance, never before.
        app.scope.launch { flush() }
    }

    suspend fun retry(conversation: String, message: Message) {
        val owner = getCurrentUserId() ?: return
        require(message.senderId == owner)
        withContext(Dispatchers.IO) {
            app.database.saveMessages(owner, conversation, listOf(message.copy(state = SyncState.QUEUED)))
        }
        cancelledMedia.remove(message.id)
        flush()
    }

    suspend fun flush() = flushLock.withLock {
        val owner = getCurrentUserId() ?: return@withLock
        if (!app.connectivity.online.value) return@withLock
        val pending = withContext(Dispatchers.IO) { app.database.outbox(owner) }
        for ((conversation, message) in pending) {
            if (getCurrentUserId() != owner || !app.connectivity.online.value) break
            suspend fun save(state: SyncState, error: String? = null) = withContext(Dispatchers.IO) {
                app.database.saveMessages(owner, conversation, listOf(message.copy(state = state,errorMessage = error)))
            }
            if(cancelledMedia.remove(message.id)){save(SyncState.FAILED,"Upload stopped. Tap Retry to continue.");continue}
            save(SyncState.SENDING)
            try {
                if(message.type in setOf("image","video","audio") && message.localMediaPath!=null && message.mediaSha256!=null) {
                    val job=app.scope.async {
                        mediaClient.upload(conversation,message) { progress -> uploads.update { it + (message.id to progress) } }
                    }
                    mediaJobs[message.id]=job
                    val receipt=try { job.await() }
                    finally {mediaJobs.remove(message.id);uploads.update { it-message.id }}
                    val cached=mediaClient.cachedUpload(conversation,message,receipt)
                    withContext(Dispatchers.IO){
                        app.database.saveMessages(owner,conversation,listOf(cached))
                        if(cached.localMediaPath!=message.localMediaPath)mediaClient.ownedFile(message.localMediaPath).delete()
                        mediaClient.trimCache(owner,java.io.File(cached.localMediaPath!!))
                    }
                    runCatching { com.jos.myapplication.notifications.OutgoingPushWorker.enqueue(app,owner,conversation,message.id) }
                    continue
                }
                withTimeout(25_000) {
                    val parent = conversations.document(conversation)
                    val ref = parent.collection("messages").document(message.id)
                    db.runTransaction { tx ->
                        // All reads before any writes. The stable ID makes ambiguous retries idempotent.
                        val thread = tx.get(parent)
                        val existing = tx.get(ref)
                        val members = thread.get("participants") as? List<*>
                        check(members?.size == 2 && owner in members) { "Chat is unavailable. Open it online first." }
                        check(getCurrentUserId() == owner) { "Account changed" }
                        if (!existing.exists()) {
                            val data = mutableMapOf<String, Any>("senderId" to owner, "text" to message.text,
                                "timestamp" to message.timestamp, "type" to message.type, "serverTimestamp" to FieldValue.serverTimestamp())
                            message.latitude?.let { data["latitude"] = it }; message.longitude?.let { data["longitude"] = it }
                            tx.set(ref, data)
                            message.imageBase64?.let {
                                tx.set(parent.collection("media").document(message.id),
                                    mapOf("senderId" to owner, "mimeType" to "image/jpeg", "base64" to it))
                            }
                            // A late offline retry cannot overwrite a newer conversation preview.
                            if (message.timestamp >= (thread.getLong("lastMessageTime") ?: 0)) {
                                tx.update(parent, mapOf("lastMessage" to when(message.type) {
                                    "image" -> "Photo"; "location" -> "Shared location"; else -> message.text
                                }, "lastMessageTime" to message.timestamp, "lastMessageId" to message.id))
                            }
                        } else check(existing.getString("senderId") == owner) { "Message ID conflict" }
                        true
                    }.awaitResult()
                }
                save(SyncState.SENT)
                runCatching { com.jos.myapplication.notifications.OutgoingPushWorker.enqueue(app,owner,conversation,message.id) }
            } catch (e: Exception) {
                val stopped=cancelledMedia.remove(message.id)
                if (e is CancellationException && e !is TimeoutCancellationException && !stopped) throw e
                // Timeout may still commit remotely; retrying uses the same ID and cannot duplicate.
                // A lost transport keeps the durable item queued for the next connectivity event.
                // Online authorization/service failures remain explicit and require a user retry.
                save(if (stopped || app.connectivity.online.value) SyncState.FAILED else SyncState.QUEUED,
                    if(e is CancellationException) "Upload stopped locally. Retry checks the original message ID." else if(e is com.google.firebase.firestore.FirebaseFirestoreException && e.code==com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) "Firestore denied this message. Check conversation access and deployed rules." else e.message?.take(400) ?: "Send failed. Check the backend and connection, then retry.")
            }
        }
    }

    fun listen(conversation: String, onState: (Boolean) -> Unit, onFailure: (Exception) -> Unit): ListenerRegistration {
        val owner = getCurrentUserId() ?: error("Please log in first")
        return conversations.document(conversation).collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING).limit(100)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (getCurrentUserId() != owner) return@addSnapshotListener
                if (error != null) { onFailure(error); return@addSnapshotListener }
                if (snapshot != null) {
                    onState(snapshot.metadata.isFromCache)
                    app.scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val local = app.database.messages(owner, conversation).associateBy { it.id }
                                val messages = snapshot.documents.mapNotNull { doc ->
                                    fromDocument(doc,local[doc.id],if(snapshot.metadata.isFromCache)SyncState.CACHED else SyncState.SENT)
                                }
                                app.database.saveMessages(owner, conversation, messages)
                            }
                        } catch (_: Exception) { onFailure(IllegalStateException("Could not update local message cache")) }
                    }
                }
            }
    }

    suspend fun image(conversation: String, id: String): String {
        val owner = getCurrentUserId() ?: error("Please log in first")
        val key = "$conversation/$id"
        val cached = withContext(Dispatchers.IO) { app.database.record(owner, "media", key) }
        if (cached != null) return cached
        val data = withTimeout(20_000) {
            conversations.document(conversation).collection("media").document(id).get().awaitResult().getString("base64")
        } ?: error("Photo unavailable")
        require(data.length <= 163840) { "Invalid photo size" }
        check(getCurrentUserId() == owner)
        withContext(Dispatchers.IO) { app.database.putRecord(owner, "media", key, data) }
        return data
    }

    suspend fun older(conversation: String, before: Message?): List<Message> {
        val owner = getCurrentUserId() ?: error("Please sign in first")
        var query: Query = conversations.document(conversation).collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING).orderBy(FieldPath.documentId(), Query.Direction.DESCENDING).limit(100)
        if (before != null) query = query.startAfter(before.timestamp, before.id)
        val page = withTimeout(20_000) { query.get(Source.SERVER).awaitResult() }
        check(getCurrentUserId() == owner) { "Account changed" }
        return page.documents.mapNotNull { doc ->
            fromDocument(doc,null,SyncState.SENT)
        }
    }
}

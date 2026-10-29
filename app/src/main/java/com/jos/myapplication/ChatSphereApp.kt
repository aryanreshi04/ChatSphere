package com.jos.myapplication

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.jos.myapplication.database.DatabaseHelper
import com.jos.myapplication.firebase.ChatRepository
import com.jos.myapplication.network.ConnectivityMonitor
import com.jos.myapplication.notifications.ChatNotifications
import com.jos.myapplication.notifications.NotificationTarget
import com.jos.myapplication.notifications.NotificationWorker
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

class ChatSphereApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, _ ->
        android.util.Log.e("ChatSphere", "Background synchronization stopped; reopen the chat and retry")
    })
    val database by lazy { DatabaseHelper(this) }
    val connectivity by lazy { ConnectivityMonitor(this, scope) }
    val repository by lazy { ChatRepository(this) }
    var foreground = false
    private var notificationListener: ListenerRegistration? = null
    override fun onCreate() {
        super.onCreate()
        instance = this
        if (BuildConfig.DEBUG && BuildConfig.USE_FIREBASE_EMULATORS) {
            FirebaseApp.initializeApp(this, FirebaseOptions.Builder().setProjectId("demo-chatsphere")
                .setApplicationId("1:1234567890:android:demo").setApiKey("demo-not-a-real-api-key").build())
            FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
            FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
        }
        scope.launch { connectivity.online.collect { if (it) repository.flush() } }
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            watchForegroundMessages()
            if (auth.currentUser != null) scope.launch {
                repository.flush()
                runCatching { ChatNotifications.registerDevice() }
            } else getSystemService(android.app.NotificationManager::class.java).cancelAll()
        }
    }
    fun watchForegroundMessages() {
        notificationListener?.remove(); notificationListener = null
        val owner = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (!foreground) return
        var initialized = false
        val ids = mutableMapOf<String, String>()
        notificationListener = FirebaseFirestore.getInstance().collection("conversations")
            .whereArrayContains("participants", owner).addSnapshotListener { snapshot, _ ->
                if (snapshot == null || snapshot.metadata.isFromCache || FirebaseAuth.getInstance().currentUser?.uid != owner) return@addSnapshotListener
                snapshot.documents.forEach { thread ->
                    val id = thread.getString("lastMessageId").orEmpty()
                    if (initialized && id.isNotBlank() && ids[thread.id] != id) {
                        NotificationWorker.enqueue(this, NotificationTarget(owner, thread.id, id))
                    }
                    ids[thread.id] = id
                }
                initialized = true
            }
    }
    companion object { lateinit var instance: ChatSphereApp; private set }
}

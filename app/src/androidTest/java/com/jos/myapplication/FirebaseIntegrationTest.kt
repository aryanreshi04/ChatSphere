package com.jos.myapplication

import android.util.Base64
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.FieldValue
import com.jos.myapplication.data.MessagePolicy
import com.jos.myapplication.data.SyncState
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class FirebaseIntegrationTest {
    @Test fun twoAccountsExchangeTextPhotoAndLocationAndRetryDoesNotDuplicate() = runBlocking {
        assumeTrue("Never run Firebase integration tests against production", BuildConfig.USE_FIREBASE_EMULATORS)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (android.os.Build.VERSION.SDK_INT >= 37) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.executeShellCommand("pm grant ${BuildConfig.APPLICATION_ID} android.permission.ACCESS_LOCAL_NETWORK").use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
        }
        val app = context.applicationContext as ChatSphereApp
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val suffix = UUID.randomUUID().toString()
        val peerApp = FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, "peer-$suffix")
        val peerAuth = FirebaseAuth.getInstance(peerApp).apply { useEmulator("10.0.2.2", 9099) }
        val peerDb = FirebaseFirestore.getInstance(peerApp).apply { useEmulator("10.0.2.2", 8080) }
        var stage = "account setup"
        try {
            val alice = Tasks.await(auth.createUserWithEmailAndPassword("alice-$suffix@example.test", "DemoPassword123!"), 20, TimeUnit.SECONDS).user!!
            val bob = Tasks.await(peerAuth.createUserWithEmailAndPassword("bob-$suffix@example.test", "DemoPassword123!"), 20, TimeUnit.SECONDS).user!!
            Tasks.await(db.collection("users").document(alice.uid).set(mapOf("name" to "Alice Test", "email" to alice.email)), 20, TimeUnit.SECONDS)
            Tasks.await(peerDb.collection("users").document(bob.uid).set(mapOf("name" to "Bob Test", "email" to bob.email)), 20, TimeUnit.SECONDS)
            val id = MessagePolicy.conversationId(alice.uid, bob.uid)
            Tasks.await(db.collection("conversations").document(id).set(mapOf("participants" to listOf(alice.uid,bob.uid), "lastMessage" to "", "lastMessageId" to "", "lastMessageTime" to 0L)), 20, TimeUnit.SECONDS)
            stage = "initial connectivity"
            withTimeout(20_000) { while (!app.connectivity.online.value) delay(100) }
            app.repository.enqueue(id, "Can we meet in the library?")
            app.repository.flush()
            val sent = withContext(Dispatchers.IO) { app.database.messages(alice.uid, id).single() }
            assertEquals(SyncState.SENT, sent.state)
            app.repository.retry(id, sent)
            val ref = peerDb.collection("conversations").document(id)
            val text = Tasks.await(ref.collection("messages").get(Source.SERVER),20,TimeUnit.SECONDS)
            assertEquals(1,text.size()); assertEquals(sent.id,text.documents.single().id)
            assertEquals(alice.uid,text.documents.single().getString("senderId"))
            val bitmap = Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
            val output = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG,80,output); bitmap.recycle()
            app.repository.enqueue(id,type="image",image=Base64.encodeToString(output.toByteArray(),Base64.NO_WRAP))
            app.repository.enqueue(id,type="location",latitude=12.0,longitude=77.0)
            app.repository.flush()
            val received = Tasks.await(ref.collection("messages").get(Source.SERVER),20,TimeUnit.SECONDS)
            assertEquals(3,received.size())
            val photo = received.documents.single { it.getString("type") == "image" }
            assertTrue(Tasks.await(ref.collection("media").document(photo.id).get(Source.SERVER),20,TimeUnit.SECONDS).getString("base64")!!.startsWith("/9j/"))
            val location = received.documents.single { it.getString("type") == "location" }
            assertEquals(12.0,location.getDouble("latitude")!!,0.0)
            // Disable the device transports: SDK disableNetwork() alone does not reliably block transaction RPCs.
            fun shell(command: String): String = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }.trim()
            }
            val wifiWasOn = shell("settings get global wifi_on") == "1"
            val dataWasOn = shell("settings get global mobile_data") == "1"
            try {
                stage = "detect transport loss"
                shell("svc wifi disable"); shell("svc data disable")
                val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
                withTimeout(20_000) { while (app.connectivity.online.value || connectivity.activeNetwork != null) delay(100) }
                app.repository.enqueue(id, "Retry after simulated network failure")
                app.repository.flush()
                val queued = withContext(Dispatchers.IO) { app.database.messages(alice.uid,id).single { it.text == "Retry after simulated network failure" } }
                assertEquals(SyncState.QUEUED, queued.state)
            } finally {
                shell(if (wifiWasOn) "svc wifi enable" else "svc wifi disable")
                shell(if (dataWasOn) "svc data enable" else "svc data disable")
            }
            stage = "detect transport recovery"
            withTimeout(30_000) { while (!app.connectivity.online.value) delay(100) }
            app.repository.flush()
            assertEquals(4,Tasks.await(ref.collection("messages").get(Source.SERVER),20,TimeUnit.SECONDS).size())
            // A forbidden destination fails closed and remains retryable, never falsely acknowledged.
            app.repository.enqueue(id + "_missing", "Unauthorized destination fixture")
            app.repository.flush()
            assertEquals(SyncState.FAILED, withContext(Dispatchers.IO) { app.database.messages(alice.uid,id + "_missing").single().state })
            val listener = app.repository.listen(id, {}, {})
            try {
                stage = "incoming listener recovery"
                val incoming = ref.collection("messages").document()
                val time = System.currentTimeMillis()
                val batch = peerDb.batch()
                batch.set(incoming,mapOf("senderId" to bob.uid,"text" to "Reply from the second account","timestamp" to time,"type" to "text","serverTimestamp" to FieldValue.serverTimestamp()))
                batch.update(ref,mapOf("lastMessage" to "Reply from the second account","lastMessageId" to incoming.id,"lastMessageTime" to time))
                Tasks.await(batch.commit(),20,TimeUnit.SECONDS)
                withTimeout(20_000) {
                    while (withContext(Dispatchers.IO) { app.database.messages(alice.uid,id).none { it.id == incoming.id && it.senderId == bob.uid } }) delay(100)
                }
            } finally { listener.remove() }
        } catch (e: Exception) { throw AssertionError("Firebase integration stage: $stage", e) }
        finally {
            Tasks.await(db.enableNetwork(),10,TimeUnit.SECONDS)
            peerAuth.signOut(); auth.signOut(); Tasks.await(peerDb.terminate()); peerApp.delete()
        }
    }
}

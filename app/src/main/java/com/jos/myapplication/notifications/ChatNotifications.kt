package com.jos.myapplication.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.messaging.FirebaseMessaging
import com.jos.myapplication.ChatSphereApp
import com.jos.myapplication.MainActivity
import com.jos.myapplication.R
import com.jos.myapplication.BuildConfig
import com.jos.myapplication.ml.MessageCategories
import com.jos.myapplication.ml.StudyAlerts
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.jos.myapplication.network.awaitResult
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class NotificationTarget(val owner: String, val conversation: String, val message: String,val kind:String="message") {
    companion object {
        private fun valid(value:String?,max:Int)=value!=null&&value.matches(Regex("[A-Za-z0-9_-]{1,$max}"))
        fun from(values: Map<String, String>): NotificationTarget? {
            val owner = values["recipientId"]; val conversation = values["conversationId"]; val message = values["messageId"]
            val kind=values["kind"].orEmpty().ifBlank{"message"}
            return if (valid(owner,128) && valid(conversation,260) && valid(message,128)&&kind in setOf("message","test")) NotificationTarget(owner!!, conversation!!, message!!,kind) else null
        }
        fun from(intent: Intent): NotificationTarget? = from(listOf("recipientId", "conversationId", "messageId","kind").associateWith { intent.getStringExtra(it).orEmpty() })
    }
}

object ChatNotifications {
    @Volatile var activeConversation: String? = null
    private val registration=Mutex()
    private val notificationLock=Mutex()
    suspend fun registerDevice(rotatedToken:String?=null)=registration.withLock {
        check(!BuildConfig.USE_FIREBASE_EMULATORS) { "FCM has no local emulator" }
        val user=FirebaseAuth.getInstance().currentUser?:return@withLock
        val token=rotatedToken?:withTimeout(15000){FirebaseMessaging.getInstance().token.awaitResult()}
        require(token.length in 20..4096&&token.none{it.isISOControl()}){"Invalid device token"}
        if(FirebaseAuth.getInstance().currentUser?.uid!=user.uid)return@withLock
        val id=MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString(""){"%02x".format(it)}
        val prefs=ChatSphereApp.instance.getSharedPreferences("push_registration",0)
        val old=prefs.getString("device_"+user.uid,null)
        val collection=FirebaseFirestore.getInstance().collection("users").document(user.uid).collection("devices")
        withTimeout(15000){FirebaseFirestore.getInstance().batch().apply{
            set(collection.document(id),mapOf("token" to token,"updatedAt" to FieldValue.serverTimestamp()))
            if(old!=null&&old!=id&&old.matches(Regex("[a-f0-9]{64}")))delete(collection.document(old))
        }.commit().awaitResult()}
        if(FirebaseAuth.getInstance().currentUser?.uid==user.uid)prefs.edit().putString("device_"+user.uid,id).apply()
    }
    suspend fun signOut(context:Context) {
        val auth=FirebaseAuth.getInstance();val owner=auth.currentUser?.uid
        try {
            if(owner!=null&&!BuildConfig.USE_FIREBASE_EMULATORS)withTimeout(3000){
                val prefs=context.getSharedPreferences("push_registration",0);val id=prefs.getString("device_"+owner,null)
                if(id!=null&&id.matches(Regex("[a-f0-9]{64}")))FirebaseFirestore.getInstance().document("users/"+owner+"/devices/"+id).delete().awaitResult()
                FirebaseMessaging.getInstance().deleteToken().awaitResult();prefs.edit().remove("device_"+owner).apply()
            }
        }catch(e:kotlinx.coroutines.CancellationException){if(e !is kotlinx.coroutines.TimeoutCancellationException)throw e}
        catch(_:Exception){/* Best effort unregister; account checks still reject stale pushes. */}
        finally {auth.signOut()}
    }
    suspend fun validTest(target:NotificationTarget):Boolean {
        if(target.kind!="test"||FirebaseAuth.getInstance().currentUser?.uid!=target.owner)return false
        val doc=try{withTimeout(12000){FirebaseFirestore.getInstance().document("users/"+target.owner+"/pushTests/"+target.message).get(Source.SERVER).awaitResult()}}
        catch(e:com.google.firebase.firestore.FirebaseFirestoreException){
            if(e.code!=com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)throw e
            // Older deployed rules may not expose the new self-test records; the same trusted
            // authenticated backend can validate them without weakening those rules.
            val result=com.jos.myapplication.network.BackendConnection.json(ChatSphereApp.instance,"/v1/push/validate",org.json.JSONObject().put("requestId",target.message))
            return result.optBoolean("valid")&&FirebaseAuth.getInstance().currentUser?.uid==target.owner
        }
        return doc.getString("kind")=="test"&&doc.getString("owner")==target.owner&&
            (doc.getTimestamp("expiresAt")?.toDate()?.time?:0)>System.currentTimeMillis()&&FirebaseAuth.getInstance().currentUser?.uid==target.owner
    }
    suspend fun validatedSender(target: NotificationTarget): String? {
        if (FirebaseAuth.getInstance().currentUser?.uid != target.owner) return null
        val parent = FirebaseFirestore.getInstance().collection("conversations").document(target.conversation)
        val thread = withTimeout(12_000) { parent.get(Source.SERVER).awaitResult() }
        val members = thread.get("participants") as? List<*> ?: return null
        if (members.size != 2 || target.owner !in members) return null
        val message = withTimeout(12_000) { parent.collection("messages").document(target.message).get(Source.SERVER).awaitResult() }
        val sender = message.getString("senderId") ?: return null
        return sender.takeIf { it != target.owner && it in members && FirebaseAuth.getInstance().currentUser?.uid == target.owner }
    }
    suspend fun receive(context: Context, target: NotificationTarget)=notificationLock.withLock {
        if (FirebaseAuth.getInstance().currentUser?.uid != target.owner || target.conversation == activeConversation) return@withLock
        val prefs = context.getSharedPreferences("chatsphere_settings", 0)
        if (!prefs.getBoolean("message_alerts_${target.owner}", true)) return@withLock
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return@withLock
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return@withLock
        if(target.kind=="test"){if(!validTest(target))return@withLock}else validatedSender(target) ?: return@withLock
        val hidden = if(target.kind=="message")withTimeout(12_000) { FirebaseFirestore.getInstance().collection("users").document(target.owner)
            .collection("hiddenChats").document(target.conversation).get(Source.SERVER).awaitResult() }else null
        if (hidden?.getString("hiddenMessageId") == target.message) return@withLock
        val app = ChatSphereApp.instance
        val key = "${target.owner}/${target.kind}/${target.conversation}/${target.message}"
        if (withContext(Dispatchers.IO) { app.database.record(target.owner, "notified", key) } != null) return@withLock
        var category: String? = null
        var studyAlert=false
        if(target.kind=="message"&&(prefs.getBoolean("ml_categories_${target.owner}",false)||prefs.getBoolean("ai_study_alerts_${target.owner}",false))) {
            val message = FirebaseFirestore.getInstance().collection("conversations").document(target.conversation)
                .collection("messages").document(target.message).get(Source.SERVER).awaitResult()
            if(message.getString("type") in setOf(null,"text"))withContext(Dispatchers.Default){
                val body=message.getString("text").orEmpty()
                val decision=StudyAlerts.decide(body,prefs.getBoolean("ai_study_alerts_${target.owner}",false),System.currentTimeMillis(),prefs.getLong("study_last_${target.owner}",0))
                studyAlert=decision.alert
                if(prefs.getBoolean("ml_categories_${target.owner}",false))category=MessageCategories().classify(body)
            }
        }
        if (FirebaseAuth.getInstance().currentUser?.uid != target.owner || target.conversation == activeConversation) return@withLock
        val sound = prefs.getBoolean("notification_sound_${target.owner}", true)
        val vibration = prefs.getBoolean("notification_vibration_${target.owner}", true)
        val channelId = (if(studyAlert)"study"else"messages")+"_s${sound}_v$vibration"
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channelId, (if(studyAlert)"Study alerts"else"Messages")+" · sound $sound · vibration $vibration", NotificationManager.IMPORTANCE_DEFAULT).apply {
            enableVibration(vibration)
            setSound(if (sound) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else null,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
        })
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("recipientId", target.owner); putExtra("conversationId", target.conversation); putExtra("messageId", target.message);putExtra("kind",target.kind)
            data = android.net.Uri.parse("chatsphere://message/${android.net.Uri.encode(key)}")
        }
        val pending = PendingIntent.getActivity(context, key.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(if(studyAlert)"ChatSphere · Study alert"else"ChatSphere")
            .setContentText(if(target.kind=="test")"Test notification received. Tap to open notification settings."else if(studyAlert)"A study-related message was detected on this device. Tap to review."else category?.let { "$it · new message (experimental category)" } ?: "You have a new message")
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_MESSAGE).build()
        // IDs and recipient checks protect against duplicate/stale/cross-account payloads. No message body in FCM.
        manager.notify(key, 0, notification)
        if(studyAlert)prefs.edit().putLong("study_last_${target.owner}",System.currentTimeMillis()).apply()
        withContext(Dispatchers.IO) { app.database.putRecord(target.owner, "notified", key, "1") }
    }
}

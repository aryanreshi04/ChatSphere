package com.jos.myapplication

import android.app.NotificationManager
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.jos.myapplication.data.MessagePolicy
import com.jos.myapplication.notifications.ChatNotifications
import com.jos.myapplication.notifications.NotificationTarget
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class NotificationFlowTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun realLocalMlNotificationIsPrivateDeduplicatedAndTapOpensItsChat()=runBlocking {
  assumeTrue(BuildConfig.USE_FIREBASE_EMULATORS)
  val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
  for(permission in listOf("android.permission.POST_NOTIFICATIONS","android.permission.ACCESS_LOCAL_NETWORK")){
   if((permission.endsWith("POST_NOTIFICATIONS")&&android.os.Build.VERSION.SDK_INT>=33)||(permission.endsWith("ACCESS_LOCAL_NETWORK")&&android.os.Build.VERSION.SDK_INT>=37))
    instrumentation.uiAutomation.executeShellCommand("pm grant "+context.packageName+" "+permission).use{java.io.FileInputStream(it.fileDescriptor).use{input->input.readBytes()}}
  }
  println("ALERT_TEST permissions ready")
  val auth=FirebaseAuth.getInstance();val db=FirebaseFirestore.getInstance();val suffix=UUID.randomUUID().toString()
  val password="DemoPassword123!";val aliceEmail="alert-alice-"+suffix+"@example.test";val bobEmail="alert-bob-"+suffix+"@example.test"
  val originalIntent=android.content.Intent(compose.activity.intent)
  val manager=context.getSystemService(NotificationManager::class.java)
  try{
   val alice=Tasks.await(auth.createUserWithEmailAndPassword(aliceEmail,password),20,TimeUnit.SECONDS).user!!
   Tasks.await(db.document("users/"+alice.uid).set(mapOf("name" to "Alert fixture A","email" to aliceEmail)),20,TimeUnit.SECONDS)
   context.getSharedPreferences("chatsphere_settings",0).edit().putBoolean("ai_study_alerts_"+alice.uid,true).putLong("study_last_"+alice.uid,0).commit()
   val bob=Tasks.await(auth.createUserWithEmailAndPassword(bobEmail,password),20,TimeUnit.SECONDS).user!!
   Tasks.await(db.document("users/"+bob.uid).set(mapOf("name" to "Alert fixture B","email" to bobEmail)),20,TimeUnit.SECONDS)
   val c=MessagePolicy.conversationId(alice.uid,bob.uid);val id=UUID.randomUUID().toString();val message="Please submit the homework assignment before class deadline"
   Tasks.await(db.document("conversations/"+c).set(mapOf("participants" to listOf(alice.uid,bob.uid),"lastMessage" to "","lastMessageId" to "","lastMessageTime" to 0)),20,TimeUnit.SECONDS)
   Tasks.await(db.document("conversations/"+c+"/messages/"+id).set(mapOf("senderId" to bob.uid,"text" to message,"timestamp" to System.currentTimeMillis(),"type" to "text","serverTimestamp" to FieldValue.serverTimestamp())),20,TimeUnit.SECONDS)
   Tasks.await(auth.signInWithEmailAndPassword(aliceEmail,password),20,TimeUnit.SECONDS)
   println("ALERT_TEST fixture ready")
   ChatNotifications.activeConversation=null
   val target=NotificationTarget(alice.uid,c,id)
   ChatNotifications.receive(context,target);ChatNotifications.receive(context,target)
   println("ALERT_TEST receive complete")
   val matching=manager.activeNotifications.filter{it.tag==alice.uid+"/message/"+c+"/"+id}
   assertEquals(1,matching.size)
   val notification=matching.single().notification
   assertTrue(notification.extras.getCharSequence("android.title").toString().contains("Study alert"))
   assertFalse(notification.extras.getCharSequence("android.text").toString().contains(message))
   ChatNotifications.receive(context,target.copy(owner=bob.uid))
   assertEquals(1,manager.activeNotifications.count{it.tag==matching.single().tag})
   println("ALERT_TEST assertions ready")
   notification.contentIntent.send()
   println("ALERT_TEST tap sent")
   compose.waitUntil(15000){compose.onAllNodes(hasText(message)).fetchSemanticsNodes().isNotEmpty()}
   compose.onNodeWithText(message).assertExists()
   println("ALERT_TEST chat visible")
  }finally{compose.runOnUiThread{compose.activity.setContent{};compose.activity.intent=originalIntent};manager.cancelAll();auth.signOut();println("ALERT_TEST cleanup complete")}
  Unit
 }
}

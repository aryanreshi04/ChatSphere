package com.jos.myapplication

import android.app.NotificationManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.jos.myapplication.ai.AssistantClient
import com.jos.myapplication.network.BackendConnection
import com.jos.myapplication.notifications.ChatNotifications
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Explicit opt-in on an isolated emulator only. No real user credentials or human chats. */
class LivePushIntegrationTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun actualFcmForegroundBackgroundTapAndGeminiUseOnlySyntheticVerificationUsers()=runBlocking {
  assumeTrue(!BuildConfig.USE_FIREBASE_EMULATORS&&InstrumentationRegistry.getArguments().getString("livePush")=="true")
  val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
  val originalIntent=android.content.Intent(compose.activity.intent)
  for(permission in listOf("android.permission.POST_NOTIFICATIONS","android.permission.ACCESS_LOCAL_NETWORK"))if((permission.endsWith("POST_NOTIFICATIONS")&&android.os.Build.VERSION.SDK_INT>=33)||(permission.endsWith("ACCESS_LOCAL_NETWORK")&&android.os.Build.VERSION.SDK_INT>=37))
   instrumentation.uiAutomation.executeShellCommand("pm grant "+context.packageName+" "+permission).use{java.io.FileInputStream(it.fileDescriptor).use{input->input.readBytes()}}
  val access=File(context.filesDir,"verification-access.key").readText().trim()
  suspend fun broker(path:String,post:Boolean=false):JSONObject {
   val builder=Request.Builder().url("http://10.0.2.2:8790"+path).header("X-Verification-Key",access)
   if(post)builder.post("{}".toRequestBody("application/json".toMediaType()))
   return BackendConnection.consume(builder.build()){response->check(response.isSuccessful){"Verification broker request failed ("+response.code+")"};JSONObject(response.body.string())}
  }
  val bootstrap=broker("/bootstrap");val auth=FirebaseAuth.getInstance()
  Tasks.await(auth.signInWithEmailAndPassword(bootstrap.getString("aliceEmail"),bootstrap.getString("alicePassword")),45,TimeUnit.SECONDS)
  val peerApp=com.google.firebase.FirebaseApp.initializeApp(context,com.google.firebase.FirebaseApp.getInstance().options,"push-verification-peer")
  val peerAuth=FirebaseAuth.getInstance(peerApp)
  Tasks.await(peerAuth.signInWithEmailAndPassword(bootstrap.getString("bobEmail"),bootstrap.getString("bobPassword")),45,TimeUnit.SECONDS)
  val peerToken=Tasks.await(peerAuth.currentUser!!.getIdToken(false),30,TimeUnit.SECONDS).token!!
  val owner=auth.currentUser!!.uid;BackendConnection.save(context,"http://10.0.2.2:8790")
  val prefs=context.getSharedPreferences("chatsphere_settings",0)
  prefs.edit().putBoolean("ai_study_alerts_"+owner,true).putLong("study_last_"+owner,0).commit()
  ChatNotifications.activeConversation=null
  // Disable the foreground Firestore observer solely in this test, proving display comes from FCM.
  ChatSphereApp.instance.foreground=false;ChatSphereApp.instance.watchForegroundMessages()
  withTimeout(90000){ChatNotifications.registerDevice()}
  val manager=context.getSystemService(NotificationManager::class.java);manager.cancelAll()
  suspend fun received(target:JSONObject,kind:String="message"):android.service.notification.StatusBarNotification=withTimeout(90000){
   val tag=owner+"/"+kind+"/"+target.getString("conversation")+"/"+target.getString("message")
   while(true){manager.activeNotifications.firstOrNull{it.tag==tag}?.let{return@withTimeout it};delay(250)}
   error("Notification not received")
  }
  suspend fun messageViaAuthenticatedSender():JSONObject {
   val target=broker("/message?dispatch=client",true)
   val body=JSONObject().put("conversationId",target.getString("conversation")).put("messageId",target.getString("message"))
   val request=Request.Builder().url("http://10.0.2.2:8790/v1/push/message").header("Authorization","Bearer "+peerToken).post(body.toString().toRequestBody("application/json".toMediaType())).build()
   BackendConnection.consume(request){response->check(response.isSuccessful){"Authorized saved-message push failed ("+response.code+")"}}
   return target
  }
  try {
  val foreground=messageViaAuthenticatedSender();val first=received(foreground)
  assertTrue(first.notification.extras.getCharSequence("android.title").toString().contains("Study alert"))
  assertFalse(first.notification.extras.getCharSequence("android.text").toString().contains(foreground.getString("text")))
  val id=UUID.randomUUID().toString()
  val self=BackendConnection.json(context,"/v1/push/test",JSONObject().put("requestId",id));assertTrue(self.optInt("accepted")>0)
  val selfTarget=JSONObject().put("conversation","notification-test").put("message",id)
  val selfNotification=received(selfTarget,"test");selfNotification.notification.contentIntent.send()
  compose.waitUntil(15000){compose.onAllNodes(hasText("Send test push to my devices")).fetchSemanticsNodes().isNotEmpty()}
  println("LIVE_FCM_SELF_TEST_VERIFIED")
  compose.runOnUiThread{compose.activity.moveTaskToBack(true)}
  delay(1000)
  val background=messageViaAuthenticatedSender();val second=received(background)
  second.notification.contentIntent.send()
  compose.waitUntil(20000){compose.onAllNodes(hasText(background.getString("text"))).fetchSemanticsNodes().isNotEmpty()}
  println("LIVE_FCM_VERIFIED foreground=true background=true studyClassifier=true tapToChat=true privatePayload=true authenticatedSavedMessage=true")
  if(InstrumentationRegistry.getArguments().getString("liveGemini")=="true"){
   val answer=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main){AssistantClient(context).reply(JSONArray().put(JSONObject().put("role","user").put("content","For a study connectivity test, what is 2 + 2? Reply briefly.")),UUID.randomUUID().toString())}
   assertTrue(answer.isNotBlank());println("LIVE_GEMINI_VERIFIED nonempty=true length="+answer.length)
  }
  compose.runOnUiThread{compose.activity.moveTaskToBack(true)}
  } finally {
   compose.runOnUiThread{compose.activity.intent=originalIntent}
   peerAuth.signOut();peerApp.delete()
  }
  Unit
  // Keep only this isolated test identity until the external closed-process check completes.
 }
}

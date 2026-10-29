package com.jos.myapplication

import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.jos.myapplication.data.Message
import com.jos.myapplication.data.MessagePolicy
import com.jos.myapplication.data.SyncState
import com.jos.myapplication.media.AttachmentPreparation
import com.jos.myapplication.media.PhotoPreparation
import com.jos.myapplication.network.BackendConnection
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

/** Explicit opt-in: real private Supabase bytes, synthetic local Firebase identities/messages only. */
class LiveMediaIntegrationTest {
 @Test fun realPrivateStorageRoundTripForVideoAndAudioAcrossTwoEmulatedUsers()=runBlocking {
  assumeTrue(BuildConfig.USE_FIREBASE_EMULATORS&&InstrumentationRegistry.getArguments().getString("liveSupabaseMedia")=="true")
  val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
  if(android.os.Build.VERSION.SDK_INT>=37)instrumentation.uiAutomation.executeShellCommand("pm grant "+context.packageName+" android.permission.ACCESS_LOCAL_NETWORK").use{java.io.FileInputStream(it.fileDescriptor).use{input->input.readBytes()}}
  val app=context.applicationContext as ChatSphereApp
  val auth=FirebaseAuth.getInstance();val db=FirebaseFirestore.getInstance();val directory=AttachmentFixtures.directory(context)
  val prefs=context.getSharedPreferences("backend_connection",0);val oldUrl=prefs.getString("url",null)
  val suffix=UUID.randomUUID().toString();val password="DemoPassword123!";val bobEmail="media-bob-"+suffix+"@example.test"
  val files=mutableListOf<java.io.File>()
  try {
   BackendConnection.save(context,"http://10.0.2.2:8788")
   val bob=Tasks.await(auth.createUserWithEmailAndPassword(bobEmail,password),20,TimeUnit.SECONDS).user!!
   Tasks.await(db.document("users/"+bob.uid).set(mapOf("name" to "Media fixture B","email" to bob.email)),20,TimeUnit.SECONDS)
   val alice=Tasks.await(auth.createUserWithEmailAndPassword("media-alice-"+suffix+"@example.test",password),20,TimeUnit.SECONDS).user!!
   Tasks.await(db.document("users/"+alice.uid).set(mapOf("name" to "Media fixture A","email" to alice.email)),20,TimeUnit.SECONDS)
   val conversation=MessagePolicy.conversationId(alice.uid,bob.uid)
   Tasks.await(db.document("conversations/"+conversation).set(mapOf("participants" to listOf(alice.uid,bob.uid),"lastMessage" to "","lastMessageId" to "","lastMessageTime" to 0L)),20,TimeUnit.SECONDS)
   withTimeout(20000){while(!app.connectivity.online.value)delay(100)}
   val original=mutableMapOf<String,String>()
   for((source,type) in listOf(AttachmentFixtures.video(directory) to "video",AttachmentFixtures.wav(directory) to "audio")){
    val prepared=AttachmentPreparation.prepare(context,FileProvider.getUriForFile(context,context.packageName+".media",source),type)
    files.add(prepared.file);original[type]=prepared.sha256
    app.repository.enqueueAttachment(conversation,prepared);app.repository.flush()
   }
   val sent=withContext(Dispatchers.IO){app.database.messages(alice.uid,conversation)}
   assertEquals(2,sent.size);for(message in sent){assertEquals(message.errorMessage,SyncState.SENT,message.state);app.repository.retry(conversation,message)}
   val docs=Tasks.await(db.collection("conversations/"+conversation+"/messages").get(Source.SERVER),30,TimeUnit.SECONDS)
   assertEquals(2,docs.size())
   Tasks.await(auth.signInWithEmailAndPassword(bobEmail,password),20,TimeUnit.SECONDS)
   for(doc in docs.documents){
    val message=Message(doc.id,doc.getString("senderId")!!,"",doc.getLong("timestamp")!!,type=doc.getString("type")!!,
     state=SyncState.SENT,mediaPath=doc.getString("mediaPath"),mimeType=doc.getString("mimeType"),mediaSize=doc.getLong("mediaSize"),mediaSha256=doc.getString("mediaSha256"),mediaName=doc.getString("mediaName"),durationMs=doc.getLong("durationMs"))
    var downloadProgress=0f
    val downloaded=app.repository.mediaFile(conversation,message){downloadProgress=it.fraction}
    assertEquals(message.mediaSize,downloaded.length());assertEquals(original[message.type],PhotoPreparation.sha256(downloaded))
    assertTrue(downloadProgress>0f);assertNotNull(AttachmentPreparation.metadata(downloaded,message.type))
    println("LIVE_MEDIA_VERIFIED type="+message.type+" bytes="+downloaded.length()+" object="+message.mediaPath)
   }
  } finally {
   if(oldUrl==null)prefs.edit().remove("url").commit()else prefs.edit().putString("url",oldUrl).commit()
   auth.signOut()
   files.filter{it.isFile}.forEach{it.delete()};directory.deleteRecursively()
  }
 }
}

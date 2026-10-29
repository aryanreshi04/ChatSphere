package com.jos.myapplication
import androidx.test.platform.app.InstrumentationRegistry
import com.jos.myapplication.data.Message
import com.jos.myapplication.data.SyncState
import com.jos.myapplication.database.DatabaseHelper
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
class MediaOutboxTest {
 @Test fun videoAndAudioMetadataRoundTripWithoutChangingLegacyDefaults(){
  for(type in listOf("video","audio")){
   val message=Message("stable-id","alice","",1234,type=type,mimeType=if(type=="video")"video/mp4" else "audio/wav",
    mediaSize=50_000_000,mediaSha256="a".repeat(64),localMediaPath="/fixture",mediaName="Class notes",durationMs=2000)
   assertEquals(message,Message.fromJson(message.json()))
  }
  val legacy=Message.fromJson("{\"id\":\"legacy\",\"senderId\":\"bob\",\"text\":\"Hello\",\"timestamp\":1234}")
  assertEquals("text",legacy.type);assertNull(legacy.mediaName);assertNull(legacy.durationMs)
 }
 @Test fun fullMediaMetadataAndRetryIdSurviveReopeningTheOutboxWithoutBase64(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val name="media-outbox-test-"+UUID.randomUUID()+".db"
  val id=UUID.randomUUID().toString()
  val message=Message(id,"alice","",1234,type="image",mimeType="image/png",mediaSize=8_000_000,
   mediaSha256="a".repeat(64),localMediaPath="/private-test-file.png",errorMessage="Fixture error")
  var db=DatabaseHelper(context,name)
  try{
   db.saveMessages("alice","alice_bob",listOf(message));db.close();db=DatabaseHelper(context,name)
   val pending=db.outbox("alice").single().second
   assertEquals(id,pending.id);assertEquals(message.mediaSize,pending.mediaSize);assertEquals(message.localMediaPath,pending.localMediaPath)
   assertNull(pending.imageBase64);assertTrue(db.outbox("bob").isEmpty())
   db.saveMessages("alice","alice_bob",listOf(pending.copy(state=SyncState.SENDING)))
   db.saveMessages("alice","alice_bob",listOf(pending.copy(state=SyncState.QUEUED)))
   assertEquals(1,db.messages("alice","alice_bob").size);assertEquals(id,db.outbox("alice").single().second.id)
  }finally{db.close();context.deleteDatabase(name)}
 }
}

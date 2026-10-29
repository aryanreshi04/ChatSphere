package com.jos.myapplication
import com.jos.myapplication.media.MediaPolicy
import org.junit.Assert.*
import org.junit.Test
class MediaPolicyTest {
 @Test fun configuredBoundariesUseActualBytesAndRespectFreeCeiling(){
  assertEquals(50_000_000L,MediaPolicy.DEFAULT_MAX_BYTES)
  MediaPolicy.validateSize(10_000_000,10_000_000)
  MediaPolicy.validateSize(50_000_000,50_000_000)
  for(size in listOf(0L,10_000_001L))assertThrows(IllegalArgumentException::class.java){MediaPolicy.validateSize(size,10_000_000)}
  assertThrows(IllegalArgumentException::class.java){MediaPolicy.limit(50_000_001)}
 }
 @Test fun signaturesRejectExtensionsAndExecutableFormats(){
  assertEquals("image/jpeg",MediaPolicy.mime(byteArrayOf(-1,-40,-1,0)))
  assertEquals("image/png",MediaPolicy.mime(byteArrayOf(-119,80,78,71,13,10,26,10)))
  assertThrows(IllegalStateException::class.java){MediaPolicy.mime("<svg>fake.png</svg>".toByteArray())}
 }
 @Test fun supportedAttachmentsUseContentAndKeepSafeNames(){
  val wave="RIFF0000WAVE".toByteArray()
  assertEquals("audio/wav",MediaPolicy.attachmentMime(wave,"audio"))
  assertEquals("audio/mpeg",MediaPolicy.attachmentMime("ID3example".toByteArray(),"audio"))
  assertEquals("video/mp4",MediaPolicy.attachmentMime("0000ftypisom0000".toByteArray(),"video"))
  assertThrows(IllegalArgumentException::class.java){MediaPolicy.attachmentMime(wave,"video")}
  assertThrows(IllegalStateException::class.java){MediaPolicy.attachmentMime("fake.mp4".toByteArray(),"video")}
  assertEquals("a_b_c_d",MediaPolicy.filename("a/b\\c\nd","fallback"))
  assertEquals("fallback",MediaPolicy.filename("  ","fallback"))
 }
 @Test fun prefixReadIsBoundedAndHandlesEarlyEof(){
  assertEquals(20,MediaPolicy.prefix(ByteArray(100).inputStream(),20).size)
  assertEquals(10,MediaPolicy.prefix(ByteArray(10).inputStream(),64).size)
 }
}

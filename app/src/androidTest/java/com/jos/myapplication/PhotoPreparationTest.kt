package com.jos.myapplication
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import com.jos.myapplication.media.PhotoPreparation
import com.jos.myapplication.media.PhotoQuality
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
class PhotoPreparationTest {
 @Test fun originalKeepsFullBytesAndBalancedResizesWithoutTheLegacy120KiBCap()=runBlocking{
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val resolver=context.contentResolver
  val uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply{
   put(MediaStore.Images.Media.DISPLAY_NAME,"chatsphere-quality-"+UUID.randomUUID()+".png")
   put(MediaStore.Images.Media.MIME_TYPE,"image/png")
  })?:error("Cannot create synthetic photo")
  val created=mutableListOf<java.io.File>()
  try{
   val random=java.util.Random(42);val pixels=IntArray(3000*700){random.nextInt() or (0xff shl 24)}
   val source=Bitmap.createBitmap(pixels,3000,700,Bitmap.Config.ARGB_8888)
   resolver.openOutputStream(uri)!!.use{source.compress(Bitmap.CompressFormat.PNG,100,it)};source.recycle()
   val originalBytes=resolver.openInputStream(uri)!!.use{it.readBytes()}
   assertTrue(originalBytes.size>120*1024)
   val original=PhotoPreparation.prepare(context,uri,PhotoQuality.ORIGINAL)
   created.add(original.file);assertArrayEquals(originalBytes,original.file.readBytes());assertEquals("image/png",original.mimeType)
   val balanced=PhotoPreparation.prepare(context,uri,PhotoQuality.BALANCED);created.add(balanced.file)
   assertTrue(balanced.size<=PhotoPreparation.maxBytes(context))
   assertTrue(balanced.size>120*1024)
   val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(balanced.file.path,bounds)
   assertTrue(bounds.outWidth<=2560);assertEquals("image/jpeg",bounds.outMimeType)
  }finally{created.forEach{it.delete()};resolver.delete(uri,null,null)}
 }
 @Test fun fakeImageBytesAreRejectedRegardlessOfProviderMime()=runBlocking{
  val context=InstrumentationRegistry.getInstrumentation().targetContext;val r=context.contentResolver
  val uri=r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply{
   put(MediaStore.Images.Media.DISPLAY_NAME,"chatsphere-invalid-"+UUID.randomUUID()+".png");put(MediaStore.Images.Media.MIME_TYPE,"image/png")
  })?:error("Cannot create fixture")
  try{r.openOutputStream(uri)!!.use{it.write("<html>not an image</html>".toByteArray())}
   try{PhotoPreparation.prepare(context,uri,PhotoQuality.ORIGINAL);fail("Fake image accepted")}catch(_:IllegalStateException){}
  }finally{r.delete(uri,null,null)}
 }
}

package com.jos.myapplication

import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.jos.myapplication.media.AttachmentPreparation
import com.jos.myapplication.media.PhotoPreparation
import com.jos.myapplication.media.MediaPolicy
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.*
import org.junit.Test

class AttachmentPreparationTest {
    @Test fun actualVideoAndAudioRetainCompleteBytesAndExtractPlayableTracks()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=AttachmentFixtures.directory(context);val prepared=mutableListOf<File>()
        try{
            val fixtures=listOf(AttachmentFixtures.video(directory) to "video",AttachmentFixtures.wav(directory) to "audio")
            for((file,type) in fixtures){
                val uri=FileProvider.getUriForFile(context,context.packageName+".media",file)
                val attachment=AttachmentPreparation.prepare(context,uri,type);prepared.add(attachment.file)
                assertEquals(type,attachment.type);assertEquals(file.length(),attachment.size)
                assertEquals(PhotoPreparation.sha256(file),attachment.sha256);assertNotNull(attachment.durationMs);assertTrue(attachment.durationMs!!>0)
                assertEquals(50_000_000L,PhotoPreparation.maxBytes(context))
            }
        }finally{prepared.forEach{it.delete()};directory.deleteRecursively()}
    }
    @Test fun oversizedAndMismatchedPickerFilesAreRejectedWithoutRemovingOriginals()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=AttachmentFixtures.directory(context)
        try{
            val wav=AttachmentFixtures.wav(directory)
            val uri=FileProvider.getUriForFile(context,context.packageName+".media",wav)
            try{AttachmentPreparation.prepare(context,uri,"video");fail("Audio accepted as video")}catch(_:IllegalArgumentException){}
            val above=File(directory,"above.wav");RandomAccessFile(above,"rw").use{it.setLength(MediaPolicy.FREE_MAX_BYTES+1)}
            val largeUri=FileProvider.getUriForFile(context,context.packageName+".media",above)
            try{AttachmentPreparation.prepare(context,largeUri,"audio");fail("Oversized file accepted")}catch(_:IllegalArgumentException){}
            assertTrue(wav.isFile);assertEquals(50_000_001L,above.length())
        }finally{directory.deleteRecursively()}
    }
}

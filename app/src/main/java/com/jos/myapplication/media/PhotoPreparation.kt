package com.jos.myapplication.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.jos.myapplication.R
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

enum class PhotoQuality { ORIGINAL, BALANCED }
data class PreparedPhoto(val file: File, val mimeType: String, val size: Long, val sha256: String)
object PhotoPreparation {
    fun maxBytes(context: Context) = MediaPolicy.limit(context.resources.getInteger(R.integer.chat_media_max_bytes).toLong())
    fun sha256(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val chunk=ByteArray(64*1024); while(true){val n=input.read(chunk);if(n<0)break;hash.update(chunk,0,n)} }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }
    suspend fun prepare(context: Context, uri: Uri, quality: PhotoQuality = PhotoQuality.BALANCED): PreparedPhoto = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir,"media-outbox").apply { mkdirs() }
        val source = File(dir, UUID.randomUUID().toString()+".source")
        val output = File(dir, UUID.randomUUID().toString()+".image")
        var keepSource = false
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(source).use { target ->
                    val bytes=ByteArray(64*1024);var count=0L
                    while(true){ensureActive();val n=input.read(bytes);if(n<0)break;count+=n
                        require(count<=MediaPolicy.FREE_MAX_BYTES) { "Source image exceeds 50 MB" };target.write(bytes,0,n)}
                }
            } ?: error("Cannot read the selected image")
            val mime=source.inputStream().use { MediaPolicy.mime(MediaPolicy.prefix(it,64)) }
            var width=0;var height=0
            val bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder,info,_ ->
                width=info.size.width;height=info.size.height
                require(width>0 && height>0 && width.toLong()*height<=100_000_000) { "Unsupported image dimensions" }
                val longest=if(quality==PhotoQuality.ORIGINAL) 1 else 2560
                val scale=minOf(1.0,longest.toDouble()/maxOf(width,height))
                decoder.setTargetSize(maxOf(1,(width*scale).toInt()),maxOf(1,(height*scale).toInt()))
                decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
            }
            try {
                if(quality==PhotoQuality.ORIGINAL){
                    MediaPolicy.validateSize(source.length(),maxBytes(context))
                    keepSource=true
                    return@withContext PreparedPhoto(source,mime,source.length(),sha256(source))
                }
                var qualityValue=90
                do {
                    ensureActive()
                    FileOutputStream(output).use { require(bitmap.compress(Bitmap.CompressFormat.JPEG,qualityValue,it)) { "Could not prepare photo" } }
                    if(output.length()<=maxBytes(context))break
                    qualityValue-=10
                } while(qualityValue>=60)
                MediaPolicy.validateSize(output.length(),maxBytes(context))
                PreparedPhoto(output,"image/jpeg",output.length(),sha256(output))
            } finally { bitmap.recycle() }
        } catch(e:Exception) { output.delete();throw e }
        finally { if(!keepSource)source.delete() } // Only temporary files created by this operation.
    }
}

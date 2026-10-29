package com.jos.myapplication.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class PreparedAttachment(val file:File,val type:String,val mimeType:String,val size:Long,val sha256:String,val name:String,val durationMs:Long?)
object AttachmentPreparation {
    val audioPickerTypes=arrayOf("audio/mpeg","audio/mp4","audio/x-m4a","audio/wav","audio/x-wav")
    fun metadata(file:File,type:String):Long? {
        val extractor=MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val tracks=(0 until extractor.trackCount).map{extractor.getTrackFormat(it)}
            val hasVideo=tracks.any{it.getString(MediaFormat.KEY_MIME)?.startsWith("video/")==true}
            val hasAudio=tracks.any{it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true}
            require(if(type=="video")hasVideo else hasAudio&&!hasVideo) { "File does not contain the selected video/audio media type" }
        } finally { extractor.release() }
        val retriever=MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf{it in 1..86_400_000}
        } finally { retriever.release() }
    }
    suspend fun prepare(context:Context,uri:Uri,type:String):PreparedAttachment=withContext(Dispatchers.IO) {
        require(type in setOf("video","audio"))
        val dir=File(context.filesDir,"media-outbox").apply{mkdirs()}
        val file=File(dir,UUID.randomUUID().toString()+".attachment")
        try {
            context.contentResolver.openInputStream(uri)?.use{input->file.outputStream().use{output->
                val chunk=ByteArray(64*1024);var total=0L
                while(true){ensureActive();val n=input.read(chunk);if(n<0)break;total+=n
                    MediaPolicy.validateSize(total,PhotoPreparation.maxBytes(context));output.write(chunk,0,n)}
            }}?:error("Cannot read the selected attachment")
            MediaPolicy.validateSize(file.length(),PhotoPreparation.maxBytes(context))
            val mime=file.inputStream().use{MediaPolicy.attachmentMime(MediaPolicy.prefix(it,64),type)}
            val duration=metadata(file,type)
            val providedName=runCatching{context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{cursor->
                if(cursor.moveToFirst())cursor.getString(0)else null
            }}.getOrNull()
            val name=MediaPolicy.filename(providedName,(if(type=="video")"Video" else "Audio")+"."+MediaPolicy.extension(mime))
            PreparedAttachment(file,type,mime,file.length(),PhotoPreparation.sha256(file),name,duration)
        } catch(e:Exception) { file.delete();throw e } // Only the temporary copy made here, never the selected source.
    }
}

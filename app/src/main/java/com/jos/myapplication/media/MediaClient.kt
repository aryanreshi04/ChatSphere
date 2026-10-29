package com.jos.myapplication.media

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.jos.myapplication.data.Message
import com.jos.myapplication.network.BackendConnection
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okio.BufferedSink
import org.json.JSONObject

data class UploadProgress(val fraction:Float,val stage:String)
class MediaClient(private val context:Context) {
    private val transport=BackendConnection.client.newBuilder()
        .callTimeout(600,java.util.concurrent.TimeUnit.SECONDS).readTimeout(600,java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(90,java.util.concurrent.TimeUnit.SECONDS).build()
    fun ownedFile(path:String):File {
        val file=File(path).canonicalFile
        val allowed=listOf(File(context.filesDir,"media-outbox").canonicalFile,File(context.filesDir,"chat-media-cache").canonicalFile)
        require(allowed.any { file.path.startsWith(it.path+File.separator) }) { "Invalid local media reference" }
        require(file.isFile) { "Queued media file is missing. Select the file again." }
        return file
    }
    suspend fun upload(conversation:String,message:Message,progress:(UploadProgress)->Unit) = withContext(Dispatchers.IO) {
        val owner=FirebaseAuth.getInstance().currentUser?.uid ?: error("Please sign in")
        require(owner==message.senderId)
        val file=ownedFile(message.localMediaPath ?: error("Media file is missing"))
        MediaPolicy.validateSize(file.length(),PhotoPreparation.maxBytes(context))
        require(PhotoPreparation.sha256(file)==message.mediaSha256) { "Queued media changed. Select the file again." }
        val body=object:RequestBody(){
            override fun contentType()=message.mimeType!!.toMediaType()
            override fun contentLength()=file.length()
            override fun writeTo(sink:BufferedSink) {
                file.inputStream().use { input ->
                    val chunk=ByteArray(64*1024);var sent=0L
                    while(true){val n=input.read(chunk);if(n<0)break;sink.write(chunk,0,n);sent+=n
                        progress(UploadProgress(sent.toFloat()/file.length(),"Sending to backend"))}
                }
                progress(UploadProgress(1f,"Verifying and storing privately"))
            }
        }
        require(Regex("[A-Za-z0-9_-]{1,260}").matches(conversation)&&Regex("[A-Za-z0-9-]{1,80}").matches(message.id))
        val builder=BackendConnection.authorized(context,"/v1/media/upload/"+conversation+"/"+message.id)
            .header("X-Media-Sha256",message.mediaSha256!!).header("X-Message-Timestamp",message.timestamp.toString())
        message.mediaName?.let{builder.header("X-Media-Name",java.net.URLEncoder.encode(it,"UTF-8").replace("+","%20"))}
        message.durationMs?.let{builder.header("X-Media-Duration-Ms",it.toString())}
        val request=builder.post(body).build()
        val result=BackendConnection.execute(request,transport).use { BackendConnection.readJson(it) }.getJSONObject("media")
        check(FirebaseAuth.getInstance().currentUser?.uid==owner) { "Account changed" }
        result
    }
    suspend fun cachedUpload(conversation:String,message:Message,receipt:JSONObject):Message = withContext(Dispatchers.IO) {
        require(receipt.getString("mediaPath")==conversation+"/"+message.id &&
            receipt.getString("mediaSha256")==message.mediaSha256 && receipt.getLong("mediaSize")==message.mediaSize &&
            receipt.getString("mimeType")==message.mimeType && receipt.getString("type")==message.type) { "Upload receipt does not match the attachment" }
        val owner=FirebaseAuth.getInstance().currentUser?.uid ?: error("Please sign in")
        check(owner==message.senderId) { "Account changed" }
        val dir=File(context.filesDir,"chat-media-cache/"+owner).apply{mkdirs()}
        val name=java.security.MessageDigest.getInstance("SHA-256").digest((conversation+"/"+message.id).toByteArray()).joinToString(""){"%02x".format(it)}
        val target=File(dir,name+".image")
        val source=ownedFile(message.localMediaPath!!)
        // A retry of an already-sent attachment may already point at this cache file.
        if(source.canonicalFile!=target.canonicalFile)source.copyTo(target,overwrite=true)
        target.setLastModified(System.currentTimeMillis())
        message.copy(state=com.jos.myapplication.data.SyncState.SENT,errorMessage=null,mediaPath=receipt.getString("mediaPath"),localMediaPath=target.path)
    }
    fun trimCache(owner:String,keep:File) {
        val dir=File(context.filesDir,"chat-media-cache/"+owner)
        var total=dir.listFiles()?.filter{it.extension=="image"}?.sumOf{it.length()}?:0
        dir.listFiles()?.filter{it.extension=="image"&&it!=keep}?.sortedBy{it.lastModified()}?.forEach{
            if(total>100_000_000){total-=it.length();it.delete()}
        }
    }
    suspend fun file(conversation:String,message:Message,progress:(UploadProgress)->Unit={}):File = withContext(Dispatchers.IO) {
        val currentOwner=FirebaseAuth.getInstance().currentUser?.uid ?: error("Please sign in")
        message.localMediaPath?.let { local ->
            if(java.io.File(local).isFile){
                val owned=ownedFile(local)
                val ownCache=File(context.filesDir,"chat-media-cache/"+currentOwner).canonicalPath+File.separator
                val pending=File(context.filesDir,"media-outbox").canonicalPath+File.separator
                require(owned.path.startsWith(ownCache)||(message.senderId==currentOwner&&owned.path.startsWith(pending))) { "Account changed" }
                require(owned.length()==message.mediaSize&&PhotoPreparation.sha256(owned)==message.mediaSha256){"Local media file changed"}
                return@withContext owned
            }
            check(message.mediaPath!=null) { "Queued media file is missing. Select it again." }
        }
        val owner=FirebaseAuth.getInstance().currentUser?.uid ?: error("Please sign in")
        require(message.mediaPath==conversation+"/"+message.id && Regex("[A-Za-z0-9_-]{1,260}").matches(conversation)&&Regex("[A-Za-z0-9-]{1,80}").matches(message.id))
        val dir=File(context.filesDir,"chat-media-cache/"+owner).apply { mkdirs() }
        val name=java.security.MessageDigest.getInstance("SHA-256").digest((conversation+"/"+message.id).toByteArray()).joinToString(""){"%02x".format(it)}
        val target=File(dir,name+".image")
        if(target.isFile && target.length()==message.mediaSize && PhotoPreparation.sha256(target)==message.mediaSha256){target.setLastModified(System.currentTimeMillis());return@withContext target}
        for(attempt in 0..1){
            val auth=BackendConnection.json(context,"/v1/media/download",JSONObject().put("conversationId",conversation).put("messageId",message.id))
            require(auth.getLong("size")==message.mediaSize && auth.getString("sha256")==message.mediaSha256 && auth.getString("mimeType")==message.mimeType) { "Media metadata does not match" }
            val url=auth.getString("url").toHttpUrl()
            require(url.isHttps && url.username.isEmpty() && url.password.isEmpty()) { "Insecure media download URL" }
            val temporary=File(dir,name+"-"+java.util.UUID.randomUUID()+".partial")
            var completed=false
            try {
                BackendConnection.consume(Request.Builder().url(url).build(),transport) { response ->
                    if(response.code==403 && attempt==0)return@consume
                    check(response.isSuccessful) { "Media download failed ("+response.code+"). Retry to request a new access link." }
                    val expected=message.mediaSize ?: error("Media size is missing")
                    MediaPolicy.validateSize(expected,MediaPolicy.FREE_MAX_BYTES)
                    response.body.byteStream().use { input -> temporary.outputStream().use { output ->
                        val chunk=ByteArray(64*1024);var count=0L
                        while(true){ensureActive();val n=input.read(chunk);if(n<0)break;count+=n;require(count<=expected){"Media download exceeds its declared size"};output.write(chunk,0,n);progress(UploadProgress(count.toFloat()/expected,"Downloading full file"))}
                    }}
                    require(temporary.length()==expected && PhotoPreparation.sha256(temporary)==message.mediaSha256) { "Incomplete or corrupted media download. Retry." }
                    require(temporary.inputStream().use { if(message.type=="image")MediaPolicy.mime(MediaPolicy.prefix(it,64))else MediaPolicy.attachmentMime(MediaPolicy.prefix(it,64),message.type) }==message.mimeType) { "Invalid downloaded media type" }
                    check(FirebaseAuth.getInstance().currentUser?.uid==owner) { "Account changed" }
                    temporary.copyTo(target,overwrite=true)
                    completed=true
                }
                if(completed) {
                    var total=dir.listFiles()?.filter{it.extension=="image"}?.sumOf{it.length()} ?: 0
                    dir.listFiles()?.filter{it.extension=="image"&&it!=target}?.sortedBy{it.lastModified()}?.forEach{if(total>100_000_000){total-=it.length();it.delete()}}
                    return@withContext target
                }
            } finally { temporary.delete() }
        }
        error("Media access link expired. Retry the download.")
    }
}

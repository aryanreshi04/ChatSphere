package com.jos.myapplication.screens.individualchat
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.content.Intent
import android.widget.Toast
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jos.myapplication.data.Message
import com.jos.myapplication.data.MessagePolicy
import com.jos.myapplication.firebase.ChatRepository
import com.jos.myapplication.location.MapIntents
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
@Composable
fun MessageContent(message:Message,conversation:String,repo:ChatRepository?,mine:Boolean) {
 val context=LocalContext.current;val scope=rememberCoroutineScope();val color=if(mine)Color.White else Color.Unspecified
 Column(Modifier.widthIn(max=280.dp).padding(14.dp)){
  when(message.type){
   "text"->Text(message.text,color=color)
   "location"->{Text("Shared location",color=color);val lat=message.latitude;val lon=message.longitude
    if(lat!=null&&lon!=null&&MessagePolicy.validLocation(lat,lon)){Text(lat.toString()+", "+lon,fontSize=12.sp,color=color);TextButton(onClick={MapIntents.open(context,lat,lon)}){Text("Open in Google Maps",color=color)}}
    else Text("Location data is unavailable",color=color)}
   "image"->{
    var bitmap by remember(message.id){mutableStateOf<Bitmap?>(null)};var file by remember(message.id){mutableStateOf<File?>(null)}
    var failure by remember(message.id){mutableStateOf<String?>(null)};var attempt by remember(message.id){mutableIntStateOf(0)}
    val mime=message.mimeType?:"image/jpeg"
    fun openFull(){file?.let{photo->runCatching{
     val uri=FileProvider.getUriForFile(context,context.packageName+".media",photo)
     context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }.onFailure{Toast.makeText(context,"No image viewer available. Use Save file.",Toast.LENGTH_LONG).show()}}}
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)){uri->if(uri!=null)scope.launch{
     try{withContext(Dispatchers.IO){val photo=file?:error("Photo unavailable");context.contentResolver.openOutputStream(uri)?.use{output->photo.inputStream().use{it.copyTo(output)}}?:error("Cannot save file")};Toast.makeText(context,"Full uploaded photo saved",Toast.LENGTH_SHORT).show()}
     catch(e:CancellationException){throw e}
     catch(_:Exception){Toast.makeText(context,"Could not save photo. Retry.",Toast.LENGTH_LONG).show()}
    }}
    LaunchedEffect(message.id,attempt,message.mediaPath,message.localMediaPath,message.imageBase64){
     failure=null
     try{
      val loaded=withContext(Dispatchers.IO){
       if(message.localMediaPath!=null||message.mediaPath!=null){
        if(repo!=null)repo.mediaFile(conversation,message)
        else File(message.localMediaPath?:error("Photo unavailable")).also{require(it.canonicalPath.startsWith(File(context.filesDir,"media-outbox").canonicalPath+File.separator))}
       }else{
        val data=message.imageBase64?:repo?.image(conversation,message.id)?:error("Photo unavailable");require(data.length<=163840)
        val key=java.security.MessageDigest.getInstance("SHA-256").digest(data.toByteArray()).joinToString(""){"%02x".format(it)}
        File(File(context.filesDir,"chat-media-cache/legacy").apply{mkdirs()},key+".jpg").also{if(!it.exists())it.writeBytes(Base64.decode(data,Base64.DEFAULT))}
       }}
      val decoded=withContext(Dispatchers.IO){ImageDecoder.decodeBitmap(ImageDecoder.createSource(loaded)){decoder,info,_->
       require(info.size.width.toLong()*info.size.height<=100_000_000){"Unsupported image dimensions"}
       val scale=minOf(1.0,1024.0/maxOf(info.size.width,info.size.height));decoder.setTargetSize(maxOf(1,(info.size.width*scale).toInt()),maxOf(1,(info.size.height*scale).toInt()));decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
      }};file=loaded;bitmap=decoded
     }catch(e:CancellationException){throw e}catch(e:Exception){failure=e.message?:"Photo download failed. Retry."}
    }
    if(bitmap!=null){
     Image(bitmap!!.asImageBitmap(),"Shared photo",Modifier.fillMaxWidth().heightIn(max=260.dp).clickable{openFull()})
     Row{TextButton(onClick={openFull()}){Text("Open full image",color=color,fontSize=11.sp)}
      TextButton(onClick={save.launch("photo-"+message.id+"."+when(mime){"image/png"->"png";"image/webp"->"webp";"image/heic","image/heif"->"heic";else->"jpg"})}){Text("Save file",color=color,fontSize=11.sp)}}
     message.mediaSize?.let{Text(String.format(java.util.Locale.US,"%.2f MB · full file",it/1_000_000.0),color=color,fontSize=10.sp)}
    }else if(failure!=null){Text(failure!!,color=color,fontSize=12.sp);TextButton(onClick={attempt++}){Text("Retry download",color=color)}}
    else{CircularProgressIndicator(Modifier.size(24.dp),color=color);Text("Loading photo…",color=color)}
   }
   "video", "audio"->AttachmentContent(message,conversation,repo,color)
   else->Text("Unsupported message type",color=color)
  }
 }
}

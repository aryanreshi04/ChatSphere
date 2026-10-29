package com.jos.myapplication.screens.individualchat

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jos.myapplication.data.Message
import com.jos.myapplication.firebase.ChatRepository
import com.jos.myapplication.media.MediaClient
import com.jos.myapplication.media.MediaPolicy
import com.jos.myapplication.media.UploadProgress
import com.jos.myapplication.media.VideoPlaybackActivity
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AttachmentContent(message:Message,conversation:String,repo:ChatRepository?,color:Color) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var file by remember(message.id){mutableStateOf<File?>(null)}
    var thumbnail by remember(message.id){mutableStateOf<Bitmap?>(null)}
    var failure by remember(message.id){mutableStateOf<String?>(null)}
    var requested by remember(message.id){mutableStateOf(message.localMediaPath!=null)}
    var attempt by remember(message.id){mutableIntStateOf(0)}
    var progress by remember(message.id){mutableStateOf<UploadProgress?>(null)}
    val mime=message.mimeType?:if(message.type=="video")"video/mp4" else "audio/mpeg"
    val name=message.mediaName?:message.type+"-"+message.id+"."+MediaPolicy.extension(mime)
    LaunchedEffect(message.id,requested,attempt,message.localMediaPath,message.mediaPath) {
        if(!requested)return@LaunchedEffect
        failure=null;progress=null
        try {
            val loaded=withContext(Dispatchers.IO){
                if(repo!=null)repo.mediaFile(conversation,message){progress=it}
                else MediaClient(context).ownedFile(message.localMediaPath?:error("Attachment unavailable"))
            }
            file=loaded
            if(message.type=="video")thumbnail=withContext(Dispatchers.IO){
                val retriever=MediaMetadataRetriever()
                try{retriever.setDataSource(loaded.path);retriever.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,640,360)}
                catch(_:Exception){null}finally{retriever.release()}
            }
        }catch(e:CancellationException){throw e}
        catch(e:Exception){failure=e.message?.take(300)?:"Could not download attachment. Retry."}
        finally{progress=null}
    }
    DisposableEffect(thumbnail){val old=thumbnail;onDispose{old?.recycle()}}
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)){uri->if(uri!=null)scope.launch{
        try{withContext(Dispatchers.IO){val source=file?:error("Download the attachment first")
            context.contentResolver.openOutputStream(uri)?.use{output->source.inputStream().use{it.copyTo(output)}}?:error("Cannot save file")}
            Toast.makeText(context,"Full file saved",Toast.LENGTH_SHORT).show()}
        catch(e:CancellationException){throw e}
        catch(_:Exception){Toast.makeText(context,"Could not save file. Download again and retry.",Toast.LENGTH_LONG).show()}
    }}
    Text(if(message.type=="video")"Video" else "Audio",color=color)
    Text(name,color=color,fontSize=12.sp)
    message.mediaSize?.let{Text(String.format(java.util.Locale.US,"%.2f MB",it/1_000_000.0),color=color,fontSize=11.sp)}
    if(file!=null){
        if(message.type=="video"){
            thumbnail?.let{Image(it.asImageBitmap(),"Video thumbnail",Modifier.fillMaxWidth().heightIn(max=180.dp))}
            TextButton(onClick={runCatching{
                val uri=FileProvider.getUriForFile(context,context.packageName+".media",file!!)
                context.startActivity(Intent(context,VideoPlaybackActivity::class.java).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }.onFailure{failure="Video unavailable. Download the file again."}}){Text("Play full-screen video",color=color)}
        }else AudioPlayback(file!!,color)
        TextButton(onClick={save.launch(name)}){Text("Save file",color=color)}
    }else if(failure!=null){
        Text(failure!!,color=color,fontSize=12.sp)
        TextButton(onClick={requested=true;attempt++}){Text("Retry download",color=color)}
    }else if(requested){
        val current=progress
        if(current!=null){LinearProgressIndicator(progress={current.fraction},modifier=Modifier.fillMaxWidth(),color=color)
            Text("Downloading "+(current.fraction*100).toInt()+"%",color=color,fontSize=11.sp)}
        else{CircularProgressIndicator(Modifier.size(22.dp),color=color);Text("Loading attachment…",color=color,fontSize=12.sp)}
        TextButton(onClick={requested=false}){Text("Stop download",color=color)}
    }else TextButton(onClick={requested=true;attempt++}){Text("Download to play",color=color)}
    if(file!=null&&failure!=null)Text(failure!!,color=color,fontSize=12.sp)
}

private fun clock(ms:Int):String=String.format(java.util.Locale.US,"%d:%02d",ms/60000,(ms/1000)%60)
@Composable
private fun AudioPlayback(file:File,color:Color) {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var player by remember(file.path){mutableStateOf<MediaPlayer?>(null)}
    var ready by remember(file.path){mutableStateOf(false)}
    var playing by remember(file.path){mutableStateOf(false)}
    var duration by remember(file.path){mutableIntStateOf(0)}
    var position by remember(file.path){mutableIntStateOf(0)}
    var failure by remember(file.path){mutableStateOf(false)}
    var seeking by remember(file.path){mutableStateOf(false)}
    var attempt by remember(file.path){mutableIntStateOf(0)}
    DisposableEffect(file.path,attempt){
        var disposed=false
        ready=false;playing=false;failure=false
        val audio=MediaPlayer();player=audio
        audio.setOnPreparedListener{if(!disposed){duration=it.duration.coerceAtLeast(0);ready=true}}
        audio.setOnCompletionListener{if(!disposed){playing=false;position=duration}}
        audio.setOnErrorListener{_,_,_->if(!disposed){failure=true;ready=false;playing=false};true}
        try{audio.setDataSource(file.path);audio.prepareAsync()}catch(_:Exception){failure=true}
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP){
            runCatching{if(audio.isPlaying)audio.pause()};playing=false
        }}
        lifecycle.addObserver(observer)
        onDispose{disposed=true;lifecycle.removeObserver(observer);audio.release();player=null}
    }
    LaunchedEffect(playing,player){while(playing){if(!seeking)position=runCatching{player?.currentPosition?:0}.getOrDefault(0);delay(250)}}
    if(failure){Text("Audio could not play on this device. Retry or save the full file.",color=color,fontSize=12.sp)
        TextButton(onClick={attempt++}){Text("Retry playback",color=color)}}
    else if(!ready)Text("Loading audio…",color=color,fontSize=12.sp)
    else {
        TextButton(onClick={runCatching{
            val audio=player?:return@runCatching
            if(playing){audio.pause();playing=false}else{if(position>=duration){audio.seekTo(0);position=0};audio.start();playing=true}
        }.onFailure{failure=true;playing=false}}){Text(if(playing)"Pause audio" else "Play audio",color=color)}
        Text(clock(position)+" / "+clock(duration),color=color,fontSize=11.sp)
        if(duration>0)Slider(value=position.coerceIn(0,duration).toFloat(),onValueChange={seeking=true;position=it.toInt()},
            onValueChangeFinished={runCatching{player?.seekTo(position)};seeking=false},valueRange=0f..duration.toFloat())
    }
}

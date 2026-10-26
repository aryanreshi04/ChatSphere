package com.jos.myapplication.screens.individualchat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import android.location.Location
import com.jos.myapplication.location.DeviceLocation
import com.jos.myapplication.media.PhotoPreparation
import com.jos.myapplication.ml.SmartReplies
import com.jos.myapplication.notifications.ChatNotifications
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.jos.myapplication.ChatSphereApp
import com.jos.myapplication.components.InitialsAvatar
import com.jos.myapplication.data.Message
import com.jos.myapplication.data.MessagePolicy
import com.jos.myapplication.data.SyncState
import com.jos.myapplication.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndividualChatScreen(contactName: String, contactInitials: String, contactUserId: String, onBackClick: () -> Unit) {
    val preview = LocalInspectionMode.current
    val app = if (preview) null else ChatSphereApp.instance
    val repo = app?.repository
    val owner = repo?.getCurrentUserId()
    val uploadStates by repo?.uploads?.collectAsState() ?: remember { mutableStateOf(emptyMap<String,com.jos.myapplication.media.UploadProgress>()) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val online by app?.connectivity?.online?.collectAsState() ?: remember { mutableStateOf(false) }
    var draft by rememberSaveable(contactUserId) { mutableStateOf("") }
    var messages by remember(contactUserId, owner) { mutableStateOf<List<Message>>(emptyList()) }
    var olderMessages by remember(contactUserId, owner) { mutableStateOf<List<Message>>(emptyList()) }
    var paging by remember { mutableStateOf(false) }
    var endOfHistory by remember { mutableStateOf(false) }
    val displayedMessages = remember(messages, olderMessages) {
        (olderMessages + messages).associateBy { it.id }.values.sortedWith(compareBy<Message> { it.timestamp }.thenBy { it.id })
    }
    var conversation by remember(contactUserId, owner) { mutableStateOf<String?>(null) }
    var error by remember(contactUserId) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var cached by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    val list = rememberLazyListState()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var photo by remember(contactUserId) { mutableStateOf<com.jos.myapplication.media.PreparedPhoto?>(null) }
    var attachment by remember(contactUserId) { mutableStateOf<com.jos.myapplication.media.PreparedAttachment?>(null) }
    var suggestions by remember(contactUserId) { mutableStateOf<List<String>>(emptyList()) }
    var smartLabel by remember { mutableStateOf("On-device ML suggestions · review before sending") }
    var smartError by remember { mutableStateOf<String?>(null) }
    var fix by remember(contactUserId) { mutableStateOf<Location?>(null) }
    fun locate() {
        if (busy) return
        busy = true
        scope.launch {
            try { fix = DeviceLocation.current(context); error = null }
            catch (e: kotlinx.coroutines.CancellationException) {
                if (e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                error = "Location timed out. Check location services and retry outdoors."
            }
            catch (_: Exception) { error = "Location unavailable. Enable device location services and try again." }
            finally { busy = false }
        }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (DeviceLocation.permitted(context)) locate()
        else error = "Location permission denied. No location was shared. You can enable permission in Android app settings."
    }
    var qualityUri by remember { mutableStateOf<android.net.Uri?>(null) }
    fun preparePhoto(uri:android.net.Uri,quality:com.jos.myapplication.media.PhotoQuality) {
        busy=true
        scope.launch {
            try{photo=PhotoPreparation.prepare(context,uri,quality);error=null}
            catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){error=e.message?:"Could not prepare photo"}
            finally{busy=false}
        }
    }
    val photoPicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->if(uri!=null)qualityUri=uri}

    fun prepareAttachment(uri:android.net.Uri,type:String) {
        busy=true
        scope.launch {
            try{attachment=com.jos.myapplication.media.AttachmentPreparation.prepare(context,uri,type);error=null}
            catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){error=e.message?:"Could not prepare attachment"}
            finally{busy=false}
        }
    }
    val videoPicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->if(uri!=null)prepareAttachment(uri,"video")}
    val audioPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)prepareAttachment(uri,"audio")}

    LaunchedEffect(contactUserId, owner, online, refresh) {
        if (repo != null && owner != null) {
            repo.createConversation(contactUserId, { conversation = it; error = null },
                { error = it.message ?: "Could not open chat" })
        }
    }
    LaunchedEffect(conversation, owner) {
        val id = conversation ?: return@LaunchedEffect
        if (app != null && owner != null) {
            app.database.changes.collect {
                try {
                    val rows = withContext(Dispatchers.IO) { app.database.messages(owner, id) }
                    if (repo?.getCurrentUserId() == owner) messages = rows
                } catch (_: Exception) { error = "Could not read local messages. Your draft is preserved." }
            }
        }
    }
    LaunchedEffect(conversation, online, refresh) {
        val id = conversation ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            ChatNotifications.activeConversation = id
            val listener = repo?.listen(id, { cached = it }, { error = "Could not refresh messages. Check access and retry." })
            try { awaitCancellation() } finally {
                listener?.remove()
                if (ChatNotifications.activeConversation == id) ChatNotifications.activeConversation = null
            }
        }
    }
    LaunchedEffect(displayedMessages.lastOrNull()?.id) {
        if (displayedMessages.isNotEmpty()) list.animateScrollToItem(displayedMessages.lastIndex)
        suggestions = emptyList()
        if (owner != null) {
            try { val result=SmartReplies.generate(messages, owner);suggestions=result.replies;smartLabel=result.label;smartError=null }
            catch (e: kotlinx.coroutines.CancellationException) { if (e !is kotlinx.coroutines.TimeoutCancellationException) throw e }
            catch (_: Exception) { smartError = "Smart replies unavailable. You can still type a message." }
        }
    }

    Scaffold(containerColor = BackgroundLight,
        topBar = {
            TopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InitialsAvatar(contactInitials, Color(0xFF90CAF9), 38.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(contactName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(if (!online) "Offline · local cache" else if (cached) "Cached · connecting" else "Connected to chat",
                            fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }, navigationIcon = {
                IconButton(onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite))
        },
        bottomBar = {
            Surface(color = SurfaceWhite) {
                Column(Modifier.navigationBarsPadding().imePadding().padding(8.dp)) {
                    if (suggestions.isNotEmpty()) {
                        Text(smartLabel, fontSize = 11.sp, color = TextSecondary)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            suggestions.forEach { reply -> SuggestionChip(onClick = { draft = reply }, label = { Text(reply) }) }
                        }
                    }
                    smartError?.let { Text(it, fontSize = 11.sp, color = TextSecondary) }
                    Row {
                        IconButton(enabled = !busy && conversation != null, onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Icon(Icons.Default.Image, "Choose a photo", tint = PurplePrimary) }
                        IconButton(enabled = !busy && conversation != null, onClick = {
                            videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.SingleMimeType("video/mp4")))
                        }) { Icon(Icons.Default.VideoLibrary, "Choose a video", tint = PurplePrimary) }
                        IconButton(enabled = !busy && conversation != null, onClick = {
                            audioPicker.launch(com.jos.myapplication.media.AttachmentPreparation.audioPickerTypes)
                        }) { Icon(Icons.Default.AudioFile, "Choose an audio file", tint = PurplePrimary) }
                        IconButton(enabled = !busy && conversation != null, onClick = {
                            if (DeviceLocation.permitted(context)) locate() else locationPermission.launch(DeviceLocation.permissions)
                        }) { Icon(Icons.Default.LocationOn, "Share current location", tint = PurplePrimary) }
                        if (busy) Text("Preparing…", Modifier.padding(12.dp), fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(draft, { if (it.length <= MessagePolicy.MAX_TEXT) draft = it },
                            Modifier.weight(1f), placeholder = { Text("Message") }, maxLines = 5,
                            enabled = !busy, shape = RoundedCornerShape(24.dp))
                        IconButton(enabled = !busy && conversation != null && draft.isNotBlank(), onClick = {
                            if (!busy) {
                                busy = true
                                scope.launch {
                                    try { repo?.enqueue(conversation!!, draft); draft = ""; error = null }
                                    catch (_: Exception) { error = "Could not save message. Your draft is preserved." }
                                    finally { busy = false }
                                }
                            }
                        }) {
                            if (busy) CircularProgressIndicator(Modifier.size(22.dp))
                            else Icon(Icons.AutoMirrored.Filled.Send, "Send message", tint = PurplePrimary)
                        }
                    }
                    Text("Queued messages send when ChatSphere reconnects. Failed messages need Retry.",
                        fontSize = 11.sp, color = TextSecondary)
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (error != null) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(error.orEmpty(), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { refresh++ }) { Text("Retry") }
                }
            }
            if (conversation != null && !endOfHistory) TextButton(enabled = online && !paging, onClick = {
                paging = true
                scope.launch {
                    try {
                        val oldest = displayedMessages.firstOrNull { it.state == SyncState.SENT || it.state == SyncState.CACHED }
                        val page = repo?.older(conversation!!, oldest).orEmpty()
                        olderMessages = (olderMessages + page).distinctBy { it.id }
                        endOfHistory = page.size < 100
                    } catch (_: Exception) { error = "Could not load older history. Check connection and retry." }
                    finally { paging = false }
                }
            }) { Text(if (paging) "Loading history…" else "Load older messages") }
            if (displayedMessages.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(if (conversation == null) "Opening chat…" else "No cached messages. Start the conversation.",
                        color = TextSecondary, modifier = Modifier.padding(24.dp))
                }
            } else LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(vertical = 12.dp)) {
                items(displayedMessages, key = { it.id }) { message ->
                    val mine = message.senderId == owner
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                        Surface(shape = RoundedCornerShape(18.dp), color = if (mine) BubbleSent else BubbleReceived) {
                            MessageContent(message, conversation.orEmpty(), repo, mine)
                        }
                        if(mine&&message.type in setOf("image","video","audio")&&message.localMediaPath!=null&&message.state==SyncState.SENDING){
                            val progress=uploadStates[message.id]
                            if(progress!=null){
                                LinearProgressIndicator(progress={progress.fraction},modifier=Modifier.width(220.dp))
                                Text(progress.stage+(if(progress.fraction<1f)" "+(progress.fraction*100).toInt()+"%" else ""),fontSize=11.sp)
                            }
                            TextButton(onClick={repo?.cancelUpload(conversation!!,message)}){Text("Stop upload")}
                        }
                        if(mine&&message.state==SyncState.FAILED)message.errorMessage?.let{Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp,modifier=Modifier.widthIn(max=280.dp))}
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(SimpleDateFormat("d MMM · h:mm a", locale).format(Date(message.timestamp)),
                                fontSize = 10.sp, color = TextSecondary)
                            if (mine) Text(" · " + when(message.state) {
                                SyncState.QUEUED -> "Queued locally"
                                SyncState.SENDING -> "Sending…"
                                SyncState.SENT -> "Sent to server"
                                SyncState.CACHED -> "Cached"
                                SyncState.FAILED -> if(message.type in setOf("image","video","audio"))"Upload failed" else "Send failed"
                            }, fontSize = 10.sp, color = TextSecondary)
                            if (mine && message.state == SyncState.FAILED) TextButton(onClick = {
                                app?.scope?.launch { repo?.retry(conversation!!, message) }
                            }) { Text("Retry") }
                        }
                    }
                }
            }
        }
    }
    qualityUri?.let{selected->
        AlertDialog(onDismissRequest={qualityUri=null},title={Text("Photo quality")},
            text={Text("Original keeps uploaded bytes, including any location metadata. Balanced resizes to 2560 px and removes metadata. Maximum "+(PhotoPreparation.maxBytes(context)/1_000_000)+" MB per file.")},
            confirmButton={TextButton(onClick={qualityUri=null;preparePhoto(selected,com.jos.myapplication.media.PhotoQuality.ORIGINAL)}){Text("Original")}},
            dismissButton={Row{
                TextButton(onClick={qualityUri=null;preparePhoto(selected,com.jos.myapplication.media.PhotoQuality.BALANCED)}){Text("Balanced")}
                TextButton(onClick={qualityUri=null}){Text("Cancel")}
            }})
    }
    attachment?.let { selected ->
        AlertDialog(onDismissRequest={if(!busy){attachment=null;scope.launch(Dispatchers.IO){selected.file.delete()}}},
            title={Text(if(selected.type=="video")"Share this video?" else "Share this audio?")},
            text={Column{Text("The full file will be shared privately with this conversation. Maximum 50 MB per file.")
                AttachmentContent(Message("preview-attachment",owner.orEmpty(),"",0,type=selected.type,mimeType=selected.mimeType,
                    mediaSize=selected.size,localMediaPath=selected.file.path,mediaName=selected.name,durationMs=selected.durationMs),"",null,Color.Unspecified)}},
            confirmButton={TextButton(enabled=!busy,onClick={busy=true;scope.launch{
                try{repo?.enqueueAttachment(conversation!!,selected);attachment=null;error=null}
                catch(_:Exception){error="Could not save attachment. Retry or cancel."}
                finally{busy=false}
            }}){Text(if(busy)"Saving…" else "Send")}},
            dismissButton={TextButton(enabled=!busy,onClick={attachment=null;scope.launch(Dispatchers.IO){selected.file.delete()}}){Text("Cancel")}})
    }
    fun discardPreview(){val file=photo?.file;photo=null;fix=null;if(file!=null)scope.launch(Dispatchers.IO){file.delete()}}
    if (photo != null || fix != null) {
        AlertDialog(onDismissRequest = { if (!busy) { discardPreview() } },
            title = { Text(if (photo != null) "Share this photo?" else "Share this location?") },
            text = {
                if (photo != null) {
                    Column {
                        Text("The full prepared image will upload to private Supabase Storage through your authorized backend. Recipients can view and save the uploaded file.")
                        MessageContent(Message("preview-photo", owner.orEmpty(), "", 0, "image", mimeType=photo?.mimeType,mediaSize=photo?.size,localMediaPath=photo?.file?.path), "", null, false)
                    }
                } else Text("Send ${fix?.latitude}, ${fix?.longitude} (accuracy ±${fix?.accuracy?.toInt()} m) to $contactName? This is a single location snapshot.")
            },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        if (photo != null) repo?.enqueuePhoto(conversation!!, photo!!)
                        else repo?.enqueue(conversation!!, type = "location", latitude = fix!!.latitude, longitude = fix!!.longitude)
                        photo = null; fix = null; error = null
                    } catch (_: Exception) { error = "Could not save attachment. Retry or cancel." }
                    finally { busy = false }
                }
            }) { Text(if (busy) "Saving…" else "Send") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { discardPreview() }) { Text("Cancel") } })
    }
}

package com.jos.myapplication.screens.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jos.myapplication.ChatSphereApp
import com.jos.myapplication.ai.AssistantClient
import com.jos.myapplication.ui.theme.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.jos.myapplication.network.LocalNetworkAccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = ChatSphereApp.instance
    val owner = app.repository.getCurrentUserId().orEmpty()
    var connectionVersion by remember { mutableIntStateOf(0) }
    val client = remember(connectionVersion) { AssistantClient(context) }
    var showBackend by remember { mutableStateOf(false) }
    var backendDraft by remember { mutableStateOf("") }
    var backendError by remember { mutableStateOf<String?>(null) }
    val consentPrefs=remember { context.getSharedPreferences("assistant_consent",0) }
    var privacyAccepted by remember(owner) { mutableStateOf(consentPrefs.getBoolean("study_consent_"+owner,false)) }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    var history by remember(owner) { mutableStateOf("[]") }
    var draft by rememberSaveable(owner) { mutableStateOf("") }
    var requestJob by remember{mutableStateOf<kotlinx.coroutines.Job?>(null)}
    var requestStatus by remember{mutableStateOf<String?>(null)}
    var busy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var localGranted by remember { mutableStateOf(LocalNetworkAccess.granted(context)) }
    val localPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        localGranted = LocalNetworkAccess.granted(context)
        if (!localGranted) error = "Local AI access denied. Enable Nearby devices in Android app settings to retry."
    }
    LaunchedEffect(owner) {
        history = withContext(Dispatchers.IO) { app.database.record(owner, "assistant", "history") } ?: "[]"
        loaded = true
    }
    suspend fun persist(json: String) {
        withContext(Dispatchers.IO) { app.database.putRecord(owner, "assistant", "history", json) }
        history = json
    }
    val messages = JSONArray(history)
    val awaitingReply = messages.length() > 0 && messages.getJSONObject(messages.length() - 1).optString("role") == "user"
    fun request(retry: Boolean) {
        if (busy || !loaded || owner.isBlank() || !privacyAccepted) return
        busy = true
        requestJob=scope.launch {
            try {
                val conversation = JSONArray(history)
                if (!retry) {
                    require(draft.isNotBlank() && draft.length <= 1000 && conversation.length() < 40)
                    conversation.put(JSONObject().put("role", "user").put("content", draft.trim()).put("id", UUID.randomUUID().toString()))
                    persist(conversation.toString()); draft = ""
                }
                val id = conversation.getJSONObject(conversation.length() - 1).getString("id")
                val response = client.reply(conversation, id){requestStatus=it}
                conversation.put(JSONObject().put("role", "assistant").put("content", response).put("id", UUID.randomUUID().toString()))
                persist(conversation.toString()); error = null
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error=e.message?:"AI is unavailable" }
            finally { busy = false;requestStatus=null }
        }
    }
    if(showBackend)AlertDialog(onDismissRequest={showBackend=false},title={Text("Connect your backend")},
        text={Column{
            Text("Enter only your trusted backend base URL. Your Firebase sign-in token is used there for authorization. USB phone: http://localhost:8787 after adb reverse. Emulator: http://10.0.2.2:8787. See docs/SETUP.md.")
            OutlinedTextField(backendDraft,{backendDraft=it},label={Text("Backend base URL")},singleLine=true)
            backendError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }},
        confirmButton={TextButton(onClick={
            try{com.jos.myapplication.network.BackendConnection.save(context,backendDraft);connectionVersion++;showBackend=false;backendError=null;error=null}
            catch(e:Exception){backendError=e.message}
        }){Text("Save")}},dismissButton={TextButton(onClick={showBackend=false}){Text("Cancel")}})
    LaunchedEffect(history) { if (messages.length() > 0) list.animateScrollToItem(messages.length() - 1) }
    Scaffold(containerColor = BackgroundLight, topBar = {
        TopAppBar(title = { Text("Student AI assistant", color = PurplePrimary) }, navigationIcon = {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }, actions = {
            TextButton(enabled = !busy && loaded, onClick = { scope.launch { persist("[]"); error = null } }) { Text("New chat") }
        })
    }, bottomBar = {
        Column(Modifier.navigationBarsPadding().imePadding().padding(12.dp)) {
            if(busy){
                LinearProgressIndicator(Modifier.fillMaxWidth())
                requestStatus?.let{Text(it,fontSize=12.sp)}
                TextButton(enabled=awaitingReply,onClick={requestJob?.cancel();error="Request stopped. Retry uses the same message."}){Text("Stop waiting")}
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (awaitingReply && !busy) TextButton(onClick = { request(true) }, enabled = client.configured && privacyAccepted) { Text("Retry last request") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(draft, { if (it.length <= 1000) draft = it }, Modifier.weight(1f), placeholder = { Text("Ask for study help") }, maxLines = 4,
                    enabled = !busy && !awaitingReply)
                TextButton(onClick = { request(false) }, enabled = loaded && !busy && !awaitingReply && client.configured && privacyAccepted && draft.isNotBlank() && messages.length() < 40) { Text("Send") }
            }
            Text("Only this assistant chat is sent to the configured backend. AI can be wrong. Limit: 20 turns; New chat clears local history.", fontSize = 11.sp)
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedButton(onClick={backendDraft=com.jos.myapplication.network.BackendConnection.base(context);showBackend=true},modifier=Modifier.padding(horizontal=16.dp)){Text("Connect backend")}
            if(!privacyAccepted)Row(Modifier.padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
                Checkbox(privacyAccepted,{privacyAccepted=it;consentPrefs.edit().putBoolean("study_consent_"+owner,it).apply()})
                Text("I am 18+. I will use non-sensitive study questions. When Gemini free tier is used, Google may review and use prompts and answers to improve products.",fontSize=11.sp)
            }
            if (client.usesLocalNetwork && !localGranted) {
                Text("The AI backend is on your local computer. Android 17 requires local-network permission for this connection.", Modifier.padding(16.dp))
                Button(onClick = { if (Build.VERSION.SDK_INT >= 37) localPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) }) { Text("Allow local AI connection") }
            }
            if (!client.configured) Text("AI backend is not configured. No generated replies are available. See docs/SETUP.md to connect a secure backend.",
                Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(16.dp)) {
                items((0 until messages.length()).map { messages.getJSONObject(it) }, key = { it.getString("id") }) { message ->
                    val mine = message.getString("role") == "user"
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                        Text(if (mine) "You" else "AI-generated", fontSize = 11.sp)
                        Surface(shape = RoundedCornerShape(18.dp), color = if (mine) BubbleSent else BubbleReceived) {
                            Text(message.getString("content"), Modifier.widthIn(max = 320.dp).padding(14.dp), color = if (mine) Color.White else TextPrimary)
                        }
                    }
                }
            }
        }
    }
}

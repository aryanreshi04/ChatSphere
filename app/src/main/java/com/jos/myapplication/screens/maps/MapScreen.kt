package com.jos.myapplication.screens.maps

import android.annotation.SuppressLint
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.webkit.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.jos.myapplication.BuildConfig
import com.jos.myapplication.location.CampusPlaces
import com.jos.myapplication.location.DirectionsPolicy
import com.jos.myapplication.data.MessagePolicy
import com.jos.myapplication.location.DeviceLocation
import com.jos.myapplication.location.MapIntents
import com.jos.myapplication.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val prefs = remember { context.getSharedPreferences("chatsphere_map", 0) }
    var tiles by remember { mutableStateOf(prefs.getBoolean("online_tiles", false)) }
    var tracking by remember { mutableStateOf(false) }
    var follow by remember { mutableStateOf(true) }
    var location by remember { mutableStateOf<Location?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var config by remember { mutableStateOf(JSONObject().put("destinations", JSONArray())) }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    val campusStore=remember{CampusPlaces(context)}
    var editingPlaceId by remember{mutableStateOf<String?>(null)}
    var campusEditor by remember{mutableStateOf(false)}
    var campusName by remember{mutableStateOf("")}
    var placeName by remember{mutableStateOf("")}
    var placeLatitude by remember{mutableStateOf("")}
    var placeLongitude by remember{mutableStateOf("")}
    var confirmedPlace by remember{mutableStateOf(false)}
    var editorError by remember{mutableStateOf<String?>(null)}
    var useGoogle by remember{mutableStateOf(BuildConfig.GOOGLE_MAPS_CONFIGURED)}
    var googleAttempt by remember{mutableIntStateOf(0)}
    var mode by remember{mutableStateOf("walking")}
    var ready by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        tracking = DeviceLocation.permitted(context)
        if (!tracking) error = "Location permission denied. You can still browse the map. Enable permission in Android app settings to try again."
    }
    LaunchedEffect(Unit) {
        try { config = withContext(Dispatchers.IO) { campusStore.load(context.assets.open("map/config.json").bufferedReader().use { JSONObject(it.readText()) }) } }
        catch (_: Exception) { error = "Map configuration could not be read." }
    }
    LaunchedEffect(tracking, refresh) {
        if (!tracking) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            loading = true
            val timeout = launch { kotlinx.coroutines.delay(25_000); if (location == null) { loading = false; error = "Waiting for a GPS fix. Check device location settings and move outdoors." } }
            try {
                DeviceLocation.updates(context).collect { fix -> location = fix; error = null; loading = false; timeout.cancel() }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { loading = false; error = e.message ?: "Location unavailable" }
            finally { timeout.cancel() }
        }
    }
    val destinations = config.optJSONArray("destinations") ?: JSONArray()
    val render = JSONObject().put("tiles", tiles).put("follow", follow)
        .put("tileUrl", config.optString("tileUrl", "https://tile.openstreetmap.org/{z}/{x}/{y}.png"))
        .put("destinations", destinations)
    location?.let { render.put("location", JSONObject().put("latitude", it.latitude).put("longitude", it.longitude).put("accuracy", it.accuracy)) }
    selected?.let { render.put("target", it) }

    Scaffold(containerColor = BackgroundLight, topBar = {
        TopAppBar(title = { Text("Live map", color = PurplePrimary) }, navigationIcon = {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Text("Your location updates only while this screen is visible. It is not shared with contacts.", fontSize = 12.sp)
                Row {
                    Button(onClick = {
                        error = null; refresh++
                        if (DeviceLocation.permitted(context)) tracking = true else permission.launch(DeviceLocation.permissions)
                    }) { Text(if (tracking) "Refresh location" else "Use my location") }
                    TextButton(onClick = { follow = !follow }) { Text(if (follow) "Following: on" else "Following: off") }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                location?.let { Text("Accuracy ±${it.accuracy.toInt()} m. Approximate permission gives a less precise position.", fontSize = 12.sp) }
                if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Row {
                    TextButton(enabled=BuildConfig.GOOGLE_MAPS_CONFIGURED,onClick={googleAttempt++;useGoogle=true;error=null}){Text("Google Maps")}
                    TextButton(onClick={useGoogle=false}){Text("OpenStreetMap")}
                }
                TextButton(onClick={editingPlaceId=null;placeName="";placeLatitude="";placeLongitude="";confirmedPlace=false;campusEditor=true;campusName=config.optString("name");editorError=null}){Text("Add destination")}
                if(!BuildConfig.GOOGLE_MAPS_CONFIGURED)Text("Google Maps SDK key is not configured; the live OpenStreetMap view and Google Maps directions remain available.",fontSize=11.sp)
                if(destinations.length()==0)Text("No campus destinations configured. Add your real locations with verified coordinates.",fontSize=12.sp)
                Row {
                    Switch(tiles, { tiles = it; prefs.edit().putBoolean("online_tiles", it).apply() })
                    Text("Online OpenStreetMap tiles", Modifier.padding(12.dp))
                }
                Text("OSM receives the viewed map area and your IP address. Best-effort demo service; no bulk/offline downloads. © OpenStreetMap contributors.", fontSize = 11.sp)
                if (destinations.length() > 0) {
                    Text(config.optString("name", "Saved places"))
                    Row {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { expanded = true }) { Text(selected?.optString("name") ?: "Choose destination") }
                            DropdownMenu(expanded, { expanded = false }) {
                                for (index in 0 until destinations.length()) {
                                    val place = destinations.getJSONObject(index)
                                    DropdownMenuItem(text = { Text(place.optString("name") + if (place.optBoolean("verified")) "" else " (unverified)") },
                                        onClick = { selected = place; follow = false; expanded = false })
                                }
                            }
                        }
                        selected?.let { place ->
                            TextButton(enabled = place.optBoolean("verified") && MessagePolicy.validLocation(place.optDouble("latitude"), place.optDouble("longitude")),
                                onClick = { MapIntents.open(context, place.getDouble("latitude"), place.getDouble("longitude"), true,location?.let{it.latitude to it.longitude},mode) }) { Text("Directions") }
                        }
                    }
                }
            }
            if(selected!=null)Row(Modifier.padding(horizontal=16.dp).horizontalScroll(rememberScrollState())){
                TextButton(onClick={mode=if(mode=="walking")"driving" else "walking"}){Text(if(mode=="walking")"Route: walking"else"Route: driving")}
                TextButton(onClick={selected?.let{MapIntents.open(context,it.getDouble("latitude"),it.getDouble("longitude"))}}){Text("Open in Google Maps")}
                TextButton(onClick={selected?.let{place->editingPlaceId=place.optString("id");campusName=config.optString("name");placeName=place.optString("name");placeLatitude=place.optDouble("latitude").toString();placeLongitude=place.optDouble("longitude").toString();confirmedPlace=false;editorError=null;campusEditor=true}}){Text("Edit destination")}
            }
            if(useGoogle&&BuildConfig.GOOGLE_MAPS_CONFIGURED)GoogleMapPanel(config,location,selected,follow,googleAttempt,
                Modifier.weight(1f).fillMaxWidth()){message->error=message;useGoogle=false}
            else AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = {
                ready=false
                WebView(it).apply {
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = false; settings.allowContentAccess = false
                    settings.setGeolocationEnabled(false)
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.userAgentString = "ChatSphere/2.0 (+https://github.com/aryanreshi04/ChatSphere)"
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                            val uri = request.url
                            val name = uri.lastPathSegment
                            if (uri.host == "appassets.androidplatform.net" && name in setOf("map.html","map.js","map.css","leaflet.js","leaflet.css")) {
                                val mime = when { name!!.endsWith("js") -> "application/javascript"; name.endsWith("css") -> "text/css"; else -> "text/html" }
                                return WebResourceResponse(mime, "UTF-8", context.assets.open("map/$name"))
                            }
                            val allowedHost = Uri.parse(config.optString("tileUrl", "https://tile.openstreetmap.org/{z}/{x}/{y}.png")).host
                            if (uri.scheme == "https" && uri.host == allowedHost) return null
                            return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), "".byteInputStream())
                        }
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                            if (request.url.host == "appassets.androidplatform.net") return false
                            if (request.isForMainFrame && request.url.scheme == "https" && request.url.host in setOf("www.google.com", "www.openstreetmap.org")) {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                            }
                            return true
                        }
                        override fun onPageFinished(view: WebView?, url: String?) { ready = true }
                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, failure: WebResourceError?) {
                            if (request?.isForMainFrame == true) error = "Map could not load. Return and reopen this screen."
                        }
                    }
                    loadUrl("https://appassets.androidplatform.net/assets/map/map.html")
                }
            }, update = { view -> if (ready) view.evaluateJavascript("window.renderMap(${render});", null) },
                onRelease = { it.stopLoading(); it.destroy() })
        }
    }
    if(campusEditor)AlertDialog(onDismissRequest={campusEditor=false},title={Text(if(editingPlaceId==null)"Campus destination"else"Edit destination")},
        text={Column(Modifier.verticalScroll(rememberScrollState())){
            Text("Enter your actual campus/place name and coordinates. These are saved on this device; no example campus is assumed.",fontSize=12.sp)
            OutlinedTextField(campusName,{campusName=it.take(80)},label={Text("Campus or place group")})
            OutlinedTextField(placeName,{placeName=it.take(80)},label={Text("Destination name")})
            OutlinedTextField(placeLatitude,{placeLatitude=it.take(30)},label={Text("Latitude")},singleLine=true)
            OutlinedTextField(placeLongitude,{placeLongitude=it.take(30)},label={Text("Longitude")},singleLine=true)
            TextButton(enabled=location!=null,onClick={location?.let{placeLatitude=it.latitude.toString();placeLongitude=it.longitude.toString()}}){Text("Use current coordinates")}
            Row{Checkbox(confirmedPlace,{confirmedPlace=it});Text("I checked these destination coordinates",fontSize=12.sp)}
            editorError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }},confirmButton={TextButton(enabled=confirmedPlace,onClick={
            try{
                val lat=placeLatitude.toDoubleOrNull()?:error("Enter a numeric latitude")
                val lon=placeLongitude.toDoubleOrNull()?:error("Enter a numeric longitude")
                config=campusStore.add(config,campusName,placeName,lat,lon,editingPlaceId)
                selected=config.getJSONArray("destinations").let{array->(0 until array.length()).map{array.getJSONObject(it)}.first{it.optString("name")==placeName.trim()}}
                follow=false;placeName="";confirmedPlace=false;campusEditor=false;editorError=null
            }catch(e:Exception){editorError=e.message?:"Could not save destination"}
        }){Text("Save destination")}},dismissButton={TextButton(onClick={campusEditor=false}){Text("Cancel")}})

}

package com.jos.myapplication.screens.maps

import android.annotation.SuppressLint
import android.location.Location
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.jos.myapplication.location.DeviceLocation
import org.json.JSONObject

@SuppressLint("MissingPermission")
@Composable
fun GoogleMapPanel(config:JSONObject,location:Location?,selected:JSONObject?,follow:Boolean,attempt:Int,
                   modifier:Modifier,onUnavailable:(String)->Unit) {
    val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
    val fail by rememberUpdatedState(onUnavailable)
    var map by remember(attempt){mutableStateOf<GoogleMap?>(null)}
    var loaded by remember(attempt){mutableStateOf(false)}
    var lastTarget by remember(attempt){mutableStateOf<String?>(null)}
    val view=remember(attempt){runCatching{MapView(context).apply{onCreate(null)}}.getOrNull()}
    if(view==null){LaunchedEffect(attempt){fail("Google Play services/map initialization unavailable. Showing OpenStreetMap.")};return}
    DisposableEffect(view,lifecycle){
        var started=false;var resumed=false;var destroyed=false
        fun start(){if(!started){view.onStart();started=true}}
        fun resume(){start();if(!resumed){view.onResume();resumed=true}}
        fun pause(){if(resumed){view.onPause();resumed=false}}
        fun stop(){pause();if(started){view.onStop();started=false}}
        val observer=LifecycleEventObserver{_,event->when(event){
            Lifecycle.Event.ON_START->start();Lifecycle.Event.ON_RESUME->resume()
            Lifecycle.Event.ON_PAUSE->pause();Lifecycle.Event.ON_STOP->stop()
            Lifecycle.Event.ON_DESTROY->{stop();view.onDestroy();destroyed=true};else->Unit
        }}
        lifecycle.addObserver(observer)
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))resume()
        else if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))start()
        onDispose{lifecycle.removeObserver(observer);if(!destroyed){stop();view.onDestroy()}}
    }

    LaunchedEffect(view){kotlinx.coroutines.delay(20000);if(!loaded)fail("Google map did not load. Check Maps SDK configuration/network. OpenStreetMap fallback is available.")}
    Box(modifier){
    AndroidView(modifier=Modifier.fillMaxSize(),factory={
        view.apply{getMapAsync{ready->
            map=ready
            ready.uiSettings.isMapToolbarEnabled=true
            ready.uiSettings.isZoomControlsEnabled=true
            ready.setOnMapLoadedCallback{loaded=true}
            ready.setOnMapClickListener{point->ready.animateCamera(CameraUpdateFactory.newLatLng(point))}
        }}
    },update={
        map?.let{ready->runCatching{
            ready.clear();ready.isMyLocationEnabled=DeviceLocation.permitted(context)
            val places=config.optJSONArray("destinations")
            if(places!=null)for(i in 0 until places.length()){
                val p=places.getJSONObject(i)
                ready.addMarker(MarkerOptions().position(LatLng(p.getDouble("latitude"),p.getDouble("longitude"))).title(p.optString("name")))
            }
            if(selected!=null&&lastTarget!=selected.optString("id",selected.optString("name"))){
                lastTarget=selected.optString("id",selected.optString("name"))
                ready.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(selected.getDouble("latitude"),selected.getDouble("longitude")),17f))
            }else if(follow&&location!=null)ready.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude,location.longitude),16f))
        }.onFailure{fail("Google map is unavailable. Showing the OpenStreetMap fallback.")}}
    })
    if(!loaded)Surface(Modifier.padding(12.dp)){Column(Modifier.padding(12.dp)){
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Loading Google Maps… You can select OpenStreetMap above if it is unavailable.")
    }}
    }
}

package com.jos.myapplication.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.net.URI

object LocalNetworkAccess {
    fun granted(context: Context) = Build.VERSION.SDK_INT < 37 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
    fun localUrl(value: String): Boolean = runCatching {
        val host = URI(value).host?.lowercase().orEmpty()
        host in setOf("localhost", "127.0.0.1", "10.0.2.2", "[::1]") || host.startsWith("10.") ||
            host.startsWith("192.168.") || host.endsWith(".local") ||
            (host.startsWith("172.") && (host.split('.').getOrNull(1)?.toIntOrNull() ?: -1) in 16..31)
    }.getOrDefault(false)
}

@Composable
fun EmulatorNetworkGate(context: Context, content: @Composable () -> Unit) {
    var granted by remember { mutableStateOf(LocalNetworkAccess.granted(context)) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = LocalNetworkAccess.granted(context); denied = !granted
    }
    if (granted) content()
    else Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Local Firebase emulator", style = MaterialTheme.typography.titleLarge)
        Text("This isolated demo connects to Firebase emulators on your computer. Android 17 requires nearby/local network permission. Your normal Firebase app does not need it for internet chats.")
        Button(onClick = { if (Build.VERSION.SDK_INT >= 37) launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) }) { Text("Allow local emulator connection") }
        if (denied) Text("Permission denied. Use Android app settings to allow Nearby devices, then reopen the demo.")
    }
}

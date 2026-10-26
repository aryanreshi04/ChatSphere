package com.jos.myapplication

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.jos.myapplication.notifications.NotificationTarget
import com.jos.myapplication.network.EmulatorNetworkGate
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.jos.myapplication.navigation.AppNavigation
import com.jos.myapplication.ui.theme.ChatSphereTheme

class MainActivity : ComponentActivity() {
    private var pendingNotification by mutableStateOf<NotificationTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingNotification = NotificationTarget.from(intent)

        setContent {
            ChatSphereTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (BuildConfig.USE_FIREBASE_EMULATORS) {
                        EmulatorNetworkGate(this@MainActivity) { AppNavigation(pendingNotification, { pendingNotification = null }) }
                    } else AppNavigation(pendingNotification, { pendingNotification = null })
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotification = NotificationTarget.from(intent)
    }
    override fun onStart() {
        super.onStart()
        (application as ChatSphereApp).apply { foreground = true; watchForegroundMessages() }
    }
    override fun onStop() {
        (application as ChatSphereApp).apply { foreground = false; watchForegroundMessages() }
        super.onStop()
    }
}

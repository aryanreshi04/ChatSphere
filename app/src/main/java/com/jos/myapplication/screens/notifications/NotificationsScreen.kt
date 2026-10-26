package com.jos.myapplication.screens.notifications

import android.content.Context
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.jos.myapplication.notifications.ChatNotifications
import kotlinx.coroutines.launch
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.jos.myapplication.ui.theme.BackgroundLight
import com.jos.myapplication.ui.theme.ChatSphereTheme
import com.jos.myapplication.ui.theme.DividerGray
import com.jos.myapplication.ui.theme.PurplePrimary
import com.jos.myapplication.ui.theme.SurfaceWhite
import com.jos.myapplication.ui.theme.TextPrimary
import com.jos.myapplication.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val userId = if (isPreview) "preview" else FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val prefs = remember(context) {
        context.getSharedPreferences("chatsphere_settings", Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()
    var testBusy by remember{mutableStateOf(false)}
    var testStatus by remember{mutableStateOf<String?>(null)}
    var testRequest by remember(userId){mutableStateOf<String?>(null)}
    var studyEnabled by remember(userId){mutableStateOf(prefs.getBoolean("ai_study_alerts_$userId",false))}
    var deviceReady by remember { mutableStateOf(false) }
    var permissionStatus by remember { mutableStateOf<String?>(null) }
    var categories by remember(userId) { mutableStateOf(prefs.getBoolean("ml_categories_$userId", false)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        permissionStatus = if (allowed) "Notification permission granted" else "Permission denied. You can enable notifications in Android settings below."
        if (allowed) scope.launch { deviceReady = runCatching { ChatNotifications.registerDevice() }.isSuccess }
    }
    LaunchedEffect(userId) {
        if (!isPreview && userId.isNotBlank()) deviceReady = runCatching { ChatNotifications.registerDevice() }.isSuccess
    }

    var messageAlerts by remember(userId) {
        mutableStateOf(prefs.getBoolean("message_alerts_$userId", true))
    }
    var soundEnabled by remember(userId) {
        mutableStateOf(prefs.getBoolean("notification_sound_$userId", true))
    }
    var vibrationEnabled by remember(userId) {
        mutableStateOf(prefs.getBoolean("notification_vibration_$userId", true))
    }

    Scaffold(
        containerColor = BackgroundLight,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Notifications", color = PurplePrimary, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BackgroundLight)
            )
        }
    ) { insets ->
        Column(
            modifier = Modifier
                .padding(insets)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Message preferences", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else permissionStatus = "Use Android notification settings below to allow alerts."
            }) { Text("Allow notifications") }
            permissionStatus?.let { Text(it, fontSize = 12.sp, color = TextSecondary) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column {
                    NotificationSwitchRow(
                        icon = Icons.Default.Notifications,
                        title = "Message alerts",
                        subtitle = "Allow new-message alerts",
                        checked = messageAlerts,
                        onCheckedChange = {
                            messageAlerts = it
                            prefs.edit().putBoolean("message_alerts_$userId", it).apply()
                        }
                    )
                    HorizontalDivider(color = DividerGray)
                    NotificationSwitchRow(
                        icon = Icons.Default.VolumeUp,
                        title = "Sound",
                        subtitle = "Play a sound for new messages",
                        checked = soundEnabled,
                        enabled = messageAlerts,
                        onCheckedChange = {
                            soundEnabled = it
                            prefs.edit().putBoolean("notification_sound_$userId", it).apply()
                        }
                    )
                    HorizontalDivider(color = DividerGray)
                    NotificationSwitchRow(
                        icon = Icons.Default.Vibration,
                        title = "Vibration",
                        subtitle = "Vibrate for new messages",
                        checked = vibrationEnabled,
                        enabled = messageAlerts,
                        onCheckedChange = {
                            vibrationEnabled = it
                            prefs.edit().putBoolean("notification_vibration_$userId", it).apply()
                        }
                    )
                }
            }

            NotificationSwitchRow(Icons.Default.Notifications,"AI study alerts",
                "Opt-in local ML classifies incoming text and highlights Study messages. Experimental; no deadline extraction or urgency prediction. At most one study alert every 30 seconds.",
                studyEnabled,onCheckedChange={studyEnabled=it;prefs.edit().putBoolean("ai_study_alerts_$userId",it).apply()})
            Button(enabled=!testBusy&&userId.isNotBlank(),onClick={
                testBusy=true
                scope.launch{try{
                    ChatNotifications.registerDevice()
                    val id=testRequest?:java.util.UUID.randomUUID().toString().also{testRequest=it}
                    val result=com.jos.myapplication.network.BackendConnection.json(context,"/v1/push/test",org.json.JSONObject().put("requestId",id))
                    testStatus=if(result.optInt("accepted")>0)"Test accepted by FCM. Check this device's notification tray; acceptance alone does not prove delivery."else"Test processed. Check the tray and Android permission/channel settings."
                    testRequest=null
                }catch(e:kotlinx.coroutines.CancellationException){throw e}
                catch(e:Exception){testStatus=e.message?.take(300)?:"Could not send test. Check the backend and Firebase configuration."}
                finally{testBusy=false}}
            }){Text(if(testBusy)"Sending test…"else"Send test push to my devices")}
            testStatus?.let{Text(it,fontSize=12.sp,color=TextSecondary)}
            Text("Device permissions", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            NotificationSwitchRow(Icons.Default.Notifications, "Experimental ML categories",
                "On-device Study / Meetup / Conversation labels; no urgency prediction. Small synthetic training set, no accuracy claims.",
                categories, onCheckedChange = { categories = it; prefs.edit().putBoolean("ml_categories_$userId", it).apply() })
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                )
                            } catch (_: Exception) {
                                Toast.makeText(context, "Can't open device settings", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Settings, null, tint = PurplePrimary)
                    Spacer(modifier = Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Android notification settings", fontWeight = FontWeight.Medium, color = TextPrimary)
                        Text("Manage ChatSphere alerts on this device", fontSize = 12.sp, color = TextSecondary)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                }
            }

            Text(
                "Foreground alerts work while ChatSphere is open outside the active conversation. Device registration: ${if (deviceReady) "saved" else "unavailable; check connection and Firestore rules"}. Closed-app push additionally needs a trusted sender running on a computer or server. Delivery has not been verified on this device. Android channel settings can override sound and vibration preferences.",
                fontSize = 13.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun NotificationSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) PurplePrimary else Color.Gray)
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (enabled) TextPrimary else TextSecondary, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Preview(showBackground = true)
@Composable
private fun NotificationsScreenPreview() {
    ChatSphereTheme { NotificationsScreen(onBackClick = {}) }
}

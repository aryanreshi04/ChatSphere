package com.jos.myapplication.screens.settings

import android.content.Context
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun SettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val userId = if (isPreview) "preview" else FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val prefs = remember(context) {
        context.getSharedPreferences("chatsphere_settings", Context.MODE_PRIVATE)
    }

    var showEmail by remember(userId) {
        mutableStateOf(prefs.getBoolean("show_email_$userId", true))
    }
    var confirmLogout by remember(userId) {
        mutableStateOf(prefs.getBoolean("confirm_logout_$userId", true))
    }
    var showAboutDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BackgroundLight,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", color = PurplePrimary, fontWeight = FontWeight.Bold) },
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
            Text("Privacy & account", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column {
                    SettingSwitchRow(
                        icon = Icons.Default.Visibility,
                        title = "Show email on profile",
                        subtitle = "Display your email under your name",
                        checked = showEmail,
                        onCheckedChange = {
                            showEmail = it
                            prefs.edit().putBoolean("show_email_$userId", it).apply()
                        }
                    )
                    HorizontalDivider(color = DividerGray)
                    SettingSwitchRow(
                        icon = Icons.Default.Logout,
                        title = "Confirm before logout",
                        subtitle = "Ask before signing out of ChatSphere",
                        checked = confirmLogout,
                        onCheckedChange = {
                            confirmLogout = it
                            prefs.edit().putBoolean("confirm_logout_$userId", it).apply()
                        }
                    )
                }
            }

            Text("About", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAboutDialog = true }
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = PurplePrimary)
                    Spacer(modifier = Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("About ChatSphere", fontWeight = FontWeight.Medium, color = TextPrimary)
                        Text("Learn about this app", fontSize = 12.sp, color = TextSecondary)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                }
            }

            Text(
                "These account preferences are saved locally on this device. They don't change settings on other devices.",
                fontSize = 13.sp,
                color = TextSecondary
            )
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About ChatSphere") },
            text = {
                Text("ChatSphere is an Android chat app built with Kotlin, Jetpack Compose, and Firebase. Your messages and profile data are managed through Firebase.")
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = PurplePrimary)
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = TextSecondary)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ChatSphereTheme { SettingsScreen(onBackClick = {}) }
}

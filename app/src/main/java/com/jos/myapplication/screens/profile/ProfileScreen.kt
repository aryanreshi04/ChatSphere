package com.jos.myapplication.screens.profile

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jos.myapplication.components.AppBottomNavBar
import com.jos.myapplication.components.BottomNavTab
import com.jos.myapplication.ui.theme.BackgroundLight
import com.jos.myapplication.ui.theme.ChatSphereTheme
import com.jos.myapplication.ui.theme.DividerGray
import com.jos.myapplication.ui.theme.LogoutRed
import com.jos.myapplication.ui.theme.PurplePrimary
import com.jos.myapplication.ui.theme.SurfaceWhite
import com.jos.myapplication.ui.theme.TextPrimary
import com.jos.myapplication.ui.theme.TextSecondary
import com.jos.myapplication.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBackClick: () -> Unit,
    onChatsClick: () -> Unit,
    onLogoutClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current

    // Firebase is never initialized inside Android Studio Compose Preview.
    val authUser = if (isPreview) null else FirebaseAuth.getInstance().currentUser
    val userId = authUser?.uid
    val preferences = remember(context) {
        context.getSharedPreferences("chatsphere_profile_photos", Context.MODE_PRIVATE)
    }

    var userName by remember(userId) {
        mutableStateOf(
            if (isPreview) "Preview User"
            else authUser?.displayName?.takeIf { it.isNotBlank() }
                ?: authUser?.email?.substringBefore('@')
                ?: "User"
        )
    }
    var userEmail by remember(userId) {
        mutableStateOf(if (isPreview) "preview@example.com" else authUser?.email.orEmpty())
    }
    var photoUri by remember(userId) {
        mutableStateOf(
            if (isPreview || userId == null) null
            else preferences.getString("profile_photo_$userId", null)
        )
    }
    var showEditDialog by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf("") }
    var savingName by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Observe the logged-in user's Firestore profile so changes appear immediately.
    DisposableEffect(userId, isPreview) {
        if (isPreview || userId == null) {
            onDispose { }
        } else {
            val listener = FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .addSnapshotListener { document, error ->
                    if (error == null && document != null && document.exists()) {
                        val name = document.getString("name")
                        val email = document.getString("email")
                        if (!name.isNullOrBlank()) userName = name
                        if (!email.isNullOrBlank()) userEmail = email
                    }
                }
            onDispose { listener.remove() }
        }
    }

    // Choose an image with a persistent read permission (no storage permission needed).
    val pickPhoto = if (isPreview) null else rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { selectedUri: Uri? ->
        if (selectedUri != null && userId != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    selectedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                preferences.edit()
                    .putString("profile_photo_$userId", selectedUri.toString())
                    .apply()
                photoUri = selectedUri.toString()
                Toast.makeText(context, "Profile picture updated", Toast.LENGTH_SHORT).show()
            } catch (exception: Exception) {
                Toast.makeText(
                    context,
                    "Could not save photo: ${exception.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val photoBitmap by produceState<Bitmap?>(initialValue = null, key1 = photoUri) {
        value = if (isPreview || photoUri.isNullOrBlank()) null
        else withContext(Dispatchers.IO) {
            loadProfileBitmap(context, photoUri!!)
        }
    }

    val initials = userName.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifEmpty { "?" }

    Scaffold(
        containerColor = BackgroundLight,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Profile",
                        fontWeight = FontWeight.Bold,
                        color = PurplePrimary,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = BackgroundLight
                )
            )
        },
        bottomBar = {
            AppBottomNavBar(
                selectedTab = BottomNavTab.PROFILE,
                onChatsClick = onChatsClick,
                onProfileClick = { }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFB39DDB)),
                contentAlignment = Alignment.Center
            ) {
                if (photoBitmap != null) {
                    Image(
                        bitmap = photoBitmap!!.asImageBitmap(),
                        contentDescription = "Your profile picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = initials,
                        color = Color.White,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = userName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = userEmail, fontSize = 14.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = {
                    if (userId != null) {
                        editedName = userName
                        showEditDialog = true
                    } else {
                        Toast.makeText(context, "Please log in first", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, PurplePrimary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PurplePrimary)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit Profile", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    if (userId != null) pickPhoto?.launch(arrayOf("image/*"))
                    else Toast.makeText(context, "Please log in first", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, PurplePrimary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PurplePrimary)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Change Profile Picture", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    ProfileMenuRow(Icons.Default.Notifications, "Notifications", TextPrimary) { }
                    HorizontalDivider(color = DividerGray)
                    ProfileMenuRow(Icons.Default.Settings, "Settings", TextPrimary) { }
                    HorizontalDivider(color = DividerGray)
                    ProfileMenuRow(
                        icon = Icons.Default.Logout,
                        label = "Logout",
                        tint = LogoutRed,
                        showChevron = false
                    ) { showLogoutDialog = true }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { if (!savingName) showEditDialog = false },
            title = { Text("Edit Profile") },
            text = {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !savingName && editedName.trim().isNotBlank() && editedName.trim().length <= 60,
                    onClick = {
                        val uid = userId ?: return@TextButton
                        val newName = editedName.trim()
                        savingName = true
                        FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(uid)
                            .set(mapOf("name" to newName), SetOptions.merge())
                            .addOnSuccessListener {
                                userName = newName
                                savingName = false
                                showEditDialog = false
                                Toast.makeText(context, "Name updated", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { error ->
                                savingName = false
                                Toast.makeText(context, error.localizedMessage ?: "Could not save name", Toast.LENGTH_LONG).show()
                            }
                    }
                ) {
                    Text(if (savingName) "Saving..." else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }, enabled = !savingName) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log out?") },
            text = { Text("You'll return to the login screen.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    if (!isPreview) FirebaseAuth.getInstance().signOut()
                    onLogoutClick()
                }) { Text("Logout", color = LogoutRed) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// Decode a reasonably sized image off the main thread.
private fun loadProfileBitmap(context: Context, uriString: String): Bitmap? {
    return try {
        val uri = Uri.parse(uriString)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 600 || bounds.outHeight / sampleSize > 600) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    showChevron: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            color = tint,
            modifier = Modifier.weight(1f)
        )
        if (showChevron) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextTertiary
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileScreenPreview() {
    ChatSphereTheme {
        ProfileScreen(
            onBackClick = {},
            onChatsClick = {},
            onLogoutClick = {}
        )
    }
}

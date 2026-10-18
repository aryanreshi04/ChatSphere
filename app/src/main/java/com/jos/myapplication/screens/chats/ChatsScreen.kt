package com.jos.myapplication.screens.chats

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jos.myapplication.components.AppBottomNavBar
import com.jos.myapplication.components.BottomNavTab
import com.jos.myapplication.components.Conversation
import com.jos.myapplication.components.InitialsAvatar
import com.jos.myapplication.firebase.ChatRepository
import com.jos.myapplication.ui.theme.BackgroundLight
import com.jos.myapplication.ui.theme.ChatSphereTheme
import com.jos.myapplication.ui.theme.DividerGray
import com.jos.myapplication.ui.theme.PurplePrimary
import com.jos.myapplication.ui.theme.SearchBarGray
import com.jos.myapplication.ui.theme.TextPrimary
import com.jos.myapplication.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ChatThread(
    val id: String,
    val participants: List<String>,
    val lastMessage: String,
    val lastMessageTime: Long,
    val lastMessageId: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    onConversationClick: (Conversation) -> Unit,
    onProfileClick: () -> Unit
) {
    val isPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val signedInUser = if (isPreview) null else FirebaseAuth.getInstance().currentUser
    val currentUserId = if (isPreview) "preview-self-id" else signedInUser?.uid

    // Display the signed-in user's own profile, not the sample "AK" avatar.
    // Firestore supplies their latest name; Authentication provides a fallback.
    var ownName by remember(currentUserId, isPreview) {
        mutableStateOf(
            if (isPreview) "Preview User"
            else signedInUser?.displayName?.takeIf { it.isNotBlank() }
                ?: signedInUser?.email?.substringBefore('@')
                ?: "User"
        )
    }

    // ProfileScreen.kt stores a photo URI locally, per account. Re-read it
    // when Chats resumes after a user changes their photo on Profile.
    val profilePreferences = remember(context) {
        context.getSharedPreferences("chatsphere_profile_photos", Context.MODE_PRIVATE)
    }
    var ownPhotoUri by remember(currentUserId, isPreview) {
        mutableStateOf(
            if (isPreview || currentUserId == null) null
            else profilePreferences.getString("profile_photo_$currentUserId", null)
        )
    }
    DisposableEffect(lifecycleOwner, currentUserId, isPreview) {
        if (isPreview || currentUserId == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    ownPhotoUri = profilePreferences.getString(
                        "profile_photo_$currentUserId", null
                    )
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            ownPhotoUri = profilePreferences.getString("profile_photo_$currentUserId", null)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }
    val ownPhotoBitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = ownPhotoUri
    ) {
        value = if (isPreview || ownPhotoUri.isNullOrBlank()) null
        else withContext(Dispatchers.IO) {
            decodeChatAvatarPhoto(context, ownPhotoUri!!)
        }
    }

    var registeredUsers by remember(isPreview) {
        mutableStateOf(
            if (isPreview) listOf(
                makeContact("preview1", "Rahul", ""),
                makeContact("preview2", "Priya", "")
            ) else emptyList()
        )
    }
    var threads by remember(isPreview) {
        mutableStateOf(
            if (isPreview) listOf(
                ChatThread("preview-thread", listOf("preview-self-id", "preview1"),
                    "Hi!", System.currentTimeMillis(), "preview-message")
            ) else emptyList()
        )
    }
    var hiddenChats by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loadedChats by remember(isPreview) { mutableStateOf(isPreview) }
    var loadedUsers by remember(isPreview) { mutableStateOf(isPreview) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var peopleSearch by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var openingUserId by remember { mutableStateOf<String?>(null) }
    var selectedToDelete by remember { mutableStateOf<ChatThread?>(null) }
    var deleting by remember { mutableStateOf(false) }

    // The contact picker uses registered users, but the home list uses only
    // conversations involving the signed-in user (not every registered user).
    DisposableEffect(isPreview, currentUserId) {
        if (isPreview || currentUserId == null) {
            onDispose { }
        } else {
            val db = FirebaseFirestore.getInstance()
            val usersListener = db.collection("users")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        errorMessage = "Could not load users: ${error.localizedMessage}"
                    } else if (snapshot != null) {
                        snapshot.documents.firstOrNull { it.id == currentUserId }
                            ?.getString("name")
                            ?.takeIf { it.isNotBlank() }
                            ?.let { ownName = it }
                        registeredUsers = snapshot.documents.mapNotNull { doc ->
                            val name = doc.getString("name")
                            if (doc.id == currentUserId || name.isNullOrBlank()) null
                            else makeContact(doc.id, name, doc.getString("email").orEmpty())
                        }
                        loadedUsers = true
                    }
                }

            val threadsListener = db.collection("conversations")
                .whereArrayContains("participants", currentUserId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        errorMessage = "Could not load chats: ${error.localizedMessage}"
                    } else if (snapshot != null) {
                        threads = snapshot.documents.mapNotNull { doc ->
                            val participants = (doc.get("participants") as? List<*>)
                                ?.filterIsInstance<String>().orEmpty()
                            if (participants.size != 2 || currentUserId !in participants) null
                            else ChatThread(
                                id = doc.id,
                                participants = participants,
                                lastMessage = doc.getString("lastMessage").orEmpty(),
                                lastMessageTime = doc.getLong("lastMessageTime") ?: 0L,
                                lastMessageId = doc.getString("lastMessageId").orEmpty()
                            )
                        }
                        loadedChats = true
                    }
                }

            // Only this user's hide markers are read/written.
            val hiddenListener = db.collection("users")
                .document(currentUserId)
                .collection("hiddenChats")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        errorMessage = "Could not load hidden chats: ${error.localizedMessage}"
                    } else if (snapshot != null) {
                        hiddenChats = snapshot.documents.associate { doc ->
                            doc.id to doc.getString("hiddenMessageId").orEmpty()
                        }
                    }
                }

            onDispose {
                usersListener.remove()
                threadsListener.remove()
                hiddenListener.remove()
            }
        }
    }

    // A hidden conversation becomes visible again when a new message changes its
    // lastMessageId. Reopening it also clears that user's hide marker.
    val visibleThreads = threads.filter { thread ->
        !(hiddenChats.containsKey(thread.id) &&
                hiddenChats[thread.id] == thread.lastMessageId)
    }.sortedByDescending { it.lastMessageTime }

    val chatsWithContacts = visibleThreads.mapNotNull { thread ->
        val otherId = thread.participants.firstOrNull { it != currentUserId }
        val contact = registeredUsers.firstOrNull { it.id == otherId }
        if (contact == null) null
        else thread to contact.copy(
            lastMessage = thread.lastMessage.ifBlank { "Tap to start chatting" },
            time = if (thread.lastMessage.isNotEmpty() && thread.lastMessageTime > 0L)
                formatChatTime(thread.lastMessageTime) else ""
        )
    }.filter { (_, contact) ->
        contact.name.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        containerColor = BackgroundLight,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Just Chat", fontWeight = FontWeight.Bold,
                        color = PurplePrimary, fontSize = 20.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onProfileClick) {
                        if (ownPhotoBitmap != null) {
                            Image(
                                bitmap = ownPhotoBitmap!!.asImageBitmap(),
                                contentDescription = "Open your profile",
                                modifier = Modifier.size(34.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            InitialsAvatar(
                                initials = initialsFromName(ownName),
                                backgroundColor = Color(0xFFB39DDB),
                                size = 34.dp
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Find people",
                            tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = BackgroundLight
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { peopleSearch = ""; showAddDialog = true },
                shape = RoundedCornerShape(16.dp),
                containerColor = PurplePrimary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Edit, contentDescription = "New chat")
            }
        },
        bottomBar = {
            AppBottomNavBar(
                selectedTab = BottomNavTab.CHATS,
                onChatsClick = { },
                onProfileClick = onProfileClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {
            ChatsSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search chats"
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage.orEmpty(),
                    color = Color(0xFFB00020),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (chatsWithContacts.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = when {
                            currentUserId == null -> "Please log in first"
                            !loadedChats || !loadedUsers -> "Loading chats..."
                            searchQuery.isNotBlank() -> "No matching chats"
                            else -> "No chats yet. Tap the pencil to start one."
                        },
                        color = TextSecondary,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(chatsWithContacts, key = { it.first.id }) { (thread, contact) ->
                        ConversationRow(
                            conversation = contact,
                            onClick = { onConversationClick(contact) },
                            onDeleteClick = { selectedToDelete = thread }
                        )
                        androidx.compose.material3.HorizontalDivider(
                            color = DividerGray,
                            thickness = 1.dp,
                            modifier = Modifier.padding(start = 82.dp)
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        val existingUserIds = visibleThreads.mapNotNull { thread ->
            thread.participants.firstOrNull { it != currentUserId }
        }.toSet()
        val people = registeredUsers.filter {
            it.id !in existingUserIds &&
                    (it.name.contains(peopleSearch, ignoreCase = true) ||
                            it.lastMessage.contains(peopleSearch, ignoreCase = true))
        }

        AlertDialog(
            onDismissRequest = { if (openingUserId == null) showAddDialog = false },
            title = { Text("Start a new chat") },
            text = {
                Column {
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage.orEmpty(),
                            color = Color(0xFFB00020),
                            fontSize = 12.sp
                        )
                    }
                    ChatsSearchBar(
                        query = peopleSearch,
                        onQueryChange = { peopleSearch = it },
                        placeholder = "Search name or email"
                    )
                    if (!loadedUsers && !isPreview) {
                        Text("Loading people...", color = TextSecondary)
                    } else if (people.isEmpty()) {
                        Text("No available people found", color = TextSecondary)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 330.dp)) {
                            items(people, key = { it.id }) { person ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    InitialsAvatar(
                                        initials = person.initials,
                                        backgroundColor = person.avatarColor,
                                        size = 38.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(person.name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            person.lastMessage,
                                            fontSize = 11.sp,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    TextButton(
                                        enabled = openingUserId == null,
                                        onClick = {
                                            if (isPreview) {
                                                showAddDialog = false
                                                onConversationClick(person)
                                            } else if (currentUserId == null) {
                                                errorMessage = "Please log in first"
                                            } else {
                                                openingUserId = person.id
                                                ChatRepository().createConversation(
                                                    otherUserId = person.id,
                                                    onSuccess = {
                                                        openingUserId = null
                                                        showAddDialog = false
                                                        onConversationClick(person)
                                                    },
                                                    onFailure = { exception ->
                                                        openingUserId = null
                                                        errorMessage = "Could not start chat: ${exception.localizedMessage}"
                                                    }
                                                )
                                            }
                                        }
                                    ) {
                                        Text(if (openingUserId == person.id) "Opening..." else "Chat")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showAddDialog = false },
                    enabled = openingUserId == null
                ) { Text("Close") }
            }
        )
    }

    val deletingThread = selectedToDelete
    if (deletingThread != null) {
        val otherId = deletingThread.participants.firstOrNull { it != currentUserId }
        val name = registeredUsers.firstOrNull { it.id == otherId }?.name ?: "this chat"
        AlertDialog(
            onDismissRequest = { if (!deleting) selectedToDelete = null },
            title = { Text("Delete chat for me?") },
            text = {
                Text("Remove $name from your chat list? This does not delete the other person's messages. The conversation history remains stored and will be visible if you reopen the chat.")
            },
            dismissButton = {
                TextButton(onClick = { selectedToDelete = null }, enabled = !deleting) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        if (isPreview) {
                            hiddenChats = hiddenChats + (deletingThread.id to deletingThread.lastMessageId)
                            selectedToDelete = null
                        } else if (currentUserId == null) {
                            errorMessage = "Please log in first"
                            selectedToDelete = null
                        } else {
                            deleting = true
                            FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(currentUserId)
                                .collection("hiddenChats")
                                .document(deletingThread.id)
                                .set(mapOf("hiddenMessageId" to deletingThread.lastMessageId))
                                .addOnSuccessListener {
                                    deleting = false
                                    selectedToDelete = null
                                }
                                .addOnFailureListener { exception ->
                                    deleting = false
                                    selectedToDelete = null
                                    errorMessage = "Could not delete chat: ${exception.localizedMessage}"
                                }
                        }
                    }
                ) { Text(if (deleting) "Deleting..." else "Delete for me") }
            }
        )
    }
}

private fun initialsFromName(name: String): String =
    name.trim().split(Regex("\\s+"))
        .filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifEmpty { "?" }

private fun makeContact(id: String, name: String, email: String): Conversation {
    return Conversation(
        id = id,
        name = name,
        initials = initialsFromName(name),
        lastMessage = email,
        time = "",
        isOnline = false,
        unreadCount = 0,
        avatarColor = Color(0xFFB39DDB)
    )
}

// Load a small thumbnail rather than decoding a full-size camera photo.
private fun decodeChatAvatarPhoto(context: Context, uriString: String): Bitmap? {
    return try {
        val uri = Uri.parse(uriString)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        var sample = 1
        while (bounds.outWidth / sample > 128 || bounds.outHeight / sample > 128) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    } catch (_: Exception) {
        null
    }
}

private fun formatChatTime(timestamp: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))

@Composable
private fun ChatsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(placeholder, color = TextSecondary) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
        },
        singleLine = true,
        shape = RoundedCornerShape(50),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = SearchBarGray,
            unfocusedContainerColor = SearchBarGray,
            disabledContainerColor = SearchBarGray,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: Conversation,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onDeleteClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InitialsAvatar(
            initials = conversation.initials,
            backgroundColor = conversation.avatarColor,
            size = 52.dp,
            isOnline = conversation.isOnline
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                conversation.name, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                conversation.lastMessage, fontSize = 14.sp, color = TextSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(conversation.time, fontSize = 12.sp, color = TextSecondary)
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Delete chat for me",
                    tint = TextSecondary)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatsScreenPreview() {
    ChatSphereTheme {
        ChatsScreen(onConversationClick = {}, onProfileClick = {})
    }
}

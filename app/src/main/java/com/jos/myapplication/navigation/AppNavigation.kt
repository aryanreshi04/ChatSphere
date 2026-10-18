package com.jos.myapplication.navigation

import android.net.Uri
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.jos.myapplication.components.Conversation
import com.jos.myapplication.data.ChatRepository
import com.jos.myapplication.data.ChatUser
import com.jos.myapplication.screens.auth.CreateAccountScreen
import com.jos.myapplication.screens.auth.LoginScreen
import com.jos.myapplication.screens.chats.ChatsScreen
import com.jos.myapplication.screens.chats.UserDiscoveryScreen
import com.jos.myapplication.screens.individualchat.IndividualChatScreen
import com.jos.myapplication.screens.profile.ProfileScreen
import java.util.Locale

object Routes {
    const val CREATE_ACCOUNT = "create_account"
    const val LOGIN = "login"
    const val CHATS = "chats"
    const val USERS = "users"
    const val INDIVIDUAL_CHAT = "individual_chat/{conversationId}/{otherUserId}"
    const val PROFILE = "profile"

    fun individualChat(conversationId: String, otherUserId: String) =
        "individual_chat/${Uri.encode(conversationId)}/${Uri.encode(otherUserId)}"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val auth = remember { FirebaseAuth.getInstance() }
    val repository = remember { ChatRepository() }
    val initialRoute = remember {
        if (auth.currentUser == null) Routes.LOGIN else Routes.CHATS
    }

    var conversations by remember { mutableStateOf(emptyList<Conversation>()) }
    var conversationsLoading by remember { mutableStateOf(true) }
    var conversationsError by remember { mutableStateOf<String?>(null) }
    var users by remember { mutableStateOf(emptyList<ChatUser>()) }
    var usersLoading by remember { mutableStateOf(true) }
    var usersError by remember { mutableStateOf<String?>(null) }
    var startingUserId by remember { mutableStateOf<String?>(null) }
    var startConversationError by remember { mutableStateOf<String?>(null) }

    NavHost(navController = navController, startDestination = initialRoute) {
        composable(Routes.CREATE_ACCOUNT) {
            CreateAccountScreen(
                onCreateAccountClick = {
                    navController.navigate(Routes.CHATS) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToLogin = { navController.navigate(Routes.LOGIN) }
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginClick = {
                    navController.navigate(Routes.CHATS) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToCreateAccount = { navController.navigate(Routes.CREATE_ACCOUNT) }
            )
        }

        composable(Routes.CHATS) {
            val uid = auth.currentUser?.uid
            val currentName = auth.currentUser?.displayName.orEmpty()
            DisposableEffect(uid) {
                if (uid == null) {
                    conversationsLoading = false
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.CHATS) { inclusive = true }
                        launchSingleTop = true
                    }
                    onDispose { }
                } else {
                    conversationsLoading = true
                    val listener = repository.listenConversations(uid) { result ->
                        result.fold(
                            onSuccess = {
                                conversations = it
                                conversationsLoading = false
                                conversationsError = null
                            },
                            onFailure = {
                                conversationsLoading = false
                                conversationsError = it.localizedMessage ?: "Could not load conversations."
                            }
                        )
                    }
                    onDispose { listener.remove() }
                }
            }
            ChatsScreen(
                onConversationClick = { conversation ->
                    navController.navigate(Routes.individualChat(conversation.id, conversation.otherUserId))
                },
                onProfileClick = { navController.navigate(Routes.PROFILE) },
                conversations = conversations,
                isLoading = conversationsLoading,
                errorMessage = conversationsError,
                currentUserInitials = initialsFor(currentName),
                onNewChatClick = { navController.navigate(Routes.USERS) }
            )
        }

        composable(Routes.USERS) {
            val uid = auth.currentUser?.uid
            DisposableEffect(uid) {
                if (uid == null) {
                    usersLoading = false
                    onDispose { }
                } else {
                    usersLoading = true
                    val listener = repository.listenUsers(uid) { result ->
                        result.fold(
                            onSuccess = {
                                users = it
                                usersLoading = false
                                usersError = null
                            },
                            onFailure = {
                                usersLoading = false
                                usersError = it.localizedMessage ?: "Could not load users."
                            }
                        )
                    }
                    onDispose { listener.remove() }
                }
            }
            UserDiscoveryScreen(
                users = users,
                isLoading = usersLoading,
                errorMessage = usersError,
                startingUserId = startingUserId,
                startErrorMessage = startConversationError,
                onUserClick = { user ->
                    val currentUid = auth.currentUser?.uid ?: return@UserDiscoveryScreen
                    startingUserId = user.uid
                    startConversationError = null
                    repository.ensureConversation(currentUid, user.uid) { result ->
                        startingUserId = null
                        result.fold(
                            onSuccess = { conversationId ->
                                navController.navigate(Routes.individualChat(conversationId, user.uid))
                            },
                            onFailure = {
                                startConversationError = it.localizedMessage ?: "Could not open this conversation."
                            }
                        )
                    }
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.INDIVIDUAL_CHAT,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("otherUserId") { type = NavType.StringType }
            )
        ) { entry ->
            val conversationId = entry.arguments?.getString("conversationId").orEmpty()
            val otherUserId = entry.arguments?.getString("otherUserId").orEmpty()
            val uid = auth.currentUser?.uid.orEmpty()
            var messages by remember(conversationId) { mutableStateOf(emptyList<com.jos.myapplication.components.ChatMessage>()) }
            var messagesLoading by remember(conversationId) { mutableStateOf(true) }
            var messagesError by remember(conversationId) { mutableStateOf<String?>(null) }
            var contactName by remember(otherUserId) { mutableStateOf("User") }
            LaunchedEffect(otherUserId) {
                if (otherUserId.isNotBlank()) {
                    repository.getUser(otherUserId) { result ->
                        result.onSuccess { contactName = it.name }
                    }
                }
            }
            DisposableEffect(conversationId, uid) {
                if (conversationId.isBlank() || uid.isBlank()) {
                    messagesLoading = false
                    onDispose { }
                } else {
                    messagesLoading = true
                    val listener = repository.listenMessages(conversationId, uid) { result ->
                        result.fold(
                            onSuccess = {
                                messages = it
                                messagesLoading = false
                                messagesError = null
                            },
                            onFailure = {
                                messagesLoading = false
                                messagesError = it.localizedMessage ?: "Could not load messages."
                            }
                        )
                    }
                    onDispose { listener.remove() }
                }
            }
            IndividualChatScreen(
                conversationId = conversationId,
                currentUserUid = uid,
                contactName = contactName,
                contactInitials = initialsFor(contactName),
                messages = messages,
                messagesLoading = messagesLoading,
                loadErrorMessage = messagesError,
                onSendMessage = { text, complete ->
                    if (uid.isBlank()) complete(IllegalStateException("You are not signed in."))
                    else repository.sendMessage(conversationId, uid, text, complete)
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Routes.PROFILE) {
            val user = auth.currentUser
            ProfileScreen(
                name = user?.displayName.orEmpty(),
                email = user?.email.orEmpty(),
                onBackClick = { navController.popBackStack() },
                onChatsClick = { navController.popBackStack() },
                onLogoutClick = {
                    auth.signOut()
                    conversations = emptyList()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.CHATS) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}

private fun initialsFor(name: String): String = name.split(" ")
    .filter(String::isNotBlank)
    .take(2)
    .joinToString("") { it.take(1).uppercase(Locale.getDefault()) }
    .ifBlank { "?" }

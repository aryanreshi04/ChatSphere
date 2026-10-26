package com.jos.myapplication.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.firestore.FirebaseFirestore
import com.jos.myapplication.screens.auth.CreateAccountScreen
import com.jos.myapplication.screens.auth.LoginScreen
import com.jos.myapplication.screens.chats.ChatsScreen
import com.jos.myapplication.screens.individualchat.IndividualChatScreen
import com.jos.myapplication.screens.profile.ProfileScreen
import com.jos.myapplication.screens.notifications.NotificationsScreen
import com.jos.myapplication.screens.settings.SettingsScreen
import com.jos.myapplication.screens.maps.MapScreen
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.runtime.LaunchedEffect
import com.jos.myapplication.notifications.NotificationTarget
import com.jos.myapplication.notifications.ChatNotifications
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.jos.myapplication.screens.assistant.AssistantScreen

object Routes {
    const val CREATE_ACCOUNT = "create_account"
    const val LOGIN = "login"
    const val CHATS = "chats"
    const val INDIVIDUAL_CHAT = "individual_chat/{userId}"
    const val PROFILE = "profile"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings"
    const val MAP = "map"
    const val ASSISTANT = "assistant"

    fun chatForUser(userId: String): String = "individual_chat/${Uri.encode(userId)}"
}

@Composable
fun AppNavigation(pendingNotification: NotificationTarget? = null, onNotificationHandled: () -> Unit = {}) {
    val navController = rememberNavController()
    val initialRoute = remember { if (FirebaseAuth.getInstance().currentUser != null) Routes.CHATS else Routes.CREATE_ACCOUNT }
    var signedInUid by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser?.uid) }
    val context = LocalContext.current
    var previousUid by remember { mutableStateOf(signedInUid) }
    DisposableEffect(Unit) {
        val auth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener { signedInUid = it.currentUser?.uid }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    LaunchedEffect(signedInUid) {
        if (previousUid != null && signedInUid != previousUid) {
            navController.navigate(if (signedInUid == null) Routes.LOGIN else Routes.CHATS) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
        previousUid = signedInUid
    }
    LaunchedEffect(pendingNotification, signedInUid) {
        val target = pendingNotification ?: return@LaunchedEffect
        if (signedInUid == null) return@LaunchedEffect
        try {
            if(target.kind=="test"){
                if(ChatNotifications.validTest(target))navController.navigate(Routes.NOTIFICATIONS){launchSingleTop=true}
                else Toast.makeText(context,"This test notification is no longer available",Toast.LENGTH_SHORT).show()
                return@LaunchedEffect
            }
            val sender = ChatNotifications.validatedSender(target)
            if (sender != null) navController.navigate(Routes.chatForUser(sender)) { launchSingleTop = true }
            else Toast.makeText(context, "This notification is unavailable for the signed-in account", Toast.LENGTH_LONG).show()
        } catch (_: Exception) { Toast.makeText(context, "Could not open notification. Check connection and open the chat from Chats.", Toast.LENGTH_LONG).show() }
        finally { onNotificationHandled() }
    }

    NavHost(
        navController = navController,
        startDestination = initialRoute
    ) {
        composable(Routes.CREATE_ACCOUNT) {
            CreateAccountScreen(
                onCreateAccountClick = { navController.navigate(Routes.LOGIN) },
                onNavigateToLogin = { navController.navigate(Routes.LOGIN) }
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginClick = {
                    navController.navigate(Routes.CHATS) {
                        popUpTo(Routes.CREATE_ACCOUNT) { inclusive = true }
                    }
                },
                onNavigateToCreateAccount = {
                    navController.navigate(Routes.CREATE_ACCOUNT)
                }
            )
        }

        composable(Routes.CHATS) {
            ChatsScreen(
                onConversationClick = { contact ->
                    navController.navigate(Routes.chatForUser(contact.id)) { launchSingleTop = true }
                },
                onProfileClick = { navController.navigate(Routes.PROFILE) },
                onMapClick = { navController.navigate(Routes.MAP) { launchSingleTop = true } },
                onAssistantClick = { navController.navigate(Routes.ASSISTANT) { launchSingleTop = true } }
            )
        }

        composable(
            route = Routes.INDIVIDUAL_CHAT,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val contactUserId = backStackEntry.arguments?.getString("userId").orEmpty()
            var contactName by remember(contactUserId) { mutableStateOf("Loading...") }
            var contactInitials by remember(contactUserId) { mutableStateOf("?") }

            // Fetch the actual user's profile; never use dummy sampleConversations.
            DisposableEffect(contactUserId) {
                if (contactUserId.isBlank()) {
                    onDispose { }
                } else {
                    val listener = FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(contactUserId)
                        .addSnapshotListener { document, error ->
                            if (error == null && document != null && document.exists()) {
                                val name = document.getString("name").orEmpty().trim()
                                contactName = name.ifEmpty { "ChatSphere user" }
                                contactInitials = name
                                    .split(Regex("\\s+"))
                                    .filter { it.isNotBlank() }
                                    .take(2)
                                    .joinToString("") { it.first().uppercaseChar().toString() }
                                    .ifEmpty { "?" }
                            }
                        }
                    onDispose { listener.remove() }
                }
            }

            IndividualChatScreen(
                contactName = contactName,
                contactInitials = contactInitials,
                contactUserId = contactUserId,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                onBackClick = { navController.popBackStack() },
                onChatsClick = { navController.popBackStack() },
                onNotificationsClick = {
                    navController.navigate(Routes.NOTIFICATIONS) { launchSingleTop = true }
                },
                onSettingsClick = {
                    navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                },
                onLogoutClick = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.CHATS) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Routes.MAP) { MapScreen { navController.popBackStack() } }
        composable(Routes.ASSISTANT) { AssistantScreen { navController.popBackStack() } }
    }
}

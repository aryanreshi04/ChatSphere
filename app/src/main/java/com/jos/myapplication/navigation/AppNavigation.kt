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

object Routes {
    const val CREATE_ACCOUNT = "create_account"
    const val LOGIN = "login"
    const val CHATS = "chats"
    const val INDIVIDUAL_CHAT = "individual_chat/{userId}"
    const val PROFILE = "profile"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings"

    fun chatForUser(userId: String): String = "individual_chat/${Uri.encode(userId)}"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.CREATE_ACCOUNT
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
                    navController.navigate(Routes.chatForUser(contact.id))
                },
                onProfileClick = { navController.navigate(Routes.PROFILE) }
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
    }
}

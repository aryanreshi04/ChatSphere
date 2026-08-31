package com.jos.myapplication.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

import com.jos.myapplication.components.sampleConversations
import com.jos.myapplication.screens.auth.CreateAccountScreen
import com.jos.myapplication.screens.auth.LoginScreen
import com.jos.myapplication.screens.chats.ChatsScreen
import com.jos.myapplication.screens.individualchat.IndividualChatScreen
import com.jos.myapplication.screens.profile.ProfileScreen

object Routes {
    const val CREATE_ACCOUNT = "create_account"
    const val LOGIN = "login"
    const val CHATS = "chats"
    const val INDIVIDUAL_CHAT = "individual_chat/{conversationId}"
    const val PROFILE = "profile"
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
                onCreateAccountClick = {
                    navController.navigate(Routes.LOGIN)
                },
                onNavigateToLogin = {
                    navController.navigate(Routes.LOGIN)
                }
            )
        }

        composable(Routes.LOGIN) {

            LoginScreen(
                onLoginClick = {

                    navController.navigate(Routes.CHATS) {
                        popUpTo(Routes.CREATE_ACCOUNT) {
                            inclusive = true
                        }
                    }

                },
                onNavigateToCreateAccount = {
                    navController.navigate(Routes.CREATE_ACCOUNT)
                }
            )
        }

        composable(Routes.CHATS) {

            ChatsScreen(
                onConversationClick = { conversation ->

                    navController.navigate(
                        "individual_chat/${conversation.id}"
                    )

                },
                onProfileClick = {
                    navController.navigate(Routes.PROFILE)
                }
            )
        }

        composable(
            route = Routes.INDIVIDUAL_CHAT,
            arguments = listOf(
                navArgument("conversationId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->

            val conversationId =
                backStackEntry.arguments?.getString("conversationId") ?: ""

            val conversation =
                sampleConversations.find {
                    it.id == conversationId
                } ?: sampleConversations.first()

            IndividualChatScreen(
                contactName = conversation.name,
                contactInitials = conversation.initials,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.PROFILE) {

            ProfileScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onChatsClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
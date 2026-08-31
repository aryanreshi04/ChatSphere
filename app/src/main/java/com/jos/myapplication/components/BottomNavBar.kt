package com.jos.myapplication.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.jos.myapplication.ui.theme.BackgroundLight
import com.jos.myapplication.ui.theme.PurpleLight
import com.jos.myapplication.ui.theme.PurplePrimary
import com.jos.myapplication.ui.theme.TextSecondary

enum class BottomNavTab {
    CHATS,
    PROFILE
}

@Composable
fun AppBottomNavBar(
    selectedTab: BottomNavTab,
    onChatsClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    NavigationBar(
        containerColor = BackgroundLight,
        tonalElevation = 0.dp
    ) {

        NavigationBarItem(
            selected = selectedTab == BottomNavTab.CHATS,
            onClick = onChatsClick,
            icon = {
                Icon(
                    Icons.Default.ChatBubble,
                    contentDescription = "Chats"
                )
            },
            label = {
                Text("Chats")
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PurplePrimary,
                selectedTextColor = PurplePrimary,
                indicatorColor = PurpleLight,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            )
        )

        NavigationBarItem(
            selected = selectedTab == BottomNavTab.PROFILE,
            onClick = onProfileClick,
            icon = {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Profile"
                )
            },
            label = {
                Text("Profile")
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PurplePrimary,
                selectedTextColor = PurplePrimary,
                indicatorColor = PurpleLight,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            )
        )
    }
}
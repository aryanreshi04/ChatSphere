package com.jos.myapplication.screens.profile
import androidx.compose.ui.tooling.preview.Preview
import com.jos.myapplication.ui.theme.ChatSphereTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jos.myapplication.components.AppBottomNavBar
import com.jos.myapplication.components.BottomNavTab
import com.jos.myapplication.ui.theme.BackgroundLight
import com.jos.myapplication.ui.theme.DividerGray
import com.jos.myapplication.ui.theme.LogoutRed
import com.jos.myapplication.ui.theme.PurplePrimary
import com.jos.myapplication.ui.theme.SurfaceWhite
import com.jos.myapplication.ui.theme.TextPrimary
import com.jos.myapplication.ui.theme.TextSecondary
import com.jos.myapplication.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBackClick: () -> Unit,
    onChatsClick: () -> Unit
) {
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
                    IconButton(
                        onClick = onBackClick
                    ) {
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

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFB39DDB)),

                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AK",
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Text(
                text = "Amit Kumar",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = "amit.kumar@example.com",
                fontSize = 14.sp,
                color = TextSecondary
            )

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            OutlinedButton(
                onClick = { },

                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),

                shape = RoundedCornerShape(50),

                border = BorderStroke(
                    1.dp,
                    PurplePrimary
                ),

                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = PurplePrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(
                    modifier = Modifier.width(8.dp)
                )

                Text(
                    text = "Edit Profile",
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            OutlinedButton(
                onClick = { },

                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),

                shape = RoundedCornerShape(50),

                border = BorderStroke(
                    1.dp,
                    PurplePrimary
                ),

                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = PurplePrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(
                    modifier = Modifier.width(8.dp)
                )

                Text(
                    text = "Change Profile Picture",
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = SurfaceWhite
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 1.dp
                ),
                modifier = Modifier.fillMaxWidth()
            ) {

                Column {

                    ProfileMenuRow(
                        icon = Icons.Default.Notifications,
                        label = "Notifications",
                        tint = TextPrimary
                    ) { }

                    HorizontalDivider(
                        color = DividerGray
                    )

                    ProfileMenuRow(
                        icon = Icons.Default.Settings,
                        label = "Settings",
                        tint = TextPrimary
                    ) { }

                    HorizontalDivider(
                        color = DividerGray
                    )

                    ProfileMenuRow(
                        icon = Icons.Default.Logout,
                        label = "Logout",
                        tint = LogoutRed,
                        showChevron = false
                    ) { }
                }
            }

            Spacer(
                modifier = Modifier.height(24.dp)
            )
        }
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
            .clickable {
                onClick()
            }
            .padding(
                horizontal = 18.dp,
                vertical = 16.dp
            ),

        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint
        )

        Spacer(
            modifier = Modifier.width(16.dp)
        )

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
            onChatsClick = {}
        )
    }
}
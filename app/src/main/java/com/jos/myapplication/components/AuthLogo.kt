package com.jos.myapplication.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jos.myapplication.ui.theme.PurplePrimary
import com.jos.myapplication.ui.theme.TextPrimary

@Composable
fun AuthLogo() {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.ChatBubble,
            contentDescription = null,
            tint = PurplePrimary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = "Chat",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = TextPrimary
        )

        Text(
            text = "Sphere",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = PurplePrimary
        )
    }
}
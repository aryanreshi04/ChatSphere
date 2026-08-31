package com.jos.myapplication.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jos.myapplication.ui.theme.OnlineGreen

@Composable
fun InitialsAvatar(
    initials: String,
    backgroundColor: Color,
    size: Dp = 48.dp,
    isOnline: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(size)
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value / 2.6f).sp
            )
        }

        if (isOnline) {
            Box(
                modifier = Modifier
                    .size(size / 3.2f)
                    .align(Alignment.BottomEnd)
                    .background(Color.White, CircleShape)
                    .padding(2.dp)
                    .background(OnlineGreen, CircleShape)
            )
        }
    }
}
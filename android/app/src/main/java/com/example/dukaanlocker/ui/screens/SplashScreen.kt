package com.example.dukaanlocker.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dukaanlocker.ui.components.LauncherLogo
import com.example.dukaanlocker.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    isDarkTheme: Boolean,
    onSplashDone: () -> Unit
) {
    val colors = LocalAppColors.current

    LaunchedEffect(Unit) {
        delay(2000)
        onSplashDone()
    }

    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LauncherLogo(modifier = Modifier.size(120.dp))

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "DUKAAN LOCKER",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Secure Business Document Vault",
                fontSize = 12.sp,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(48.dp))

            CircularProgressIndicator(
                modifier = Modifier.alpha(alpha).size(24.dp),
                strokeWidth = 2.dp,
                color = colors.primary
            )
        }
    }
}

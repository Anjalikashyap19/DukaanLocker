package com.iadv.dukaanlocker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.api.NotificationItem
import com.iadv.dukaanlocker.ui.theme.LocalAppColors
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    notifications: List<NotificationItem>,
    onBack: () -> Unit,
    onClearAll: () -> Unit,
    onNotificationClick: (NotificationItem) -> Unit,
    onMarkAllRead: () -> Unit
) {
    val colors = LocalAppColors.current

    // Auto-mark all as read when screen opens
    LaunchedEffect(Unit) {
        onMarkAllRead()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Notifications", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (notifications.isNotEmpty()) {
                        TextButton(onClick = onClearAll) {
                            Text("Clear all", color = colors.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.cardBg,
                    titleContentColor = colors.textPrimary
                )
            )
        },
        containerColor = colors.background
    ) { padding ->
        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = colors.textSecondary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No notifications yet",
                        fontSize = 16.sp,
                        color = colors.textSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(notifications) { notification ->
                    NotificationCard(
                        notification = notification,
                        colors = colors,
                        onClick = { onNotificationClick(notification) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(notification: NotificationItem, colors: com.iadv.dukaanlocker.ui.theme.AppColors, onClick: () -> Unit) {
    val icon = when (notification.type) {
        "WELCOME" -> Icons.Default.CheckCircle
        "EXPIRING_SOON" -> Icons.Default.Warning
        "EXPIRED" -> Icons.Default.Warning
        "MISSING_DOCUMENT" -> Icons.Default.Info
        "NO_BUSINESS" -> Icons.Default.CheckCircle
        else -> Icons.Default.Info
    }

    val iconTint = when (notification.type) {
        "WELCOME" -> Color(0xFF4CAF50)
        "EXPIRED" -> Color.Red
        "EXPIRING_SOON" -> Color(0xFFFF9800)
        "MISSING_DOCUMENT" -> Color(0xFF2196F3)
        "NO_BUSINESS" -> Color(0xFF4CAF50)
        else -> colors.textSecondary
    }

    val bgAlpha = if (notification.isRead) 0.3f else 0.6f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = colors.cardBg.copy(alpha = bgAlpha)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    notification.title,
                    fontSize = 14.sp,
                    fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    notification.body,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    lineHeight = 16.sp
                )
                if (notification.createdAt != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        formatTimeAgo(notification.createdAt),
                        fontSize = 10.sp,
                        color = colors.textSecondary.copy(alpha = 0.7f)
                    )
                }
            }

            if (!notification.isRead) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.primary)
                )
            }
        }
    }
}

private fun formatTimeAgo(createdAt: String): String {
    return try {
        // Parse as UTC Instant (server now stores timestamps as UTC)
        val instant = java.time.Instant.parse(createdAt)
        val now = java.time.Instant.now()
        val seconds = java.time.Duration.between(instant, now).seconds
        when {
            seconds < 60 -> "Just now"
            seconds < 3600 -> "${seconds / 60}m ago"
            seconds < 86400 -> "${seconds / 3600}h ago"
            else -> "${seconds / 86400}d ago"
        }
    } catch (e: Exception) {
        ""
    }
}

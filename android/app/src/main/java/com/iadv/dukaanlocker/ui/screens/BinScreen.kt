package com.iadv.dukaanlocker.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.api.NotificationItem
import com.iadv.dukaanlocker.ui.theme.LocalAppColors

@Composable
fun BinScreen(
    onBack: () -> Unit,
    notifications: List<NotificationItem>,
    onDeletePermanent: () -> Unit,
    onRestore: (NotificationItem) -> Unit,
    onRestoreAll: () -> Unit,
    onDeleteNotification: (NotificationItem) -> Unit
) {
    val colors = LocalAppColors.current
    var deleteTarget by remember { mutableStateOf<NotificationItem?>(null) }

    // Auto-mark as read when screen opens
    LaunchedEffect(Unit) {
        // Optional: mark bin notifications as read
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.background,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Bin",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (notifications.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onRestoreAll,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text(
                                "Restore",
                                color = colors.primary,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onDeletePermanent,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text(
                                "Delete permanently",
                                color = Color.Red,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
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
                        "Bin is empty",
                        fontSize = 16.sp,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No notifications in bin",
                        fontSize = 14.sp,
                        color = colors.textSecondary.copy(alpha = 0.7f)
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
                    BinNotificationCard(
                        notification = notification,
                        colors = colors,
                        onRestore = { onRestore(notification) },
                        onLongClick = { deleteTarget = notification }
                    )
                }
            }
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            shape = RoundedCornerShape(20.dp),
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    "Delete notification?",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete this notification?",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteTarget?.let(onDeleteNotification)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                Button(
                    onClick = { deleteTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BinNotificationCard(
    notification: NotificationItem,
    colors: com.iadv.dukaanlocker.ui.theme.AppColors,
    onRestore: () -> Unit,
    onLongClick: () -> Unit
) {
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
            .padding(8.dp)
            .combinedClickable(
                onClick = onRestore,
                onLongClick = onLongClick
            ),
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

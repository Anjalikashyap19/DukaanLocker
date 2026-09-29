package com.iadv.dukaanlocker.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
fun NotificationScreen(
    notifications: List<NotificationItem>,
    onBack: () -> Unit,
    onClearAll: () -> Unit,
    onOpenBin: () -> Unit,
    onNotificationClick: (NotificationItem) -> Unit,
    onMarkAllRead: () -> Unit,
    onMoveToBin: (NotificationItem) -> Unit,
    onRenew: (NotificationItem) -> Unit = {}
) {
    val colors = LocalAppColors.current
    var binTarget by remember { mutableStateOf<NotificationItem?>(null) }
    var renewTarget by remember { mutableStateOf<NotificationItem?>(null) }
    var selectedCategory by remember { mutableStateOf(NotificationCategory.ALL) }
    val filteredNotifications = remember(notifications, selectedCategory) {
        notifications.filter { it.matchesCategory(selectedCategory) }
    }

    // Auto-mark all as read when screen opens
    LaunchedEffect(Unit) {
        onMarkAllRead()
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
                        "Notifications",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onOpenBin) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Bin",
                            tint = colors.textPrimary
                        )
                    }
                    if (notifications.isNotEmpty()) {
                        TextButton(
                            onClick = onClearAll,
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text(
                                "Clear all",
                                color = colors.primary,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        },
        containerColor = colors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (notifications.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NotificationCategory.values().forEach { category ->
                        val count = if (category == NotificationCategory.ALL) {
                            notifications.size
                        } else {
                            notifications.count { it.matchesCategory(category) }
                        }
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = {
                                Text(
                                    "${category.label} ($count)",
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (filteredNotifications.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                            if (notifications.isEmpty()) "No notifications yet"
                            else "No ${selectedCategory.label.lowercase()} notifications",
                            fontSize = 16.sp,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(filteredNotifications) { notification ->
                        NotificationCard(
                            notification = notification,
                            colors = colors,
                            onClick = { onNotificationClick(notification) },
                            onLongClick = { binTarget = notification },
                            onRenew = { renewTarget = notification }
                        )
                    }
                }
            }
        }
    }

    if (binTarget != null) {
        AlertDialog(
            onDismissRequest = { binTarget = null },
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
                    "Move to bin?",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    "Do you want to move this notification to bin?",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        binTarget?.let(onMoveToBin)
                        binTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Move", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                Button(
                    onClick = { binTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }

    if (renewTarget != null) {
        val target = renewTarget!!
        AlertDialog(
            onDismissRequest = { renewTarget = null },
            shape = RoundedCornerShape(20.dp),
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    "Renew document?",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    "We'll renew your ${(target.metadata ?: "document").replace('_', ' ')} and upload the certificate to your Dukaan Locker within 24 hours.",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenew(target)
                        renewTarget = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Renew", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Button(
                    onClick = { renewTarget = null },
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
private fun NotificationCard(
    notification: NotificationItem,
    colors: com.iadv.dukaanlocker.ui.theme.AppColors,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRenew: (() -> Unit)? = null
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
            .combinedClickable(
                onClick = onClick,
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
                if (onRenew != null && notification.type in RENEWABLE_NOTIFICATION_TYPES) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onRenew,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Renew", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
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

private enum class NotificationCategory(val label: String) {
    ALL("All"),
    MISSING_DOC("Missing Doc"),
    ALERT("Alert"),
    ACTIVITY("Activity")
}

private val ALERT_NOTIFICATION_TYPES = setOf("EXPIRING_SOON", "EXPIRED", "NO_BUSINESS")

private val RENEWABLE_NOTIFICATION_TYPES = setOf("EXPIRING_SOON", "EXPIRED")

private fun NotificationItem.effectiveCategory(): NotificationCategory = when (category) {
    "MISSING_DOC" -> NotificationCategory.MISSING_DOC
    "ALERT" -> NotificationCategory.ALERT
    "ACTIVITY" -> NotificationCategory.ACTIVITY
    else -> when {
        type == "MISSING_DOCUMENT" -> NotificationCategory.MISSING_DOC
        type in ALERT_NOTIFICATION_TYPES -> NotificationCategory.ALERT
        else -> NotificationCategory.ACTIVITY
    }
}

private fun NotificationItem.matchesCategory(category: NotificationCategory): Boolean =
    effectiveCategory() == category

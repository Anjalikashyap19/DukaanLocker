package com.iadv.dukaanlocker.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.ToastMessage
import com.iadv.dukaanlocker.ToastType
import com.iadv.dukaanlocker.ui.theme.AppColors
import com.iadv.dukaanlocker.ui.theme.LocalAppColors

@Composable
fun ToastOverlay(
    toasts: List<ToastMessage>,
    onDismiss: (Long) -> Unit
) {
    if (toasts.isEmpty()) return

    val colors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        toasts.forEach { toast ->
            ToastItem(
                toast = toast,
                colors = colors,
                onDismiss = { onDismiss(toast.id) }
            )
        }
    }
}

@Composable
private fun ToastItem(
    toast: ToastMessage,
    colors: AppColors,
    onDismiss: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    val offsetXAnim = animateFloatAsState(
        targetValue = if (isVisible) 0f else 400f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
    )

    val alphaAnim = animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(200)
    )

    val toastBg = when (toast.type) {
        ToastType.SUCCESS -> Color(0xFFD1FAE5)  // Light green
        ToastType.ERROR -> Color(0xFFFEF2F2)    // Light red
        ToastType.WARNING -> Color(0xFFFFFBE6)  // Light amber
        ToastType.INFO -> Color(0xFFEFF6FF)     // Light blue
    }

    val iconTint = when (toast.type) {
        ToastType.SUCCESS -> Color(0xFF059669)  // Emerald
        ToastType.ERROR -> Color(0xFFDC2626)    // Red
        ToastType.WARNING -> Color(0xFFF59E0B)  // Amber
        ToastType.INFO -> Color(0xFF2563EB)     // Blue
    }

    val textColor = when (toast.type) {
        ToastType.SUCCESS -> Color(0xFF065F46)  // Dark green
        ToastType.ERROR -> Color(0xFF991B1B)    // Dark red
        ToastType.WARNING -> Color(0xFF92400E)  // Dark amber
        ToastType.INFO -> Color(0xFF1E3A8A)     // Dark blue
    }

    val icon = when (toast.type) {
        ToastType.SUCCESS -> Icons.Default.CheckCircle
        ToastType.ERROR -> Icons.Default.Error
        ToastType.WARNING -> Icons.Default.Warning
        ToastType.INFO -> Icons.Default.Info
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .animateContentSize()
            .graphicsLayer {
                translationX = offsetXAnim.value
                alpha = alphaAnim.value
            },
        shape = RoundedCornerShape(12.dp),
        color = toastBg,
        shadowElevation = 4.dp,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = iconTint
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = toast.message,
                    fontSize = 13.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = textColor,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    modifier = Modifier.size(18.dp),
                    tint = textColor.copy(alpha = 0.5f)
                )
            }
        }
    }
}
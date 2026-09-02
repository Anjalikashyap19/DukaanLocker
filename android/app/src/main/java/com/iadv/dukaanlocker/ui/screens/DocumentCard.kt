package com.iadv.dukaanlocker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.*
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.strings.LocalAppLanguage
import com.iadv.dukaanlocker.ui.theme.*

// ── Document Card ────────────────────────────────────────────────────────────
@Composable
fun DocumentCard(
    doc: DocumentItem,
    onFetch: () -> Unit,
    onUpload: () -> Unit,
    onView: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalAppColors.current
    val lang = LocalAppLanguage.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = doc.status != "MISSING") { onView() },
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            when (doc.status) {
                "FETCHED" -> colors.success.copy(alpha = 0.4f)
                "UPLOADED" -> colors.secondary.copy(alpha = 0.4f)
                else -> colors.border
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(doc.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text(
                        docDescription(doc.type),
                        fontSize = 11.sp, color = colors.textSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when (doc.status) {
                                "FETCHED" -> colors.success.copy(alpha = 0.15f)
                                "UPLOADED" -> colors.secondary.copy(alpha = 0.15f)
                                else -> colors.error.copy(alpha = 0.15f)
                            }
                        )
                        .border(
                            1.dp,
                            when (doc.status) {
                                "FETCHED" -> colors.success.copy(alpha = 0.5f)
                                "UPLOADED" -> colors.secondary.copy(alpha = 0.5f)
                                else -> colors.error.copy(alpha = 0.5f)
                            },
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        when (doc.status) {
                            "FETCHED" -> AppStrings.get(lang, "FETCHED")
                            "UPLOADED" -> AppStrings.get(lang, "UPLOADED")
                            else -> AppStrings.get(lang, "REQUIRED")
                        },
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = when (doc.status) {
                            "FETCHED" -> colors.success
                            "UPLOADED" -> colors.secondary
                            else -> colors.error
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (doc.status == "MISSING") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onFetch,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary.copy(alpha = 0.1f),
                            contentColor = colors.primary
                        ),
                        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(AppStrings.get(lang, "Auto-Fetch"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onUpload,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.secondary.copy(alpha = 0.1f),
                            contentColor = colors.secondary
                        ),
                        border = BorderStroke(1.dp, colors.secondary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(AppStrings.get(lang, "Upload"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        if (doc.regNumber.isNotBlank()) {
                            Text("${AppStrings.get(lang, "REG:")} ${doc.regNumber}", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = colors.textPrimary)
                        }
                        if (doc.expiryDate.isNotBlank()) {
                            Text("${AppStrings.get(lang, "Exp:")} ${doc.expiryDate}", fontSize = 10.sp, color = colors.textSecondary)
                        }
                        if (doc.status == "UPLOADED" && !doc.fileUrl.isNullOrBlank()) {
                            Text(AppStrings.get(lang, "✓ Certificate uploaded"), fontSize = 10.sp, color = colors.success, fontWeight = FontWeight.Medium)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Show preview button if fileUrl is available
                        if (!doc.fileUrl.isNullOrBlank()) {
                            IconButton(onClick = onView, modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(colors.success.copy(alpha = 0.15f))) {
                                Icon(Icons.Default.Visibility, contentDescription = AppStrings.get(lang, "Preview Certificate"), tint = colors.success, modifier = Modifier.size(14.dp))
                            }
                        } else {
                            IconButton(onClick = onView, modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(colors.border)) {
                                Icon(Icons.Default.Visibility, contentDescription = AppStrings.get(lang, "View"), tint = colors.accent, modifier = Modifier.size(14.dp))
                            }
                        }
                        IconButton(onClick = onUpload, modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(colors.secondary.copy(alpha = 0.15f))) {
                            Icon(Icons.Default.Refresh, contentDescription = AppStrings.get(lang, "Reupload"), tint = colors.secondary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CertificateField(
    label: String, value: String, isHighlight: Boolean = false,
    textColor: Color = Color.Unspecified,
    colors: AppColors = LocalAppColors.current
) {
    val effectiveTextColor = if (textColor == Color.Unspecified) colors.textPrimary else textColor
    Column {
        Text(label.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary.copy(alpha = 0.6f), letterSpacing = 0.5.sp)
        Text(value, fontSize = if (isHighlight) 14.sp else 12.sp, fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal,
            color = effectiveTextColor, fontFamily = if (isHighlight) FontFamily.Monospace else FontFamily.Default)
        Spacer(modifier = Modifier.height(2.dp))
        HorizontalDivider(color = colors.border.copy(alpha = 0.5f), thickness = 0.5.dp)
    }
}

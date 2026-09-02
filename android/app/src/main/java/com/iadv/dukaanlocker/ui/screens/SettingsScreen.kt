package com.iadv.dukaanlocker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.iadv.dukaanlocker.ui.components.SkeletonSettingsCard
import com.iadv.dukaanlocker.ui.components.SkeletonText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.*
import com.iadv.dukaanlocker.api.ShopResponse
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.strings.LocalAppLanguage
import com.iadv.dukaanlocker.ui.theme.*

// ── Settings Screen (bottom-nav "Settings" tab) ───────────────────────────────
@Composable
fun SettingsScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    user: UserAccount,
    onLogout: () -> Unit,
    businesses: List<ShopResponse> = emptyList(),
    isLoadingShops: Boolean = false
) {
    val colors = LocalAppColors.current
    val lang = LocalAppLanguage.current
    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.background,
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text(AppStrings.get(lang, "Settings"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text(AppStrings.get(lang, "Account & preferences"), fontSize = 12.sp, color = colors.textSecondary)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                if (isLoadingShops) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SkeletonText(width = 0.5f, height = 12)
                        Spacer(modifier = Modifier.height(4.dp))
                        repeat(2) {
                            SkeletonSettingsCard()
                        }
                    }
                } else if (businesses.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            AppStrings.get(lang, "Businesses you manage"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                            letterSpacing = 1.sp
                        )
                        businesses.forEach { shop ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                                border = BorderStroke(1.dp, colors.border),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(colors.primary.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Store, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                    Text(shop.shopName.ifBlank { AppStrings.get(lang, "Business") }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text(AppStrings.get(lang, "Manager Access"), fontSize = 12.sp, color = colors.primary, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = colors.border)
                                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${AppStrings.get(lang, "Owner:")} ${shop.ownerName}", fontSize = 14.sp, color = colors.textSecondary)
                    Text("${AppStrings.get(lang, "Mobile:")} +91 ${shop.mobile}", fontSize = 14.sp, color = colors.textSecondary)
                                    val businessAddress = listOfNotNull(shop.address, shop.city, shop.state, shop.pincode)
                                        .filter { it.isNotBlank() }
                                        .joinToString(", ")
                    if (businessAddress.isNotBlank()) {
                        Text("${AppStrings.get(lang, "Address:")} $businessAddress", fontSize = 14.sp, color = colors.textSecondary)
                                    }
                                }
                            }
                        }
                    }
                } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                    border = BorderStroke(1.dp, colors.border),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(colors.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Store, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                 Text(user.name.ifBlank { AppStrings.get(lang, "User") }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                Text(user.role, fontSize = 12.sp, color = colors.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = colors.border)
                        Spacer(modifier = Modifier.height(8.dp))
                         Text("${AppStrings.get(lang, "Mobile:")} +91 ${user.mobile}", fontSize = 14.sp, color = colors.textSecondary)
                        if (user.email.isNotBlank()) {
                            Text("${AppStrings.get(lang, "Email:")} ${user.email}", fontSize = 14.sp, color = colors.textSecondary)
                        }
                    }
                }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                    border = BorderStroke(1.dp, colors.border),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        TextButton(
                            onClick = onToggleTheme,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                     contentDescription = AppStrings.get(lang, "Toggle theme"),
                                    modifier = Modifier.size(20.dp),
                                    tint = colors.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                     if (isDarkTheme) AppStrings.get(lang, "Switch to Light Theme") else AppStrings.get(lang, "Switch to Dark Theme"),
                                    color = colors.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        HorizontalDivider(color = colors.border)
                        TextButton(
                            onClick = onLogout,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Logout, contentDescription = null, tint = colors.error, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                 Text(AppStrings.get(lang, "Logout & Clear Session"), color = colors.error, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

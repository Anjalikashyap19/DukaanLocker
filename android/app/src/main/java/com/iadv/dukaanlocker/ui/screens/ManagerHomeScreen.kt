package com.iadv.dukaanlocker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.iadv.dukaanlocker.ui.components.SkeletonBusinessCard
import com.iadv.dukaanlocker.ui.components.ShimmerEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.BusinessProfile
import com.iadv.dukaanlocker.DocumentItem
import com.iadv.dukaanlocker.ManagerAccess
import com.iadv.dukaanlocker.UserAccount
import com.iadv.dukaanlocker.docDescription
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.strings.LocalAppLanguage
import com.iadv.dukaanlocker.ui.theme.*

@Composable
fun ManagerHomeScreen(
    user: UserAccount,
    managerAccess: ManagerAccess,
    businesses: List<BusinessProfile>,
    documents: List<DocumentItem>,
    onFetchDoc: (DocumentItem) -> Unit,
    onUploadDoc: (DocumentItem) -> Unit,
    onViewDoc: (DocumentItem) -> Unit,
    onDeleteDoc: (DocumentItem) -> Unit,
    onLogout: () -> Unit,
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {},
    isLoadingShops: Boolean = false
) {
    val colors = LocalAppColors.current
    val lang = LocalAppLanguage.current
    val assignedBusinesses = if (!isLoadingShops) businesses.filter { it.id in managerAccess.assignedBusinessIds } else emptyList()
    var selectedBusinessId by remember { mutableStateOf<String?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Logout confirmation dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = colors.cardBg,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(AppStrings.get(lang, "Logout"), fontWeight = FontWeight.Bold, color = colors.textPrimary)
            },
            text = {
                Text(AppStrings.get(lang, "Are you sure you want to logout?"), color = colors.textSecondary, fontSize = 14.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.error),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(AppStrings.get(lang, "Logout"), color = colors.textOnPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                        Text(AppStrings.get(lang, "Cancel"), color = colors.primary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        if (isLoadingShops) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ShimmerEffect(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(12.dp),
                        shape = RoundedCornerShape(4.dp)
                    )
                }
                items(3) {
                    SkeletonBusinessCard()
                }
            }
        } else {
            // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.background,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("${AppStrings.get(lang, "Welcome,")} ${user.name}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        Text(
                            "${assignedBusinesses.size} ${if (assignedBusinesses.size == 1) AppStrings.get(lang, "Business") else AppStrings.get(lang, "Businesses")} • ${AppStrings.get(lang, "Manager")}",
                            fontSize = 12.sp, color = colors.textSecondary
                        )
                        Text("${AppStrings.get(lang, "Code:")} ${managerAccess.code}", fontSize = 11.sp, color = colors.primary)
                    }
                }
                IconButton(onClick = { showLogoutDialog = true }) {
                    Icon(Icons.Default.Logout, contentDescription = AppStrings.get(lang, "Logout"), tint = colors.textSecondary)
                }
            }
        }

        if (selectedBusinessId == null) {
            // Assigned Businesses List
            if (assignedBusinesses.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Business, contentDescription = null, tint = colors.textSecondary.copy(alpha = 0.3f), modifier = Modifier.size(80.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(AppStrings.get(lang, "No Assigned Businesses"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
                        Text(AppStrings.get(lang, "Contact the owner for access"), fontSize = 13.sp, color = colors.textSecondary.copy(alpha = 0.6f))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(AppStrings.get(lang, "ASSIGNED BUSINESSES"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary, letterSpacing = 1.sp)
                    }

                    items(assignedBusinesses) { business ->
                        val bizDocs = documents.filter { it.businessId == business.id }
                        val secured = bizDocs.count { it.status != "MISSING" }
                        val total = bizDocs.size

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedBusinessId = business.id },
                            colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                            border = BorderStroke(1.dp, colors.border),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Store, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(business.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                    Text("${business.category} • $secured/$total ${AppStrings.get(lang, "docs secured")}", fontSize = 12.sp, color = colors.textSecondary)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.textSecondary)
                            }
                        }
                    }
                }
            }
        } else {
            // Selected business detail with documents
            val business = assignedBusinesses.find { it.id == selectedBusinessId }
            if (business != null) {
                val bizDocs = documents.filter { it.businessId == business.id }
                ManagerBusinessDetailView(
                    business = business,
                    documents = bizDocs,
                    onBack = { selectedBusinessId = null },
                    onFetch = onFetchDoc,
                    onUpload = onUploadDoc,
                    onView = onViewDoc,
                    onDelete = onDeleteDoc
                )
            }
        }
        }
    }
}

@Composable
private fun ManagerBusinessDetailView(
    business: BusinessProfile,
    documents: List<DocumentItem>,
    onBack: () -> Unit,
    onFetch: (DocumentItem) -> Unit,
    onUpload: (DocumentItem) -> Unit,
    onView: (DocumentItem) -> Unit,
    onDelete: (DocumentItem) -> Unit
) {
    val colors = LocalAppColors.current
    val lang = LocalAppLanguage.current
    val secured = documents.count { it.status != "MISSING" }
    val total = documents.size

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(modifier = Modifier.fillMaxWidth(), color = colors.background, shadowElevation = 4.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                     Icon(Icons.Default.ArrowBack, contentDescription = AppStrings.get(lang, "Back"), tint = colors.textPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(business.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text("${business.category} • ${business.scale}", fontSize = 12.sp, color = colors.textSecondary)
                }
                Text("$secured/$total", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = if (secured == total) colors.success else colors.accent)
            }
        }

        Text(
            AppStrings.get(lang, "REQUIRED DOCUMENTS"),
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary, letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(documents) { doc ->
                DocumentCard(
                    doc = doc,
                    onFetch = { onFetch(doc) },
                    onUpload = { onUpload(doc) },
                    onView = { onView(doc) },
                    onDelete = { onDelete(doc) }
                )
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}


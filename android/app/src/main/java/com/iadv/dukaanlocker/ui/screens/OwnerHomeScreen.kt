package com.iadv.dukaanlocker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.iadv.dukaanlocker.ui.components.SkeletonBusinessCard
import com.iadv.dukaanlocker.ui.components.ShimmerEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.StoreMallDirectory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.*
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.strings.LocalAppLanguage
import com.iadv.dukaanlocker.ui.theme.*

@Composable
fun OwnerHomeScreen(
    user: UserAccount,
    businesses: List<BusinessProfile>,
    documents: List<DocumentItem>,
    managers: List<ManagerAccess> = emptyList(),
    onAddBusiness: () -> Unit,
    onEditBusiness: (BusinessProfile) -> Unit,
    onManageManagers: () -> Unit,
    onFetchDoc: (DocumentItem) -> Unit,
    onUploadDoc: (DocumentItem) -> Unit,
    onViewDoc: (DocumentItem) -> Unit,
    onDeleteDoc: (DocumentItem) -> Unit,
    onLogout: () -> Unit,
    onBusinessSelected: (String) -> Unit = {},
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {},
    onBiometricLoginToggle: ((Boolean) -> Unit)? = null,
    isBiometricLoginEnabled: Boolean = false,
    isBiometricAvailable: Boolean = false,
    onAuthenticateForBiometric: (() -> Unit)? = null,
    showAddBusiness: Boolean = true,
    showManageManagers: Boolean = true,
    isLoadingShops: Boolean = false,
    isLoadingDocuments: Boolean = false
) {
    val colors = LocalAppColors.current
    val lang = LocalAppLanguage.current
    var selectedBusinessId by remember { mutableStateOf<String?>(null) }

    var showSettings by remember { mutableStateOf(false) }
    var biometricLoginEnabled by remember { mutableStateOf(isBiometricLoginEnabled) }
    LaunchedEffect(isBiometricLoginEnabled) {
        biometricLoginEnabled = isBiometricLoginEnabled
    }

    val businessDocs = if (selectedBusinessId != null)
        documents.filter { it.businessId == selectedBusinessId }
    else emptyList()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.background,
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                Icon(Icons.Default.Store, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                 Text("${AppStrings.get(lang, "Welcome,")} ${user.name}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                 Text(
                                     if (businesses.size == 1) "1 ${AppStrings.get(lang, "Business")} • ${AppStrings.get(lang, "Owner")}"
                                     else "${businesses.size} ${AppStrings.get(lang, "Businesses")} • ${AppStrings.get(lang, "Owner")}",
                                     fontSize = 12.sp, color = colors.textSecondary
                                 )
                            }
                        }
                        IconButton(onClick = { showSettings = true }) {
                             Icon(Icons.Default.Settings, contentDescription = AppStrings.get(lang, "Settings"), tint = colors.textSecondary)
                        }
                    }
                }
            }

            if (isLoadingShops) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        ShimmerEffect(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(12.dp),
                            shape = RoundedCornerShape(4.dp)
                        )
                    }
                    items(3) {
                        SkeletonBusinessCard()
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            } else if (businesses.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.StoreMallDirectory, contentDescription = null, tint = colors.textSecondary.copy(alpha = 0.3f), modifier = Modifier.size(96.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                         Text(AppStrings.get(lang, "No Businesses Added"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
                         Text(AppStrings.get(lang, "Tap + to add your first business"), fontSize = 14.sp, color = colors.textSecondary.copy(alpha = 0.6f))
                    }
                }
            } else if (selectedBusinessId == null) {
                // Business List View
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                             Text(AppStrings.get(lang, "YOUR BUSINESSES"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary, letterSpacing = 1.sp)
                            if (showManageManagers) {
                                TextButton(onClick = onManageManagers) {
                                    Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                     Text(AppStrings.get(lang, "Managers"), color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    items(businesses) { business ->
                        BusinessCard(
                            business = business,
                            documents = documents.filter { it.businessId == business.id },
                            managers = managers,
                            onSelect = {
                                selectedBusinessId = business.id
                                onBusinessSelected(business.id)
                            },
                            onEdit = { onEditBusiness(business) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            } else {
                // Business Detail / Document Management View
                val selectedBusiness = businesses.find { it.id == selectedBusinessId }
                if (selectedBusiness != null) {
                    BusinessDetailView(
                        business = selectedBusiness,
                        documents = businessDocs,
                        onBack = { selectedBusinessId = null },
                        onEdit = { onEditBusiness(selectedBusiness) },
                        onFetch = onFetchDoc,
                        onUpload = onUploadDoc,
                        onView = onViewDoc,
                        onDelete = onDeleteDoc
                    )
                }
            }
        }

        // Settings Dialog
        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                containerColor = colors.cardBg,
                shape = RoundedCornerShape(20.dp),
                title = {
                     Text(AppStrings.get(lang, "Settings"), fontWeight = FontWeight.Bold, color = colors.textPrimary)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                         Text("${AppStrings.get(lang, "Account:")} ${user.name}", color = colors.textSecondary, fontSize = 14.sp)
                         Text("${AppStrings.get(lang, "Mobile:")} +91 ${user.mobile}", color = colors.textSecondary, fontSize = 14.sp)
                         Text("${AppStrings.get(lang, "Role:")} ${user.role}", color = colors.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = colors.border)

                        // Theme Toggle
                        TextButton(onClick = {
                            showSettings = false
                            onToggleTheme()
                        }) {
                            Icon(
                                if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                 contentDescription = AppStrings.get(lang, "Toggle theme"), modifier = Modifier.size(18.dp),
                                tint = colors.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                 if (isDarkTheme) AppStrings.get(lang, "Switch to Light Theme") else AppStrings.get(lang, "Switch to Dark Theme"),
                                color = colors.primary, fontWeight = FontWeight.Bold
                            )
                        }

                        // Biometric Login Toggle (only if biometric is available)
                        if (isBiometricAvailable && onBiometricLoginToggle != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Lock,
                                         contentDescription = AppStrings.get(lang, "Biometric Login"),
                                        modifier = Modifier.size(18.dp),
                                        tint = colors.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                             AppStrings.get(lang, "Biometric Login"),
                                            color = colors.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                             if (biometricLoginEnabled) AppStrings.get(lang, "Auto-login with fingerprint enabled") else AppStrings.get(lang, "Enable fingerprint auto-login"),
                                            color = colors.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Switch(
                                    checked = biometricLoginEnabled,
                                    onCheckedChange = { newValue ->
                                        if (newValue) {
                                            onAuthenticateForBiometric?.invoke()
                                        } else {
                                            biometricLoginEnabled = false
                                            onBiometricLoginToggle(false)
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = colors.primary,
                                        checkedThumbColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = colors.border)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Logout Button
                        TextButton(onClick = {
                            showSettings = false
                            onLogout()
                        }) {
                            Icon(Icons.Default.Logout, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                             Text(AppStrings.get(lang, "Logout & Clear Session"), color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettings = false }) {
                        Text(AppStrings.get(lang, "Close"), color = colors.primary)
                    }
                }
            )
        }

        // FAB for adding businesses (only for owners)
        if (selectedBusinessId == null && showAddBusiness) {
            FloatingActionButton(
                onClick = onAddBusiness,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .navigationBarsPadding(),
                containerColor = colors.primary,
                contentColor = colors.background,
                shape = RoundedCornerShape(16.dp)
            ) {
                             Icon(Icons.Default.Add, contentDescription = AppStrings.get(lang, "Add Business"), modifier = Modifier.size(28.dp))
            }
        }
    }
}

// ── Business Card ────────────────────────────────────────────────────────────
@Composable
private fun BusinessCard(
    business: BusinessProfile,
    documents: List<DocumentItem>,
    managers: List<ManagerAccess> = emptyList(),
    onSelect: () -> Unit,
    onEdit: () -> Unit
) {
    val colors = LocalAppColors.current
    val lang = LocalAppLanguage.current
    val secured = documents.count { it.status != "MISSING" }
    val total = documents.size
    val progress = if (total > 0) secured.toFloat() / total.toFloat() else 0f
    val bizManagers = managers.filter { business.id in it.assignedBusinessIds }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        border = BorderStroke(1.dp, colors.border),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // ── Row 1: icon + name + category + edit ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Store, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(business.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            business.category, fontSize = 11.sp, color = colors.textSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(" • ", fontSize = 11.sp, color = colors.textSecondary.copy(alpha = 0.3f))
                        Text(business.scale, fontSize = 11.sp, color = colors.textSecondary)
                    }
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = AppStrings.get(lang, "Edit"), tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                }
            }

            // ── Row 2: Owner + City, State + Branch ──
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.padding(start = 52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(business.ownerName, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary.copy(alpha = 0.8f))
                if (business.city.isNotBlank() || business.state.isNotBlank()) {
                    Text("  •  ", fontSize = 11.sp, color = colors.textSecondary.copy(alpha = 0.3f))
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = colors.textSecondary.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        buildString {
                            if (business.city.isNotBlank()) append(business.city)
                            if (business.city.isNotBlank() && business.state.isNotBlank()) append(", ")
                            if (business.state.isNotBlank()) append(business.state)
                        },
                        fontSize = 11.sp, color = colors.textSecondary, maxLines = 1
                    )
                }
                if (business.branchName.isNotBlank()) {
                    Text("  •  ", fontSize = 11.sp, color = colors.textSecondary.copy(alpha = 0.3f))
                    Icon(Icons.Default.Business, contentDescription = null, tint = colors.textSecondary.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(business.branchName, fontSize = 11.sp, color = colors.textSecondary, maxLines = 1)
                }
            }

            // ── Row 3: Managers ──
            if (bizManagers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.padding(start = 52.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.People, contentDescription = null, tint = colors.secondary.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        bizManagers.map { it.managerName }.joinToString(", "),
                        fontSize = 11.sp, color = colors.secondary, maxLines = 1
                    )
                }
            }

            // ── Divider ──
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = colors.border.copy(alpha = 0.5f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // ── Document Progress ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (progress == 1f) colors.success.copy(alpha = 0.15f)
                                else colors.primary.copy(alpha = 0.1f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = if (progress == 1f) colors.success else colors.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (progress == 1f) AppStrings.get(lang, "All documents secured") else if (total == 0) AppStrings.get(lang, "No documents") else "$secured ${AppStrings.get(lang, "of")} $total ${AppStrings.get(lang, "completed")}",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${(progress * 100).toInt()}%",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = if (progress == 1f) colors.success else colors.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.width(56.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.border)) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = progress)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (progress == 1f) colors.success else colors.primary)
                        )
                    }
                }
            }
        }
    }
}

// ── Business Detail / Document Management ────────────────────────────────────
@Composable
private fun BusinessDetailView(
    business: BusinessProfile,
    documents: List<DocumentItem>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onFetch: (DocumentItem) -> Unit,
    onUpload: (DocumentItem) -> Unit,
    onView: (DocumentItem) -> Unit,
    onDelete: (DocumentItem) -> Unit
) {
    val colors = LocalAppColors.current
    val lang = LocalAppLanguage.current
    val secured = documents.count { it.status != "MISSING" }
    val total = documents.size
    val progress = if (total > 0) secured.toFloat() / total.toFloat() else 0f

    Column(modifier = Modifier.fillMaxSize()) {
        // Business Detail Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.background,
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = AppStrings.get(lang, "Back"), tint = colors.textPrimary)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(business.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        Text("${business.category} • ${business.scale} • ${business.state}", fontSize = 12.sp, color = colors.textSecondary)
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = AppStrings.get(lang, "Edit"), tint = colors.primary)
                    }
                }
            }
        }

        // Progress Summary
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBg),
            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(AppStrings.get(lang, "DOCUMENT COMPLIANCE"), fontSize = 11.sp, color = colors.textSecondary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(
                        if (progress == 1f) AppStrings.get(lang, "ALL DOCUMENTS SECURED ✓")
                        else "$secured ${AppStrings.get(lang, "of")} $total ${AppStrings.get(lang, "documents completed")}",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = if (progress == 1f) colors.success else colors.accent
                    )
                }
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(44.dp),
                        color = if (progress == 1f) colors.success else colors.primary,
                        trackColor = colors.border,
                        strokeWidth = 4.dp
                    )
                    Text("$secured/$total", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 0.dp)
                    .padding(bottom = 16.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (progress == 1f) colors.success else colors.primary,
                trackColor = colors.border
            )
        }

        // Required Documents Section
        Text(
            text = AppStrings.get(lang, "REQUIRED DOCUMENTS"),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textSecondary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
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

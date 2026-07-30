package com.example.dukaanlocker.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.StoreMallDirectory
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.dukaanlocker.*
import com.example.dukaanlocker.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
    onToggleTheme: () -> Unit = {}
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var selectedBusinessId by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    // Filter documents for selected business
    val businessDocs = if (selectedBusinessId != null)
        documents.filter { it.businessId == selectedBusinessId }
    else emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
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
                            Text("Welcome, ${user.name}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            Text(
                                if (businesses.size == 1) "1 Business • Owner"
                                else "${businesses.size} Businesses • Owner",
                                fontSize = 12.sp, color = colors.textSecondary
                            )
                        }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = colors.textSecondary)
                    }
                }
            }
        }

        if (businesses.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.StoreMallDirectory, contentDescription = null, tint = colors.textSecondary.copy(alpha = 0.3f), modifier = Modifier.size(96.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No Businesses Added", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
                    Text("Tap + to add your first business", fontSize = 14.sp, color = colors.textSecondary.copy(alpha = 0.6f))
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
                        Text("YOUR BUSINESSES", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary, letterSpacing = 1.sp)
                        TextButton(onClick = onManageManagers) {
                            Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Managers", color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                        items(businesses) { business ->                        BusinessCard(
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
                    Spacer(modifier = Modifier.height(72.dp)) // FAB padding
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
                Text("Settings", fontWeight = FontWeight.Bold, color = colors.textPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Account: ${user.name}", color = colors.textSecondary, fontSize = 14.sp)
                    Text("Mobile: +91 ${user.mobile}", color = colors.textSecondary, fontSize = 14.sp)
                    Text("Role: ${user.role}", color = colors.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = colors.border)
                    TextButton(onClick = {
                        showSettings = false
                        onToggleTheme()
                    }) {
                        Icon(
                            if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle theme", modifier = Modifier.size(18.dp),
                            tint = colors.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isDarkTheme) "Switch to Light Theme" else "Switch to Dark Theme",
                            color = colors.primary, fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = colors.border)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = {
                        showSettings = false
                        onLogout()
                    }) {
                        Icon(Icons.Default.Logout, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Logout & Clear Session", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("Close", color = colors.primary)
                }
            }
        )
    }

    // FAB for adding businesses
    if (selectedBusinessId == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomEnd
        ) {
            FloatingActionButton(
                onClick = onAddBusiness,
                modifier = Modifier.padding(20.dp),
                containerColor = colors.primary,
                contentColor = colors.background,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Business", modifier = Modifier.size(28.dp))
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
    val secured = documents.count { it.status != "MISSING" }
    val total = documents.size.coerceAtLeast(1)
    val progress = secured.toFloat() / total.toFloat()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        border = BorderStroke(1.dp, colors.border),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top row: icon + name + edit
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Store, contentDescription = null, tint = colors.primary, modifier = Modifier.size(26.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(business.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1)
                    Spacer(modifier = Modifier.height(1.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(business.category, fontSize = 11.sp, color = colors.textSecondary, maxLines = 1)
                        Text("  •  ", fontSize = 11.sp, color = colors.textSecondary.copy(alpha = 0.3f))
                        Text(business.scale, fontSize = 11.sp, color = colors.textSecondary)
                    }
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(business.ownerName, fontSize = 12.sp, color = colors.textPrimary.copy(alpha = 0.8f), fontWeight = FontWeight.Medium)
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                }
            }

            // Location: City, State
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.padding(start = 62.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = colors.textSecondary.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    buildString {
                        if (business.city.isNotBlank()) append(business.city)
                        if (business.city.isNotBlank() && business.state.isNotBlank()) append(", ")
                        if (business.state.isNotBlank()) append(business.state)
                    },
                    fontSize = 11.sp, color = colors.textSecondary, maxLines = 1
                )
            }

            // Branch info
            if (business.branchName.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.padding(start = 62.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Business, contentDescription = null, tint = colors.textSecondary.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(business.branchName, fontSize = 11.sp, color = colors.textSecondary, maxLines = 1)
                }
            }

            // Assigned managers
            val bizManagers = managers.filter { business.id in it.assignedBusinessIds }
            if (bizManagers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.padding(start = 62.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.People, contentDescription = null, tint = colors.secondary.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        bizManagers.map { it.managerName }.joinToString(", "),
                        fontSize = 11.sp, color = colors.secondary, maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = colors.border.copy(alpha = 0.5f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Document Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
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
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            if (progress == 1f) "All documents secured" else "Documents ($total required)",
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary
                        )
                        Text(
                            if (total > 0) "$secured of $total completed"
                            else "Loading...",
                            fontSize = 11.sp, color = colors.textSecondary
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${(progress * 100).toInt()}%",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = if (progress == 1f) colors.success else colors.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.width(64.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(colors.border)) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = progress)
                                .clip(RoundedCornerShape(3.dp))
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(business.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        Text("${business.category} • ${business.scale} • ${business.state}", fontSize = 12.sp, color = colors.textSecondary)
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = colors.primary)
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
                    Text("DOCUMENT COMPLIANCE", fontSize = 11.sp, color = colors.textSecondary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(
                        if (progress == 1f) "ALL DOCUMENTS SECURED ✓"
                        else "$secured of $total documents completed",
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
            text = "REQUIRED DOCUMENTS",
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

// ── Document Card ────────────────────────────────────────────────────────────
@Composable
private fun DocumentCard(
    doc: DocumentItem,
    onFetch: () -> Unit,
    onUpload: () -> Unit,
    onView: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalAppColors.current
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
                                else -> Color.Red.copy(alpha = 0.15f)
                            }
                        )
                        .border(
                            1.dp,
                            when (doc.status) {
                                "FETCHED" -> colors.success.copy(alpha = 0.5f)
                                "UPLOADED" -> colors.secondary.copy(alpha = 0.5f)
                                else -> Color.Red.copy(alpha = 0.5f)
                            },
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        when (doc.status) {
                            "FETCHED" -> "FETCHED"
                            "UPLOADED" -> "UPLOADED"
                            else -> "REQUIRED"
                        },
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = when (doc.status) {
                            "FETCHED" -> colors.success
                            "UPLOADED" -> colors.secondary
                            else -> Color.Red
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
                        Text("Auto-Fetch", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                        Text("Upload", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("REG: ${doc.regNumber}", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = colors.textPrimary)
                        Text("Exp: ${doc.expiryDate}", fontSize = 10.sp, color = colors.textSecondary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(onClick = onView, modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(colors.border)) {
                            Icon(Icons.Default.Visibility, contentDescription = "View", tint = colors.accent, modifier = Modifier.size(14.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(colors.border)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Dialog: Fetch Document ───────────────────────────────────────────────────
@Composable
fun FetchDocumentDialog(
    doc: DocumentItem,
    shopName: String,
    onDismiss: () -> Unit,
    onSuccess: (regNum: String, issue: String, expiry: String) -> Unit
) {
    val colors = LocalAppColors.current
    var regInput by remember { mutableStateOf("") }
    var isFetching by remember { mutableStateOf(false) }
    var fetchProgress by remember { mutableStateOf(0f) }
    var currentStepText by remember { mutableStateOf("Connecting to National Database...") }

    val labelText = docFetchLabel(doc.type)

    LaunchedEffect(isFetching) {
        if (isFetching) {
            val steps = listOf(
                0.2f to "Connecting to National Portal Gateway...",
                0.5f to "Verifying digital credentials against database...",
                0.8f to "Fetching official e-Certificate...",
                1.0f to "Encrypting and locking in Dukaan Vault..."
            )
            for ((progress, text) in steps) {
                delay(1000)
                fetchProgress = progress
                currentStepText = text
            }
            delay(800)
            val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val today = Date()
            val expiryCal = Calendar.getInstance().apply { add(Calendar.YEAR, 5) }
            onSuccess(regInput.uppercase(), formatter.format(today), formatter.format(expiryCal.time))
        }
    }

    Dialog(onDismissRequest = { if (!isFetching) onDismiss() }) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBg),
            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!isFetching) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = colors.primary, modifier = Modifier.size(48.dp))
                    Text("Auto-Fetch Official Doc", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text("Securely fetch your ${doc.name} from government databases.", fontSize = 12.sp, color = colors.textSecondary, textAlign = TextAlign.Center)

                    OutlinedTextField(
                        value = regInput,
                        onValueChange = { regInput = it },
                        label = { Text(labelText) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel", color = colors.textPrimary) }
                        Button(
                            onClick = { if (regInput.isNotBlank()) isFetching = true },
                            enabled = regInput.isNotBlank(),
                            modifier = Modifier.weight(1.5f),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.background)
                        ) { Text("Confirm Fetch", fontWeight = FontWeight.Bold) }
                    }
                } else {
                    CircularProgressIndicator(progress = { fetchProgress }, modifier = Modifier.size(64.dp), color = colors.primary, trackColor = colors.border, strokeWidth = 6.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("VERIFYING CREDENTIALS", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.accent, letterSpacing = 1.sp)
                    Text(currentStepText, fontSize = 12.sp, color = colors.textSecondary, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ── Dialog: Upload Document ──────────────────────────────────────────────────
@Composable
fun UploadDocumentDialog(
    doc: DocumentItem,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val colors = LocalAppColors.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBg),
            border = BorderStroke(1.dp, colors.secondary.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(48.dp))
                Text("Upload Local File", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text("Select a clear scan or image of your ${doc.name}", fontSize = 12.sp, color = colors.textSecondary, textAlign = TextAlign.Center)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.background)
                        .clickable { onSuccess() }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRoundRect(
                            color = colors.secondary.copy(alpha = 0.5f),
                            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(28.dp))
                        Text("Tap to simulate file upload", fontSize = 13.sp, color = colors.secondary, fontWeight = FontWeight.SemiBold)
                        Text("Supports PDF, PNG, JPG (Max 5MB)", fontSize = 10.sp, color = colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textPrimary) }
                }
            }
        }
    }
}

// ── Dialog: Certificate Viewer ───────────────────────────────────────────────
@Composable
fun CertificateViewerDialog(
    doc: DocumentItem,
    business: BusinessProfile,
    onDismiss: () -> Unit
) {
    val colors = LocalAppColors.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(2.dp, colors.primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("GOVERNMENT OF INDIA", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray, letterSpacing = 1.sp)
                    Text(
                        when (doc.type) {
                            "GST" -> "DEPARTMENT OF REVENUE • GOODS AND SERVICES TAX"
                            "FSSAI" -> "FOOD SAFETY AND STANDARDS AUTHORITY OF INDIA"
                            "ShopEstablishment" -> "DEPARTMENT OF LABOUR & TRADE COMPLIANCE"
                            "DrugLicense" -> "DRUG CONTROL ADMINISTRATION"
                            "HealthTrade" -> "MUNICIPAL CORPORATION REGULATORY DEPT"
                            "FireNOC" -> "STATE FIRE AND EMERGENCY SERVICES"
                            "Udyam" -> "MINISTRY OF MICRO, SMALL & MEDIUM ENTERPRISES"
                            "PAN" -> "INCOME TAX DEPARTMENT • GOVERNMENT OF INDIA"
                            "TAN" -> "INCOME TAX DEPARTMENT • TDS WING"
                            "BusinessRegistration" -> "MINISTRY OF CORPORATE AFFAIRS • ROC"
                            "TradeLicense" -> "MUNICIPAL CORPORATION • COMMERCIAL TAXES"
                            "LabourLicense" -> "DEPARTMENT OF LABOUR & EMPLOYMENT"
                            "FactoryLicense" -> "CHIEF INSPECTOR OF FACTORIES"
                            "EatingHouse" -> "FOOD SAFETY & MUNICIPAL CORPORATION"
                            "PollutionControl" -> "STATE POLLUTION CONTROL BOARD"
                            "ContractorLicense" -> "STATE CONTRACTOR LICENSING AUTHORITY"
                            "BuildingPermit" -> "URBAN DEVELOPMENT AUTHORITY"
                            "BuildingSafety" -> "MUNICIPAL BUILDING SAFETY DEPT"
                            "PSARA" -> "HOME DEPARTMENT • PRIVATE SECURITY"
                            "WarehouseRegistration" -> "FOOD CORPORATION OF INDIA / STATE WAREHOUSE"
                            "InstitutionApproval" -> "EDUCATION DEPARTMENT / UGC / AICTE"
                            "TrustSocietyReg" -> "REGISTRAR OF SOCIETIES / TRUST ACT"
                            "NGO_12A_80G" -> "INCOME TAX DEPARTMENT • EXEMPTIONS"
                            "ClinicalEstablishment" -> "STATE CLINICAL ESTABLISHMENTS AUTHORITY"
                            "MedicalCouncil" -> "MEDICAL COUNCIL OF INDIA / STATE COUNCIL"
                            "BioMedicalWaste" -> "CENTRAL POLLUTION CONTROL BOARD"
                            "HotelLicense" -> "TOURISM DEPARTMENT / MUNICIPAL CORP"
                            "RTO_Permit" -> "REGIONAL TRANSPORT OFFICE"
                            "RBI_IRDAI_SEBI_Auth" -> "RBI / IRDAI / SEBI REGULATORY AUTHORITY"
                            "BIS" -> "BUREAU OF INDIAN STANDARDS"
                            "BIS_Hallmark" -> "BUREAU OF INDIAN STANDARDS • HALLMARK"
                            "DPIIT" -> "DPIIT • MINISTRY OF COMMERCE & INDUSTRY"
                            "FertilizerLicense" -> "DEPARTMENT OF AGRICULTURE / FERTILIZER DIVISION"
                            "VetApproval" -> "DEPARTMENT OF ANIMAL HUSBANDRY"
                            "NGO_DAR" -> "NGO DARPAN • NITI AAYOG"
                            "FCRA" -> "MINISTRY OF HOME AFFAIRS • FCRA WING"
                            else -> "OFFICIAL REGULATORY DEPARTMENT"
                        },
                        fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray, textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = colors.primary, thickness = 2.dp, modifier = Modifier.width(180.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(doc.name.uppercase(), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black, textAlign = TextAlign.Center)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CertificateField("Registration / License No", doc.regNumber, isHighlight = true)
                    CertificateField("Legal Name of Business", business.name.uppercase())
                    CertificateField("Name of Proprietor/Owner", business.ownerName)
                    CertificateField("State of Registration", business.state)
                    CertificateField("Category & Scale", "${business.category} (${business.scale})")
                    CertificateField("Issue Date", doc.issueDate)
                    CertificateField("Validity / Expiry Date", doc.expiryDate)
                    CertificateField("Locker Status", if (doc.status == "FETCHED") "VERIFIED GOVERNMENT DATA" else "SECURE USER UPLOADS",
                        textColor = if (doc.status == "FETCHED") colors.success else colors.secondary)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.background, contentColor = colors.textOnPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Close Document View", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun CertificateField(label: String, value: String, isHighlight: Boolean = false, textColor: Color = Color.Black) {
    Column {
        Text(label.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 0.5.sp)
        Text(value, fontSize = if (isHighlight) 14.sp else 12.sp, fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal,
            color = textColor, fontFamily = if (isHighlight) FontFamily.Monospace else FontFamily.Default)
        Spacer(modifier = Modifier.height(2.dp))
        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f), thickness = 0.5.dp)
    }
}

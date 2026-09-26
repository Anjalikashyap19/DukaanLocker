package com.iadv.dukaanlocker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.iadv.dukaanlocker.ui.components.SkeletonDocumentCard
import com.iadv.dukaanlocker.ui.components.ShimmerEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.*
import com.iadv.dukaanlocker.api.ShopResponse
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.strings.LocalAppLanguage
import com.iadv.dukaanlocker.ui.theme.*

// ── Docs Screen (bottom-nav "Docs" tab) ───────────────────────────────────────
@Composable
fun DocsScreen(
    documents: List<DocumentItem>,
    onFetchDoc: (DocumentItem) -> Unit,
    onUploadDoc: (DocumentItem) -> Unit,
    onViewDoc: (DocumentItem) -> Unit,
    onDeleteDoc: (DocumentItem) -> Unit = {},
    businesses: List<ShopResponse> = emptyList(),
    isLoadingDocuments: Boolean = false,
    onAddCustomDoc: (String) -> Unit = {},
    targetMissingDocumentType: String? = null
) {
    val colors = LocalAppColors.current
    val lang = LocalAppLanguage.current
    val shopNameById = businesses.associate { it.id.toString() to it.shopName }
    val grouped = documents.groupBy { it.type }
    val orderedTypes = grouped.keys.sortedBy { grouped[it]?.first()?.name ?: it }

    // Auto-expand the section for the target missing document type
    var expandedType by remember { mutableStateOf<String?>(targetMissingDocumentType) }
    LaunchedEffect(targetMissingDocumentType) {
        expandedType = targetMissingDocumentType
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.background,
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text(
                    AppStrings.get(lang, "Documents"),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    if (isLoadingDocuments) AppStrings.get(lang, "Loading documents...")
                    else if (documents.isEmpty()) AppStrings.get(lang, "No documents yet")
                    else "${documents.size} ${AppStrings.get(lang, "document")}${if (documents.size == 1) "" else "s"} ${AppStrings.get(lang, "across")} ${businesses.size} ${AppStrings.get(lang, "business")}${if (businesses.size == 1) "" else "es"}",
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            }
        }

        if (isLoadingDocuments) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(4) {
                    SkeletonDocumentCard()
                }
                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        } else if (documents.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = colors.textSecondary.copy(alpha = 0.3f),
                        modifier = Modifier.size(96.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(AppStrings.get(lang, "No Documents"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
                    Text(AppStrings.get(lang, "Upload or auto-fetch documents from a business"), fontSize = 14.sp, color = colors.textSecondary.copy(alpha = 0.6f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
items(orderedTypes) { type ->
                        val docs = grouped[type] ?: emptyList()
                        val isTargetMissing = targetMissingDocumentType != null && type == targetMissingDocumentType
                         DocTypeSection(
                            title = docs.first().name,
                            count = docs.size,
                            docs = docs,
                            shopNameById = shopNameById,
                            lang = lang,
                            onFetchDoc = onFetchDoc,
                            onUploadDoc = onUploadDoc,
                            onViewDoc = onViewDoc,
                            onDeleteDoc = onDeleteDoc,
                            isExpanded = expandedType == type,
                            onExpandChange = { expandedType = if (expandedType == type) null else type },
                            isTargetMissing = isTargetMissing
                        )
                    }
                if (businesses.isNotEmpty()) {
                    item {
                        OutlinedButton(
                            onClick = { onAddCustomDoc(businesses.first().id.toString()) },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(AppStrings.get(lang, "Add Custom Document"), color = colors.primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }
    }
}

@Composable
fun DocTypeSection(
    title: String,
    count: Int,
    docs: List<DocumentItem>,
    shopNameById: Map<String, String>,
    lang: String,
    onFetchDoc: (DocumentItem) -> Unit,
    onUploadDoc: (DocumentItem) -> Unit,
    onViewDoc: (DocumentItem) -> Unit,
    onDeleteDoc: (DocumentItem) -> Unit = {},
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    isTargetMissing: Boolean = false
) {
    val colors = LocalAppColors.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                if (isTargetMissing) BorderStroke(2.dp, colors.error) else BorderStroke(1.dp, colors.border)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isTargetMissing) colors.error.copy(alpha = 0.05f) else colors.cardBg
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandChange(!isExpanded) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = if (isTargetMissing) colors.error else colors.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isTargetMissing) colors.error else colors.textPrimary
                    )
                    Text(
                        "$count ${AppStrings.get(lang, "document")}${if (count == 1) "" else "s"}",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) AppStrings.get(lang, "Collapse") else AppStrings.get(lang, "Expand"),
                    tint = colors.textSecondary
                )
            }

            if (isExpanded) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    docs.forEach { doc ->
                        Column {
                            val shopName = shopNameById[doc.businessId]
                            if (!shopName.isNullOrBlank()) {
                                Text(
                                    shopName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.secondary,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                )
                            }
                            DocumentCard(
                                doc = doc,
                                onFetch = { onFetchDoc(doc) },
                                onUpload = { onUploadDoc(doc) },
                                onView = { onViewDoc(doc) },
                                onDelete = { onDeleteDoc(doc) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

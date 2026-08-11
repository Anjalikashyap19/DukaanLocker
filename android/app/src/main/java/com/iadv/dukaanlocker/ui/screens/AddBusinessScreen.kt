package com.iadv.dukaanlocker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.BuildConfig
import com.iadv.dukaanlocker.BusinessProfile
import com.iadv.dukaanlocker.ManagerAccess
import com.iadv.dukaanlocker.api.OlaMapsClient
import com.iadv.dukaanlocker.api.OlaPrediction
import com.iadv.dukaanlocker.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBusinessScreen(
    initial: BusinessProfile? = null,
    managers: List<ManagerAccess> = emptyList(),
    assignedManagerId: String? = null,
    onSave: (BusinessProfile) -> Unit,
    onManagerSelected: ((managerId: String?) -> Unit)? = null,
    onCancel: () -> Unit
) {
    val colors = LocalAppColors.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var ownerName by remember { mutableStateOf(initial?.ownerName ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "") }
    var scale by remember { mutableStateOf(initial?.scale ?: "Micro") }
    var branchName by remember { mutableStateOf(initial?.branchName ?: "") }
    var state by remember { mutableStateOf(initial?.state ?: "Maharashtra") }
    var city by remember { mutableStateOf(initial?.city ?: "") }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var showScaleDropdown by remember { mutableStateOf(false) }
    var showStateDropdown by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<OlaPrediction>>(emptyList()) }
    var isSearchingLocation by remember { mutableStateOf(false) }
    var locationSelected by remember { mutableStateOf(false) }
    var selectedManagerId by remember { mutableStateOf(assignedManagerId) }
    var showManagerDropdown by remember { mutableStateOf(false) }

    val olaMapsApi = remember { OlaMapsClient.apiService }

    val categories = listOf(
        "Beauty, Salon & Personal Care",
        "Marriage, Banquet & Event Services",
        "Corporate Offices & Commercial Establishments",
        "Banking, Finance & Insurance",
        "Professional & Consultancy Services",
        "Contractors, Builders & Developers",
        "Labour, Security & Manpower Services",
        "Courier, Logistics & Warehousing",
        "Education & Training",
        "NGO, Welfare & Research Organisations",
        "Food Retail & Grocery",
        "Food Wholesale, Distribution & Supply",
        "Food Manufacturing & Processing",
        "Restaurants, Hotels & Catering",
        "Bakery, Sweets & Confectionery",
        "Beverages, Dairy & Packaged Water",
        "Meat, Fish, Poultry & Livestock",
        "Fruits, Vegetables & Agricultural Produce",
        "Agriculture Inputs & Allied Activities",
        "Garments, Textile & Tailoring",
        "Jewellery, Cosmetics & Fashion Accessories",
        "General Retail & Variety Stores",
        "Stationery, Books, Printing & Publishing",
        "IT, Software & Digital Services",
        "Electronics, Electrical & Telecom",
        "Repair, Maintenance & Technical Services",
        "Healthcare, Clinics & Diagnostics",
        "Pharmacy, Medicines & Medical Equipment",
        "Hotels, Lodging & Hospitality",
        "Automobile, Transport & Travel",
        "Construction Materials, Hardware & Industrial Goods",
        "Manufacturing, Workshops & Industrial Activities"
    )
    val scales = listOf("Micro", "Small", "Medium", "Large")
    val states = listOf(
        "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh",
        "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jharkhand",
        "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur",
        "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab", "Rajasthan",
        "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh",
        "Uttarakhand", "West Bengal",
        "Andaman & Nicobar Islands", "Chandigarh",
        "Dadra & Nagar Haveli and Daman & Diu", "Delhi (NCT)",
        "Jammu & Kashmir", "Ladakh", "Lakshadweep", "Puducherry"
    )

    LaunchedEffect(branchName) {
        if (locationSelected || branchName.length < 2 || branchName.isBlank()) {
            if (branchName.isBlank()) suggestions = emptyList()
            return@LaunchedEffect
        }
        isSearchingLocation = true
        delay(400)
        try {
            val response = olaMapsApi.autocomplete(branchName, BuildConfig.OLA_MAPS_API_KEY)
            if (response.isSuccessful && branchName.isNotBlank()) {
                suggestions = response.body()?.predictions
                    ?.filter { it.description.contains("India", ignoreCase = true) }
                    ?: emptyList()
            } else {
                suggestions = emptyList()
            }
        } catch (_: Exception) {
            suggestions = emptyList()
        }
        isSearchingLocation = false
    }

    fun normalizeStateName(s: String): String =
        s.lowercase().replace("&", " and ").replace(Regex("[^a-zA-Z\\s]"), "").replace(Regex("\\s+"), " ").trim()

    fun onSuggestionSelected(suggestion: OlaPrediction) {
        branchName = suggestion.structuredFormatting?.mainText
            ?: suggestion.description.split(",").first().trim()
        suggestions = emptyList()
        locationSelected = true

        val addressText = suggestion.structuredFormatting?.secondaryText ?: suggestion.description
        val parts = addressText.split(",").map { it.trim() }
        val filtered = parts.filter { !it.equals("India", ignoreCase = true) }
        if (filtered.size >= 2) {
            var extractedCity = ""
            var extractedState = ""
            for (i in filtered.indices.reversed()) {
                val raw = filtered[i]
                val stripped = raw.replace(Regex("[^a-zA-Z&\\s]"), "").trim()
                if (stripped.length < 3) continue
                val norm = normalizeStateName(stripped)
                val matched = states.firstOrNull { state ->
                    val ns = normalizeStateName(state)
                    ns == norm || norm.contains(ns) || ns.contains(norm) ||
                    norm.replace(" ", "") == ns.replace(" ", "")
                }
                if (matched != null) {
                    extractedState = matched
                    if (i > 0) extractedCity = filtered[i - 1]
                    break
                }
            }
            if (extractedState.isNotEmpty()) {
                city = extractedCity
                state = extractedState
            } else {
                city = filtered[filtered.size - 2]
            }
        } else if (filtered.size == 1) {
            city = filtered[0]
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
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
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (initial == null) "Add New Business" else "Edit Business",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }
        }

        // Category counter hint
        Text(
            text = "${categories.size} categories available",
            fontSize = 11.sp,
            color = colors.textSecondary.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Business Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Business / Shop Name", color = colors.textSecondary) },
                leadingIcon = { Icon(Icons.Default.Store, contentDescription = null, tint = colors.primary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                    focusedLabelColor = colors.primary, cursorColor = colors.primary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Owner Name
            OutlinedTextField(
                value = ownerName,
                onValueChange = { ownerName = it },
                label = { Text("Owner / Proprietor Name", color = colors.textSecondary) },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = colors.primary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                    focusedLabelColor = colors.primary, cursorColor = colors.primary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Category Dropdown
            ExposedDropdownMenuBox(
                expanded = showCategoryDropdown,
                onExpandedChange = { showCategoryDropdown = !showCategoryDropdown }
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Business Category", color = colors.textSecondary) },
                    leadingIcon = { Icon(Icons.Default.Category, contentDescription = null, tint = colors.primary) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryDropdown) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(expanded = showCategoryDropdown, onDismissRequest = { showCategoryDropdown = false }) {
                    categories.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item) },
                            onClick = { category = item; showCategoryDropdown = false }
                        )
                    }
                }
            }

            // Scale Dropdown
            ExposedDropdownMenuBox(
                expanded = showScaleDropdown,
                onExpandedChange = { showScaleDropdown = !showScaleDropdown }
            ) {
                OutlinedTextField(
                    value = scale,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Business Scale", color = colors.textSecondary) },
                    leadingIcon = { Icon(Icons.Default.TrendingUp, contentDescription = null, tint = colors.primary) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showScaleDropdown) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(expanded = showScaleDropdown, onDismissRequest = { showScaleDropdown = false }) {
                    scales.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item) },
                            onClick = { scale = item; showScaleDropdown = false }
                        )
                    }
                }
            }

            // Branch / Location Name (required — with Ola Maps autocomplete)
            ExposedDropdownMenuBox(
                expanded = suggestions.isNotEmpty(),
                onExpandedChange = { if (!it) suggestions = emptyList() }
            ) {
                OutlinedTextField(
                    value = branchName,
                    onValueChange = { branchName = it; locationSelected = false },
                    label = { Text("Branch / Location Name *", color = colors.textSecondary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.primary) },
                    trailingIcon = {
                        if (isSearchingLocation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colors.primary
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                        focusedLabelColor = colors.primary, cursorColor = colors.primary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    supportingText = if (branchName.isBlank()) {
                        { Text("Type to search your location", color = colors.textSecondary.copy(alpha = 0.6f), fontSize = 11.sp) }
                    } else null
                )
                ExposedDropdownMenu(
                    expanded = suggestions.isNotEmpty(),
                    onDismissRequest = { suggestions = emptyList() }
                ) {
                    suggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = suggestion.structuredFormatting?.mainText
                                            ?: suggestion.description.split(",").first().trim(),
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = suggestion.structuredFormatting?.secondaryText
                                            ?: suggestion.description.split(",").drop(1).joinToString(", ").trim(),
                                        fontSize = 12.sp,
                                        color = colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            onClick = { onSuggestionSelected(suggestion) }
                        )
                    }
                }
            }

            // State Dropdown
            ExposedDropdownMenuBox(
                expanded = showStateDropdown,
                onExpandedChange = { showStateDropdown = !showStateDropdown }
            ) {
                OutlinedTextField(
                    value = state,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("State of Operation", color = colors.textSecondary) },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = colors.primary) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showStateDropdown) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(expanded = showStateDropdown, onDismissRequest = { showStateDropdown = false }) {
                    states.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item) },
                            onClick = { state = item; showStateDropdown = false }
                        )
                    }
                }
            }

            // City
            OutlinedTextField(
                value = city,
                onValueChange = { city = it },
                label = { Text("City / Town", color = colors.textSecondary) },
                leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, tint = colors.primary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                    focusedLabelColor = colors.primary, cursorColor = colors.primary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Assign to Manager (only show if managers are available)
            if (managers.isNotEmpty() && onManagerSelected != null) {
                ExposedDropdownMenuBox(
                    expanded = showManagerDropdown,
                    onExpandedChange = { showManagerDropdown = !showManagerDropdown }
                ) {
                    OutlinedTextField(
                        value = managers.find { it.id == selectedManagerId }?.managerName ?: "Unassigned",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Assign to Manager", color = colors.textSecondary) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = colors.primary) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showManagerDropdown) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(expanded = showManagerDropdown, onDismissRequest = { showManagerDropdown = false }) {
                        DropdownMenuItem(
                            text = { Text("Unassigned", fontWeight = if (selectedManagerId == null) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { selectedManagerId = null; showManagerDropdown = false }
                        )
                        managers.forEach { mgr ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(mgr.managerName, fontWeight = if (selectedManagerId == mgr.id) FontWeight.Bold else FontWeight.Normal)
                                        Text("Code: ${mgr.code}", fontSize = 11.sp, color = colors.textSecondary)
                                    }
                                },
                                onClick = { selectedManagerId = mgr.id; showManagerDropdown = false }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save Button
            val formValid = name.isNotBlank() && ownerName.isNotBlank()
                    && category.isNotBlank() && branchName.isNotBlank()
            Button(
                onClick = {
                    if (formValid) {
                        onSave(
                            BusinessProfile(
                                id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                                name = name, ownerName = ownerName,
                                category = category, scale = scale,
                                state = state, city = city, branchName = branchName
                            )
                        )
                        // Notify parent about selected manager
                        if (onManagerSelected != null) {
                            onManagerSelected(selectedManagerId)
                        }
                    }
                },
                enabled = formValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary, contentColor = colors.background
                )
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("SAVE BUSINESS", fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.sp)
            }

            // Bottom spacing for system navigation bar
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

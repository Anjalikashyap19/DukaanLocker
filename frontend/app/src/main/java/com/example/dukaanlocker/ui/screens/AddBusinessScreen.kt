package com.example.dukaanlocker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dukaanlocker.BusinessProfile
import com.example.dukaanlocker.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBusinessScreen(
    initial: BusinessProfile? = null,
    onSave: (BusinessProfile) -> Unit,
    onCancel: () -> Unit
) {
    val colors = LocalAppColors.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var ownerName by remember { mutableStateOf(initial?.ownerName ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "") }
    var scale by remember { mutableStateOf(initial?.scale ?: "Micro") }
    var state by remember { mutableStateOf(initial?.state ?: "Maharashtra") }
    var city by remember { mutableStateOf(initial?.city ?: "") }
    var branchName by remember { mutableStateOf(initial?.branchName ?: "") }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var showScaleDropdown by remember { mutableStateOf(false) }
    var showStateDropdown by remember { mutableStateOf(false) }

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
        // States
        "Andhra Pradesh",
        "Arunachal Pradesh",
        "Assam",
        "Bihar",
        "Chhattisgarh",
        "Goa",
        "Gujarat",
        "Haryana",
        "Himachal Pradesh",
        "Jharkhand",
        "Karnataka",
        "Kerala",
        "Madhya Pradesh",
        "Maharashtra",
        "Manipur",
        "Meghalaya",
        "Mizoram",
        "Nagaland",
        "Odisha",
        "Punjab",
        "Rajasthan",
        "Sikkim",
        "Tamil Nadu",
        "Telangana",
        "Tripura",
        "Uttar Pradesh",
        "Uttarakhand",
        "West Bengal",
        // Union Territories
        "Andaman & Nicobar Islands",
        "Chandigarh",
        "Dadra & Nagar Haveli and Daman & Diu",
        "Delhi (NCT)",
        "Jammu & Kashmir",
        "Ladakh",
        "Lakshadweep",
        "Puducherry"
    )

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

            // Branch Name (conditional)
            OutlinedTextField(
                value = branchName,
                onValueChange = { branchName = it },
                label = { Text("Branch / Location Name (optional)", color = colors.textSecondary) },
                leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = colors.primary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                    focusedLabelColor = colors.primary, cursorColor = colors.primary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Save Button
            Button(
                onClick = {
                    if (name.isNotBlank() && ownerName.isNotBlank() && category.isNotBlank()) {
                        onSave(
                            BusinessProfile(
                                id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                                name = name, ownerName = ownerName,
                                category = category, scale = scale,
                                state = state, city = city, branchName = branchName
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && ownerName.isNotBlank() && category.isNotBlank(),
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
        }
    }
}

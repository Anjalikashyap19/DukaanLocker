package com.example.dukaanlocker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.dukaanlocker.BusinessProfile
import com.example.dukaanlocker.ManagerAccess
import com.example.dukaanlocker.ui.theme.*

@Composable
fun ManageManagersScreen(
    managers: List<ManagerAccess>,
    businesses: List<BusinessProfile>,
    onAddManager: (String, List<String>) -> Unit,
    onDeleteManager: (String) -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalAppColors.current
    var showAddDialog by remember { mutableStateOf(false) }

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Manage Managers", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text("${managers.size} manager(s) active", fontSize = 12.sp, color = colors.textSecondary)
                }
                // Add Manager Button
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.background),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        if (managers.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = null,
                        tint = colors.textSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No Managers Added Yet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
                    Text("Tap 'Add' to invite a manager", fontSize = 13.sp, color = colors.textSecondary.copy(alpha = 0.6f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(managers) { manager ->
                    ManagerCard(
                        manager = manager,
                        businesses = businesses,
                        onDelete = { onDeleteManager(manager.code) }
                    )
                }
            }
        }
    }

    // Add Manager Dialog
    if (showAddDialog) {
        AddManagerDialog(
            businesses = businesses,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, businessIds ->
                onAddManager(name, businessIds)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ManagerCard(
    manager: ManagerAccess,
    businesses: List<BusinessProfile>,
    onDelete: () -> Unit
) {
    val colors = LocalAppColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        border = BorderStroke(1.dp, colors.border),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                            .background(colors.secondary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(manager.managerName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = colors.primary, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Code: ${manager.code}", fontSize = 12.sp, color = colors.primary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = colors.border, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(10.dp))

            Text("Assigned Businesses:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(6.dp))

            manager.assignedBusinessIds.forEach { bizId ->
                val biz = businesses.find { it.id == bizId }
                if (biz != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Store, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${biz.name} • ${biz.category}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AddManagerDialog(
    businesses: List<BusinessProfile>,
    onDismiss: () -> Unit,
    onConfirm: (String, List<String>) -> Unit
) {
    val colors = LocalAppColors.current
    var managerName by remember { mutableStateOf("") }
    val selectedBusinessIds = remember { mutableStateListOf<String>() }
    val generatedCode = remember {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        (1..6).map { chars.random() }.joinToString("")
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colors.cardBg),
            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = colors.primary, modifier = Modifier.size(40.dp))
                Text("Add New Manager", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text("Enter manager details and assign businesses", fontSize = 13.sp, color = colors.textSecondary)

                OutlinedTextField(
                    value = managerName,
                    onValueChange = { managerName = it },
                    label = { Text("Manager Name", color = colors.textSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                        cursorColor = colors.primary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                // Generated code display
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = colors.background),
                    border = BorderStroke(1.dp, colors.border),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Access Code", fontSize = 10.sp, color = colors.textSecondary)
                            Text(generatedCode, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.primary, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                }

                // Business selection
                Text("Assign Businesses:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)

                businesses.forEach { biz ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (selectedBusinessIds.contains(biz.id)) selectedBusinessIds.remove(biz.id)
                                else selectedBusinessIds.add(biz.id)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedBusinessIds.contains(biz.id),
                            onCheckedChange = {
                                if (selectedBusinessIds.contains(biz.id)) selectedBusinessIds.remove(biz.id)
                                else selectedBusinessIds.add(biz.id)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = colors.primary, uncheckedColor = colors.textSecondary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(biz.name, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
                            Text(biz.category, fontSize = 11.sp, color = colors.textSecondary)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel", color = colors.textSecondary)
                    }
                    Button(
                        onClick = { onConfirm(managerName, selectedBusinessIds.toList()) },
                        enabled = managerName.isNotBlank() && selectedBusinessIds.isNotEmpty(),
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.background),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Create Manager", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


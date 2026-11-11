package com.iadv.dukaanlocker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.api.FssaComplianceStatus
import com.iadv.dukaanlocker.ui.theme.LocalAppColors
import com.iadv.dukaanlocker.ui.components.EmptyStateView

@Composable
fun FssaComplianceScreen(
    onBack: () -> Unit,
    complianceList: List<FssaComplianceStatus>,
    isLoading: Boolean,
    onMarkFiled: (Long) -> Unit
) {
    val colors = LocalAppColors.current

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.background,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                        IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp, end = 12.dp)) {
                    Text(
                        "FSSAI Annual Return",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        "Food Safety and Standards Authority of India",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(complianceList) { compliance ->
                val isFiled = compliance.filed
                val isOverdue = compliance.status == "overdue"

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOverdue) Color(0xFFFDECEA) else colors.cardBg
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isOverdue) Color.Red else colors.border
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${compliance.shopName}${compliance.branchName?.let { " ($it)" } ?: ""}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "Filing window: 1st Jan – 31st May",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                            Icon(
                                imageVector = if (isFiled) Icons.Filled.CheckCircle else Icons.Filled.Info,
                                contentDescription = null,
                                tint = if (isFiled) Color(0xFF4CAF50) else if (isOverdue) Color.Red else Color(0xFFFF9800),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        when {
                            isFiled -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Return filed on ${compliance.filedDate ?: "—"}",
                                        fontSize = 14.sp,
                                        color = Color(0xFF4CAF50),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            isOverdue -> {
                                Column {
                                    Text(
                                        text = "Overdue by ${compliance.overdueDays} day(s)",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Red
                                    )
                                    Text(
                                        text = "Fine: ₹${compliance.fine}/day × ${compliance.overdueDays} days = ₹${compliance.fine}",
                                        fontSize = 13.sp,
                                        color = colors.textSecondary
                                    )
                                    Text(
                                        text = "Service charge: ₹${compliance.serviceCharge}",
                                        fontSize = 13.sp,
                                        color = colors.textSecondary
                                    )
                                    Text(
                                        text = "Total payable: ₹${compliance.totalPayable}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Red
                                    )
                                }
                            }
                            else -> {
                                Text(
                                    text = "Filing is due. Please file before 31st May to avoid penalties.",
                                    fontSize = 13.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (!isFiled) {
                            Button(
                                onClick = { onMarkFiled(compliance.shopId) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isOverdue) Color.Red else colors.primary
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (isOverdue) "Mark as Filed" else "Mark as Filed",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        if (complianceList.isEmpty() && !isLoading) {
            EmptyStateView(
                title = "No FSSAI applicable shops",
                subtitle = "Shops marked as FSSAI-applicable will appear here with filing status."
            )
        }
    }
}

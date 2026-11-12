package com.iadv.dukaanlocker.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easebuzz.payment.kit.PWECheckoutActivity
import com.iadv.dukaanlocker.AppViewModel
import com.iadv.dukaanlocker.ToastType
import com.iadv.dukaanlocker.api.FssaComplianceStatus
import com.iadv.dukaanlocker.ui.components.EmptyStateView
import com.iadv.dukaanlocker.ui.theme.LocalAppColors

@Composable
fun FssaComplianceScreen(
    onBack: () -> Unit,
    complianceList: List<FssaComplianceStatus>,
    isLoading: Boolean,
    onMarkFiled: (Long) -> Unit,
    vm: AppViewModel = viewModel()
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current

    val payResult = vm.fssaReturnPaymentResult
    val paying = vm.fssaReturnPaymentLoading
    val payError = vm.fssaReturnPaymentError

    // Easebuzz SDK launcher — opens the payment page in-app.
    val payLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        val data = activityResult.data
        val sdkResult = data?.getStringExtra("result")
        val paymentId = vm.fssaReturnPaymentResult?.paymentId
        when (sdkResult) {
            "payment_successfull" -> paymentId?.let { vm.verifyFssaReturnPayment(it) }
            "payment_failed", "txn_session_timeout", "user_cancelled",
            "back_pressed", "error_server_error" -> {
                vm.showToast("Payment cancelled or failed: $sdkResult", ToastType.ERROR)
                vm.resetFssaReturnPayment()
            }
            else -> vm.resetFssaReturnPayment()
        }
    }

    // When a fresh pending payment is created, open the Easebuzz checkout.
    LaunchedEffect(payResult) {
        val pendingResult = payResult ?: return@LaunchedEffect
        if (pendingResult.status != "PENDING") return@LaunchedEffect

        val accessKey = pendingResult.accessKey
        if (!accessKey.isNullOrBlank()) {
            try {
                val intent = Intent(context, PWECheckoutActivity::class.java).apply {
                    putExtra("access_key", accessKey)
                    putExtra("pay_mode", pendingResult.payMode ?: "production")
                }
                payLauncher.launch(intent)
                return@LaunchedEffect
            } catch (_: Exception) {
                // fall through to browser checkout below
            }
        }
        pendingResult.gatewayCheckoutUrl?.let { url ->
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                vm.showToast("Could not open payment page: ${e.message}", ToastType.ERROR)
            }
        }
    }

    var showFilingForm by remember { mutableStateOf(false) }
    var filingShopId by remember { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.background,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
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

        if (isLoading && complianceList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(complianceList) { compliance ->
                    FssaComplianceCard(
                        compliance = compliance,
                        paying = paying,
                        payError = payError,
                        onFileNow = {
                            filingShopId = compliance.shopId
                            showFilingForm = true
                        },
                        onMarkFiled = { onMarkFiled(compliance.shopId) }
                    )
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

    if (showFilingForm && filingShopId != null) {
        val shopName = complianceList.firstOrNull { it.shopId == filingShopId }?.shopName
            ?: "this shop"
        FilingDetailsDialog(
            shopName = shopName,
            onDismiss = { showFilingForm = false },
            onSubmit = { fy, turnover, employees, kob, category, notes ->
                val shopId = filingShopId
                showFilingForm = false
                if (shopId != null) {
                    vm.createFssaReturnPayment(shopId, fy, turnover, employees, kob, category, notes)
                }
            }
        )
    }
}

private fun defaultFinancialYear(): String {
    val today = java.time.LocalDate.now()
    val start = if (today.monthValue >= 4) today.year - 1 else today.year - 2
    return "$start-" + ((start + 1) % 100).toString().padStart(2, '0')
}

@Composable
private fun FilingDetailsDialog(
    shopName: String,
    onDismiss: () -> Unit,
    onSubmit: (String, Double, Int?, String?, String?, String?) -> Unit
) {
    val colors = LocalAppColors.current
    var financialYear by remember { mutableStateOf(defaultFinancialYear()) }
    var turnover by remember { mutableStateOf("") }
    var employees by remember { mutableStateOf("") }
    var kindOfBusiness by remember { mutableStateOf("") }
    var foodCategory by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBg,
        title = { Text("Filing details", fontWeight = FontWeight.Bold, color = colors.textPrimary) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "We need these details to file the annual return for $shopName.",
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
                OutlinedTextField(
                    value = financialYear,
                    onValueChange = { financialYear = it },
                    label = { Text("Financial year") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = turnover,
                    onValueChange = { input ->
                        turnover = input.filter { it.isDigit() || it == '.' }
                    },
                    label = { Text("Annual turnover (₹) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = employees,
                    onValueChange = { input -> employees = input.filter { it.isDigit() } },
                    label = { Text("Number of employees") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = kindOfBusiness,
                    onValueChange = { kindOfBusiness = it },
                    label = { Text("Kind of business") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = foodCategory,
                    onValueChange = { foodCategory = it },
                    label = { Text("Food category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Anything else we should know") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error!!, color = colors.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = turnover.toDoubleOrNull()
                if (amount == null || amount < 0) {
                    error = "Please enter the annual turnover"
                    return@TextButton
                }
                onSubmit(
                    financialYear.ifBlank { defaultFinancialYear() },
                    amount,
                    employees.toIntOrNull(),
                    kindOfBusiness.ifBlank { null },
                    foodCategory.ifBlank { null },
                    notes.ifBlank { null }
                )
            }) {
                Text("Pay & File", fontWeight = FontWeight.Bold, color = colors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        }
    )
}

@Composable
private fun FssaComplianceCard(
    compliance: FssaComplianceStatus,
    paying: Boolean,
    payError: String?,
    onFileNow: () -> Unit,
    onMarkFiled: () -> Unit
) {
    val colors = LocalAppColors.current
    val isFiled = compliance.filed
    val isOverdue = compliance.status == "overdue"

    val accent = when {
        isFiled -> colors.success
        isOverdue -> colors.error
        else -> colors.warning
    }
    val container = if (isOverdue) colors.error.copy(alpha = 0.10f) else colors.cardBg
    val border = if (isOverdue) colors.error.copy(alpha = 0.35f) else colors.border

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, border),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${compliance.shopName}${compliance.branchName?.let { " ($it)" } ?: ""}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Filing window: 1 Jan – 31 May",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
                StatusPill(
                    label = when {
                        isFiled -> "Filed"
                        isOverdue -> "Overdue"
                        compliance.status == "due_soon" -> "Due soon"
                        else -> "Due"
                    },
                    color = accent
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                isFiled -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = colors.success,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Return filed on ${compliance.filedDate ?: "—"}",
                            fontSize = 14.sp,
                            color = colors.success,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                isOverdue -> {
                    Text(
                        text = "Overdue by ${compliance.overdueDays} day(s)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.error
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val perDay = if (compliance.overdueDays > 0)
                        compliance.fine.toLong() / compliance.overdueDays else 100L
                    FineRow("Late fee", "₹$perDay/day × ${compliance.overdueDays} days", "₹${compliance.fine}")
                    FineRow("Service charge", "DukaanLocker filing", "₹${compliance.serviceCharge}")

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = colors.border
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Total payable",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            "₹${compliance.totalPayable}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.error
                        )
                    }
                }

                else -> {
                    Text(
                        text = "Filing is due. File before 31 May to avoid a ₹100/day late fee.",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                }
            }

            if (!isFiled) {
                Spacer(modifier = Modifier.height(18.dp))

                if (!payError.isNullOrBlank()) {
                    Text(
                        text = payError,
                        fontSize = 12.sp,
                        color = colors.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onFileNow,
                        enabled = !paying,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.textOnPrimary
                        )
                    ) {
                        if (paying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colors.textOnPrimary
                            )
                        } else {
                            Icon(
                                Icons.Filled.Payment,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("File It Now", fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = onMarkFiled,
                        enabled = !paying,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary)
                    ) {
                        Text(
                            "Mark as Filed",
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun FineRow(label: String, detail: String, amount: String) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, color = colors.textPrimary, fontWeight = FontWeight.Medium)
            Text(detail, fontSize = 11.sp, color = colors.textSecondary)
        }
        Text(amount, fontSize = 14.sp, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
    }
}

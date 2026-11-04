package com.iadv.dukaanlocker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.*
import com.iadv.dukaanlocker.api.*
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.strings.LocalAppLanguage
import com.iadv.dukaanlocker.ui.theme.*
import java.math.BigDecimal
import java.math.RoundingMode

// ── Renewal Payment Screen (FSSAI document renewal via Easebuzz) ───────────────
@Composable
fun RenewalPaymentScreen(
    doc: DocumentItem,
    onBack: () -> Unit,
    onPaySuccess: () -> Unit,
    vm: AppViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val lang = LocalAppLanguage.current

    var durationYears by remember { mutableIntStateOf(1) }
    var kobSelection by remember { mutableStateOf<String?>(null) }
    var paymentSuccess by remember { mutableStateOf(false) }
    var paymentError by remember { mutableStateOf<String?>(null) }

    val breakdown = vm.paymentCreateResult?.breakdown
    val isLoading = vm.paymentLoading
    val error = vm.paymentError
    val result = vm.paymentCreateResult

    // (Re)create payment when duration or KOB changes
    LaunchedEffect(durationYears, kobSelection) {
        if (!isLoading && error == null && !paymentSuccess && result == null) {
            vm.createRenewalPayment(doc, durationYears)
        }
    }

    // Store the checkout URL when the backend returns a pending payment
    if (result != null && result.status == "PENDING" && result.gatewayCheckoutUrl != null) {
        // Payment URL would be opened in a WebView here
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Text("←", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            }
            Text("FSSAI Renewal Payment", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            IconButton(onClick = {}) {
                Text("💳", fontSize = 24.sp, color = colors.primary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Document info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colors.cardBg),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(doc.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text(
                    "Shop ID: ${doc.businessId}  •  Type: ${doc.type.replace("_", " ").capitalize()}",
                    fontSize = 12.sp, color = colors.textSecondary, modifier = Modifier.padding(top = 4.dp)
                )
                if (doc.expiryDate.isNotBlank()) {
                    Text(
                        "Expires: ${formatExpiryBodyDate(doc.expiryDate)}",
                        fontSize = 12.sp, color = colors.textSecondary, modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Duration selector
        Text(AppStrings.get(lang, "Select Duration"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        DropdownMenu(
            expanded = true,
            onDismissRequest = {},
            modifier = Modifier.fillMaxWidth()
        ) {
            (1..5).forEach { years ->
                DropdownMenuItem(
                    text = { Text("$years ${if (years == 1) "Year" else "Years"}", fontSize = 14.sp, color = colors.textPrimary) },
                    onClick = { durationYears = years }
                )
            }
        }
        Text(
            "$durationYears ${if (durationYears == 1) "Year" else "Years"} selected",
            fontSize = 12.sp, color = colors.textSecondary, modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // KOB selector (for State-tier pricing)
        Text("Kind of Business (KOB)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        DropdownMenu(
            expanded = true,
            onDismissRequest = {},
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf(
                "Retailer / Wholeseller / Hotels / Restaurant",
                "Manufacturing / Dairy Products",
                "Mills"
            ).forEach { kob ->
                DropdownMenuItem(
                    text = { Text(kob, fontSize = 14.sp, color = colors.textPrimary) },
                    onClick = { kobSelection = kob }
                )
            }
        }
        if (kobSelection != null) {
            Text("KOB: $kobSelection", fontSize = 12.sp, color = colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Cost breakdown
        if (breakdown != null || isLoading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Cost Breakdown (INR)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (isLoading) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (breakdown != null) {
                        moneyRow("Subtotal", breakdown.subtotal ?: 0.0, colors.textSecondary, false)
                        moneyRow("Govt Charge", breakdown.govCharge ?: 0.0, colors.textSecondary, false)
                        moneyRow("Service Charge", breakdown.serviceCharge ?: 0.0, colors.textSecondary, false)
                        moneyRow("GST (18%)", breakdown.gstAmount ?: 0.0, colors.textSecondary, false)
                        val penalty = breakdown.penalty ?: 0.0
                        if (penalty > 0) moneyRow("Penalty", penalty, colors.error, false)
                        Divider(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        moneyRow("Total", breakdown.totalAmount ?: 0.0, colors.primary, true)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Error
        if (error != null && !paymentSuccess) {
            Text(
                error,
                fontSize = 12.sp, color = colors.error,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        // Pay button
        if (!paymentSuccess && !isLoading && breakdown != null && result != null) {
            Button(
                onClick = { paymentSuccess = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("💳", fontSize = 20.sp, color = Color(0xFFFFFFFF))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Pay \u20B9${formatAmount(breakdown.totalAmount)}",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFFFFF)
                    )
                }
            }
        }

        // Success state
        if (paymentSuccess) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("✓", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = colors.success)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Payment Successful",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.success,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Your renewal payment has been received. The certificate will be uploaded to your Dukaan Locker within 24 hours.",
                    fontSize = 14.sp, color = colors.textSecondary, textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { onPaySuccess() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold, color = Color(0xFFFFFFFF))
                }
            }
        }
    }

    // ── Payment processing notice ──
    Text(
        "Redirecting to secure payment gateway...",
        fontSize = 14.sp,
        color = colors.textSecondary,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun moneyRow(label: String, amount: Double, color: Color, isTotal: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal,
            color = if (isTotal) color else Color(0.6f, 0.6f, 0.6f, 1f)
        )
        Text(
            "\u20B9${formatAmount(amount)}",
            fontSize = 13.sp,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal,
            color = color
        )
    }
}

private fun formatAmount(amount: Double?): String {
    return if (amount == null) "0.00" else BigDecimal(amount).setScale(2, RoundingMode.HALF_UP).toString()
}

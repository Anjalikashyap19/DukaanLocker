package com.iadv.dukaanlocker.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iadv.dukaanlocker.*
import com.iadv.dukaanlocker.ToastType
import com.iadv.dukaanlocker.api.*
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.strings.LocalAppLanguage
import com.iadv.dukaanlocker.ui.theme.*
import java.math.BigDecimal
import java.math.RoundingMode

// ── Renewal Payment Screen (FSSAI document renewal via Easebuzz EasyCollect link) ──
// KOB is resolved by the backend from the FSSAI API automatically — no KOB dropdown on client.
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

    val breakdown = vm.paymentCreateResult?.breakdown
    val isLoading = vm.paymentLoading
    val error = vm.paymentError
    val result = vm.paymentCreateResult
    val paymentSuccess = vm.renewalPaymentSuccess
    val kobText = vm.renewalKobText

    // Consume the Easebuzz payment return deep-link once when screen appears / result changes
    LaunchedEffect(result) {
        vm.consumePendingPaymentRoute()
    }
    LaunchedEffect(Unit) {
        vm.consumePendingPaymentRoute()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = AppStrings.get(lang, "Back"), tint = colors.textPrimary, modifier = Modifier.size(24.dp))
            }
            Text("FSSAI Renewal Payment", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            IconButton(onClick = {}) {
                Icon(Icons.Default.Payment, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
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
                if (kobText != null && kobText.isNotBlank()) {
                    Text(
                        "Kind of Business: $kobText",
                        fontSize = 12.sp, color = colors.textSecondary, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Duration selector (simple buttons — no popup placement issues)
        Text(AppStrings.get(lang, "Select Duration"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (1..5).forEach { years ->
                val selected = durationYears == years
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { durationYears = years }
                        .background(
                            if (selected) colors.primary else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            if (selected) 0.dp else 1.dp,
                            if (selected) Color.Transparent else colors.border,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(vertical = 10.dp)
                ) {
                    Text(
                        if (years == 1) "1 Year" else "$years Years",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else colors.textPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
        Text(
            "$durationYears ${if (durationYears == 1) "Year" else "Years"} selected",
            fontSize = 12.sp, color = colors.textSecondary, modifier = Modifier.padding(top = 4.dp)
        )

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

        // Create payment - shown when no payment exists yet (explicit action, no auto-create)
        if (result == null && !isLoading && !paymentSuccess) {
            Button(
                onClick = { vm.createRenewalPayment(doc, durationYears) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null, tint = Color(0xFFFFFFFF), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Create Payment",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFFFFF)
                    )
                }
            }
        }

        // Pay button - shown when a pending Easebuzz payment link exists
        if (!paymentSuccess && !isLoading && result != null
                && result.status == "PENDING"
                && result.gatewayCheckoutUrl?.isNotBlank() == true) {
            Button(
                onClick = {
                    result?.gatewayCheckoutUrl?.let { checkoutUrl ->
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                data = android.net.Uri.parse(checkoutUrl)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                        or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            vm.showToast("Could not open payment link: ${e.message}", ToastType.ERROR)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null, tint = Color(0xFFFFFFFF), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Pay \u20B9${formatAmount(result?.breakdown?.totalAmount)}",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFFFFF)
                    )
                }
            }
        }

        // Success state - shown when the payment return deep-link confirms success
        if (paymentSuccess) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.success, modifier = Modifier.size(48.dp))
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

        Spacer(modifier = Modifier.height(24.dp))
    }

    // ── Payment processing notice ──
    // Link handles payment flow; return deep-link confirms success
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

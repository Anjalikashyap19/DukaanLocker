package com.iadv.dukaanlocker.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import com.easebuzz.payment.kit.PWECheckoutActivity
import com.iadv.dukaanlocker.*
import com.iadv.dukaanlocker.ToastType
import com.iadv.dukaanlocker.api.PaymentCreateResponse
import com.iadv.dukaanlocker.api.WaterTestingPaymentRequest
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.strings.LocalAppLanguage
import com.iadv.dukaanlocker.ui.theme.*

/**
 * Water Testing Certificate payment screen.
 *
 * Shows three fixed-price testing categories:
 *   BASIC      — ₹1,999
 *   CHEMICAL   — ₹2,999
 *   MICROBIOLOGICAL — ₹3,999
 *
 * User selects a category, the app creates a payment via the backend,
 * then opens the Easebuzz SDK checkout for payment.
 */
@Composable
fun WaterTestingPaymentScreen(
    shopId: Long,
    documentId: Long?,
    onBack: () -> Unit,
    onPaySuccess: () -> Unit,
    vm: AppViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val lang = LocalAppLanguage.current

    var selectedTestType by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var paymentError by remember { mutableStateOf<String?>(null) }

    val testTypes = listOf(
        Triple("BASIC", "Basic Report", "₹1,999"),
        Triple("CHEMICAL", "Chemical Water Testing Report", "₹2,999"),
        Triple("MICROBIOLOGICAL", "Microbiological Testing Report", "₹3,999")
    )

    // Create payment when a test type is selected
    LaunchedEffect(selectedTestType) {
        if (selectedTestType != null) {
            isLoading = true
            paymentError = null
            vm.createWaterTestingPayment(
                shopId = shopId,
                documentId = documentId,
                testType = selectedTestType!!
            )
            isLoading = false
        }
    }

    // Consume the Easebuzz payment return deep-link
    LaunchedEffect(vm.paymentCreateResult) {
        vm.consumePendingPaymentRoute()
    }

    // Easebuzz SDK launcher — opens the payment page in-app via PWECheckoutActivity
    val payLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
        val data = activityResult.data
        if (data != null) {
            val sdkResult = data.getStringExtra("result")
            when (sdkResult) {
                "payment_successfull" -> {
                    // Verify with backend before showing success (never trust client-only result)
                    vm.paymentCreateResult?.paymentId?.let { vm.verifyWaterTestingPayment(it) }
                }
                "payment_failed", "txn_session_timeout", "user_cancelled", "back_pressed", "error_server_error" -> {
                    vm.showToast("Payment cancelled or failed: $sdkResult", ToastType.ERROR)
                }
                else -> {
                    vm.showToast("Unknown payment result: $sdkResult", ToastType.ERROR)
                }
            }
        } else {
            vm.showToast("Payment activity returned no result", ToastType.ERROR)
        }
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
            Text("Water Testing Payment", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            IconButton(onClick = {}) {
                Icon(Icons.Default.Payment, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Info text
        Text(
            text = "Select your water testing package",
            fontSize = 14.sp,
            color = colors.textSecondary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Test type selection cards
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            testTypes.forEach { testType ->
                val type = testType.first
                val label = testType.second
                val price = testType.third
                val isSelected = selectedTestType == type
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isLoading) {
                            selectedTestType = type
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) colors.primary.copy(alpha = 0.1f) else colors.cardBg
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Water testing certificate - ${type.lowercase()}",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = price,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) colors.primary else colors.textPrimary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Error
        val currentError = paymentError
        if (currentError != null && !vm.waterTestingPaymentSuccess) {
            Text(
                text = currentError,
                fontSize = 12.sp,
                color = colors.error,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        // Pay button - shown when a payment result exists and status is PENDING
        val result = vm.paymentCreateResult
        if (!vm.waterTestingPaymentSuccess && !isLoading && result != null
                && result.status == "PENDING"
                && result.accessKey != null && result.accessKey.isNotBlank()) {
            Button(
                onClick = {
                    val accessKey = result.accessKey!!
                    val payMode = result.payMode
                        ?: if (result.gatewayCheckoutUrl?.contains("testpay") == true) "test" else "production"
                    val intent = Intent(context, PWECheckoutActivity::class.java)
                    intent.putExtra("access_key", accessKey)
                    intent.putExtra("pay_mode", payMode)
                    payLauncher.launch(intent)
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
                        text = "Pay ${result.breakdown?.totalAmount?.let { "\u20B9${formatAmount(it)}" } ?: "\u20B90.00"}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFFFFF)
                    )
                }
            }
        }

        // Success state
        if (vm.waterTestingPaymentSuccess) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.success, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Payment Successful",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.success,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your water testing payment has been received. The certificate will be processed shortly.",
                    fontSize = 14.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
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
}

private fun formatAmount(amount: Double?): String {
    return if (amount == null) "0.00" else java.math.BigDecimal(amount).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
}

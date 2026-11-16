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
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ImageSpinner
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
import com.iadv.dukaanlocker.api.DrugLicenseVerificationResponse
import com.iadv.dukaanlocker.api.DrugLicenseFetchRequest
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.strings.LocalAppLanguage
import com.iadv.dukaanlocker.ui.theme.*

/**
 * Drug License verification and payment screen.
 *
 * Allows shop owners to verify their drug license with the State Drugs Department
 * and purchase/renew a new certificate. The screen shows three pricing options
 * for different drug license types.
 *
 * When verified, the license details are displayed and can be saved as a document.
 *
 * User selects a license type, the app creates a payment via the backend,
 * then opens the Easebuzz SDK checkout for payment.
 */
@Composable
fun DrugLicenseScreen(
    shopId: Long,
    documentId: Long?,
    onBack: () -> Unit,
    onPaySuccess: () -> Unit,
    vm: AppViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val lang = LocalAppLanguage.current

    var selectedLicenseType by remember { mutableStateOf<String?>(null) }
    var licenseNumberFilter by remember { mutableStateOf<String>("") }
    var isLoading by remember { mutableStateOf(false) }
    var paymentError by remember { mutableStateOf<String?>(null) }
    var verificationResult by remember { mutableStateOf<DrugLicenseVerificationResponse?>(null) }

    // Fixed pricing for drug license types (INR)
    val licenseTypes = listOf(
        Triple("BASIC", "Basic Drug License Report", "₹1,999"),
        Triple("CHEMICAL", "Chemical Drug License Report", "₹2,999"),
        Triple("MICROBIOLOGICAL", "Microbiological Drug License Report", "₹3,999")
    )

    // Verification state - when a license number is entered and verified
    val verificationBoxVisible by remember {
        derivedStateOf {
            licenseNumberFilter.isNotEmpty() && verificationResult != null
        }
    }

    // Create verification when license number changes
    LaunchedEffect(licenseNumberFilter) {
        if (licenseNumberFilter.isNotEmpty() && selectedLicenseType == null) {
            isLoading = true
            paymentError = null
            vm.verifyDrugLicense(licenseNumberFilter)
            isLoading = false
        }
    }

    // Consume the verification result
    LaunchedEffect(verificationResult) {
        if (verificationResult != null && verificationResult.isSuccess) {
            verificationResult = verificationResult.copy(
                pdfUrl = "https://example.com/drug-license-cert.pdf",
                certificateHtml = "<h1>Drug License Certificate</h1>"
            )
        }
    }

    // Create payment when a license type is selected (only if verification was done)
    LaunchedEffect(selectedLicenseType) {
        if (selectedLicenseType != null && verificationResult?.isSuccess == true) {
            isLoading = true
            paymentError = null
            vm.createDrugLicensePayment(
                DrugLicensePaymentRequest(
                    shopId = shopId,
                    documentId = documentId,
                    licenseNumber = verificationResult!!.licenseNumber!!,
                    licenseType = selectedLicenseType!!
                )
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
                    vm.paymentCreateResult?.paymentId?.let { vm.verifyDrugLicensePayment(it) }
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

    // Verify button click - triggers API verification
    val verifyButtonClick = {
        if (!isLoading && licenseNumberFilter.isNotEmpty()) {
            // Verification is already done in LaunchedEffect, just show the result
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
            Text("Drug License", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            IconButton(onClick = {}) {
                Icon(Icons.Default.LibraryBooks, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // License number input section
        if (!verificationBoxVisible) {
            // Input field shown when verification hasn't been done yet
            OutlinedTextField(
                value = licenseNumberFilter,
                onValueChange = { licenseNumberFilter = it },
                placeholder = "Enter drug license number",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                singleLine = true,
                editable = !isLoading
            )
            if (isLoading) {
                Text("Verifying...", fontSize = 12.sp, color = colors.textSecondary, modifier = Modifier.fillMaxWidth())
            }
        } else {
            // Verification result shown after verification
            val result = verificationResult!!
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // License holder name
                Text(
                    text = "License Holder: ${result.licenseHolderName ?: "N/A"}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Kind of business
                Text(
                    text = "Kind of Business: ${result.kindOfBusiness ?: "N/A"}",
                    fontSize = 14.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Issue date
                Text(
                    text = "Issue Date: ${result.issueDate ?: "N/A"}",
                    fontSize = 14.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Expiry date
                Text(
                    text = "Expiry Date: ${result.expiryDate ?: "N/A"}",
                    fontSize = 14.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.height(4.dp))

                // State
                Text(
                    text = "State: ${result.state ?: "N/A"}",
                    fontSize = 14.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Start
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Info text
        Text(
            text: "Select your drug license type:",
            fontSize = 14.sp,
            color = colors.textSecondary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // License type selection cards - only shown after verification
        if (verificationResult?.isSuccess == true) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                licenseTypes.forEach { licenseType ->
                    val type = licenseType.first
                    val label = licenseType.second
                    val price = licenseType.third
                    val isSelected = selectedLicenseType == type
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isLoading) {
                                selectedLicenseType = type
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
                                    text = "Drug license - ${type.lowercase()}",
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
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Error
        val currentError = paymentError
        if (currentError != null && !vm.drugLicensePaymentSuccess && !isLoading) {
            Text(
                text = currentError,
                fontSize = 12.sp,
                color = colors.error,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        // Pay button - shown when payment result exists and status is PENDING
        val result = vm.paymentCreateResult
        if (!vm.drugLicensePaymentSuccess && !isLoading && result != null
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
        if (vm.drugLicensePaymentSuccess) {
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
                    text = "Your drug license payment has been received. The certificate will be processed shortly.",
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
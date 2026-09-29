package com.iadv.dukaanlocker.ui.screens

import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.iadv.dukaanlocker.*
import com.iadv.dukaanlocker.api.FssaiVerificationResponse
import com.iadv.dukaanlocker.api.GstVerificationResponse
import com.iadv.dukaanlocker.api.UdyamVerifyResponse
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.strings.LocalAppLanguage
import com.iadv.dukaanlocker.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/** User-facing fetch budget: the dialog shows a 1-minute countdown. */
private const val FETCH_COUNTDOWN_SECONDS = 60

@Composable
fun FetchDocumentDialog(
    doc: DocumentItem,
    shopName: String,
    onDismiss: () -> Unit,
    onSuccess: (regNum: String, issue: String, expiry: String) -> Unit,
    onFetchGst: ((shopId: String, gstin: String, (Boolean, GstVerificationResponse?) -> Unit) -> Unit)? = null,
    onInitMsmeCaptcha: (((String, String) -> Unit) -> Unit)? = null,
    onFetchMsme: ((shopId: String, udyamNumber: String, sessionId: String, captchaText: String, (Boolean, UdyamVerifyResponse?) -> Unit) -> Unit)? = null,
    onFetchFssai: ((shopId: String, licenseNumber: String, (Boolean, FssaiVerificationResponse?) -> Unit) -> Unit)? = null
) {
    val colors = LocalAppColors.current
    val lang = LocalAppLanguage.current
    var regInput by remember { mutableStateOf("") }
    var isFetching by remember { mutableStateOf(false) }
    var fetchProgress by remember { mutableStateOf(0f) }
    var currentStepText by remember { mutableStateOf(AppStrings.get(lang, "Connecting to National Database...")) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var gstResponse by remember { mutableStateOf<GstVerificationResponse?>(null) }
    var msmeResponse by remember { mutableStateOf<UdyamVerifyResponse?>(null) }
    var fssaiResponse by remember { mutableStateOf<FssaiVerificationResponse?>(null) }

    // 1-minute countdown state
    var remainingSeconds by remember { mutableStateOf(FETCH_COUNTDOWN_SECONDS) }
    var timedOut by remember { mutableStateOf(false) }

    // MSME captcha state
    val isMsme = doc.type == "MSME_CERTIFICATE"
    var msmeCaptchaImage by remember { mutableStateOf<String?>(null) }
    var msmeSessionId by remember { mutableStateOf("") }
    var captchaInput by remember { mutableStateOf("") }
    var isLoadingCaptcha by remember { mutableStateOf(false) }
    var captchaLoaded by remember { mutableStateOf(false) }

    // Hold pending results from callbacks
    var pendingGstResult by remember { mutableStateOf<Pair<Boolean, GstVerificationResponse?>?>(null) }
    var pendingMsmeResult by remember { mutableStateOf<Pair<Boolean, UdyamVerifyResponse?>?>(null) }
    var pendingFssaiResult by remember { mutableStateOf<Pair<Boolean, FssaiVerificationResponse?>?>(null) }

    val labelText = docFetchLabel(doc.type)
    val isGst = doc.type == "GST"
    val isFssai = doc.type == "FSSAI_FOOD_LICENSE"

    // Init MSME captcha when dialog opens for MSME
    LaunchedEffect(isMsme) {
        if (isMsme && onInitMsmeCaptcha != null && !captchaLoaded && !isLoadingCaptcha) {
            isLoadingCaptcha = true
            onInitMsmeCaptcha { sessionId, captchaBase64 ->
                msmeSessionId = sessionId
                msmeCaptchaImage = captchaBase64
                isLoadingCaptcha = false
                captchaLoaded = true
            }
        }
    }

    // Handle GST pending result
    LaunchedEffect(pendingGstResult) {
        pendingGstResult?.let { (success, response) ->
            pendingGstResult = null
            if (timedOut) return@let
            isFetching = false
            if (success && response != null) {
                gstResponse = response
                val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val today = Date()
                val expiryCal = Calendar.getInstance().apply { add(Calendar.YEAR, 5) }
                onSuccess(
                    response.gstin ?: regInput.uppercase(),
                    response.registrationDate ?: formatter.format(today),
                    formatter.format(expiryCal.time)
                )
            } else {
                fetchError = response?.errorMessage ?: "Verification failed. Please check the GSTIN and try again."
            }
        }
    }

    // Handle MSME pending result
    LaunchedEffect(pendingMsmeResult) {
        pendingMsmeResult?.let { (success, response) ->
            pendingMsmeResult = null
            if (timedOut) return@let
            isFetching = false
            if (success && response != null) {
                msmeResponse = response
                val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val today = Date()
                val expiryCal = Calendar.getInstance().apply { add(Calendar.YEAR, 5) }
                onSuccess(
                    regInput.uppercase(),
                    response.udyamNumber ?: formatter.format(today),
                    formatter.format(expiryCal.time)
                )
            } else {
                fetchError = response?.errorMessage ?: "Verification failed. Please check the Udyam number and captcha."
            }
        }
    }

    // Handle FSSAI pending result
    LaunchedEffect(pendingFssaiResult) {
        pendingFssaiResult?.let { (success, response) ->
            pendingFssaiResult = null
            if (timedOut) return@let
            isFetching = false
            if (success && response != null) {
                fssaiResponse = response
                onSuccess(
                    response.licenseNumber ?: regInput.trim(),
                    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
                    formatFssaiExpiry(response.expiryDate)
                )
            } else {
                fetchError = response?.errorMessage ?: "Verification failed. Please check the FSSAI license number and try again."
            }
        }
    }

    // 1-minute countdown — expires while still fetching -> "Unable to fetch. Please retry."
    LaunchedEffect(isFetching) {
        if (isFetching) {
            timedOut = false
            remainingSeconds = FETCH_COUNTDOWN_SECONDS
            while (remainingSeconds > 0) {
                delay(1000)
                remainingSeconds--
            }
            if (isFetching) {
                timedOut = true
                isFetching = false
                fetchError = "Unable to fetch. Please retry."
            }
        }
    }

    LaunchedEffect(isFetching) {
        if (isFetching) {
            fetchError = null
            gstResponse = null
            msmeResponse = null
            fssaiResponse = null
            timedOut = false

            if (isGst && onFetchGst != null) {
                val steps = listOf(
                    0.2f to AppStrings.get(lang, "Connecting to GSTN Portal Gateway..."),
                    0.5f to AppStrings.get(lang, "Verifying GSTIN against government database..."),
                    0.8f to AppStrings.get(lang, "Fetching taxpayer details..."),
                    1.0f to AppStrings.get(lang, "Encrypting and locking in Dukaan Vault...")
                )
                for ((progress, text) in steps) {
                    delay(600)
                    fetchProgress = progress
                    currentStepText = text
                }
                onFetchGst(doc.businessId, regInput.trim().uppercase()) { success, response ->
                    pendingGstResult = success to response
                }
            } else if (isFssai && onFetchFssai != null) {
                val steps = listOf(
                    0.2f to AppStrings.get(lang, "Connecting to FSSAI Portal Gateway..."),
                    0.5f to AppStrings.get(lang, "Verifying license number against government database..."),
                    0.8f to AppStrings.get(lang, "Fetching food license details..."),
                    1.0f to AppStrings.get(lang, "Encrypting and locking in Dukaan Vault...")
                )
                for ((progress, text) in steps) {
                    delay(600)
                    fetchProgress = progress
                    currentStepText = text
                }
                onFetchFssai(doc.businessId, regInput.trim()) { success, response ->
                    pendingFssaiResult = success to response
                }
            } else if (isMsme && onFetchMsme != null && msmeSessionId.isNotBlank() && captchaInput.isNotBlank()) {
                val steps = listOf(
                    0.2f to AppStrings.get(lang, "Connecting to Udyam Portal Gateway..."),
                    0.5f to AppStrings.get(lang, "Verifying Udyam number against government database..."),
                    0.8f to AppStrings.get(lang, "Fetching official e-Certificate..."),
                    1.0f to AppStrings.get(lang, "Encrypting and locking in Dukaan Vault...")
                )
                for ((progress, text) in steps) {
                    delay(600)
                    fetchProgress = progress
                    currentStepText = text
                }
                onFetchMsme(doc.businessId, regInput.trim().uppercase(), msmeSessionId, captchaInput.trim()) { success, response ->
                    pendingMsmeResult = success to response
                }
            }
        }
    }

    Dialog(onDismissRequest = { if (!isFetching) onDismiss() }) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBg),
            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!isFetching) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = colors.primary, modifier = Modifier.size(48.dp))
                    Text(AppStrings.get(lang, "Auto-Fetch Official Doc"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text("${AppStrings.get(lang, "Securely fetch your")} ${doc.name} ${AppStrings.get(lang, "from government databases.")}", fontSize = 12.sp, color = colors.textSecondary, textAlign = TextAlign.Center)

                    if (fetchError != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colors.error.copy(alpha = 0.1f)),
                            border = BorderStroke(1.dp, colors.error.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                fetchError!!,
                                modifier = Modifier.padding(12.dp),
                                fontSize = 12.sp,
                                color = colors.error,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // GST verified details
                    if (gstResponse != null && gstResponse!!.success) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colors.accent.copy(alpha = 0.1f)),
                            border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("GSTIN Verified!", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                                Spacer(modifier = Modifier.height(4.dp))
                                gstResponse!!.legalName?.let { Text("Legal Name: $it", fontSize = 11.sp, color = colors.textSecondary) }
                                gstResponse!!.tradeName?.let { Text("Trade Name: $it", fontSize = 11.sp, color = colors.textSecondary) }
                                gstResponse!!.status?.let { Text("Status: $it", fontSize = 11.sp, color = colors.textSecondary) }
                            }
                        }
                    }

                    // MSME verified details
                    if (msmeResponse != null && msmeResponse!!.success) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colors.accent.copy(alpha = 0.1f)),
                            border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Udyam Verified!", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                                Spacer(modifier = Modifier.height(4.dp))
                                msmeResponse!!.udyamNumber?.let { Text("Udyam No: $it", fontSize = 11.sp, color = colors.textSecondary) }
                            }
                        }
                    }

                    // FSSAI verified details
                    if (fssaiResponse != null && fssaiResponse!!.success) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colors.accent.copy(alpha = 0.1f)),
                            border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("FSSAI License Verified!", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                                Spacer(modifier = Modifier.height(4.dp))
                                fssaiResponse!!.companyName?.let { Text("FBO Name: $it", fontSize = 11.sp, color = colors.textSecondary) }
                                fssaiResponse!!.licenseNumber?.let { Text("License No: $it", fontSize = 11.sp, color = colors.textSecondary) }
                                fssaiResponse!!.status?.let { Text("Status: $it", fontSize = 11.sp, color = colors.textSecondary) }
                                fssaiResponse!!.expiryDate?.let { Text("Valid Till: $it", fontSize = 11.sp, color = colors.textSecondary) }
                            }
                        }
                    }

                    // Number input
                    OutlinedTextField(
                        value = regInput,
                        onValueChange = { regInput = it },
                        label = { Text(if (isMsme) "Udyam Reg. No. (UDYAM-XX-XX-XXXXXXX)" else labelText) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // MSME captcha section
                    if (isMsme) {
                        if (isLoadingCaptcha) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp), color = colors.primary, strokeWidth = 3.dp)
                            Text("Loading captcha...", fontSize = 11.sp, color = colors.textSecondary)
                        } else if (msmeCaptchaImage != null) {
                            // Captcha image
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = colors.background),
                                border = BorderStroke(1.dp, colors.border),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Enter the captcha below", fontSize = 11.sp, color = colors.textSecondary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    CaptchaImage(base64 = msmeCaptchaImage!!)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = captchaInput,
                                        onValueChange = { captchaInput = it },
                                        label = { Text("Captcha") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                            // Refresh captcha button
                            TextButton(onClick = {
                                captchaLoaded = false
                                isLoadingCaptcha = true
                                captchaInput = ""
                                onInitMsmeCaptcha?.invoke { sessionId, captchaBase64 ->
                                    msmeSessionId = sessionId
                                    msmeCaptchaImage = captchaBase64
                                    isLoadingCaptcha = false
                                    captchaLoaded = true
                                }
                            }) {
                                Text("Refresh Captcha", fontSize = 11.sp, color = colors.primary)
                            }
                        }
                    }

                    // Buttons
                    val canSubmit = if (isMsme) {
                        regInput.isNotBlank() && captchaInput.isNotBlank() && msmeSessionId.isNotBlank()
                    } else {
                        regInput.isNotBlank()
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(AppStrings.get(lang, "Cancel"), color = colors.textPrimary) }
                        Button(
                            onClick = { if (canSubmit) isFetching = true },
                            enabled = canSubmit,
                            modifier = Modifier.weight(1.5f),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.background)
                        ) { Text(AppStrings.get(lang, "Confirm Fetch"), fontWeight = FontWeight.Bold) }
                    }
                } else {
                    CircularProgressIndicator(progress = { fetchProgress }, modifier = Modifier.size(64.dp), color = colors.primary, trackColor = colors.border, strokeWidth = 6.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(AppStrings.get(lang, "VERIFYING CREDENTIALS"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.accent, letterSpacing = 1.sp)
                    Text(currentStepText, fontSize = 12.sp, color = colors.textSecondary, textAlign = TextAlign.Center)
                    Text(
                        "Time remaining: ${remainingSeconds}s",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (remainingSeconds <= 10) colors.error else colors.textPrimary
                    )
                }
            }
        }
    }
}

/** Converts the upstream FSSAI expiry date (dd-MM-yyyy) to the app's dd/MM/yyyy form. */
private fun formatFssaiExpiry(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val parts = raw.trim().split("-")
    return if (parts.size == 3) "${parts[0]}/${parts[1]}/${parts[2]}" else raw
}

@Composable
private fun CaptchaImage(base64: String) {
    val colors = LocalAppColors.current
    val bitmap = remember(base64) {
        try {
            val cleaned = if (base64.contains(",")) base64.substringAfter(",") else base64
            val bytes = Base64.decode(cleaned, Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Captcha",
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            contentScale = ContentScale.Fit
        )
    } else {
        Text("Failed to load captcha", fontSize = 11.sp, color = colors.error)
    }
}

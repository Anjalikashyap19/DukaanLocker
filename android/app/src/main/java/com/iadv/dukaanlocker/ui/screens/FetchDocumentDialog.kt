package com.iadv.dukaanlocker.ui.screens

import android.content.Context
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.iadv.dukaanlocker.ui.components.ToastOverlay
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/** User-facing fetch budget: the dialog shows a 1-minute countdown. */
private const val FETCH_COUNTDOWN_SECONDS = 60

/** Max fetch attempts before the 5-minute lockout kicks in. */
private const val FETCH_MAX_ATTEMPTS = 5

/** Lockout length once FETCH_MAX_ATTEMPTS failed attempts are used up. */
private const val FETCH_LOCKOUT_MS = 5 * 60 * 1000L

private const val FETCH_RATE_PREFS = "fetch_rate_limit"
private const val FETCH_RATE_KEY_ATTEMPTS = "failed_attempts"
private const val FETCH_RATE_KEY_LOCKOUT = "lockout_until"

/**
 * Client-side fetch throttle: 5 failed attempts start a 5-minute lockout during
 * which the Confirm button stays disabled. Persisted so reopening the dialog
 * (or restarting the app) does not reset the budget.
 */
private object FetchRateLimiter {
    private fun prefs(context: Context) =
        context.getSharedPreferences(FETCH_RATE_PREFS, Context.MODE_PRIVATE)

    fun lockoutRemainingMs(context: Context): Long {
        val remaining = prefs(context).getLong(FETCH_RATE_KEY_LOCKOUT, 0L) - System.currentTimeMillis()
        return remaining.coerceAtLeast(0L)
    }

    /** Registers a failed attempt; returns the remaining lockout (0 when not locked). */
    fun recordFailure(context: Context): Long {
        val p = prefs(context)
        val active = p.getLong(FETCH_RATE_KEY_LOCKOUT, 0L) - System.currentTimeMillis()
        if (active > 0) return active

        val next = p.getInt(FETCH_RATE_KEY_ATTEMPTS, 0) + 1
        if (next >= FETCH_MAX_ATTEMPTS) {
            p.edit()
                .putInt(FETCH_RATE_KEY_ATTEMPTS, 0)
                .putLong(FETCH_RATE_KEY_LOCKOUT, System.currentTimeMillis() + FETCH_LOCKOUT_MS)
                .apply()
            return FETCH_LOCKOUT_MS
        }
        p.edit().putInt(FETCH_RATE_KEY_ATTEMPTS, next).apply()
        return 0L
    }

    /** Clears the failed-attempt budget after a successful fetch. */
    fun recordSuccess(context: Context) {
        prefs(context).edit().putInt(FETCH_RATE_KEY_ATTEMPTS, 0).apply()
    }
}

/** Renders a lockout duration as m:ss. */
private fun formatLockout(ms: Long): String {
    val totalSeconds = ((ms + 999) / 1000).toInt()
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/** Document types that have a working auto-fetch flow. Anything else shows a "Coming Soon" popup. */
val SUPPORTED_FETCH_TYPES = setOf("GST", "MSME_CERTIFICATE", "FSSAI_FOOD_LICENSE")

@Composable
fun FetchDocumentDialog(
    doc: DocumentItem,
    shopName: String,
    toasts: List<ToastMessage> = emptyList(),
    onDismissToast: (Long) -> Unit = {},
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
    var fetchError by remember { mutableStateOf<String?>(null) }
    var gstResponse by remember { mutableStateOf<GstVerificationResponse?>(null) }
    var msmeResponse by remember { mutableStateOf<UdyamVerifyResponse?>(null) }
    var fssaiResponse by remember { mutableStateOf<FssaiVerificationResponse?>(null) }

    // 1-minute countdown state
    var remainingSeconds by remember { mutableStateOf(FETCH_COUNTDOWN_SECONDS) }
    var timedOut by remember { mutableStateOf(false) }

    // ── Fetch rate limit: 5 failed attempts start a 5-minute lockout ──
    val context = LocalContext.current
    var lockoutRemainingMs by remember { mutableStateOf(FetchRateLimiter.lockoutRemainingMs(context)) }
    val isLockedOut = lockoutRemainingMs > 0

    fun onFetchFailure() {
        lockoutRemainingMs = FetchRateLimiter.recordFailure(context)
    }

    fun onFetchSuccess() {
        FetchRateLimiter.recordSuccess(context)
    }

    // Ticks the lockout timer once a second until it expires.
    LaunchedEffect(isLockedOut) {
        if (!isLockedOut) return@LaunchedEffect
        while (true) {
            delay(1000)
            val remaining = FetchRateLimiter.lockoutRemainingMs(context)
            lockoutRemainingMs = remaining
            if (remaining <= 0) break
        }
    }

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

    // Ordered progress ticks used to animate the ring while the fetch is running.
    val fetchTicks: List<Float> = listOf(0.2f, 0.5f, 0.8f, 1.0f)

    // Init MSME captcha when dialog opens for MSME
    LaunchedEffect(isMsme) {
        if (isMsme && onInitMsmeCaptcha != null && !captchaLoaded && !isLoadingCaptcha) {
            isLoadingCaptcha = true
            onInitMsmeCaptcha { sessionId, captchaBase64 ->
                // Only mark as loaded when a real image came back — otherwise the
                // empty-string failure path would render "Failed to load captcha"
                // with no way to retry.
                val loaded = captchaBase64.isNotBlank()
                msmeSessionId = if (loaded) sessionId else ""
                msmeCaptchaImage = if (loaded) captchaBase64 else null
                isLoadingCaptcha = false
                captchaLoaded = loaded
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
                onFetchSuccess()
            } else {
                fetchError = response?.errorMessage ?: "Verification failed. Please check the GSTIN and try again."
                onFetchFailure()
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
                onFetchSuccess()
            } else {
                fetchError = response?.errorMessage ?: "Verification failed. Please check the Udyam number and captcha."
                onFetchFailure()
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
                onFetchSuccess()
            } else {
                fetchError = response?.errorMessage ?: "Verification failed. Please check the FSSAI license number and try again."
                onFetchFailure()
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
                onFetchFailure()
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

            val runTicks: suspend () -> Unit = {
                fetchTicks.forEach { progress ->
                    delay(600)
                    fetchProgress = progress
                }
            }

            if (isGst && onFetchGst != null) {
                runTicks()
                onFetchGst(doc.businessId, regInput.trim().uppercase()) { success, response ->
                    pendingGstResult = success to response
                }
            } else if (isFssai && onFetchFssai != null) {
                runTicks()
                onFetchFssai(doc.businessId, regInput.trim()) { success, response ->
                    pendingFssaiResult = success to response
                }
            } else if (isMsme && onFetchMsme != null && msmeSessionId.isNotBlank() && captchaInput.isNotBlank()) {
                runTicks()
                onFetchMsme(doc.businessId, regInput.trim().uppercase(), msmeSessionId, captchaInput.trim()) { success, response ->
                    pendingMsmeResult = success to response
                }
            }
        }
    }

    Dialog(onDismissRequest = { if (!isFetching) onDismiss() }) {
        Card(
            modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBg),
            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Toasts: the dialog is its own window, so the main-screen overlay is
                //    hidden behind it — render the same queue inside the dialog as well.
                if (toasts.isNotEmpty()) {
                    ToastOverlay(toasts = toasts, onDismiss = onDismissToast)
                }

                if (!isFetching) {
                    // ── Header ──
                    Box(
                        modifier = Modifier.size(64.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = colors.primary, modifier = Modifier.size(30.dp))
                    }
                    Text(AppStrings.get(lang, "Auto-Fetch Official Doc"), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text(
                        "${AppStrings.get(lang, "Securely fetch your")} ${doc.name} ${AppStrings.get(lang, "from government databases.")}",
                        fontSize = 12.5.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    // ── Business this document will be locked to ──
                    if (shopName.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(colors.primary.copy(alpha = 0.08f))
                                .border(1.dp, colors.primary.copy(alpha = 0.25f), RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Store, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
                            Text(
                                shopName,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (fetchError != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colors.error.copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, colors.error.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = colors.error, modifier = Modifier.size(18.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(AppStrings.get(lang, "Fetch Failed"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.error)
                                    Text(fetchError!!, fontSize = 12.sp, color = colors.textPrimary, lineHeight = 17.sp)
                                }
                            }
                        }
                    }

                    // GST verified details
                    if (gstResponse != null && gstResponse!!.success) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colors.success.copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, colors.success.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = colors.success, modifier = Modifier.size(18.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("GSTIN Verified!", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.success)
                                    gstResponse!!.legalName?.let { Text("Legal Name: $it", fontSize = 11.sp, color = colors.textSecondary) }
                                    gstResponse!!.tradeName?.let { Text("Trade Name: $it", fontSize = 11.sp, color = colors.textSecondary) }
                                    gstResponse!!.status?.let { Text("Status: $it", fontSize = 11.sp, color = colors.textSecondary) }
                                }
                            }
                        }
                    }

                    // MSME verified details
                    if (msmeResponse != null && msmeResponse!!.success) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colors.success.copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, colors.success.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = colors.success, modifier = Modifier.size(18.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("Udyam Verified!", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.success)
                                    msmeResponse!!.udyamNumber?.let { Text("Udyam No: $it", fontSize = 11.sp, color = colors.textSecondary) }
                                }
                            }
                        }
                    }

                    // FSSAI verified details
                    if (fssaiResponse != null && fssaiResponse!!.success) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colors.success.copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, colors.success.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = colors.success, modifier = Modifier.size(18.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("FSSAI License Verified!", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.success)
                                    fssaiResponse!!.companyName?.let { Text("FBO Name: $it", fontSize = 11.sp, color = colors.textSecondary) }
                                    fssaiResponse!!.licenseNumber?.let { Text("License No: $it", fontSize = 11.sp, color = colors.textSecondary) }
                                    fssaiResponse!!.status?.let { Text("Status: $it", fontSize = 11.sp, color = colors.textSecondary) }
                                    fssaiResponse!!.expiryDate?.let { Text("Valid Till: $it", fontSize = 11.sp, color = colors.textSecondary) }
                                }
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.border,
                            focusedLabelColor = colors.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
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
                                    val loaded = captchaBase64.isNotBlank()
                                    msmeSessionId = if (loaded) sessionId else ""
                                    msmeCaptchaImage = if (loaded) captchaBase64 else null
                                    isLoadingCaptcha = false
                                    captchaLoaded = loaded
                                }
                            }) {
                                Text("Refresh Captcha", fontSize = 11.sp, color = colors.primary)
                            }
                        } else {
                            // Init failed — offer a retry instead of silently leaving
                            // the section blank with no way forward.
                            Text(
                                "Couldn't load the CAPTCHA from the Udyam portal.",
                                fontSize = 11.sp,
                                color = colors.error
                            )
                            TextButton(onClick = {
                                isLoadingCaptcha = true
                                onInitMsmeCaptcha?.invoke { sessionId, captchaBase64 ->
                                    val loaded = captchaBase64.isNotBlank()
                                    msmeSessionId = if (loaded) sessionId else ""
                                    msmeCaptchaImage = if (loaded) captchaBase64 else null
                                    isLoadingCaptcha = false
                                    captchaLoaded = loaded
                                }
                            }) {
                                Text("Retry CAPTCHA", fontSize = 11.sp, color = colors.primary)
                            }
                        }
                    }

                    // Buttons
                    val canSubmit = if (isMsme) {
                        regInput.isNotBlank() && captchaInput.isNotBlank() && msmeSessionId.isNotBlank()
                    } else {
                        regInput.isNotBlank()
                    } && !isLockedOut

                    // ── Rate-limit lockout notice ──
                    if (isLockedOut) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colors.error.copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, colors.error.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, tint = colors.error, modifier = Modifier.size(18.dp))
                                Column {
                                    Text(
                                        "Too many failed attempts.",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.error
                                    )
                                    Text(
                                        "Try again in ${formatLockout(lockoutRemainingMs)}",
                                        fontSize = 12.sp,
                                        color = colors.textPrimary
                                    )
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            border = BorderStroke(1.dp, colors.border)
                        ) {
                            Text(
                                AppStrings.get(lang, "Cancel"),
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Button(
                            onClick = { if (canSubmit) isFetching = true },
                            enabled = canSubmit,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.background)
                        ) {
                            Text(
                                AppStrings.get(lang, "Confirm "),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                } else {
                    // ── Fetching state: everything explicitly centered in the popup ──
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(colors.primary.copy(alpha = 0.07f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { fetchProgress },
                                modifier = Modifier.size(92.dp),
                                color = colors.primary,
                                trackColor = colors.primary.copy(alpha = 0.18f),
                                strokeWidth = 7.dp
                            )
                            Text(
                                "${(fetchProgress * 100).toInt()}%",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }

                        Text(
                            AppStrings.get(lang, "Processing fetch"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            textAlign = TextAlign.Center
                        )

                        // ── Countdown chip ──
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(colors.background.copy(alpha = 0.7f))
                                .border(1.dp, colors.border, RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = if (remainingSeconds <= 10) colors.error else colors.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "Time remaining: ${remainingSeconds}s",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (remainingSeconds <= 10) colors.error else colors.textPrimary
                            )
                        }
                    }
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

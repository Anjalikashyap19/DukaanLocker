package com.iadv.dukaanlocker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.api.RateLimitInfo
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.theme.AppColors
import kotlinx.coroutines.delay

/**
 * Three-step self-service password reset for owner accounts: registered mobile
 * number -> OTP -> new password.
 *
 * The server answers the send step identically for known and unknown numbers, so
 * every success message here stays deliberately neutral ("if this number is
 * registered..."). The flow only advances to the password step after the OTP is
 * accepted, which is what actually proves the caller owns the number.
 */
@Composable
fun ForgotPasswordScreen(
    colors: AppColors,
    lang: String,
    onRequest: (mobile: String, onResult: (Boolean, String?, RateLimitInfo?) -> Unit) -> Unit,
    onReset: (mobile: String, otp: String, password: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    var step by remember { mutableStateOf("mobile") }
    var mobile by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var mobileError by remember { mutableStateOf(false) }
    var otpError by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }
    var confirmPasswordError by remember { mutableStateOf(false) }

    var isChecking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    // Server-driven rate-limit state, mirrored so the resend control can show a
    // live countdown instead of letting the user tap into a 429.
    var waitSeconds by remember { mutableIntStateOf(0) }
    var isLocked by remember { mutableStateOf(false) }
    var remainingSends by remember { mutableIntStateOf(-1) }

    val isWaiting = waitSeconds > 0
    val lockedActive = isLocked && waitSeconds > 0

    LaunchedEffect(isWaiting) {
        while (waitSeconds > 0) {
            delay(1000)
            waitSeconds -= 1
        }
    }
    LaunchedEffect(waitSeconds) {
        if (waitSeconds == 0) {
            isLocked = false
            remainingSends = -1
        }
    }

    fun applyRateLimit(limit: RateLimitInfo?) {
        if (limit == null) return
        waitSeconds = limit.retryAfterSeconds
        isLocked = limit.locked
        if (limit.remainingSends >= 0) remainingSends = limit.remainingSends
    }

    fun requestOtp(isResend: Boolean) {
        isChecking = true
        message = null
        isError = false
        onRequest(mobile.trim()) { success, msg, limit ->
            isChecking = false
            applyRateLimit(limit)
            if (success) {
                step = "otp"
                otp = ""
                otpError = false
                message = msg
                isError = false
            } else {
                // A throttle is not an input error: keep the number field clean and
                // let the countdown explain the failure.
                if (limit == null && !isResend) mobileError = true
                isError = true
                message = msg ?: AppStrings.get(lang, "Could not send OTP. Please try again.")
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = AppStrings.get(lang, "Back"),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        AppStrings.get(lang, "Forgot Password"),
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary
                    )
                    Text(
                        AppStrings.get(
                            lang,
                            when (step) {
                                "mobile" -> "Enter the mobile number registered to your account"
                                "otp" -> "Enter the OTP sent to your mobile"
                                else -> "Choose a new password"
                            }
                        ),
                        fontSize = 12.sp, color = colors.textSecondary
                    )
                }
            }

            val focusManager = LocalFocusManager.current
            when (step) {
                "mobile" -> OutlinedTextField(
                    value = mobile,
                    onValueChange = {
                        mobile = it.filter { ch -> ch.isDigit() }.take(10)
                        mobileError = false
                        message = null
                        isError = false
                    },
                    label = { Text(AppStrings.get(lang, "Mobile Number"), color = colors.textSecondary) },
                    placeholder = { Text("9876543210", color = colors.textSecondary.copy(alpha = 0.4f)) },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                    isError = mobileError,
                    supportingText = if (mobileError) {{
                        Text(AppStrings.get(lang, "Enter the 10-digit registered mobile number"), color = colors.error)
                    }} else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.clearFocus() }),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                        focusedLabelColor = colors.primary, cursorColor = colors.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                "otp" -> OutlinedTextField(
                    value = otp,
                    onValueChange = {
                        otp = it.filter { ch -> ch.isDigit() }.take(6)
                        otpError = false
                        message = null
                        isError = false
                    },
                    label = { Text(AppStrings.get(lang, "OTP"), color = colors.textSecondary) },
                    placeholder = { Text("6-digit OTP", color = colors.textSecondary.copy(alpha = 0.4f)) },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                    isError = otpError,
                    supportingText = if (otpError) {{
                        Text(AppStrings.get(lang, "Enter the 6-digit OTP"), color = colors.error)
                    }} else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.clearFocus() }),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                        focusedLabelColor = colors.primary, cursorColor = colors.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                else -> {
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            passwordError = false
                            confirmPasswordError = false
                            message = null
                            isError = false
                        },
                        label = { Text(AppStrings.get(lang, "New Password"), color = colors.textSecondary) },
                        placeholder = { Text(AppStrings.get(lang, "Enter your password"), color = colors.textSecondary.copy(alpha = 0.4f)) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) AppStrings.get(lang, "Hide password") else AppStrings.get(lang, "Show password"),
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        isError = passwordError,
                        supportingText = if (passwordError) {{
                            Text(AppStrings.get(lang, "Use 8+ characters with upper, lower, number & symbol"), color = colors.error)
                        }} else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.clearFocus() }),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                            focusedLabelColor = colors.primary, cursorColor = colors.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            confirmPasswordError = false
                            message = null
                            isError = false
                        },
                        label = { Text(AppStrings.get(lang, "Confirm Password"), color = colors.textSecondary) },
                        placeholder = { Text(AppStrings.get(lang, "Re-enter your password"), color = colors.textSecondary.copy(alpha = 0.4f)) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        isError = confirmPasswordError,
                        supportingText = if (confirmPasswordError) {{
                            Text(AppStrings.get(lang, "Passwords do not match"), color = colors.error)
                        }} else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                            focusedLabelColor = colors.primary, cursorColor = colors.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            if (!message.isNullOrBlank()) {
                Text(
                    text = message!!,
                    fontSize = 12.sp,
                    color = if (isError) colors.error else colors.primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = {
                    when (step) {
                        "mobile" -> {
                            mobileError = mobile.length != 10
                            if (!mobileError) requestOtp(isResend = false)
                        }
                        "otp" -> {
                            otpError = otp.length != 6
                            if (!otpError) {
                                step = "password"
                                message = null
                                isError = false
                            }
                        }
                        else -> {
                            passwordError = !isStrongPassword(password)
                            confirmPasswordError = confirmPassword != password || confirmPassword.isEmpty()
                            if (!passwordError && !confirmPasswordError) {
                                isChecking = true
                                message = null
                                isError = false
                                onReset(mobile.trim(), otp, password) { success, msg ->
                                    isChecking = false
                                    if (success) {
                                        onDone()
                                    } else {
                                        isError = true
                                        message = msg ?: AppStrings.get(lang, "Could not reset password. Please try again.")
                                    }
                                }
                            }
                        }
                    }
                },
                enabled = !isChecking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.background,
                    disabledContainerColor = colors.primary.copy(alpha = 0.35f),
                    disabledContentColor = colors.background.copy(alpha = 0.7f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = colors.background, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        AppStrings.get(
                            lang,
                            when (step) {
                                "mobile" -> "SEND OTP"
                                "otp" -> "VERIFY OTP"
                                else -> "SAVE NEW PASSWORD"
                            }
                        ),
                        fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp
                    )
                }
            }

            // Resend lives on the OTP step — where a user who never received the
            // code actually is. Verifying is never blocked by the send limits.
            if (step == "otp") {
                OutlinedButton(
                    onClick = { if (!isChecking && !isWaiting) requestOtp(isResend = true) },
                    enabled = !isChecking && !isWaiting,
                    border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colors.primary,
                        disabledContentColor = colors.textSecondary.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colors.primary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            AppStrings.get(lang, "RESEND OTP"),
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            if (step != "password" && isWaiting) {
                Text(
                    text = if (lockedActive) {
                        String.format(AppStrings.get(lang, "Too many attempts. Try again in %s"), formatCountdown(waitSeconds))
                    } else {
                        String.format(AppStrings.get(lang, "Resend OTP in %s"), formatCountdown(waitSeconds))
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (lockedActive) colors.error else colors.textSecondary,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (step == "otp" && remainingSends in 1..2) {
                Text(
                    text = String.format(AppStrings.get(lang, "%d resend(s) left before a 10 minute lockout"), remainingSends),
                    fontSize = 12.sp,
                    color = colors.error,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Step changes keep the typed values, so a wrong move is one tap to undo.
            if (step == "otp") {
                TextButton(onClick = { step = "mobile"; message = null; isError = false }) {
                    Text(AppStrings.get(lang, "Change mobile number"), fontSize = 12.sp, color = colors.textSecondary)
                }
            } else if (step == "password") {
                TextButton(onClick = { step = "otp"; message = null; isError = false }) {
                    Text(AppStrings.get(lang, "Change OTP"), fontSize = 12.sp, color = colors.textSecondary)
                }
            }
        }
    }
}

/** Same rule the registration form enforces, kept in one place per screen. */
private fun isStrongPassword(pw: String): Boolean {
    if (pw.length < 8) return false
    if (!pw.any { it.isUpperCase() }) return false
    if (!pw.any { it.isLowerCase() }) return false
    if (!pw.any { it.isDigit() }) return false
    if (!pw.any { !it.isLetterOrDigit() }) return false
    return true
}

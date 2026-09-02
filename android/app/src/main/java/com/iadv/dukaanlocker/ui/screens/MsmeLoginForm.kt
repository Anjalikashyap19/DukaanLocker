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
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.theme.AppColors

@Composable
fun MsmeLoginForm(
    colors: AppColors,
    lang: String,
    onMsmeLoginRequest: (msmeNumber: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onMsmeLoginVerify: (msmeNumber: String, otp: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    var step by remember { mutableStateOf("number") }
    var msmeNumber by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var numberError by remember { mutableStateOf(false) }
    var otpError by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

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
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        AppStrings.get(lang, "MSME Login"),
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary
                    )
                    Text(
                        AppStrings.get(lang, if (step == "number") "Enter your Udyam number" else "Enter the OTP sent to your mobile"),
                        fontSize = 12.sp, color = colors.textSecondary
                    )
                }
            }

            val focusManager = LocalFocusManager.current
            if (step == "number") {
                OutlinedTextField(
                    value = msmeNumber,
                    onValueChange = { msmeNumber = it.filter { ch -> ch.isLetterOrDigit() || ch == '-' }.uppercase(); numberError = false; message = null },
                    label = { Text(AppStrings.get(lang, "MSME / Udyam Number"), color = colors.textSecondary) },
                    placeholder = { Text("UDYAM-XX-XX-XXXXXXX", color = colors.textSecondary.copy(alpha = 0.4f)) },
                    leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                    isError = numberError,
                    supportingText = if (numberError) {{
                        Text(AppStrings.get(lang, "Enter a valid Udyam number (e.g. UDYAM-UP-09-0001234)"), color = colors.error)
                    }} else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.clearFocus() }
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                        focusedLabelColor = colors.primary, cursorColor = colors.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            } else {
                OutlinedTextField(
                    value = otp,
                    onValueChange = { otp = it.filter { ch -> ch.isDigit() }.take(8); otpError = false; message = null },
                    label = { Text(AppStrings.get(lang, "OTP"), color = colors.textSecondary) },
                    placeholder = { Text("6-digit OTP", color = colors.textSecondary.copy(alpha = 0.4f)) },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                    isError = otpError,
                    supportingText = if (otpError) {{
                        Text(AppStrings.get(lang, "Enter the 6-digit OTP"), color = colors.error)
                    }} else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (otp.length >= 4) {
                                isChecking = true
                                message = null
                                onMsmeLoginVerify(msmeNumber.trim().uppercase(), otp) { success, msg ->
                                    isChecking = false
                                    if (!success) {
                                        otpError = true
                                        message = msg ?: AppStrings.get(lang, "Invalid OTP. Please try again.")
                                    }
                                }
                            }
                        }
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                        focusedLabelColor = colors.primary, cursorColor = colors.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            if (!message.isNullOrBlank()) {
                Text(
                    text = message!!,
                    fontSize = 12.sp,
                    color = if (otpError || numberError) colors.error else colors.primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = {
                    if (step == "number") {
                        numberError = !isValidMsme(msmeNumber)
                        if (!numberError) {
                            isChecking = true
                            message = null
                            onMsmeLoginRequest(msmeNumber.trim().uppercase()) { success, msg ->
                                isChecking = false
                                if (success) {
                                    step = "otp"
                                    message = msg
                                } else {
                                    numberError = true
                                    message = msg ?: AppStrings.get(lang, "Could not send OTP. Please try again.")
                                }
                            }
                        }
                    } else {
                        otpError = otp.length < 4
                        if (!otpError) {
                            isChecking = true
                            message = null
                            onMsmeLoginVerify(msmeNumber.trim().uppercase(), otp) { success, msg ->
                                isChecking = false
                                if (!success) {
                                    otpError = true
                                    message = msg ?: AppStrings.get(lang, "Invalid OTP. Please try again.")
                                }
                            }
                        }
                    }
                },
                enabled = !isChecking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.background)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = colors.background, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Business, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        AppStrings.get(lang, if (step == "number") "SEND OTP" else "VERIFY & LOGIN"),
                        fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp
                    )
                }
            }

            if (step == "otp") {
                TextButton(onClick = {
                    step = "number"
                    otp = ""
                    otpError = false
                    message = null
                }) {
                    Text(AppStrings.get(lang, "Change Udyam number"), fontSize = 12.sp, color = colors.textSecondary)
                }
            }
        }
    }
}

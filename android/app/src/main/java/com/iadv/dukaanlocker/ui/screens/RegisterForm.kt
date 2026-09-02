package com.iadv.dukaanlocker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.theme.AppColors

@Composable
fun RegisterForm(
    colors: AppColors,
    lang: String,
    name: String, onNameChange: (String) -> Unit, nameError: Boolean,
    email: String, onEmailChange: (String) -> Unit, emailError: Boolean,
    password: String, onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean, onTogglePasswordVisible: () -> Unit,
    passwordError: Boolean, passwordStrength: PasswordStrength,
    mobile: String, onMobileChange: (String) -> Unit, mobileError: Boolean,
    registerWithMsme: Boolean, onToggleRegisterWithMsme: () -> Unit,
    msmeNumber: String, onMsmeNumberChange: (String) -> Unit, msmeNumberError: Boolean,
    captchaCode: String, isGovCaptcha: Boolean = false, onRefreshCaptcha: () -> Unit,
    captchaInput: String, msmeCaptchaLoading: Boolean = false, onCaptchaInputChange: (String) -> Unit, captchaError: Boolean,
    isChecking: Boolean,
    onBack: () -> Unit,
    onRegister: () -> Unit,
    onGoogleSignIn: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
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
                        AppStrings.get(lang, if (registerWithMsme) "MSME Registration" else "Create Account"),
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary
                    )
                    Text(
                        AppStrings.get(lang, if (registerWithMsme) "Verify your Udyam registration" else "Set up your business document vault"),
                        fontSize = 12.sp, color = colors.textSecondary
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.primary.copy(alpha = 0.06f))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Business,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        AppStrings.get(lang, "Have MSME Number?"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Text(
                        AppStrings.get(lang, "Register with your Udyam registration"),
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
                Switch(
                    checked = registerWithMsme,
                    onCheckedChange = { onToggleRegisterWithMsme() },
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.primary)
                )
            }

            val focusManager = LocalFocusManager.current
            if (registerWithMsme) {
                OutlinedTextField(
                    value = msmeNumber,
                    onValueChange = { newValue ->
                        val filtered = newValue.filter { ch -> ch.isLetterOrDigit() || ch == '-' }.uppercase()
                        onMsmeNumberChange(filtered)
                    },
                    label = { Text(AppStrings.get(lang, "MSME / Udyam Number"), color = colors.textSecondary) },
                    placeholder = { Text("UDYAM-XX-XX-XXXXXXX", color = colors.textSecondary.copy(alpha = 0.4f)) },
                    leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                    isError = msmeNumberError,
                    supportingText = if (msmeNumberError) {{
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
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text(AppStrings.get(lang, "Full Name"), color = colors.textSecondary) },
                    placeholder = { Text("Ramesh Sharma", color = colors.textSecondary.copy(alpha = 0.4f)) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                    isError = nameError,
                    supportingText = if (nameError) {{ Text(AppStrings.get(lang, "Name is required"), color = colors.error) }} else null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
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
            }

            OutlinedTextField(
                value = mobile,
                onValueChange = onMobileChange,
                label = { Text(AppStrings.get(lang, "Mobile Number"), color = colors.textSecondary) },
                placeholder = { Text("9876543210", color = colors.textSecondary.copy(alpha = 0.4f)) },
                leadingIcon = {
                    Text(
                        text = "+91",
                        color = colors.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                },
                isError = mobileError,
                supportingText = if (mobileError) {{ Text(AppStrings.get(lang, "Enter a valid 10-digit mobile number"), color = colors.error) }} else null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
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

            if (registerWithMsme) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = captchaInput,
                        onValueChange = onCaptchaInputChange,
                        label = { Text(AppStrings.get(lang, "Enter CAPTCHA"), color = colors.textSecondary) },
                        leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                        isError = captchaError,
                        supportingText = if (captchaError) {{
                            Text(AppStrings.get(lang, "Captcha doesn't match"), color = colors.error)
                        }} else null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary, unfocusedBorderColor = colors.border,
                            focusedLabelColor = colors.primary, cursorColor = colors.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (msmeCaptchaLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.primary.copy(alpha = 0.07f))
                                .border(1.dp, colors.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colors.primary, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Loading government captcha...", fontSize = 12.sp, color = colors.textSecondary)
                            }
                        }
                    } else if (isGovCaptcha && (captchaCode.contains("data:image") || captchaCode.startsWith("http"))) {
                        GovCaptchaBox(
                            captchaBase64 = captchaCode,
                            colors = colors,
                            lang = lang,
                            onRefresh = onRefreshCaptcha
                        )
                    } else {
                        CaptchaBox(code = captchaCode, colors = colors, lang = lang, onRefresh = onRefreshCaptcha)
                    }
                }
            } else {
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = { Text(AppStrings.get(lang, "Email Address"), color = colors.textSecondary) },
                    placeholder = { Text("you@example.com", color = colors.textSecondary.copy(alpha = 0.4f)) },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                    isError = emailError,
                    supportingText = if (emailError) {{ Text(AppStrings.get(lang, "Enter a valid email address"), color = colors.error) }} else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        capitalization = KeyboardCapitalization.None,
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
            }

            if (!registerWithMsme) {
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(AppStrings.get(lang, "Password"), color = colors.textSecondary) },
                placeholder = { Text(AppStrings.get(lang, "8+ chars, upper, lower, digit, special"), color = colors.textSecondary.copy(alpha = 0.4f)) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    IconButton(onClick = onTogglePasswordVisible, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                isError = passwordError,
                supportingText = {
                    if (passwordError) {
                        Text(AppStrings.get(lang, "Must be 8+ chars with uppercase, lowercase, digit & special char"), color = colors.error)
                    } else if (password.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            ) {
                                repeat(4) { index ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(
                                                if (index < passwordStrength.level) passwordStrength.color
                                                else colors.border.copy(alpha = 0.4f)
                                            )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                AppStrings.get(lang, passwordStrength.label),
                                fontSize = 11.sp,
                                color = passwordStrength.color,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (name.isNotBlank() && email.isNotBlank() && password.length >= 8 && mobile.length == 10) {
                            onRegister()
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

            Spacer(modifier = Modifier.height(2.dp))

            Button(
                onClick = onRegister,
                enabled = if (registerWithMsme) {
                    msmeNumber.isNotBlank() && mobile.length == 10 &&
                        captchaInput.isNotBlank() && !isChecking
                } else {
                    name.isNotBlank() && email.isNotBlank() && password.length >= 8 && mobile.length == 10 && !isChecking
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.background)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = colors.background, strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (registerWithMsme) Icons.Default.Business else Icons.Default.PersonAdd,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        AppStrings.get(lang, if (registerWithMsme) "REGISTER WITH MSME" else "CREATE VAULT"),
                        fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

package com.iadv.dukaanlocker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
fun OwnerLoginForm(
    colors: AppColors,
    lang: String,
    email: String, onEmailChange: (String) -> Unit, emailError: Boolean,
    password: String, onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean, onTogglePasswordVisible: () -> Unit,
    passwordError: Boolean,
    isChecking: Boolean,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onMsmeLoginRequest: (msmeNumber: String, onResult: (Boolean, String?) -> Unit) -> Unit = { _, _ -> },
    onMsmeLoginVerify: (msmeNumber: String, otp: String, onResult: (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> }
) {
    var msmeMode by remember { mutableStateOf(false) }
    if (msmeMode) {
        MsmeLoginForm(
            colors = colors,
            lang = lang,
            onMsmeLoginRequest = onMsmeLoginRequest,
            onMsmeLoginVerify = onMsmeLoginVerify,
            onBack = { msmeMode = false }
        )
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = AppStrings.get(lang, "Back"), tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(AppStrings.get(lang, "Owner Sign In"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text(AppStrings.get(lang, "Access your business dashboard"), fontSize = 12.sp, color = colors.textSecondary)
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
                        AppStrings.get(lang, "Have a Udyam number?"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Text(
                        AppStrings.get(lang, "Sign in with Udyam number & OTP"),
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
                Switch(
                    checked = msmeMode,
                    onCheckedChange = { msmeMode = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.primary)
                )
            }

            val focusManager = LocalFocusManager.current
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

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(AppStrings.get(lang, "Password"), color = colors.textSecondary) },
                placeholder = { Text(AppStrings.get(lang, "Enter your password"), color = colors.textSecondary.copy(alpha = 0.4f)) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    IconButton(onClick = onTogglePasswordVisible, modifier = Modifier.size(32.dp)) {
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
                supportingText = if (passwordError) {{ Text(AppStrings.get(lang, "Password is required"), color = colors.error) }} else null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (email.isNotBlank() && password.isNotBlank()) {
                            onLogin()
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

            Button(
                onClick = onLogin,
                enabled = email.isNotBlank() && password.isNotBlank() && !isChecking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.background)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = colors.background, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Login, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(AppStrings.get(lang, "SECURE ACCESS"), fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp)
                }
            }
        }
    }
}

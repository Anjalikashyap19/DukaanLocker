package com.iadv.dukaanlocker.ui.screens

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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.api.RateLimitInfo
import com.iadv.dukaanlocker.ui.components.FieldErrorText
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.theme.AppColors
import com.iadv.dukaanlocker.ui.theme.NotoSans

@Composable
fun OwnerLoginForm(
    colors: AppColors,
    lang: String,
    email: String, onEmailChange: (String) -> Unit, emailError: Boolean,
    password: String, onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean, onTogglePasswordVisible: () -> Unit,
    passwordError: Boolean,
    isChecking: Boolean,
    lockoutRemainingMs: Long = 0L,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onForgotPassword: () -> Unit = {},
    onMsmeLoginRequest: (msmeNumber: String, onResult: (Boolean, String?, RateLimitInfo?) -> Unit) -> Unit = { _, _ -> },
    onMsmeLoginVerify: (msmeNumber: String, otp: String, onResult: (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> }
) {
    val focusManager = LocalFocusManager.current
    var msmeMode by remember { mutableStateOf(false) }
    if (msmeMode) {
        MsmeLoginForm(
            colors = colors,
            lang = lang,
            onMsmeLoginRequest = onMsmeLoginRequest,
            onMsmeLoginVerify = onMsmeLoginVerify,
            onBack = {
                focusManager.clearFocus(force = true)
                msmeMode = false
            },
            scrollable = false
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ── Header: back + title ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = AppStrings.get(lang, "Back"),
                    modifier = Modifier.size(20.dp),
                    tint = colors.textSecondary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    AppStrings.get(lang, "Owner Sign In"),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    AppStrings.get(lang, "Access your business dashboard"),
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // ── MSME toggle (subtle tinted surface) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.primary.copy(alpha = 0.06f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Business,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    AppStrings.get(lang, "Have a Udyam number?"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Text(
                    AppStrings.get(lang, "Sign in with Udyam number & OTP"),
                    fontSize = 10.sp,
                    color = colors.textSecondary
                )
            }
            Switch(
                checked = msmeMode,
                onCheckedChange = { newValue ->
                    focusManager.clearFocus(force = true)
                    msmeMode = newValue
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.primary,
                    checkedThumbColor = Color.White
                )
            )
        }

        UiTextField(
            label = AppStrings.get(lang, "Email Address"),
            placeholder = "you@example.com",
            value = email,
            onValueChange = onEmailChange,
            leadingIcon = Icons.Default.Email,
            isError = emailError,
            errorText = if (emailError) AppStrings.get(lang, "Enter a valid email address") else null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.clearFocus(force = true) }),
            colors = colors
        )

        UiTextField(
            label = AppStrings.get(lang, "Password"),
            placeholder = AppStrings.get(lang, "Enter your password"),
            value = password,
            onValueChange = onPasswordChange,
            leadingIcon = Icons.Default.Lock,
            trailingIcon = {
                IconButton(onClick = onTogglePasswordVisible, modifier = Modifier.size(48.dp)) {
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
            errorText = if (passwordError) AppStrings.get(lang, "Password is required") else null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus(force = true)
                    if (email.isNotBlank() && password.isNotBlank()) {
                        onLogin()
                    }
                }
            ),
            colors = colors
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onForgotPassword,
                modifier = Modifier.padding(top = 4.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = colors.primary)
            ) {
                Text(
                    AppStrings.get(lang, "forgot password?"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.primary
                )
            }
        }

        LoginLockoutBanner(colors = colors, remainingMs = lockoutRemainingMs)

        Button(
            onClick = onLogin,
            enabled = email.isNotBlank() && password.isNotBlank() && !isChecking && lockoutRemainingMs <= 0L,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.background,
                disabledContainerColor = colors.primary.copy(alpha = 0.4f),
                disabledContentColor = colors.background.copy(alpha = 0.6f)
            )
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = colors.background,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    AppStrings.get(lang, "SECURE ACCESS"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

/** ── Reusable input field (shared with RegisterForm) ── */
@Composable
private fun UiTextField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    errorText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(),
    keyboardActions: KeyboardActions = KeyboardActions(),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    colors: AppColors,
    modifier: Modifier = Modifier
) {
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = if (isError) colors.error else colors.primary,
        unfocusedBorderColor = if (isError) colors.error.copy(alpha = 0.5f) else colors.border,
        focusedLabelColor = if (isError) colors.error else colors.primary,
        unfocusedLabelColor = colors.textSecondary,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        cursorColor = colors.primary
    )

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp, color = colors.textSecondary) },
        placeholder = { Text(placeholder, fontSize = 12.sp, color = colors.textSecondary.copy(alpha = 0.4f)) },
        leadingIcon = leadingIcon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (isError) colors.error else colors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        trailingIcon = trailingIcon,
        isError = isError,
        supportingText = if (isError && errorText != null) {
            { FieldErrorText(text = errorText, colors = colors) }
        } else {
            null
        },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        colors = textFieldColors,
        shape = RoundedCornerShape(12.dp),
        textStyle = TextStyle(
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = NotoSans,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Normal
        )
    )
}
